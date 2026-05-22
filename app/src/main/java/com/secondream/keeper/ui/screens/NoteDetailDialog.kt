package com.secondream.keeper.ui.screens

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.secondream.keeper.R
import com.secondream.keeper.data.model.Attachment
import com.secondream.keeper.data.model.ChecklistItem
import com.secondream.keeper.data.model.Note
import com.secondream.keeper.data.model.KeepColors
import com.secondream.keeper.viewmodel.NoteViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailView(
    note: Note?, // Null means "Create New Note"
    viewModel: NoteViewModel,
    initialLaunchType: String? = null,
    onDismiss: () -> Unit
) {
    // Current draft notes fields
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var colorHex by remember { mutableStateOf(note?.colorHex ?: viewModel.defaultNoteColor.value) }
    var isPinned by remember { mutableStateOf(note?.isPinned ?: false) }
    var labelsCSV by remember { mutableStateOf(note?.labels ?: "") }

    // Checklists items
    var checklistItems by remember {
        mutableStateOf(note?.getChecklist() ?: emptyList())
    }
    var isChecklistActive by remember {
        mutableStateOf(note?.getChecklist()?.isNotEmpty() == true)
    }
    var newChecklistItemText by remember { mutableStateOf("") }

    // Attachments lists
    val tempAttachments by viewModel.tempAttachments.collectAsState()
    val uploadingTasks by viewModel.uploadingTasks.collectAsState()
    var savedAttachments by remember {
        mutableStateOf(note?.getAttachments() ?: emptyList())
    }

    // Combine saved and newly simulated uploaded attachments
    val allAttachments = savedAttachments + tempAttachments

    // Color selector state visibility
    var showColorTray by remember { mutableStateOf(false) }
    var showLabelPicker by remember { mutableStateOf(false) }

    // Media viewer active state
    var activeViewerAttachment by remember { mutableStateOf<Attachment?>(null) }

    // Synchronize newly completed uploads into note detail dialog
    LaunchedEffect(note?.id) {
        viewModel.clearTempAttachments()
    }

    val themeIsDark = isSystemInDarkThemeCustom(viewModel)
    val cardBackground = KeepColors.getColorForHex(colorHex, themeIsDark)
    val contentColor = KeepColors.getContentColorForHex(colorHex, themeIsDark)

    val context = LocalContext.current

    // Voice dictation states
    var showVoiceTray by remember { mutableStateOf(false) }
    var dictationText by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    // Real on-device speech recognition (free, no API key)
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    // Buffer that retains text confirmed before the current listening session so partial
    // results from the recognizer never overwrite previously dictated content.
    var dictationBaseText by remember { mutableStateOf("") }

    DisposableEffect(speechRecognizer) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val startSpeechRecognition: () -> Unit = startRec@{
        val rec = speechRecognizer
        if (rec == null) {
            voiceError = "Riconoscimento vocale non disponibile su questo dispositivo"
            return@startRec
        }
        voiceError = null
        dictationBaseText = if (dictationText.isBlank()) "" else dictationText.trimEnd() + " "
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isRecordingVoice = false
                voiceError = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Errore audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Errore client"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permesso microfono mancante"
                    SpeechRecognizer.ERROR_NETWORK -> "Errore di rete"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout di rete"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nessun parlato riconosciuto"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Riconoscitore occupato, riprova"
                    SpeechRecognizer.ERROR_SERVER -> "Errore del server"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nessun parlato rilevato"
                    else -> "Errore $error"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    dictationText = dictationBaseText + text
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    dictationText = dictationBaseText + text
                    dictationBaseText = dictationText.trimEnd() + " "
                }
                isRecordingVoice = false
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        try {
            rec.startListening(intent)
            isRecordingVoice = true
        } catch (e: Exception) {
            e.printStackTrace()
            voiceError = "Impossibile avviare il riconoscimento: ${e.message}"
            isRecordingVoice = false
        }
    }

    val stopSpeechRecognition: () -> Unit = {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isRecordingVoice = false
    }

    // Mic permission launcher: starts recognition immediately on grant
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognition()
        } else {
            voiceError = "Permesso microfono negato"
        }
    }

    val toggleVoiceDictation: () -> Unit = {
        if (isRecordingVoice) {
            stopSpeechRecognition()
        } else {
            val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            if (perm == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Handlers for actual launcher selections
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            var name = "attached_file"
            var size = "Unknown size"
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx)
                        if (sizeIdx != -1) {
                            val bytes = cursor.getLong(sizeIdx)
                            size = when {
                                bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                                bytes >= 1024 -> String.format("%d KB", bytes / 1024)
                                else -> "$bytes Bytes"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (name == "attached_file") {
                name = "Image_${System.currentTimeMillis()}.jpg"
            }
            val copyResult = copyUriToLocalFileSafe(context, it)
            when (copyResult) {
                is CopyResult.Success -> {
                    savedAttachments = savedAttachments + Attachment(
                        id = java.util.UUID.randomUUID().toString(),
                        type = "image",
                        uri = copyResult.fileUri,
                        name = name,
                        size = size
                    )
                }
                CopyResult.TooLarge -> {
                    android.widget.Toast.makeText(
                        context,
                        "Immagine troppo grande (max 30 MB)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                CopyResult.Error -> {
                    android.widget.Toast.makeText(
                        context, "Errore durante l'aggiunta dell'immagine",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            var name = "attached_file"
            var size = "Unknown size"
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx)
                        if (sizeIdx != -1) {
                            val bytes = cursor.getLong(sizeIdx)
                            size = when {
                                bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                                bytes >= 1024 -> String.format("%d KB", bytes / 1024)
                                else -> "$bytes Bytes"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (name == "attached_file") {
                name = "Video_${System.currentTimeMillis()}.mp4"
            }
            val copyResultV = copyUriToLocalFileSafe(context, it)
            when (copyResultV) {
                is CopyResult.Success -> {
                    savedAttachments = savedAttachments + Attachment(
                        id = java.util.UUID.randomUUID().toString(),
                        type = "video",
                        uri = copyResultV.fileUri,
                        name = name,
                        size = size
                    )
                }
                CopyResult.TooLarge -> {
                    android.widget.Toast.makeText(
                        context,
                        "Video troppo grande (max 30 MB)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                CopyResult.Error -> {
                    android.widget.Toast.makeText(
                        context, "Errore durante l'aggiunta del video",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            var name = "attached_file"
            var size = "Unknown size"
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx)
                        if (sizeIdx != -1) {
                            val bytes = cursor.getLong(sizeIdx)
                            size = when {
                                bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                                bytes >= 1024 -> String.format("%d KB", bytes / 1024)
                                else -> "$bytes Bytes"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (name == "attached_file") {
                name = "Document_${System.currentTimeMillis()}.pdf"
            }
            val copyResultF = copyUriToLocalFileSafe(context, it)
            when (copyResultF) {
                is CopyResult.Success -> {
                    savedAttachments = savedAttachments + Attachment(
                        id = java.util.UUID.randomUUID().toString(),
                        type = "file",
                        uri = copyResultF.fileUri,
                        name = name,
                        size = size
                    )
                }
                CopyResult.TooLarge -> {
                    android.widget.Toast.makeText(
                        context,
                        "File troppo grande (max 30 MB)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                CopyResult.Error -> {
                    android.widget.Toast.makeText(
                        context, "Errore durante l'aggiunta del file",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Trigger initial picker or setup on startup
    LaunchedEffect(initialLaunchType) {
        when (initialLaunchType) {
            "todo" -> {
                isChecklistActive = true
            }
            "image" -> {
                try {
                    imageLauncher.launch("image/*")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            "file" -> {
                try {
                    fileLauncher.launch("*/*")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }


    // Snapshot of the note's state at OPEN time — used to decide whether
    // the user actually made changes. If nothing differs at close time, we
    // skip the update entirely (so no Drive banner triggers on back nav).
    val initialTitle = remember(note?.id) { note?.title ?: "" }
    val initialContent = remember(note?.id) { note?.content ?: "" }
    val initialColorHex = remember(note?.id) { note?.colorHex ?: "" }
    val initialIsPinned = remember(note?.id) { note?.isPinned ?: false }
    val initialLabels = remember(note?.id) { note?.labels ?: "" }
    val initialChecklistJson = remember(note?.id) { note?.checklistJson ?: "" }
    val initialAttachmentsJson = remember(note?.id) { note?.attachmentsJson ?: "" }

    val saveAndDismiss: () -> Unit = {
        val currentChecklistJson = if (checklistItems.isNotEmpty())
            ChecklistItem.toJsonArray(checklistItems) else ""
        val currentAttachmentsJson = if (allAttachments.isNotEmpty())
            Attachment.toJsonArray(allAttachments) else ""

        if (note != null) {
            // Existing note: only update if at least one field actually changed
            val changed =
                title != initialTitle ||
                content != initialContent ||
                colorHex != initialColorHex ||
                isPinned != initialIsPinned ||
                labelsCSV != initialLabels ||
                currentChecklistJson != initialChecklistJson ||
                currentAttachmentsJson != initialAttachmentsJson

            if (changed) {
                viewModel.updateNote(
                    note.copy(
                        title = title,
                        content = content,
                        colorHex = colorHex,
                        isPinned = isPinned,
                        labels = labelsCSV,
                        checklistJson = currentChecklistJson,
                        attachmentsJson = currentAttachmentsJson
                    )
                )
            }
        } else {
            // New note: only create if the user typed/added something
            if (title.isNotBlank() || content.isNotBlank() ||
                checklistItems.isNotEmpty() || allAttachments.isNotEmpty()) {
                viewModel.createNote(
                    title = title,
                    content = content,
                    colorHex = colorHex,
                    labelsList = labelsCSV.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    checklistItems = checklistItems,
                    attachmentsList = allAttachments,
                    isPinned = isPinned
                )
            }
        }
        onDismiss()
    }

    BackHandler(enabled = true) {
        saveAndDismiss()
    }

    val focusManager = LocalFocusManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(if (note == null) stringResource(R.string.new_note_title) else stringResource(R.string.edit_note_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = cardBackground,
                        titleContentColor = contentColor,
                        navigationIconContentColor = contentColor,
                        actionIconContentColor = contentColor
                    ),
                    navigationIcon = {
                        IconButton(onClick = { saveAndDismiss() }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back_and_save))
                        }
                    },
                    actions = {
                        // Share
                        IconButton(onClick = {
                            val paths = allAttachments
                                .map { it.uri }
                                .filter { it.startsWith("file://") || it.startsWith("/") }
                            com.secondream.keeper.util.FileOpener.shareNote(
                                context = context,
                                title = title,
                                content = content,
                                attachmentPaths = paths
                            )
                        }) {
                            Icon(Icons.Filled.Share, stringResource(R.string.share_note))
                        }

                        // Pin — toggle, save & exit. Stays as plain top-bar icon
                        // so it inherits contentColor (black on light, white on
                        // dark) and is always readable.
                        IconButton(onClick = {
                            isPinned = !isPinned
                            saveAndDismiss()
                        }) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.pin_tooltip)
                            )
                        }

                        if (note != null) {
                            // Archive — toggle & exit
                            IconButton(onClick = {
                                if (note.isArchived) viewModel.unarchiveNote(note.id)
                                else viewModel.archiveNote(note.id)
                                onDismiss()
                            }) {
                                Icon(
                                    imageVector = if (note.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                    contentDescription = if (note.isArchived)
                                        stringResource(R.string.unarchive_note)
                                    else
                                        stringResource(R.string.archive_tooltip)
                                )
                            }

                            // Delete — confirm then trash & exit
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.trash_tooltip)
                                )
                            }
                        }
                    }
                )
            },
            containerColor = cardBackground,
            contentColor = contentColor
        ) { padding ->
            // Delete confirmation
            if (showDeleteConfirm && note != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = {
                        Text("Eliminare la nota?", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text("La nota verrà spostata nel cestino. Puoi recuperarla finché non svuoti il cestino.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                viewModel.trashNote(note.id)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Elimina", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Annulla")
                        }
                    },
                    shape = RoundedCornerShape(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Main scroll container
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Attachment Previews section (Always show beautiful grid)
                    if (allAttachments.isNotEmpty()) {
                        val attachmentTitle = if (allAttachments.size == 1) {
                            stringResource(R.string.attachments_badge, 1)
                        } else {
                            stringResource(R.string.attachments_badge_plural, allAttachments.size)
                        }
                        Text(
                            text = attachmentTitle.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allAttachments.forEach { attachment ->
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.05f))
                                        .border(1.dp, contentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .clickable { activeViewerAttachment = attachment }
                                ) {
                                    if (attachment.type == "image") {
                                        val modelToLoad: Any = if (attachment.uri.startsWith("file://")) {
                                            java.io.File(attachment.uri.removePrefix("file://"))
                                        } else if (attachment.uri.startsWith("content://")) {
                                            Uri.parse(attachment.uri)
                                        } else {
                                            attachment.uri
                                        }
                                        AsyncImage(
                                            model = modelToLoad,
                                            contentDescription = attachment.name,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // Video / File Custom Cards
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = if (attachment.type == "video") Icons.Default.PlayCircle else Icons.Default.Description,
                                                contentDescription = attachment.type,
                                                tint = contentColor.copy(alpha = 0.8f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Text(
                                                text = attachment.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                color = contentColor
                                            )
                                            Text(
                                                text = attachment.size,
                                                fontSize = 10.sp,
                                                color = contentColor.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    // Remove attachment icon button
                                    IconButton(
                                        onClick = {
                                            if (tempAttachments.contains(attachment)) {
                                                viewModel.removeTempAttachment(attachment.id)
                                            } else {
                                                savedAttachments = savedAttachments.filter { it.id != attachment.id }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.TopEnd)
                                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Remove File",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Active simulation upload tasks (Linear progress bars)
                    if (uploadingTasks.isNotEmpty()) {
                        Text(
                            text = "SIMULATED FILE UPLOADING...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Bold
                        )
                        uploadingTasks.forEach { task ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (task.type == "video") Icons.Filled.VideoFile else Icons.Filled.InsertDriveFile,
                                        contentDescription = task.type,
                                        tint = contentColor
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(task.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
                                        LinearProgressIndicator(
                                            progress = { task.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = Color(0xFFF57C00),
                                            trackColor = Color.LightGray.copy(alpha = 0.4f),
                                        )
                                        Text(
                                            "${(task.progress * 100).toInt()}% • ${task.size}",
                                            fontSize = 11.sp,
                                            color = contentColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Note Inputs
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text(stringResource(R.string.title_placeholder), fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = contentColor
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = contentColor,
                            unfocusedTextColor = contentColor,
                            focusedPlaceholderColor = contentColor.copy(alpha = 0.4f),
                            unfocusedPlaceholderColor = contentColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text(stringResource(R.string.content_placeholder), fontSize = 16.sp) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = contentColor),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = contentColor,
                            unfocusedTextColor = contentColor,
                            focusedPlaceholderColor = contentColor.copy(alpha = 0.4f),
                            unfocusedPlaceholderColor = contentColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )

                    // Removed redundant Immagine/Allegato/Checklist chips
                    // (same actions already available in the bottom toolbar).

                    Spacer(modifier = Modifier.height(8.dp))

                    // Checklist Section (if activated/non-empty)
                    if (isChecklistActive || checklistItems.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.03f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // No more bulky "ELEMENTI CHECKLIST (N)" header — let
                                // the list speak for itself. Only a small "convert"
                                // icon-button in the top-end corner when items exist.
                                if (checklistItems.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val checklistText = checklistItems.joinToString("\n") { item ->
                                                    if (item.checked) "• [x] ${item.text}" else "• [ ] ${item.text}"
                                                }
                                                content = if (content.isNotBlank()) "$content\n\n$checklistText" else checklistText
                                                checklistItems = emptyList()
                                                isChecklistActive = false
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.TextFields,
                                                contentDescription = stringResource(R.string.convert_to_text),
                                                tint = contentColor.copy(alpha = 0.55f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Render dynamic editable checklist list
                                Column {
                                    checklistItems.forEachIndexed { idx, item ->
                                        // Material 3 Expressive style row: pill background, animated check,
                                        // strike-through on the text with subtle fade
                                        val rowBg by animateColorAsState(
                                            targetValue = if (item.checked)
                                                contentColor.copy(alpha = 0.06f)
                                            else
                                                contentColor.copy(alpha = 0.10f),
                                            animationSpec = tween(220),
                                            label = "checklist_row_bg"
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(rowBg)
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Custom circular check button (Pixel/Keep style, much
                                            // bigger and more tappable than default Checkbox)
                                            val tickScale by animateFloatAsState(
                                                targetValue = if (item.checked) 1f else 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                ),
                                                label = "tick_scale"
                                            )
                                            val tickContainerColor by animateColorAsState(
                                                targetValue = if (item.checked)
                                                    Color(0xFFFFCA28)
                                                else
                                                    Color.Transparent,
                                                animationSpec = tween(220),
                                                label = "tick_container"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(tickContainerColor)
                                                    .border(
                                                        width = if (item.checked) 0.dp else 1.6.dp,
                                                        color = contentColor.copy(alpha = 0.55f),
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        checklistItems = checklistItems.mapIndexed { i, it ->
                                                            if (i == idx) it.copy(checked = !it.checked) else it
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF1A1A1A),
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .graphicsLayer {
                                                            scaleX = tickScale
                                                            scaleY = tickScale
                                                            alpha = tickScale
                                                        }
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            TextField(
                                                value = item.text,
                                                onValueChange = { newTxt ->
                                                    checklistItems = checklistItems.mapIndexed { i, it ->
                                                        if (i == idx) it.copy(text = newTxt) else it
                                                    }
                                                },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 15.sp,
                                                    fontWeight = if (item.checked) FontWeight.Normal else FontWeight.Medium,
                                                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                                                    color = if (item.checked) contentColor.copy(alpha = 0.45f) else contentColor
                                                ),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    disabledContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent,
                                                    focusedTextColor = contentColor,
                                                    unfocusedTextColor = contentColor
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    checklistItems = checklistItems.filterIndexed { i, _ -> i != idx }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Delete item",
                                                    tint = contentColor.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Quick Append Checklist Item
                                    val newItemFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "New item link",
                                            tint = contentColor.copy(alpha = 0.6f)
                                        )
                                        TextField(
                                            value = newChecklistItemText,
                                            onValueChange = { newChecklistItemText = it },
                                            placeholder = { Text(stringResource(R.string.checklist_placeholder), fontSize = 14.sp) },
                                            singleLine = true,
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                                            ),
                                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                                onDone = {
                                                    if (newChecklistItemText.isNotBlank()) {
                                                        checklistItems = checklistItems + ChecklistItem(newChecklistItemText)
                                                        newChecklistItemText = ""
                                                        // Keep focus on this field so the user can keep adding items
                                                        // without re-tapping the field
                                                        try { newItemFocusRequester.requestFocus() } catch (_: Exception) {}
                                                    }
                                                }
                                            ),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                focusedTextColor = contentColor,
                                                unfocusedTextColor = contentColor
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(newItemFocusRequester)
                                        )
                                        IconButton(
                                            onClick = {
                                                if (newChecklistItemText.isNotBlank()) {
                                                    checklistItems = checklistItems + ChecklistItem(newChecklistItemText)
                                                    newChecklistItemText = ""
                                                }
                                            },
                                            enabled = newChecklistItemText.isNotBlank()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Save Checklist Item",
                                                tint = if (newChecklistItemText.isNotBlank()) Color(0xFF2E7D32) else Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Direct Labels Input Bar
                    if (showLabelPicker) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = labelsCSV,
                                onValueChange = { labelsCSV = it },
                                label = { Text(stringResource(R.string.add_tags_label)) },
                                placeholder = { Text("Esempio: Spesa, Lavoro, Idee") },
                                supportingText = { Text("Inserisci le etichette separate da virgole", fontSize = 11.sp, color = contentColor.copy(alpha = 0.6f)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = contentColor,
                                    unfocusedBorderColor = contentColor.copy(alpha = 0.3f),
                                    focusedTextColor = contentColor,
                                    unfocusedTextColor = contentColor,
                                    focusedLabelColor = contentColor,
                                    unfocusedLabelColor = contentColor.copy(alpha = 0.6f),
                                    focusedPlaceholderColor = contentColor.copy(alpha = 0.4f),
                                    unfocusedPlaceholderColor = contentColor.copy(alpha = 0.4f),
                                    cursorColor = contentColor
                                )
                            )

                            // Quick tag chips suggestions
                            val quickSuggestionSet = listOf("Personale", "Lavoro", "Idee", "Viaggi", "Sincronizzato", "Cloud")
                            val currentLabels = labelsCSV.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

                            Text(
                                text = "Tocca un tag rapido per aggiungerlo o rimuoverlo:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor.copy(alpha = 0.6f)
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                quickSuggestionSet.forEach { quickTag ->
                                    val isTagged = currentLabels.any { it.equals(quickTag, ignoreCase = true) }
                                    val chipBg = if (isTagged) contentColor.copy(alpha = 0.12f) else Color.Transparent
                                    val chipBorderColor = if (isTagged) contentColor else contentColor.copy(alpha = 0.25f)

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(chipBg)
                                            .border(1.dp, chipBorderColor, RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (isTagged) {
                                                    // Case-insensitive removal
                                                    labelsCSV = currentLabels
                                                        .filter { !it.equals(quickTag, ignoreCase = true) }
                                                        .joinToString(", ")
                                                } else {
                                                    // Add
                                                    labelsCSV = if (labelsCSV.trim().isBlank()) {
                                                        quickTag
                                                    } else {
                                                        "${labelsCSV.trim().removeSuffix(",")}, $quickTag"
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isTagged) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active tag indicator",
                                                    tint = contentColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = quickTag,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = contentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (labelsCSV.isNotBlank()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            labelsCSV.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { label ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = contentColor.copy(alpha = 0.08f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = contentColor)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove tag",
                                            tint = contentColor.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable {
                                                    labelsCSV = labelsCSV
                                                        .split(",")
                                                        .map { it.trim() }
                                                        .filter { it.isNotEmpty() && it != label }
                                                        .joinToString(",")
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(110.dp)) // Leave room for action toolbar
                }

                // BOTTOM ACTION TOOLBAR
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(cardBackground)
                ) {
                    Divider(color = contentColor.copy(alpha = 0.15f))

                    // Expanded Color Tray Panel
                    AnimatedVisibility(
                        visible = showColorTray,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardBackground)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.select_bg_color),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(KeepColors.colors) { item ->
                                    val itemBgColor = Color(android.graphics.Color.parseColor(if (themeIsDark) item.darkHex else item.lightHex))
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(itemBgColor)
                                            .border(
                                                width = if (colorHex == item.lightHex || colorHex == item.darkHex) 3.dp else 1.dp,
                                                color = if (colorHex == item.lightHex || colorHex == item.darkHex) Color(0xFFFF9100) else Color.Gray.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                colorHex = if (themeIsDark) item.darkHex else item.lightHex
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (colorHex == item.lightHex || colorHex == item.darkHex) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Chosen color",
                                                tint = Color(0xFFFF9100)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Voice Dictation: now opened as a Material bottom sheet
                    if (showVoiceTray) {
                        VoiceRecorderSheet(
                            isRecording = isRecordingVoice,
                            dictationText = dictationText,
                            voiceError = voiceError,
                            onToggleRecording = { toggleVoiceDictation() },
                            onDismiss = {
                                if (isRecordingVoice) stopSpeechRecognition()
                                showVoiceTray = false
                                voiceError = null
                            },
                            onConfirm = { editedText ->
                                if (isRecordingVoice) stopSpeechRecognition()
                                val finalText = editedText.ifBlank { dictationText }
                                content = if (content.isNotBlank()) "$content\n$finalText" else finalText
                                dictationText = ""
                                dictationBaseText = ""
                                showVoiceTray = false
                                voiceError = null
                            }
                        )
                    }

                    // Main Tool buttons — colored card chips below each icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val accentChipColor = MaterialTheme.colorScheme.primary
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ToolbarChip(
                                icon = Icons.Outlined.Palette,
                                tint = accentChipColor,
                                contentDescription = stringResource(R.string.palette_tooltip),
                                onClick = { showColorTray = !showColorTray }
                            )
                            ToolbarChip(
                                icon = Icons.Outlined.Label,
                                tint = accentChipColor,
                                contentDescription = stringResource(R.string.tags_tooltip),
                                onClick = { showLabelPicker = !showLabelPicker }
                            )
                            ToolbarChip(
                                icon = Icons.Outlined.CheckBox,
                                tint = accentChipColor,
                                contentDescription = stringResource(R.string.checklist_tooltip),
                                onClick = { isChecklistActive = true }
                            )
                            ToolbarChip(
                                icon = if (showVoiceTray) Icons.Filled.Mic else Icons.Outlined.Mic,
                                tint = accentChipColor,
                                contentDescription = stringResource(R.string.voice_recorder_title),
                                onClick = { showVoiceTray = !showVoiceTray },
                                active = showVoiceTray
                            )
                        }

                        // Attachment picker
                        var showAttachmentMenu by remember { mutableStateOf(false) }
                        ToolbarChip(
                            icon = Icons.Default.AttachFile,
                            tint = accentChipColor,
                            contentDescription = stringResource(R.string.attach_file_tooltip),
                            onClick = { showAttachmentMenu = true }
                        )
                        if (showAttachmentMenu) {
                            AttachmentPickerSheet(
                                onDismiss = { showAttachmentMenu = false },
                                onImage = {
                                    showAttachmentMenu = false
                                    imageLauncher.launch("image/*")
                                },
                                onVideo = {
                                    showAttachmentMenu = false
                                    videoLauncher.launch("video/*")
                                },
                                onFile = {
                                    showAttachmentMenu = false
                                    fileLauncher.launch("*/*")
                                }
                            )
                        }
                    }
                }
            }
        }

    // Attachment Full-Screen media playing modal — for image/video/audio only.
    // For file-type attachments (PDF, ZIP, doc, etc.) launch the system intent
    // so the user picks their preferred external app.
    activeViewerAttachment?.let { attachment ->
        if (attachment.type == "file") {
            LaunchedEffect(attachment.id) {
                com.secondream.keeper.util.FileOpener.openFile(
                    context = context,
                    filePath = attachment.uri,
                    displayName = attachment.name
                )
                activeViewerAttachment = null
            }
        } else {
            MediaViewerDialog(
                attachment = attachment,
                onDismiss = { activeViewerAttachment = null }
            )
        }
    }
}

@Composable
fun isSystemInDarkThemeCustom(viewModel: NoteViewModel): Boolean {
    val darkPref by viewModel.darkThemeOption.collectAsState()
    return when (darkPref) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
}

/** Max attachment size in bytes (30 MB). Attempts above this are refused. */
const val MAX_ATTACHMENT_SIZE_BYTES: Long = 30L * 1024L * 1024L

/**
 * Result of an attachment copy attempt: either a successful URI or a typed failure.
 */
sealed class CopyResult {
    data class Success(val fileUri: String) : CopyResult()
    object TooLarge : CopyResult()
    object Error : CopyResult()
}

/**
 * Copy a content URI to the app's private files folder (persistent, NOT cache).
 * Rejects attachments larger than [MAX_ATTACHMENT_SIZE_BYTES].
 */
fun copyUriToLocalFileSafe(context: android.content.Context, uri: Uri): CopyResult {
    return try {
        // Check size first via OpenableColumns or stat
        val sizeBytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()
        if (sizeBytes != null && sizeBytes > MAX_ATTACHMENT_SIZE_BYTES) {
            return CopyResult.TooLarge
        }

        val inputStream = context.contentResolver.openInputStream(uri) ?: return CopyResult.Error
        val type = context.contentResolver.getType(uri)
        val extension = when {
            type?.startsWith("image/") == true -> "jpg"
            type?.startsWith("video/") == true -> "mp4"
            else -> "pdf"
        }

        // Persistent storage: filesDir is preserved across cache wipes / OS cleanups
        val attachmentsDir = java.io.File(context.filesDir, "attachments").apply { mkdirs() }
        val file = java.io.File(attachmentsDir, "att_${System.currentTimeMillis()}.$extension")

        var bytesCopied = 0L
        inputStream.use { input ->
            file.outputStream().use { output ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    bytesCopied += n
                    if (bytesCopied > MAX_ATTACHMENT_SIZE_BYTES) {
                        // Streaming exceeded the limit — abort and clean up
                        output.close()
                        file.delete()
                        return CopyResult.TooLarge
                    }
                }
            }
        }
        CopyResult.Success("file://" + file.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
        CopyResult.Error
    }
}

/**
 * Legacy helper kept for backward compatibility: returns the URI string,
 * with the new size check applied. Empty string means rejected/failed.
 */
fun copyUriToLocalFile(context: android.content.Context, uri: Uri): String {
    return when (val r = copyUriToLocalFileSafe(context, uri)) {
        is CopyResult.Success -> r.fileUri
        else -> ""
    }
}

// ToolbarChip composable is defined in NoteEditorComponents.kt
