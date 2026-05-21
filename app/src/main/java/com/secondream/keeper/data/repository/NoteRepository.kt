package com.secondream.keeper.data.repository

import com.secondream.keeper.data.local.NoteDao
import com.secondream.keeper.data.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val noteDao: NoteDao) {
    val activeNotes: Flow<List<Note>> = noteDao.getActiveNotes()
    val archivedNotes: Flow<List<Note>> = noteDao.getArchivedNotes()
    val trashedNotes: Flow<List<Note>> = noteDao.getTrashedNotes()

    fun getNoteById(id: Long): Flow<Note?> = noteDao.getNoteById(id)

    suspend fun getNoteByIdSync(id: Long): Note? = noteDao.getNoteByIdSync(id)

    val allExistingLabels: Flow<Set<String>> = noteDao.getAllLabelsFlow().map { rawLabels ->
        rawLabels.flatMap { labelStr ->
            labelStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }.toSet()
    }

    suspend fun insertNote(note: Note): Long {
        return noteDao.insertNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateNote(note: Note) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun togglePin(noteId: Long) {
        val note = noteDao.getNoteByIdSync(noteId)
        if (note != null) {
            noteDao.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun trashNote(noteId: Long) {
        val note = noteDao.getNoteByIdSync(noteId)
        if (note != null) {
            noteDao.updateNote(note.copy(isTrashed = true, isPinned = false, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun restoreNote(noteId: Long) {
        val note = noteDao.getNoteByIdSync(noteId)
        if (note != null) {
            noteDao.updateNote(note.copy(isTrashed = false, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteNotePermanently(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun emptyTrash() {
        noteDao.emptyTrash()
    }

    suspend fun updateDriveFolder(noteId: Long, folderId: String?, syncedAt: Long) {
        noteDao.updateDriveFolder(noteId, folderId, syncedAt)
    }

    suspend fun getNoteByDriveFolderId(folderId: String): Note? = noteDao.getNoteByDriveFolderId(folderId)

    suspend fun getTrashedNotesSync(): List<Note> = noteDao.getTrashedNotesSync()

    suspend fun getAllNotesSync(): List<Note> = noteDao.getAllNotesSync()

    suspend fun archiveNote(noteId: Long) {
        val note = noteDao.getNoteByIdSync(noteId)
        if (note != null) {
            noteDao.updateNote(note.copy(isArchived = true, isPinned = false, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun unarchiveNote(noteId: Long) {
        val note = noteDao.getNoteByIdSync(noteId)
        if (note != null) {
            noteDao.updateNote(note.copy(isArchived = false, updatedAt = System.currentTimeMillis()))
        }
    }
}
