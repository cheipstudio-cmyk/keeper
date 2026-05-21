package com.secondream.keeper.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondream.keeper.data.drive.DriveSyncRepository
import com.secondream.keeper.data.local.AppDatabase
import com.secondream.keeper.data.model.Attachment
import com.secondream.keeper.data.model.ChecklistItem
import com.secondream.keeper.data.model.Note
import com.secondream.keeper.data.model.NoteColor
import com.secondream.keeper.data.model.KeepColors
import com.secondream.keeper.data.repository.NoteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface NavigationScreen {
    object Notes : NavigationScreen
    object Reminders : NavigationScreen // Added for rich Keep experience
    data class Label(val label: String) : NavigationScreen
    object Archive : NavigationScreen
    object Trash : NavigationScreen
    object Settings : NavigationScreen
}

data class UploadingAttachment(
    val id: String,
    val type: String,
    val name: String,
    val size: String,
    val progress: Float, // 0.0f to 1.0f
    val originalUri: String
)

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    private val prefs = application.getSharedPreferences("keep_notes_prefs", Context.MODE_PRIVATE)

    // Raw sources from repository
    val activeNotes: StateFlow<List<Note>>
    val archivedNotes: StateFlow<List<Note>>
    val trashedNotes: StateFlow<List<Note>>
    val allLabels: StateFlow<Set<String>>

    // Search and filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedLabelFilter = MutableStateFlow<String?>(null)
    val selectedLabelFilter = _selectedLabelFilter.asStateFlow()

    private val _currentScreen = MutableStateFlow<NavigationScreen>(NavigationScreen.Notes)
    val currentScreen = _currentScreen.asStateFlow()

    // Screen-filtered Notes combined with search queries
    val filteredNotes: StateFlow<List<Note>>

    // Settings
    private val _isGridView = MutableStateFlow(prefs.getBoolean("is_grid_view", true))
    val isGridView = _isGridView.asStateFlow()

    private val _darkThemeOption = MutableStateFlow(prefs.getString("dark_theme", "system") ?: "system")
    val darkThemeOption = _darkThemeOption.asStateFlow()

    private val _defaultNoteColor = MutableStateFlow(prefs.getString("default_note_color", "#FFFFFF") ?: "#FFFFFF")
    val defaultNoteColor = _defaultNoteColor.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString("user_name", "Explorer") ?: "Explorer")
    val userName = _userName.asStateFlow()

    // Note draft and temporary attachments upload simulation
    private val _tempAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val tempAttachments = _tempAttachments.asStateFlow()

    private val _uploadingTasks = MutableStateFlow<List<UploadingAttachment>>(emptyList())
    val uploadingTasks = _uploadingTasks.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NoteRepository(database.noteDao())

        activeNotes = repository.activeNotes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        archivedNotes = repository.archivedNotes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        trashedNotes = repository.trashedNotes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allLabels = repository.allExistingLabels.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

        // Combine filter logic
        filteredNotes = combine(
            _currentScreen,
            activeNotes,
            archivedNotes,
            trashedNotes,
            _searchQuery
        ) { screen, active, archived, trashed, query ->
            val baseList = when (screen) {
                is NavigationScreen.Notes -> active
                is NavigationScreen.Reminders -> active.filter { it.getChecklist().isNotEmpty() } // Checklist filters for rich view
                is NavigationScreen.Archive -> archived
                is NavigationScreen.Trash -> trashed
                is NavigationScreen.Label -> active.filter { it.getLabelsList().contains(screen.label) }
                is NavigationScreen.Settings -> emptyList()
            }

            if (query.isBlank()) {
                baseList
            } else {
                baseList.filter { note ->
                    note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true) ||
                    note.getChecklist().any { it.text.contains(query, ignoreCase = true) } ||
                    note.getLabelsList().any { it.contains(query, ignoreCase = true) } ||
                    note.getAttachments().any { it.name.contains(query, ignoreCase = true) }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // Setters
    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun navigateTo(screen: NavigationScreen) {
        _currentScreen.value = screen
    }

    fun toggleLayoutView() {
        val newVal = !_isGridView.value
        _isGridView.value = newVal
        prefs.edit().putBoolean("is_grid_view", newVal).apply()
    }

    fun setDarkThemeOption(option: String) {
        _darkThemeOption.value = option
        prefs.edit().putString("dark_theme", option).apply()
    }

    fun setDefaultNoteColor(hex: String) {
        _defaultNoteColor.value = hex
        prefs.edit().putString("default_note_color", hex).apply()
    }

    fun setUserName(name: String) {
        _userName.value = name
        prefs.edit().putString("user_name", name).apply()
    }

    // Google Cloud Sync States
    private val _isGoogleConnected = MutableStateFlow(prefs.getBoolean("google_connected", false))
    val isGoogleConnected = _isGoogleConnected.asStateFlow()

    private val _googleEmail = MutableStateFlow(prefs.getString("google_email", "") ?: "")
    val googleEmail = _googleEmail.asStateFlow()

    private val _isSyncingNotes = MutableStateFlow(false)
    val isSyncingNotes = _isSyncingNotes.asStateFlow()

    private val _syncMessage = MutableStateFlow("")
    val syncMessage = _syncMessage.asStateFlow()

    // Auto-upload toggle
    private val _autoUploadEnabled = MutableStateFlow(prefs.getBoolean("auto_upload_drive", false))
    val autoUploadEnabled = _autoUploadEnabled.asStateFlow()

    // Cached root folder ID on Drive
    private val _keeperRootFolderId = MutableStateFlow(prefs.getString("keeper_root_folder_id", null))

    // Surface a recoverable auth intent (first-time consent prompt) for the Activity to launch
    private val _pendingAuthIntent = MutableStateFlow<Intent?>(null)
    val pendingAuthIntent = _pendingAuthIntent.asStateFlow()

    // Drive sync repository
    private val driveRepo: DriveSyncRepository = DriveSyncRepository(application.applicationContext)

    fun setAutoUploadEnabled(enabled: Boolean) {
        _autoUploadEnabled.value = enabled
        prefs.edit().putBoolean("auto_upload_drive", enabled).apply()
        // When toggled on, kick off a full sync immediately
        if (enabled && _isGoogleConnected.value && _googleEmail.value.isNotBlank()) {
            syncAllNotesToDrive()
        }
    }

    fun clearPendingAuthIntent() {
        _pendingAuthIntent.value = null
    }

    fun connectGoogleAccount(email: String) {
        _isGoogleConnected.value = true
        _googleEmail.value = email
        prefs.edit()
            .putBoolean("google_connected", true)
            .putString("google_email", email)
            .apply()
    }

    fun disconnectGoogleAccount() {
        _isGoogleConnected.value = false
        _googleEmail.value = ""
        prefs.edit()
            .putBoolean("google_connected", false)
            .remove("google_email")
            .apply()
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //   Real Google Drive sync (scope: drive.file — only files the app creates)
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Try the OAuth handshake and create/find the Keeper root folder.
     * Called once when the user picks an account from the chooser.
     */
    fun connectAndSyncGoogleAccount(email: String) {
        viewModelScope.launch {
            _isSyncingNotes.value = true
            _syncMessage.value = "Connessione a Google Drive..."

            val result = driveRepo.ensureRootFolder(email)
            when (result) {
                is DriveSyncRepository.SyncOutcome.Success -> {
                    _keeperRootFolderId.value = result.folderId
                    prefs.edit().putString("keeper_root_folder_id", result.folderId).apply()
                    _googleEmail.value = email
                    _isGoogleConnected.value = true
                    prefs.edit()
                        .putBoolean("google_connected", true)
                        .putString("google_email", email)
                        .apply()
                    _syncMessage.value = "Connesso. Cartella Keeper pronta su Drive."

                    // If auto-upload was already on, immediately sync everything
                    if (_autoUploadEnabled.value) {
                        syncAllNotesToDriveBlocking(email, result.folderId)
                    }
                }
                is DriveSyncRepository.SyncOutcome.NeedsUserAction -> {
                    // First-time consent: caller must launch this intent
                    _pendingAuthIntent.value = result.intent
                    _syncMessage.value = "Conferma il consenso Drive per continuare..."
                }
                is DriveSyncRepository.SyncOutcome.Failure -> {
                    _syncMessage.value = "Errore: ${result.message}"
                }
            }
            delay(700)
            _isSyncingNotes.value = false
        }
    }

    /**
     * Manual sync: foreground operation, with progress callback.
     * Iterates ALL notes (active + archived + trashed) and uploads each one.
     */
    fun syncNotesToCloud(email: String, onProgress: (Float, String) -> Unit, onComplete: () -> Unit) {
        viewModelScope.launch {
            onProgress(0.05f, "Connessione a Drive...")
            val rootId = ensureRootFolderCached(email)
            if (rootId == null) {
                onProgress(0.0f, "Impossibile accedere a Google Drive")
                delay(1200)
                onComplete()
                return@launch
            }
            val all = repository.getAllNotesSync()
            if (all.isEmpty()) {
                onProgress(1.0f, "Nessuna nota da sincronizzare")
                delay(700)
                onComplete()
                return@launch
            }
            var index = 0
            var failures = 0
            for (note in all) {
                index++
                val pct = 0.05f + (index.toFloat() / all.size) * 0.9f
                onProgress(pct, "Sincronizzo ${index}/${all.size}: ${note.title.take(20).ifBlank { "senza titolo" }}")
                val outcome = driveRepo.uploadNote(email, note, rootId)
                when (outcome) {
                    is DriveSyncRepository.SyncOutcome.Success -> {
                        repository.updateDriveFolder(note.id, outcome.folderId, System.currentTimeMillis())
                    }
                    is DriveSyncRepository.SyncOutcome.NeedsUserAction -> {
                        _pendingAuthIntent.value = outcome.intent
                        onProgress(pct, "Serve consenso utente. Continua dall'app.")
                        delay(1500)
                        onComplete()
                        return@launch
                    }
                    is DriveSyncRepository.SyncOutcome.Failure -> {
                        failures++
                        android.util.Log.w("DriveSync", "Nota ${note.id}: ${outcome.message}")
                    }
                }
            }
            val msg = if (failures == 0) "Backup completato (${all.size} note)"
                      else "Backup parziale: ${all.size - failures} ok, $failures errori"
            onProgress(1.0f, msg)
            delay(900)
            onComplete()
        }
    }

    /**
     * Background full-sync (kicked off when auto-upload is toggled ON).
     */
    private fun syncAllNotesToDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            val email = _googleEmail.value
            if (email.isBlank()) return@launch
            val rootId = ensureRootFolderCached(email) ?: return@launch
            syncAllNotesToDriveBlocking(email, rootId)
        }
    }

    private suspend fun syncAllNotesToDriveBlocking(email: String, rootId: String) {
        val all = repository.getAllNotesSync()
        for (note in all) {
            when (val outcome = driveRepo.uploadNote(email, note, rootId)) {
                is DriveSyncRepository.SyncOutcome.Success -> {
                    repository.updateDriveFolder(note.id, outcome.folderId, System.currentTimeMillis())
                }
                is DriveSyncRepository.SyncOutcome.NeedsUserAction -> {
                    _pendingAuthIntent.value = outcome.intent
                    return
                }
                is DriveSyncRepository.SyncOutcome.Failure -> {
                    android.util.Log.w("DriveSync", "auto-upload failure note ${note.id}: ${outcome.message}")
                }
            }
        }
    }

    /**
     * Try to upload a single note in the background.
     * Best-effort: failures are logged but don't surface to the UI.
     */
    private fun maybeAutoUploadNote(noteId: Long) {
        if (!_autoUploadEnabled.value) return
        if (!_isGoogleConnected.value) return
        val email = _googleEmail.value
        if (email.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val note = repository.getNoteByIdSync(noteId) ?: return@launch
                val rootId = ensureRootFolderCached(email) ?: return@launch
                when (val outcome = driveRepo.uploadNote(email, note, rootId)) {
                    is DriveSyncRepository.SyncOutcome.Success -> {
                        repository.updateDriveFolder(note.id, outcome.folderId, System.currentTimeMillis())
                    }
                    is DriveSyncRepository.SyncOutcome.NeedsUserAction -> {
                        _pendingAuthIntent.value = outcome.intent
                    }
                    is DriveSyncRepository.SyncOutcome.Failure -> {
                        android.util.Log.w("DriveSync", "Auto-upload note $noteId: ${outcome.message}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Delete a note's Drive folder. Called when permanently deleting.
     */
    private fun maybeDeleteNoteFolder(folderId: String?) {
        val id = folderId ?: return
        if (id.isBlank()) return
        if (!_isGoogleConnected.value) return
        val email = _googleEmail.value
        if (email.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val outcome = driveRepo.deleteNoteFolder(email, id)) {
                    is DriveSyncRepository.SyncOutcome.Success -> {}
                    is DriveSyncRepository.SyncOutcome.NeedsUserAction -> {
                        _pendingAuthIntent.value = outcome.intent
                    }
                    is DriveSyncRepository.SyncOutcome.Failure -> {
                        android.util.Log.w("DriveSync", "Delete folder failed: ${outcome.message}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun ensureRootFolderCached(email: String): String? {
        _keeperRootFolderId.value?.let { return it }
        return when (val outcome = driveRepo.ensureRootFolder(email)) {
            is DriveSyncRepository.SyncOutcome.Success -> {
                _keeperRootFolderId.value = outcome.folderId
                prefs.edit().putString("keeper_root_folder_id", outcome.folderId).apply()
                outcome.folderId
            }
            is DriveSyncRepository.SyncOutcome.NeedsUserAction -> {
                _pendingAuthIntent.value = outcome.intent
                null
            }
            is DriveSyncRepository.SyncOutcome.Failure -> {
                android.util.Log.w("DriveSync", "ensureRoot failed: ${outcome.message}")
                null
            }
        }
    }


    // Attachment upload simulation
    fun simulateAttachmentUpload(type: String, name: String, size: String, uri: String) {
        val taskId = UUID.randomUUID().toString()
        val newTask = UploadingAttachment(
            id = taskId,
            type = type,
            name = name,
            size = size,
            progress = 0.0f,
            originalUri = uri
        )

        _uploadingTasks.update { it + newTask }

        viewModelScope.launch {
            var currentProgress = 0.0f
            while (currentProgress < 1.0f) {
                delay(120) // Simulated network speed increments
                currentProgress += (0.1f + (0.15f * Math.random().toFloat()))
                if (currentProgress > 1.0f) currentProgress = 1.0f
                _uploadingTasks.update { tasks ->
                    tasks.map { if (it.id == taskId) it.copy(progress = currentProgress) else it }
                }
            }

            // Completed! Move to temporary attachments
            val finalAttachment = Attachment(
                id = UUID.randomUUID().toString(),
                type = type,
                uri = uri,
                name = name,
                size = size
            )
            _tempAttachments.update { it + finalAttachment }
            _uploadingTasks.update { tasks -> tasks.filter { it.id != taskId } }
        }
    }

    fun clearTempAttachments() {
        _tempAttachments.value = emptyList()
    }

    fun removeTempAttachment(attId: String) {
        _tempAttachments.update { list -> list.filter { it.id != attId } }
    }

    // Persistent Note CRUD Actions
    fun createNote(
        title: String,
        content: String,
        colorHex: String = _defaultNoteColor.value,
        labelsList: List<String> = emptyList(),
        checklistItems: List<ChecklistItem> = emptyList(),
        attachmentsList: List<Attachment> = emptyList(),
        isPinned: Boolean = false
    ) {
        viewModelScope.launch {
            val checklistStr = if (checklistItems.isNotEmpty()) ChecklistItem.toJsonArray(checklistItems) else ""
            val attachmentsStr = if (attachmentsList.isNotEmpty()) Attachment.toJsonArray(attachmentsList) else ""
            val labelsStr = labelsList.joinToString(",")

            val newNote = Note(
                title = title,
                content = content,
                colorHex = colorHex,
                isPinned = isPinned,
                isArchived = _currentScreen.value is NavigationScreen.Archive,
                labels = labelsStr,
                checklistJson = checklistStr,
                attachmentsJson = attachmentsStr
            )
            val newId = repository.insertNote(newNote)
            maybeAutoUploadNote(newId)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note)
            maybeAutoUploadNote(note.id)
        }
    }

    // Fast Note status changes
    fun togglePinNote(noteId: Long) {
        viewModelScope.launch {
            repository.togglePin(noteId)
            maybeAutoUploadNote(noteId)
        }
    }

    fun trashNote(noteId: Long) {
        viewModelScope.launch {
            repository.trashNote(noteId)
            maybeAutoUploadNote(noteId)
        }
    }

    fun restoreNote(noteId: Long) {
        viewModelScope.launch {
            repository.restoreNote(noteId)
            maybeAutoUploadNote(noteId)
        }
    }

    fun archiveNote(noteId: Long) {
        viewModelScope.launch {
            repository.archiveNote(noteId)
            maybeAutoUploadNote(noteId)
        }
    }

    fun unarchiveNote(noteId: Long) {
        viewModelScope.launch {
            repository.unarchiveNote(noteId)
            maybeAutoUploadNote(noteId)
        }
    }

    fun deleteNotePermanently(note: Note) {
        viewModelScope.launch {
            repository.deleteNotePermanently(note)
            maybeDeleteNoteFolder(note.driveFolderId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            // Capture the trashed notes BEFORE deleting so we know their Drive folder IDs
            val trashedBefore = repository.getTrashedNotesSync()
            repository.emptyTrash()
            trashedBefore.forEach { note ->
                maybeDeleteNoteFolder(note.driveFolderId)
            }
        }
    }
}
