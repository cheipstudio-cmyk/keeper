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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        // Cleanup stale cache files from older app versions that wrote
        // attachments into cacheDir. New attachments now live in filesDir.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = application.applicationContext
                val oldDirs = listOf(
                    java.io.File(ctx.cacheDir, "drive_attachments"),
                    ctx.cacheDir
                )
                // Wipe the legacy drive_attachments dir
                oldDirs[0].deleteRecursively()
                // Wipe individual "attached_*.{jpg,mp4,pdf}" files from cacheDir root
                oldDirs[1].listFiles()?.forEach { f ->
                    if (f.isFile && f.name.startsWith("attached_")) f.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
        prefs.edit()
            .putString("user_name", name)
            .putBoolean("user_name_manual", true)
            .putString("user_name_email", _googleEmail.value)
            .apply()
    }

    /**
     * Convert a Google account email like "eugenio.casale@gmail.com" into a
     * presentable display name "Eugenio Casale". Falls back to the local part
     * unchanged if it has no separators.
     */
    private fun deriveDisplayNameFromEmail(email: String): String {
        val local = email.substringBefore("@").trim()
        if (local.isBlank()) return ""
        val parts = local.split(".", "_", "-", "+")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return parts.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Update the displayed username from an email — but ONLY if the user
     * never set it manually FOR THIS EMAIL. Switching to a different Google
     * account forces a re-derivation, because the manual override was for
     * the previous account.
     */
    private fun maybeUpdateUserNameFromEmail(email: String) {
        val lastDerivedFromEmail = prefs.getString("user_name_email", "") ?: ""
        val manualOverride = prefs.getBoolean("user_name_manual", false)

        // If the user manually set the name for THIS email, keep it
        if (manualOverride && lastDerivedFromEmail == email) return

        val derived = deriveDisplayNameFromEmail(email)
        if (derived.isNotBlank()) {
            _userName.value = derived
            prefs.edit()
                .putString("user_name", derived)
                .putString("user_name_email", email)
                .putBoolean("user_name_manual", false)
                .apply()
        }
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

    // Onboarding: shown only on first launch (until user dismisses or connects)
    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))
    val onboardingCompleted = _onboardingCompleted.asStateFlow()

    // Live network status (true = device has internet)
    val isOnline: StateFlow<Boolean> = com.secondream.keeper.util.NetworkObserver
        .observe(application.applicationContext)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.secondream.keeper.util.NetworkObserver.isCurrentlyOnline(application.applicationContext)
        )

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }

    // Drive sync repository
    private val driveRepo: DriveSyncRepository = DriveSyncRepository(application.applicationContext)

    // Per-note mutex map: serializes concurrent uploads for the same note
    // to prevent duplicate folders being created on Drive in race conditions.
    private val noteUploadLocks = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

    // ─── Real-time upload progress (Drive sync banner) ───

    /** A single ongoing upload tracked for the UI banner. */
    data class UploadProgress(
        val noteId: Long,
        val noteTitle: String,
        val currentFileName: String,
        val bytesUploaded: Long,
        val totalBytes: Long,
        val isPaused: Boolean = false
    ) {
        val progress: Float
            get() = if (totalBytes > 0) (bytesUploaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                    else 0f
        val sizeMb: Float
            get() = totalBytes / (1024f * 1024f)
    }

    // Active uploads keyed by noteId
    private val _activeUploads = MutableStateFlow<Map<Long, UploadProgress>>(emptyMap())
    val activeUploads: StateFlow<Map<Long, UploadProgress>> = _activeUploads.asStateFlow()

    // User-paused note IDs (paused uploads are skipped until resumed)
    private val _pausedNotes = MutableStateFlow<Set<Long>>(emptySet())
    val pausedNotes: StateFlow<Set<Long>> = _pausedNotes.asStateFlow()

    fun pauseUpload(noteId: Long) {
        _pausedNotes.value = _pausedNotes.value + noteId
        _activeUploads.value = _activeUploads.value.mapValues { (id, prog) ->
            if (id == noteId) prog.copy(isPaused = true) else prog
        }
    }

    fun resumeUpload(noteId: Long) {
        _pausedNotes.value = _pausedNotes.value - noteId
        _activeUploads.value = _activeUploads.value - noteId
        // Retry the upload now
        maybeAutoUploadNote(noteId)
    }

    private fun setUploadProgress(progress: UploadProgress) {
        _activeUploads.value = _activeUploads.value + (progress.noteId to progress)
    }

    private fun clearUploadProgress(noteId: Long) {
        _activeUploads.value = _activeUploads.value - noteId
    }

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
    private val _isConnectingAccount = MutableStateFlow(false)

    fun connectAndSyncGoogleAccount(email: String) {
        // Prevent re-entry while a connect is in flight — fixes the "account
        // chooser appears twice" symptom from racing UI taps and the auth
        // recovery flow re-triggering.
        if (_isConnectingAccount.value) return
        _isConnectingAccount.value = true
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
                    // First successful login completes onboarding
                    completeOnboarding()
                    // Refresh the display name from the connected account
                    // (unless the user already overrode it manually)
                    maybeUpdateUserNameFromEmail(email)
                    _syncMessage.value = "Connesso. Cartella Keeper pronta su Drive."

                    // Auto-import existing notes from Drive (covers the case
                    // when user cleared app data and re-logs in)
                    _syncMessage.value = "Cerco note esistenti su Drive..."
                    val importOutcome = driveRepo.importNotesFromDrive(
                        accountName = email,
                        rootFolderId = result.folderId
                    )
                    if (importOutcome is DriveSyncRepository.ImportOutcome.Success) {
                        var importedCount = 0
                        for (imp in importOutcome.notes) {
                            val existing = repository.getNoteByDriveFolderId(imp.driveFolderId)
                            if (existing == null) {
                                val newNote = Note(
                                    title = imp.title,
                                    content = imp.content,
                                    colorHex = imp.colorHex,
                                    isPinned = imp.isPinned,
                                    isArchived = imp.isArchived,
                                    isTrashed = imp.isTrashed,
                                    createdAt = imp.createdAt,
                                    updatedAt = imp.updatedAt,
                                    labels = imp.labels,
                                    checklistJson = imp.checklistJson,
                                    attachmentsJson = imp.attachmentsJson,
                                    driveFolderId = imp.driveFolderId,
                                    driveSyncedAt = System.currentTimeMillis()
                                )
                                repository.insertNote(newNote)
                                importedCount++
                            }
                        }
                        if (importedCount > 0) {
                            _syncMessage.value = "Recuperate $importedCount note da Drive"
                        } else {
                            _syncMessage.value = "Connesso. Nessuna nota su Drive."
                        }
                    } else if (importOutcome is DriveSyncRepository.ImportOutcome.NeedsUserAction) {
                        _pendingAuthIntent.value = importOutcome.intent
                    }

                    // If auto-upload was already on, also push local notes
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
            _isConnectingAccount.value = false
        }
    }

    /**
     * Manual sync: BIDIRECTIONAL.
     * Step 1: Download from Drive all notes that are not yet in the local DB
     * Step 2: Upload all local notes to Drive
     */
    fun syncNotesToCloud(email: String, onProgress: (Float, String) -> Unit, onComplete: () -> Unit) {
        viewModelScope.launch {
            onProgress(0.02f, "Connessione a Drive...")
            val rootId = ensureRootFolderCached(email)
            if (rootId == null) {
                onProgress(0.0f, "Impossibile accedere a Google Drive")
                delay(1200)
                onComplete()
                return@launch
            }

            // ─── STEP 1: download from Drive ───
            onProgress(0.05f, "Cerco note su Drive...")
            val importOutcome = driveRepo.importNotesFromDrive(
                accountName = email,
                rootFolderId = rootId,
                onProgress = { current, total, label ->
                    val pct = 0.05f + (current.toFloat() / total.coerceAtLeast(1)) * 0.45f
                    onProgress(pct, label)
                }
            )
            var importedCount = 0
            when (importOutcome) {
                is DriveSyncRepository.ImportOutcome.Success -> {
                    for (imp in importOutcome.notes) {
                        // Skip if already in local DB
                        val existing = repository.getNoteByDriveFolderId(imp.driveFolderId)
                        if (existing == null) {
                            val newNote = Note(
                                title = imp.title,
                                content = imp.content,
                                colorHex = imp.colorHex,
                                isPinned = imp.isPinned,
                                isArchived = imp.isArchived,
                                isTrashed = imp.isTrashed,
                                createdAt = imp.createdAt,
                                updatedAt = imp.updatedAt,
                                labels = imp.labels,
                                checklistJson = imp.checklistJson,
                                attachmentsJson = imp.attachmentsJson,
                                driveFolderId = imp.driveFolderId,
                                driveSyncedAt = System.currentTimeMillis()
                            )
                            repository.insertNote(newNote)
                            importedCount++
                        } else if (imp.updatedAt > existing.updatedAt) {
                            // Drive has newer version — overwrite local
                            repository.updateNote(
                                existing.copy(
                                    title = imp.title,
                                    content = imp.content,
                                    colorHex = imp.colorHex,
                                    isPinned = imp.isPinned,
                                    isArchived = imp.isArchived,
                                    isTrashed = imp.isTrashed,
                                    labels = imp.labels,
                                    updatedAt = imp.updatedAt,
                                    checklistJson = imp.checklistJson,
                                    attachmentsJson = imp.attachmentsJson,
                                    driveSyncedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                is DriveSyncRepository.ImportOutcome.NeedsUserAction -> {
                    _pendingAuthIntent.value = importOutcome.intent
                    onProgress(0.0f, "Serve consenso utente per Drive")
                    delay(1500)
                    onComplete()
                    return@launch
                }
                is DriveSyncRepository.ImportOutcome.Failure -> {
                    android.util.Log.w("DriveSync", "Import fallito: ${importOutcome.message}")
                    onProgress(0.50f, "Errore download: ${importOutcome.message}")
                    delay(800)
                }
            }

            // ─── STEP 2: upload local notes ───
            val all = repository.getAllNotesSync()
            if (all.isEmpty()) {
                onProgress(1.0f, if (importedCount > 0) "Importate $importedCount note da Drive" else "Nessuna nota presente")
                delay(900)
                onComplete()
                return@launch
            }
            var index = 0
            var failures = 0
            for (note in all) {
                index++
                val pct = 0.50f + (index.toFloat() / all.size) * 0.48f
                onProgress(pct, "Carico ${index}/${all.size}: ${note.title.take(20).ifBlank { "senza titolo" }}")
                when (val outcome = driveRepo.uploadNote(email, note, rootId)) {
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
            val msg = buildString {
                if (importedCount > 0) append("$importedCount scaricate da Drive. ")
                append(if (failures == 0) "${all.size} caricate" else "${all.size - failures} ok, $failures errori")
            }
            onProgress(1.0f, msg)
            delay(1100)
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
     *
     * Race-condition guard: a per-note Mutex serializes concurrent uploads
     * for the same noteId, otherwise rapid edits could trigger TWO concurrent
     * folder-creation requests and produce duplicate folders on Drive.
     */
    private fun maybeAutoUploadNote(noteId: Long) {
        if (!_autoUploadEnabled.value) return
        if (!_isGoogleConnected.value) return
        if (_pausedNotes.value.contains(noteId)) return // user-paused
        if (!isOnline.value) return // offline — auto-retry will happen via observer
        val email = _googleEmail.value
        if (email.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val lock = noteUploadLocks.getOrPut(noteId) { Mutex() }
            lock.withLock {
                try {
                    val note = repository.getNoteByIdSync(noteId) ?: return@withLock
                    val rootId = ensureRootFolderCached(email) ?: return@withLock
                    val outcome = driveRepo.uploadNote(
                        accountName = email,
                        note = note,
                        rootFolderId = rootId,
                        progressListener = { fileName, uploaded, total ->
                            // Don't show progress for tiny note.json
                            if (total > 50_000L) {
                                setUploadProgress(
                                    UploadProgress(
                                        noteId = note.id,
                                        noteTitle = note.title.ifBlank { "Nota senza titolo" },
                                        currentFileName = fileName,
                                        bytesUploaded = uploaded,
                                        totalBytes = total,
                                        isPaused = _pausedNotes.value.contains(note.id)
                                    )
                                )
                            }
                        }
                    )
                    when (outcome) {
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
                } finally {
                    clearUploadProgress(noteId)
                }
            }
        }
    }

    /**
     * Auto-retry pending uploads when the device comes back online.
     */
    init {
        viewModelScope.launch {
            var wasOnline = isOnline.value
            isOnline.collect { nowOnline ->
                if (!wasOnline && nowOnline && _autoUploadEnabled.value && _isGoogleConnected.value) {
                    // Network restored — try syncing any locally-modified notes
                    val email = _googleEmail.value
                    if (email.isNotBlank()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val rootId = ensureRootFolderCached(email) ?: return@launch
                            syncAllNotesToDriveBlocking(email, rootId)
                        }
                    }
                }
                wasOnline = nowOnline
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
