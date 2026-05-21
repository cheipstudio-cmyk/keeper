package com.secondream.keeper.data.drive

import android.content.Context
import android.net.Uri
import com.secondream.keeper.data.model.Attachment
import com.secondream.keeper.data.model.Note
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * High-level Drive sync orchestration.
 *
 * Per-note folder structure on Drive:
 *
 *   Keeper /
 *     note_{noteId}_{slugTitle} /
 *       note.json
 *       img_{attId}_{name}.jpg
 *       vid_{attId}_{name}.mp4
 *       doc_{attId}_{name}.pdf
 *
 * note.json contains the full Note metadata: title, content, color, checklist, labels,
 * attachment manifest (name + Drive filename), timestamps.
 */
class DriveSyncRepository(
    private val context: Context,
    private val drive: DriveSync = DriveSync(context)
) {

    /**
     * One-shot upload (or update) of a single note to Drive.
     * Returns the Drive folder ID (existing or newly created) on success, or null on failure.
     */
    sealed class SyncOutcome {
        data class Success(val folderId: String) : SyncOutcome()
        data class NeedsUserAction(val intent: android.content.Intent) : SyncOutcome()
        data class Failure(val message: String) : SyncOutcome()
    }

    suspend fun uploadNote(
        accountName: String,
        note: Note,
        rootFolderId: String
    ): SyncOutcome {
        val folderName = noteFolderName(note)
        val ensure = drive.ensureNoteFolder(
            accountName = accountName,
            rootFolderId = rootFolderId,
            noteFolderName = folderName,
            existingFolderId = note.driveFolderId
        )
        val folderId = when (ensure) {
            is DriveSync.Result.Success -> ensure.value
            is DriveSync.Result.NeedsUserAction -> return SyncOutcome.NeedsUserAction(ensure.intent)
            is DriveSync.Result.Error -> return SyncOutcome.Failure(ensure.message)
        }

        // 1. Upload note.json (always update — content may have changed)
        val attachments = note.getAttachments()
        val noteJson = buildNoteJson(note, attachments)
        val jsonResult = drive.upsertJsonFile(
            accountName = accountName,
            parentFolderId = folderId,
            fileName = DriveSync.NOTE_JSON_NAME,
            jsonContent = noteJson
        )
        when (jsonResult) {
            is DriveSync.Result.Success -> {}
            is DriveSync.Result.NeedsUserAction -> return SyncOutcome.NeedsUserAction(jsonResult.intent)
            is DriveSync.Result.Error -> return SyncOutcome.Failure(jsonResult.message)
        }

        // 2. Upload each attachment that exists locally and isn't already on Drive
        attachments.forEach { att ->
            val localFile = resolveLocalFile(att) ?: return@forEach
            val driveFileName = driveFileNameFor(att)
            val mimeType = mimeTypeFor(att)
            val uploadResult = drive.uploadAttachment(
                accountName = accountName,
                parentFolderId = folderId,
                file = localFile,
                targetFileName = driveFileName,
                mimeType = mimeType
            )
            when (uploadResult) {
                is DriveSync.Result.Success -> { /* ok */ }
                is DriveSync.Result.NeedsUserAction -> return SyncOutcome.NeedsUserAction(uploadResult.intent)
                is DriveSync.Result.Error -> {
                    // Don't abort the whole sync on one failed attachment, but log it
                    // Caller can retry the note's sync later
                    android.util.Log.w("DriveSync", "Allegato non caricato: ${att.name} - ${uploadResult.message}")
                }
            }
        }

        return SyncOutcome.Success(folderId)
    }

    /**
     * Delete a note's folder (and everything inside) from Drive.
     */
    suspend fun deleteNoteFolder(accountName: String, folderId: String): SyncOutcome {
        return when (val r = drive.deleteResource(accountName, folderId)) {
            is DriveSync.Result.Success -> SyncOutcome.Success(folderId)
            is DriveSync.Result.NeedsUserAction -> SyncOutcome.NeedsUserAction(r.intent)
            is DriveSync.Result.Error -> SyncOutcome.Failure(r.message)
        }
    }

    /**
     * Ensure the top-level Keeper folder exists, returning its ID.
     */
    suspend fun ensureRootFolder(accountName: String): SyncOutcome {
        return when (val r = drive.ensureRootFolder(accountName)) {
            is DriveSync.Result.Success -> SyncOutcome.Success(r.value)
            is DriveSync.Result.NeedsUserAction -> SyncOutcome.NeedsUserAction(r.intent)
            is DriveSync.Result.Error -> SyncOutcome.Failure(r.message)
        }
    }

    /**
     * Represents one note imported from Drive, with the binary attachments
     * already saved to local cache. Caller inserts/updates in local DB.
     */
    data class ImportedNote(
        val driveFolderId: String,
        val title: String,
        val content: String,
        val colorHex: String,
        val isPinned: Boolean,
        val isArchived: Boolean,
        val isTrashed: Boolean,
        val labels: String,
        val createdAt: Long,
        val updatedAt: Long,
        val checklistJson: String,
        val attachmentsJson: String
    )

    sealed class ImportOutcome {
        data class Success(val notes: List<ImportedNote>) : ImportOutcome()
        data class NeedsUserAction(val intent: android.content.Intent) : ImportOutcome()
        data class Failure(val message: String) : ImportOutcome()
    }

    /**
     * Import every note found under the Keeper root folder on Drive.
     * For each `note_*` subfolder:
     *  - Downloads `note.json` and parses metadata
     *  - Downloads each attachment binary to local cache
     *  - Returns the full set as ImportedNote objects (caller inserts into DB)
     */
    suspend fun importNotesFromDrive(
        accountName: String,
        rootFolderId: String,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): ImportOutcome {
        // 1. List all subfolders of root
        val rootList = when (val r = drive.listFolderContents(accountName, rootFolderId)) {
            is DriveSync.Result.Success -> r.value
            is DriveSync.Result.NeedsUserAction -> return ImportOutcome.NeedsUserAction(r.intent)
            is DriveSync.Result.Error -> return ImportOutcome.Failure(r.message)
        }
        val noteFolders = rootList.filter { it.mimeType == DriveSync.FOLDER_MIME && it.name.startsWith("note_") }
        if (noteFolders.isEmpty()) return ImportOutcome.Success(emptyList())

        val cacheDir = File(context.cacheDir, "drive_attachments").apply { mkdirs() }
        val imported = mutableListOf<ImportedNote>()

        noteFolders.forEachIndexed { index, folder ->
            onProgress(index + 1, noteFolders.size, "Importo: ${folder.name}")

            // 2. List the folder's contents
            val folderContents = when (val r = drive.listFolderContents(accountName, folder.id)) {
                is DriveSync.Result.Success -> r.value
                is DriveSync.Result.NeedsUserAction -> return ImportOutcome.NeedsUserAction(r.intent)
                is DriveSync.Result.Error -> {
                    android.util.Log.w("DriveSync", "Skip folder ${folder.name}: ${r.message}")
                    return@forEachIndexed
                }
            }

            // 3. Find note.json and download it
            val noteJsonEntry = folderContents.firstOrNull { it.name == DriveSync.NOTE_JSON_NAME }
            if (noteJsonEntry == null) {
                android.util.Log.w("DriveSync", "Skip folder ${folder.name}: no note.json")
                return@forEachIndexed
            }
            val noteJsonText = when (val r = drive.downloadFileAsString(accountName, noteJsonEntry.id)) {
                is DriveSync.Result.Success -> r.value
                is DriveSync.Result.NeedsUserAction -> return ImportOutcome.NeedsUserAction(r.intent)
                is DriveSync.Result.Error -> {
                    android.util.Log.w("DriveSync", "Skip ${folder.name}: cannot download note.json (${r.message})")
                    return@forEachIndexed
                }
            }

            // 4. Parse note.json
            val parsed = try {
                JSONObject(noteJsonText)
            } catch (e: Exception) {
                android.util.Log.w("DriveSync", "Skip ${folder.name}: invalid note.json")
                return@forEachIndexed
            }

            // 5. Download each attachment binary
            val attachmentsArr = parsed.optJSONArray("attachments") ?: JSONArray()
            val newAttachmentsArr = JSONArray()
            for (i in 0 until attachmentsArr.length()) {
                val attObj = attachmentsArr.getJSONObject(i)
                val attId = attObj.optString("id")
                val attType = attObj.optString("type")
                val originalName = attObj.optString("originalName")
                val driveFileName = attObj.optString("driveFileName")
                val sizeLabel = attObj.optString("size", "")

                // Find the corresponding file on Drive by name
                val driveAttEntry = folderContents.firstOrNull { it.name == driveFileName }
                if (driveAttEntry == null) {
                    android.util.Log.w("DriveSync", "Allegato non trovato: $driveFileName")
                    continue
                }

                val localFile = File(cacheDir, "${folder.id}_$driveFileName")
                if (!localFile.exists()) {
                    val r = drive.downloadFileToLocal(accountName, driveAttEntry.id, localFile)
                    when (r) {
                        is DriveSync.Result.Success -> { /* ok */ }
                        is DriveSync.Result.NeedsUserAction -> return ImportOutcome.NeedsUserAction(r.intent)
                        is DriveSync.Result.Error -> {
                            android.util.Log.w("DriveSync", "Skip allegato $driveFileName: ${r.message}")
                            continue
                        }
                    }
                }

                val newAtt = JSONObject().apply {
                    put("id", attId)
                    put("type", attType)
                    put("uri", "file://${localFile.absolutePath}")
                    put("name", originalName)
                    put("size", sizeLabel)
                }
                newAttachmentsArr.put(newAtt)
            }

            // 6. Build ImportedNote
            val checklistArr = parsed.optJSONArray("checklist") ?: JSONArray()
            val checklistJson = checklistArr.toString()
            imported.add(
                ImportedNote(
                    driveFolderId = folder.id,
                    title = parsed.optString("title", ""),
                    content = parsed.optString("content", ""),
                    colorHex = parsed.optString("colorHex", "#FFFFFF"),
                    isPinned = parsed.optBoolean("isPinned", false),
                    isArchived = parsed.optBoolean("isArchived", false),
                    isTrashed = parsed.optBoolean("isTrashed", false),
                    labels = parsed.optString("labels", ""),
                    createdAt = parsed.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = parsed.optLong("updatedAt", System.currentTimeMillis()),
                    checklistJson = if (checklistArr.length() > 0) checklistJson else "",
                    attachmentsJson = if (newAttachmentsArr.length() > 0) newAttachmentsArr.toString() else ""
                )
            )
        }

        return ImportOutcome.Success(imported)
    }

    // ─────────────────────────────────────── helpers ───────────────────────────────────────

    private fun buildNoteJson(note: Note, attachments: List<Attachment>): String {
        val root = JSONObject()
        root.put("id", note.id)
        root.put("title", note.title)
        root.put("content", note.content)
        root.put("colorHex", note.colorHex)
        root.put("isPinned", note.isPinned)
        root.put("isArchived", note.isArchived)
        root.put("isTrashed", note.isTrashed)
        root.put("labels", note.labels)
        root.put("createdAt", note.createdAt)
        root.put("updatedAt", note.updatedAt)

        // Checklist
        val checklistArr = JSONArray()
        note.getChecklist().forEach { item ->
            checklistArr.put(JSONObject().apply {
                put("text", item.text)
                put("checked", item.checked)
            })
        }
        root.put("checklist", checklistArr)

        // Attachments manifest (each entry tells you the Drive filename to fetch)
        val attArr = JSONArray()
        attachments.forEach { att ->
            attArr.put(JSONObject().apply {
                put("id", att.id)
                put("type", att.type)
                put("originalName", att.name)
                put("driveFileName", driveFileNameFor(att))
                put("size", att.size)
            })
        }
        root.put("attachments", attArr)

        return root.toString(2)
    }

    private fun noteFolderName(note: Note): String {
        val slug = note.title.ifBlank { "untitled" }
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "untitled" }
        return "note_${note.id}_$slug"
    }

    private fun driveFileNameFor(att: Attachment): String {
        val prefix = when (att.type) {
            "image" -> "img"
            "video" -> "vid"
            "audio" -> "aud"
            else -> "doc"
        }
        val cleanName = att.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60)
        val safeName = if (cleanName.isBlank()) "file" else cleanName
        return "${prefix}_${att.id.take(8)}_$safeName"
    }

    private fun resolveLocalFile(att: Attachment): File? {
        val uri = att.uri
        return try {
            when {
                uri.startsWith("file://") -> File(Uri.parse(uri).path ?: return null).takeIf { it.exists() }
                uri.startsWith("content://") -> null // not directly accessible; would need copy first
                uri.startsWith("http://") || uri.startsWith("https://") -> null
                else -> File(uri).takeIf { it.exists() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mimeTypeFor(att: Attachment): String {
        return when (att.type) {
            "image" -> {
                val lower = att.name.lowercase()
                when {
                    lower.endsWith(".png") -> "image/png"
                    lower.endsWith(".gif") -> "image/gif"
                    lower.endsWith(".webp") -> "image/webp"
                    else -> "image/jpeg"
                }
            }
            "video" -> {
                val lower = att.name.lowercase()
                when {
                    lower.endsWith(".webm") -> "video/webm"
                    lower.endsWith(".mkv") -> "video/x-matroska"
                    lower.endsWith(".3gp") -> "video/3gpp"
                    else -> "video/mp4"
                }
            }
            "audio" -> {
                val lower = att.name.lowercase()
                when {
                    lower.endsWith(".mp3") -> "audio/mpeg"
                    lower.endsWith(".wav") -> "audio/wav"
                    lower.endsWith(".ogg") -> "audio/ogg"
                    else -> "audio/mp4"
                }
            }
            else -> "application/octet-stream"
        }
    }
}
