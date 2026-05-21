package com.secondream.keeper.data.drive

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks to Google Drive REST API v3 using the user's Google account.
 *
 * Auth: GoogleAuthUtil.getToken with scope "drive.file".
 * drive.file = the app can only see/edit files it creates itself.
 * This avoids the OAuth verification process required for full Drive scope.
 *
 * The user must have:
 *  - A Google account installed on the device
 *  - An OAuth Android client registered on Google Cloud Console
 *    with package=com.secondream.keeper and SHA-1 matching the signing keystore
 */
class DriveSync(private val context: Context) {

    companion object {
        private const val SCOPE_PREFIX = "oauth2:"
        const val DRIVE_FILE_SCOPE = "${SCOPE_PREFIX}https://www.googleapis.com/auth/drive.file"

        private const val API_BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"

        const val ROOT_FOLDER_NAME = "Keeper"
        const val NOTE_JSON_NAME = "note.json"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val JSON_MIME = "application/json"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class NeedsUserAction(val intent: android.content.Intent) : Result<Nothing>()
        data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    }

    /**
     * Get an OAuth access token for the given Google account, scope drive.file.
     * Throws UserRecoverableAuthException when first-time consent is required:
     * caller must launch the intent to prompt the user.
     */
    @Throws(Exception::class)
    private suspend fun token(accountName: String): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, accountName, DRIVE_FILE_SCOPE)
    }

    /**
     * Find the root "Keeper" folder in Drive, or create it if it doesn't exist.
     * Returns its Drive ID.
     */
    suspend fun ensureRootFolder(accountName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = token(accountName)
            val existing = findFolderByName(accessToken, ROOT_FOLDER_NAME, parentId = null)
            if (existing != null) return@withContext Result.Success(existing)
            val created = createFolder(accessToken, ROOT_FOLDER_NAME, parentId = null)
            if (created != null) Result.Success(created)
            else Result.Error("Impossibile creare la cartella Keeper su Drive")
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsUserAction(it) } ?: Result.Error("Autorizzazione necessaria senza intent recuperabile")
        } catch (e: Exception) {
            Result.Error("Errore connessione Drive: ${e.message}", e)
        }
    }

    /**
     * Find or create a sub-folder for one note under the root folder.
     */
    suspend fun ensureNoteFolder(
        accountName: String,
        rootFolderId: String,
        noteFolderName: String,
        existingFolderId: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = token(accountName)

            // If we have a known folder ID, verify it still exists and rename if title changed
            if (!existingFolderId.isNullOrBlank()) {
                val verified = verifyFolderExists(accessToken, existingFolderId)
                if (verified) {
                    renameFile(accessToken, existingFolderId, noteFolderName)
                    return@withContext Result.Success(existingFolderId)
                }
            }

            // Else create fresh
            val created = createFolder(accessToken, noteFolderName, parentId = rootFolderId)
            if (created != null) Result.Success(created)
            else Result.Error("Impossibile creare cartella della nota su Drive")
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsUserAction(it) } ?: Result.Error("Autorizzazione necessaria senza intent recuperabile")
        } catch (e: Exception) {
            Result.Error("Errore Drive: ${e.message}", e)
        }
    }

    /**
     * Upload (or overwrite) a small text file (note.json) into a folder.
     * If a file with the same name exists, it's overwritten.
     */
    suspend fun upsertJsonFile(
        accountName: String,
        parentFolderId: String,
        fileName: String,
        jsonContent: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = token(accountName)
            val existing = findFileInFolder(accessToken, parentFolderId, fileName)
            if (existing != null) {
                val ok = updateFileContent(accessToken, existing, jsonContent.toByteArray(Charsets.UTF_8), JSON_MIME)
                if (ok) Result.Success(existing)
                else Result.Error("Impossibile aggiornare $fileName su Drive")
            } else {
                val created = createFileWithContent(
                    accessToken = accessToken,
                    name = fileName,
                    parentId = parentFolderId,
                    mimeType = JSON_MIME,
                    bytes = jsonContent.toByteArray(Charsets.UTF_8)
                )
                if (created != null) Result.Success(created)
                else Result.Error("Impossibile creare $fileName su Drive")
            }
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsUserAction(it) } ?: Result.Error("Autorizzazione necessaria senza intent recuperabile")
        } catch (e: Exception) {
            Result.Error("Errore upload note.json: ${e.message}", e)
        }
    }

    /**
     * Upload a binary attachment (image/video/doc) to the note folder using resumable upload.
     * Skips if a file with same name already exists in folder.
     */
    suspend fun uploadAttachment(
        accountName: String,
        parentFolderId: String,
        file: File,
        targetFileName: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.Error("File locale non trovato: ${file.absolutePath}")
            val accessToken = token(accountName)
            val existing = findFileInFolder(accessToken, parentFolderId, targetFileName)
            if (existing != null) {
                // already uploaded once, skip
                return@withContext Result.Success(existing)
            }
            val id = uploadResumable(accessToken, parentFolderId, file, targetFileName, mimeType)
            if (id != null) Result.Success(id)
            else Result.Error("Upload fallito per $targetFileName")
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsUserAction(it) } ?: Result.Error("Autorizzazione necessaria senza intent recuperabile")
        } catch (e: Exception) {
            Result.Error("Errore upload allegato: ${e.message}", e)
        }
    }

    /**
     * Delete a folder (and its contents) by ID.
     */
    suspend fun deleteResource(accountName: String, resourceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val accessToken = token(accountName)
            val request = Request.Builder()
                .url("$API_BASE/files/$resourceId")
                .delete()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 404) Result.Success(true)
                else Result.Error("Errore eliminazione (HTTP ${resp.code})")
            }
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsUserAction(it) } ?: Result.Error("Autorizzazione necessaria senza intent recuperabile")
        } catch (e: Exception) {
            Result.Error("Errore eliminazione: ${e.message}", e)
        }
    }

    /**
     * List names of all files in a Drive folder. Used to skip already-uploaded attachments.
     */
    suspend fun listFolderContents(accountName: String, folderId: String): Result<List<DriveEntry>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = token(accountName)
            val q = URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
            val request = Request.Builder()
                .url("$API_BASE/files?q=$q&fields=files(id,name,mimeType)&pageSize=200")
                .get()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Error("List failed: HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val arr = JSONObject(body).optJSONArray("files") ?: JSONArray()
                val out = mutableListOf<DriveEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(DriveEntry(o.getString("id"), o.getString("name"), o.getString("mimeType")))
                }
                Result.Success(out)
            }
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsUserAction(it) } ?: Result.Error("Autorizzazione necessaria senza intent recuperabile")
        } catch (e: Exception) {
            Result.Error("Errore listing: ${e.message}", e)
        }
    }

    /**
     * Download a Drive file's content as a String (for small text files like note.json).
     */
    suspend fun downloadFileAsString(accountName: String, fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = token(accountName)
            val request = Request.Builder()
                .url("$API_BASE/files/$fileId?alt=media")
                .get()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Error("Download fallito: HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                Result.Success(body)
            }
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsUserAction(it) } ?: Result.Error("Autorizzazione necessaria senza intent recuperabile")
        } catch (e: Exception) {
            Result.Error("Errore download stringa: ${e.message}", e)
        }
    }

    /**
     * Download a Drive file's binary content to a local File on disk (for attachments).
     */
    suspend fun downloadFileToLocal(accountName: String, fileId: String, destination: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val accessToken = token(accountName)
            val request = Request.Builder()
                .url("$API_BASE/files/$fileId?alt=media")
                .get()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Error("Download fallito: HTTP ${resp.code}")
                val sink = destination.outputStream()
                sink.use { out ->
                    resp.body?.byteStream()?.copyTo(out)
                }
                Result.Success(destination)
            }
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsUserAction(it) } ?: Result.Error("Autorizzazione necessaria senza intent recuperabile")
        } catch (e: Exception) {
            Result.Error("Errore download file: ${e.message}", e)
        }
    }

    // ─────────────────────────────────────── private helpers ───────────────────────────────────────

    private fun findFolderByName(accessToken: String, name: String, parentId: String?): String? {
        val escaped = name.replace("'", "\\'")
        val parentClause = if (parentId != null) " and '$parentId' in parents" else ""
        val qRaw = "name = '$escaped' and mimeType = '$FOLDER_MIME' and trashed = false$parentClause"
        val q = URLEncoder.encode(qRaw, "UTF-8")
        val request = Request.Builder()
            .url("$API_BASE/files?q=$q&fields=files(id,name)&pageSize=1")
            .get()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            val files = JSONObject(body).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).getString("id")
        }
    }

    private fun verifyFolderExists(accessToken: String, folderId: String): Boolean {
        val request = Request.Builder()
            .url("$API_BASE/files/$folderId?fields=id,trashed")
            .get()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val body = resp.body?.string().orEmpty()
            val trashed = JSONObject(body).optBoolean("trashed", false)
            return !trashed
        }
    }

    private fun createFolder(accessToken: String, name: String, parentId: String?): String? {
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            if (parentId != null) put("parents", JSONArray().apply { put(parentId) })
        }
        val request = Request.Builder()
            .url("$API_BASE/files?fields=id")
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            return JSONObject(body).optString("id").takeIf { it.isNotBlank() }
        }
    }

    private fun renameFile(accessToken: String, fileId: String, newName: String) {
        val metadata = JSONObject().apply { put("name", newName) }
        val request = Request.Builder()
            .url("$API_BASE/files/$fileId")
            .patch(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(request).execute().close()
    }

    private fun findFileInFolder(accessToken: String, folderId: String, fileName: String): String? {
        val escaped = fileName.replace("'", "\\'")
        val qRaw = "name = '$escaped' and '$folderId' in parents and trashed = false"
        val q = URLEncoder.encode(qRaw, "UTF-8")
        val request = Request.Builder()
            .url("$API_BASE/files?q=$q&fields=files(id,name)&pageSize=1")
            .get()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            val files = JSONObject(body).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).getString("id")
        }
    }

    private fun createFileWithContent(
        accessToken: String,
        name: String,
        parentId: String,
        mimeType: String,
        bytes: ByteArray
    ): String? {
        // Two-step: create metadata, then upload content via media upload
        val metadata = JSONObject().apply {
            put("name", name)
            put("parents", JSONArray().apply { put(parentId) })
            put("mimeType", mimeType)
        }
        val createReq = Request.Builder()
            .url("$API_BASE/files?fields=id")
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        val fileId: String = client.newCall(createReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            JSONObject(body).optString("id").takeIf { it.isNotBlank() }
        } ?: return null

        val ok = updateFileContent(accessToken, fileId, bytes, mimeType)
        return if (ok) fileId else null
    }

    private fun updateFileContent(
        accessToken: String,
        fileId: String,
        bytes: ByteArray,
        mimeType: String
    ): Boolean {
        val request = Request.Builder()
            .url("$UPLOAD_BASE/files/$fileId?uploadType=media")
            .patch(bytes.toRequestBody(mimeType.toMediaType()))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(request).execute().use { resp ->
            return resp.isSuccessful
        }
    }

    /**
     * Resumable upload for arbitrary-size files.
     * Step 1: POST to /upload/drive/v3/files?uploadType=resumable with metadata → get session URL from Location header
     * Step 2: PUT raw bytes to that session URL
     */
    private fun uploadResumable(
        accessToken: String,
        parentFolderId: String,
        file: File,
        targetFileName: String,
        mimeType: String
    ): String? {
        val metadata = JSONObject().apply {
            put("name", targetFileName)
            put("parents", JSONArray().apply { put(parentFolderId) })
            put("mimeType", mimeType)
        }
        val initReq = Request.Builder()
            .url("$UPLOAD_BASE/files?uploadType=resumable")
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("X-Upload-Content-Type", mimeType)
            .addHeader("X-Upload-Content-Length", file.length().toString())
            .build()

        val sessionUrl: String = client.newCall(initReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.header("Location") ?: return null
        }

        val uploadReq = Request.Builder()
            .url(sessionUrl)
            .put(file.asRequestBody(mimeType.toMediaType()))
            .build()

        client.newCall(uploadReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            return JSONObject(body).optString("id").takeIf { it.isNotBlank() }
        }
    }

    data class DriveEntry(val id: String, val name: String, val mimeType: String)
}
