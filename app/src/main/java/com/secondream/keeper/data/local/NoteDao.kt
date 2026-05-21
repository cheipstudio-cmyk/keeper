package com.secondream.keeper.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.secondream.keeper.data.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun getTrashedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Long): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteByIdSync(id: Long): Note?

    @Query("SELECT DISTINCT labels FROM notes")
    fun getAllLabelsFlow(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("UPDATE notes SET driveFolderId = :folderId, driveSyncedAt = :syncedAt WHERE id = :noteId")
    suspend fun updateDriveFolder(noteId: Long, folderId: String?, syncedAt: Long)

    @Query("SELECT * FROM notes WHERE driveFolderId = :folderId LIMIT 1")
    suspend fun getNoteByDriveFolderId(folderId: String): Note?

    @Query("SELECT * FROM notes WHERE isTrashed = 1")
    suspend fun getTrashedNotesSync(): List<Note>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<Note>
}
