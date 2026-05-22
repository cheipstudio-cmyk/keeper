package com.secondream.keeper.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Open any local file (PDF, ZIP, DOC, txt, image, video, …) using whatever
 * external app the user has installed for that mime type. Falls back to a
 * generic chooser if no default exists.
 */
object FileOpener {

    private fun mimeTypeFor(filename: String): String {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".rar") -> "application/vnd.rar"
            lower.endsWith(".7z") -> "application/x-7z-compressed"
            lower.endsWith(".tar") -> "application/x-tar"
            lower.endsWith(".gz") -> "application/gzip"

            lower.endsWith(".doc") -> "application/msword"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".xls") -> "application/vnd.ms-excel"
            lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
            lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            lower.endsWith(".odt") -> "application/vnd.oasis.opendocument.text"
            lower.endsWith(".ods") -> "application/vnd.oasis.opendocument.spreadsheet"

            lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log") -> "text/plain"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".xml") -> "text/xml"
            lower.endsWith(".json") -> "application/json"

            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".ogg") -> "audio/ogg"
            lower.endsWith(".flac") -> "audio/flac"

            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".avi") -> "video/x-msvideo"
            lower.endsWith(".mov") -> "video/quicktime"

            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".bmp") -> "image/bmp"

            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            lower.endsWith(".epub") -> "application/epub+zip"

            else -> "*/*"
        }
    }

    /**
     * Try to open a local file path with Android's standard Intent.ACTION_VIEW.
     * Returns true on success, false if no app handled it.
     */
    fun openFile(context: Context, filePath: String, displayName: String? = null): Boolean {
        return try {
            val file = if (filePath.startsWith("file://")) {
                File(Uri.parse(filePath).path ?: return false)
            } else {
                File(filePath)
            }
            if (!file.exists()) return false

            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val mime = mimeTypeFor(displayName ?: file.name)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Apri con").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            com.secondream.keeper.KeeperApplication.skipNextLock = true
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Share a note (text + optional attachment) via standard share sheet.
     * If attachments contain at least one local file, sends a multi-part SEND intent.
     */
    fun shareNote(context: Context, title: String, content: String, attachmentPaths: List<String> = emptyList()) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val validUris = attachmentPaths.mapNotNull { path ->
                try {
                    val file = if (path.startsWith("file://")) {
                        File(Uri.parse(path).path ?: return@mapNotNull null)
                    } else File(path)
                    if (file.exists()) FileProvider.getUriForFile(context, authority, file) else null
                } catch (e: Exception) {
                    null
                }
            }

            val composedText = buildString {
                if (title.isNotBlank()) {
                    append(title)
                    append("\n\n")
                }
                append(content)
            }

            val intent = if (validUris.isEmpty()) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, composedText)
                }
            } else if (validUris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, validUris[0])
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, composedText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(validUris))
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, composedText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(intent, "Condividi nota").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            com.secondream.keeper.KeeperApplication.skipNextLock = true
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Open a generic URL (used for the donation button etc.).
     */
    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            com.secondream.keeper.KeeperApplication.skipNextLock = true
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
