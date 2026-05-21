package com.secondream.keeper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val colorHex: String = "#FFFFFF", // Default White / Transparent
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val labels: String = "", // CSV e.g. "Work,Personal"
    val checklistJson: String = "", // JSON of checklist items
    val attachmentsJson: String = "", // JSON of attachments
    val driveFolderId: String? = null, // Google Drive folder ID once synced
    val driveSyncedAt: Long = 0L // timestamp of last successful upload to Drive
) {
    // Helpers to decode checklists
    fun getChecklist(): List<ChecklistItem> {
        if (checklistJson.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(checklistJson)
            val list = mutableListOf<ChecklistItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ChecklistItem(
                        text = obj.optString("text", ""),
                        checked = obj.optBoolean("checked", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Helpers to decode attachments
    fun getAttachments(): List<Attachment> {
        if (attachmentsJson.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(attachmentsJson)
            val list = mutableListOf<Attachment>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Attachment(
                        id = obj.optString("id", ""),
                        type = obj.optString("type", "image"),
                        uri = obj.optString("uri", ""),
                        name = obj.optString("name", ""),
                        size = obj.optString("size", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLabelsList(): List<String> {
        if (labels.isBlank()) return emptyList()
        return labels.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

data class ChecklistItem(
    val text: String,
    val checked: Boolean = false
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("text", text)
        obj.put("checked", checked)
        return obj
    }

    companion object {
        fun toJsonArray(items: List<ChecklistItem>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }
    }
}

data class Attachment(
    val id: String,
    val type: String, // "image", "video", "file"
    val uri: String,
    val name: String,
    val size: String
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("type", type)
        obj.put("uri", uri)
        obj.put("name", name)
        obj.put("size", size)
        return obj
    }

    companion object {
        fun toJsonArray(items: List<Attachment>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }
    }
}

// Keep-style Note Colors
data class NoteColor(
    val name: String,
    val lightHex: String,
    val darkHex: String,
    val lightContentHex: String = "#1C1B1F",
    val darkContentHex: String = "#E6E1E5"
)

object KeepColors {
    val colors = listOf(
        NoteColor("Default", "#FFFFFF", "#12131A", "#0F172A", "#F8F9FF"),
        NoteColor("Coral", "#FF9E9E", "#4A1515", "#0F172A", "#FED7D7"),
        NoteColor("Orange", "#FFD180", "#4C2D13", "#0F172A", "#FEEBC8"),
        NoteColor("Yellow", "#FFF475", "#4B4610", "#0F172A", "#FEFCBF"),
        NoteColor("Green", "#CCFF90", "#1E3F1C", "#0F172A", "#C6F6D5"),
        NoteColor("Teal", "#A7FFEB", "#103B35", "#0F172A", "#E6FFFA"),
        NoteColor("Blue", "#AECBFA", "#152A4A", "#0F172A", "#EBF8FF"),
        NoteColor("Dark Blue", "#C5CAE9", "#1A1C2E", "#0F172A", "#E0E6FF"),
        NoteColor("Purple", "#D7AEFB", "#391A4F", "#0F172A", "#FAF5FF"),
        NoteColor("Pink", "#FFB7D5", "#4E162D", "#0F172A", "#FFF5F7"),
        NoteColor("Brown", "#EAE0D5", "#3C2B1C", "#0F172A", "#EDF2F7"),
        NoteColor("Gray", "#E2E8F0", "#232635", "#0F172A", "#E2E8F0")
    )

    fun getColorForHex(hex: String, isDark: Boolean): androidx.compose.ui.graphics.Color {
        val match = colors.find { it.lightHex.equals(hex, true) || it.darkHex.equals(hex, true) }
        return if (match != null) {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(if (isDark) match.darkHex else match.lightHex))
        } else {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(if (isDark) "#1E1E1E" else "#FFFFFF"))
        }
    }

    fun getContentColorForHex(hex: String, isDark: Boolean): androidx.compose.ui.graphics.Color {
        val match = colors.find { it.lightHex.equals(hex, true) || it.darkHex.equals(hex, true) }
        return if (match != null) {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(if (isDark) match.darkContentHex else match.lightContentHex))
        } else {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(if (isDark) "#E6E1E5" else "#1C1B1F"))
        }
    }
}
