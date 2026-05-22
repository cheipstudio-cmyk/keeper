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
    private lateinit var repository: NoteRepository
    private val prefs = application.getSharedPreferences("keep_notes_prefs", Context.MODE_PRIVATE)

    // Raw sources from repository — pre-initialized to safe empty defaults so
    // even if the repository fails to wire up (e.g. after a corrupted data
    // wipe), the ViewModel still constructs and the app opens.
    private val _activeNotes = MutableStateFlow<List<Note>>(emptyList())
    val activeNotes: StateFlow<List<Note>> = _activeNotes.asStateFlow()

    private val _archivedNotes = MutableStateFlow<List<Note>>(emptyList())
    val archivedNotes: StateFlow<List<Note>> = _archivedNotes.asStateFlow()

    private val _trashedNotes = MutableStateFlow<List<Note>>(emptyList())
    val trashedNotes: StateFlow<List<Note>> = _trashedNotes.asStateFlow()

    private val _allLabels = MutableStateFlow<Set<String>>(emptySet())
    val allLabels: StateFlow<Set<String>> = _allLabels.asStateFlow()

    private val _filteredNotes = MutableStateFlow<List<Note>>(emptyList())
    val filteredNotes: StateFlow<List<Note>> = _filteredNotes.asStateFlow()

    // Search and filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedLabelFilter = MutableStateFlow<String?>(null)
    val selectedLabelFilter = _selectedLabelFilter.asStateFlow()

    private val _currentScreen = MutableStateFlow<NavigationScreen>(NavigationScreen.Notes)
    val currentScreen = _currentScreen.asStateFlow()

    // Settings
    private val _isGridView = MutableStateFlow(prefs.getBoolean("is_grid_view", true))
    val isGridView = _isGridView.asStateFlow()

    private val _darkThemeOption = MutableStateFlow(prefs.getString("dark_theme", "system") ?: "system")
    val darkThemeOption = _darkThemeOption.asStateFlow()

    private val _defaultNoteColor = MutableStateFlow(prefs.getString("default_note_color", "#FFFFFF") ?: "#FFFFFF")
    val defaultNoteColor = _defaultNoteColor.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString("user_name", "Explorer") ?: "Explorer")

    // App lock: when enabled, MainActivity shows a LockScreen overlay on
    // launch / resume until biometric (or device credential) authentication
    // succeeds. Setting is persisted in prefs.
    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean("app_lock_enabled", false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _isLocked = MutableStateFlow(prefs.getBoolean("app_lock_enabled", false))
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun setAppLockEnabled(enabled: Boolean) {
        _appLockEnabled.value = enabled
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
        // Turning off the lock immediately unlocks the UI
        if (!enabled) _isLocked.value = false
    }

    fun lockApp() {
        if (_appLockEnabled.value) _isLocked.value = true
    }

    fun unlockApp() { _isLocked.value = false }

    // External requests to open a specific note (from widget tap).
    // MainActivity drains this and triggers the detail dialog.
    private val _openNoteRequest = MutableStateFlow<Note?>(null)
    val openNoteRequest: StateFlow<Note?> = _openNoteRequest.asStateFlow()

    private fun refreshWidgets() {
        try {
            com.secondream.keeper.widget.KeeperWidgetProvider.updateAll(
                getApplication<Application>().applicationContext
            )
        } catch (e: Exception) {
            android.util.Log.w("Keeper", "Widget refresh failed", e)
        }
    }

    fun requestOpenNoteById(noteId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val n = repository.getNoteByIdSync(noteId)
                if (n != null) _openNoteRequest.value = n
            } catch (e: Exception) {
                android.util.Log.w("Keeper", "requestOpenNoteById failed", e)
            }
        }
    }

    fun clearOpenNoteRequest() { _openNoteRequest.value = null }
    val userName = _userName.asStateFlow()

    // Persistent set of user-created labels that may have no note yet.
    // Try/catch around prefs read because data-clear edge cases can
    // sometimes return malformed sets and we never want to crash here.
    private val _extraLabels = MutableStateFlow<Set<String>>(
        try {
            val raw = prefs.getStringSet("extra_labels", null)
            raw?.toSet() ?: emptySet()
        } catch (e: Exception) {
            e.printStackTrace()
            emptySet()
        }
    )

    fun createEmptyLabel(name: String) {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return
        val newSet = _extraLabels.value + cleaned
        _extraLabels.value = newSet
        prefs.edit().putStringSet("extra_labels", newSet).apply()
    }

    fun deleteLabel(name: String) {
        viewModelScope.launch {
            try {
                val newSet = _extraLabels.value - name
                _extraLabels.value = newSet
                prefs.edit().putStringSet("extra_labels", newSet).apply()
                repository.removeLabelFromAllNotes(name)
                // If we were currently viewing that label, go back to Notes
                if (_currentScreen.value is NavigationScreen.Label &&
                    (_currentScreen.value as NavigationScreen.Label).label == name) {
                    _currentScreen.value = NavigationScreen.Notes
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Accent color (the Keep yellow by default). Persisted as a Long ARGB.
    private val _accentColorArgb = MutableStateFlow(
        prefs.getLong("accent_color_argb", 0xFFFFCA28L)
    )
    val accentColorArgb: StateFlow<Long> = _accentColorArgb.asStateFlow()

    fun setAccentColor(argb: Long) {
        _accentColorArgb.value = argb
        prefs.edit().putLong("accent_color_argb", argb).apply()
    }

    // Note draft and temporary attachments upload simulation
    private val _tempAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val tempAttachments = _tempAttachments.asStateFlow()

    private val _uploadingTasks = MutableStateFlow<List<UploadingAttachment>>(emptyList())
    val uploadingTasks = _uploadingTasks.asStateFlow()

    init {
        // ── DB initialization: wrapped because we'd rather have an empty app
        //    than a crash loop after a data wipe.
        try {
            val database = AppDatabase.getDatabase(application)
            repository = NoteRepository(database.noteDao())
        } catch (e: Exception) {
            android.util.Log.e("Keeper", "Database init FAILED", e)
            // Last-resort dummy: attempt a fresh in-memory instance
            try {
                val database = AppDatabase.getDatabase(application)
                repository = NoteRepository(database.noteDao())
            } catch (e2: Exception) {
                android.util.Log.e("Keeper", "Second DB attempt failed", e2)
                throw e2
            }
        }

        // ── Wire Flows from repo into our StateFlow defaults.
        //    Each in its own coroutine so one failing doesn't kill the others.
        viewModelScope.launch {
            try { repository.activeNotes.collect { _activeNotes.value = it } }
            catch (e: Exception) { android.util.Log.e("Keeper", "activeNotes flow failed", e) }
        }
        viewModelScope.launch {
            try { repository.archivedNotes.collect { _archivedNotes.value = it } }
            catch (e: Exception) { android.util.Log.e("Keeper", "archivedNotes flow failed", e) }
        }
        viewModelScope.launch {
            try { repository.trashedNotes.collect { _trashedNotes.value = it } }
            catch (e: Exception) { android.util.Log.e("Keeper", "trashedNotes flow failed", e) }
        }
        viewModelScope.launch {
            try {
                combine(
                    repository.allExistingLabels,
                    _extraLabels
                ) { dbLabels, extra -> (dbLabels + extra).toSet() }
                    .collect { _allLabels.value = it }
            } catch (e: Exception) {
                android.util.Log.e("Keeper", "allLabels flow failed", e)
            }
        }
        viewModelScope.launch {
            try {
                combine(
                    _currentScreen,
                    activeNotes,
                    archivedNotes,
                    trashedNotes,
                    _searchQuery
                ) { screen, active, archived, trashed, query ->
                    val baseList = when (screen) {
                        is NavigationScreen.Notes -> active
                        is NavigationScreen.Reminders -> active.filter { it.getChecklist().isNotEmpty() }
                        is NavigationScreen.Archive -> archived
                        is NavigationScreen.Trash -> trashed
                        is NavigationScreen.Label -> active.filter { it.getLabelsList().contains(screen.label) }
                        is NavigationScreen.Settings -> emptyList()
                    }
                    if (query.isBlank()) baseList
                    else baseList.filter { note ->
                        note.title.contains(query, ignoreCase = true) ||
                        note.content.contains(query, ignoreCase = true) ||
                        note.getChecklist().any { it.text.contains(query, ignoreCase = true) } ||
                        note.getLabelsList().any { it.contains(query, ignoreCase = true) } ||
                        note.getAttachments().any { it.name.contains(query, ignoreCase = true) }
                    }
                }.collect { _filteredNotes.value = it }
            } catch (e: Exception) {
                android.util.Log.e("Keeper", "filteredNotes flow failed", e)
            }
        }

        // Cache cleanup
        try {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val ctx = application.applicationContext
                    val oldDir = java.io.File(ctx.cacheDir, "drive_attachments")
                    if (oldDir.exists()) oldDir.deleteRecursively()
                    ctx.cacheDir?.listFiles()?.forEach { f ->
                        try {
                            if (f.isFile && f.name.startsWith("attached_")) f.delete()
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Keeper", "Cache cleanup failed", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Keeper", "Cache cleanup launch failed", e)
        }

        // One-time username migration
        try {
            if (!prefs.getBoolean("migration_v0_7_name", false)) {
                val email = prefs.getString("user_name_email", "") ?: ""
                if (email.isBlank()) {
                    prefs.edit()
                        .putBoolean("user_name_manual", false)
                        .putString("user_name", "Explorer")
                        .putBoolean("migration_v0_7_name", true)
                        .apply()
                    _userName.value = "Explorer"
                } else {
                    prefs.edit().putBoolean("migration_v0_7_name", true).apply()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Keeper", "Username migration failed", e)
        }

        // Force re-derive on startup if already connected
        try {
            val connectedEmail = prefs.getString("google_email", "") ?: ""
            val isConnected = prefs.getBoolean("google_connected", false)
            if (isConnected && connectedEmail.isNotBlank()) {
                forceUpdateUserNameFromEmail(connectedEmail)
            }
        } catch (e: Exception) {
            android.util.Log.e("Keeper", "Force update username failed", e)
        }

        // Recovery: re-upload notes whose last edit didn't finish syncing
        // (user closed the app before the banner completed).
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(2500) // wait for connection/auth setup
                if (_isGoogleConnected.value && _autoUploadEnabled.value && isOnline.value) {
                    val pending = repository.getAllNotesSync().filter {
                        !it.isTrashed && it.updatedAt > it.driveSyncedAt
                    }
                    if (pending.isNotEmpty()) {
                        android.util.Log.i("Keeper", "Recovery upload: ${pending.size} pending notes")
                        pending.forEach { note -> maybeAutoUploadNote(note.id) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("Keeper", "Pending upload recovery failed", e)
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
     * Always re-derive the display name from the connected account's email.
     * Switching accounts switches the name. The Settings screen can still call
     * setUserName for a manual override, but it'll be reset on the next
     * account switch — that's by design (we want the name to match the account).
     */
    private fun forceUpdateUserNameFromEmail(email: String) {
        val derived = deriveDisplayNameFromEmail(email)
        if (derived.isNotBlank()) {
            _userName.value = derived
            prefs.edit()
                .putString("user_name", derived)
                .putString("user_name_email", email)
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
    val isConnectingAccount: StateFlow<Boolean> = _isConnectingAccount.asStateFlow()

    // Edit-synced banner driven by REAL upload progress. Surfaces only when
    // the user actually edits a note (saveNote/updateNote) — pin/archive/color
    // changes don't pop the banner. Three phases:
    //   SYNCING: live progress bar tied to the upload bytes
    //   DONE:    full bar with check for ~1.2s
    //   HIDDEN:  not shown
    enum class EditBannerPhase { HIDDEN, SYNCING, DONE }
    data class EditBannerState(
        val phase: EditBannerPhase = EditBannerPhase.HIDDEN,
        val progress: Float = 0f
    )
    private val _editBanner = MutableStateFlow(EditBannerState())
    val editBanner: StateFlow<EditBannerState> = _editBanner.asStateFlow()

    // Note IDs that recently had a user-driven edit. The banner only shows
    // when the next upload is for one of these notes.
    private val pendingBannerNotes = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    internal fun markNoteEditedForBanner(noteId: Long) {
        pendingBannerNotes.add(noteId)
    }

    private fun setBannerSyncing(progress: Float) {
        _editBanner.value = EditBannerState(EditBannerPhase.SYNCING, progress)
    }

    private fun setBannerDone() {
        // Brief "done" indicator then hide. Short so the UI feels snappy.
        viewModelScope.launch {
            _editBanner.value = EditBannerState(EditBannerPhase.DONE, 1f)
            kotlinx.coroutines.delay(700)
            _editBanner.value = EditBannerState(EditBannerPhase.HIDDEN, 0f)
        }
    }

    private fun setBannerHidden() {
        _editBanner.value = EditBannerState(EditBannerPhase.HIDDEN, 0f)
    }

    /**
     * When the user picks an account that's DIFFERENT from the previously
     * connected one AND there are local notes, we surface a confirmation
     * dialog before wiping the local DB and importing the new account's notes.
     */
    data class AccountSwitchRequest(val newEmail: String, val previousEmail: String)
    private val _accountSwitchRequest = MutableStateFlow<AccountSwitchRequest?>(null)
    val accountSwitchRequest: StateFlow<AccountSwitchRequest?> = _accountSwitchRequest.asStateFlow()

    fun connectAndSyncGoogleAccount(email: String) {
        // Prevent re-entry while a connect is in flight
        if (_isConnectingAccount.value) return

        // Detect account switch: if we already had a different account, ask
        // the user to confirm wiping local notes. Querying the DB directly
        // here (in a coroutine) instead of relying on the activeNotes Flow
        // value, which can be empty if the Flow hasn't collected yet.
        val previousEmail = prefs.getString("google_email", null)
        if (!previousEmail.isNullOrBlank() && previousEmail != email) {
            _isConnectingAccount.value = false
            viewModelScope.launch(Dispatchers.IO) {
                val hasLocalData = try {
                    repository.getAllNotesSync().isNotEmpty()
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                if (hasLocalData) {
                    _accountSwitchRequest.value = AccountSwitchRequest(
                        newEmail = email,
                        previousEmail = previousEmail
                    )
                } else {
                    _isConnectingAccount.value = true
                    doConnectAccount(email)
                }
            }
            return
        }

        _isConnectingAccount.value = true
        doConnectAccount(email)
    }

    /**
     * User confirmed switching accounts. Best-effort flow:
     *   1) Upload remaining local notes to the OLD account's Drive (safety
     *      net, so nothing is lost if the previous sync missed something).
     *   2) Wipe local DB + attachments.
     *   3) Connect to the new account and import its notes.
     */
    /**
     * User confirmed switching accounts. Sequential phases, each visible
     * to the user via _syncMessage:
     *   1. Sync local notes to the OLD account (safety net)
     *   2. Wipe local DB (HARD delete) + attachments folder
     *   3. Verify the wipe actually emptied the DB (paranoia)
     *   4. Connect to the new account
     *   5. Import notes from the new account's Drive
     */
    fun confirmAccountSwitch() {
        val req = _accountSwitchRequest.value ?: return
        _accountSwitchRequest.value = null
        _isConnectingAccount.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // ── Phase 1: safety sync to OLD account ──
            try {
                val rootId = _keeperRootFolderId.value
                val notes = repository.getAllNotesSync()
                if (!rootId.isNullOrBlank() && notes.isNotEmpty()) {
                    _syncMessage.value = "1/4 Salvataggio finale di ${notes.size} note su ${req.previousEmail}..."
                    for ((index, note) in notes.withIndex()) {
                        _syncMessage.value = "1/4 Sincronizzo nota ${index + 1}/${notes.size}..."
                        try {
                            when (val outcome = driveRepo.uploadNote(req.previousEmail, note, rootId)) {
                                is DriveSyncRepository.SyncOutcome.Success -> {
                                    repository.updateDriveFolder(
                                        note.id,
                                        outcome.folderId,
                                        System.currentTimeMillis()
                                    )
                                }
                                else -> {
                                    android.util.Log.w(
                                        "Keeper",
                                        "Safety sync of note ${note.id} failed; continuing"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("Keeper", "Safety sync exception note ${note.id}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("Keeper", "Phase 1 (safety sync) failed", e)
            }

            // ── Phase 2: HARD wipe local DB + attachments ──
            _syncMessage.value = "2/4 Cancellazione dati locali..."
            try {
                repository.wipeAllNotes()
                val attDir = java.io.File(getApplication<Application>().filesDir, "attachments")
                if (attDir.exists()) attDir.deleteRecursively()
            } catch (e: Exception) {
                android.util.Log.e("Keeper", "Phase 2 (wipe) failed", e)
            }

            // ── Phase 3: verify wipe is effective ──
            _syncMessage.value = "3/4 Verifica..."
            val remaining = try { repository.getAllNotesSync().size } catch (_: Exception) { 0 }
            if (remaining > 0) {
                // Force a second wipe if anything slipped through
                android.util.Log.w("Keeper", "Wipe verification: $remaining notes remained, retrying")
                try { repository.wipeAllNotes() } catch (_: Exception) {}
            }

            // Reset cached root folder so we don't reuse the OLD one
            _keeperRootFolderId.value = null
            prefs.edit().remove("keeper_root_folder_id").apply()

            // ── Phase 4: connect to new account & import ──
            _syncMessage.value = "4/4 Connessione a ${req.newEmail}..."
            doConnectAccount(req.newEmail)
        }
    }

    /** User cancelled: keep current state, abort the switch. */
    fun cancelAccountSwitch() {
        _accountSwitchRequest.value = null
        _isConnectingAccount.value = false
    }

    private fun doConnectAccount(email: String) {
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
                    forceUpdateUserNameFromEmail(email)
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
                                // Drive banner progress from real bytes if this
                                // note was flagged as user-edited.
                                if (pendingBannerNotes.contains(note.id) && total > 0) {
                                    val p = (uploaded.toFloat() / total).coerceIn(0f, 1f)
                                    setBannerSyncing(p)
                                }
                            }
                        }
                    )
                    when (outcome) {
                        is DriveSyncRepository.SyncOutcome.Success -> {
                            repository.updateDriveFolder(note.id, outcome.folderId, System.currentTimeMillis())
                            if (pendingBannerNotes.contains(note.id)) {
                                pendingBannerNotes.remove(note.id)
                                setBannerDone()
                            }
                        }
                        is DriveSyncRepository.SyncOutcome.NeedsUserAction -> {
                            _pendingAuthIntent.value = outcome.intent
                            if (pendingBannerNotes.contains(note.id)) {
                                pendingBannerNotes.remove(note.id)
                                setBannerHidden()
                            }
                        }
                        is DriveSyncRepository.SyncOutcome.Failure -> {
                            android.util.Log.w("DriveSync", "Auto-upload note $noteId: ${outcome.message}")
                            if (pendingBannerNotes.contains(note.id)) {
                                pendingBannerNotes.remove(note.id)
                                setBannerHidden()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    pendingBannerNotes.remove(noteId)
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
            markNoteEditedForBanner(newId)
            maybeAutoUploadNote(newId)
            refreshWidgets()
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note)
            markNoteEditedForBanner(note.id)
            maybeAutoUploadNote(note.id)
            refreshWidgets()
        }
    }

    // Fast Note status changes
    fun togglePinNote(noteId: Long) {
        viewModelScope.launch {
            repository.togglePin(noteId)
            maybeAutoUploadNote(noteId)
            refreshWidgets()
        }
    }

    fun trashNote(noteId: Long) {
        viewModelScope.launch {
            repository.trashNote(noteId)
            maybeAutoUploadNote(noteId)
            refreshWidgets()
        }
    }

    fun restoreNote(noteId: Long) {
        viewModelScope.launch {
            repository.restoreNote(noteId)
            maybeAutoUploadNote(noteId)
            refreshWidgets()
        }
    }

    fun archiveNote(noteId: Long) {
        viewModelScope.launch {
            repository.archiveNote(noteId)
            maybeAutoUploadNote(noteId)
            refreshWidgets()
        }
    }

    fun unarchiveNote(noteId: Long) {
        viewModelScope.launch {
            repository.unarchiveNote(noteId)
            maybeAutoUploadNote(noteId)
            refreshWidgets()
        }
    }

    fun deleteNotePermanently(note: Note) {
        viewModelScope.launch {
            repository.deleteNotePermanently(note)
            maybeDeleteNoteFolder(note.driveFolderId)
            refreshWidgets()
        }
    }

    // Progress state shown while emptying the trash (LOCAL + remote Drive).
    data class EmptyTrashState(
        val active: Boolean = false,
        val processed: Int = 0,
        val total: Int = 0
    )
    private val _emptyTrashState = MutableStateFlow(EmptyTrashState())
    val emptyTrashState: StateFlow<EmptyTrashState> = _emptyTrashState.asStateFlow()

    fun emptyTrash() {
        viewModelScope.launch {
            val trashedBefore = try {
                repository.getTrashedNotesSync()
            } catch (e: Exception) {
                emptyList()
            }
            val total = trashedBefore.size
            _emptyTrashState.value = EmptyTrashState(active = true, processed = 0, total = total)

            // Wipe locally first (instant)
            try { repository.emptyTrash() } catch (e: Exception) { e.printStackTrace() }

            // Then walk Drive folder deletions, advancing progress as we go
            trashedBefore.forEachIndexed { index, note ->
                try {
                    maybeDeleteNoteFolder(note.driveFolderId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                _emptyTrashState.value = EmptyTrashState(
                    active = true,
                    processed = index + 1,
                    total = total
                )
            }
            // Brief "done" state then hide
            kotlinx.coroutines.delay(700)
            _emptyTrashState.value = EmptyTrashState(active = false, processed = 0, total = 0)
            refreshWidgets()
        }
    }
}
