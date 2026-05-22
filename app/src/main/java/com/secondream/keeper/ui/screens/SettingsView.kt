package com.secondream.keeper.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondream.keeper.R
import com.secondream.keeper.data.model.KeepColors
import com.secondream.keeper.viewmodel.NoteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsView(
    viewModel: NoteViewModel
) {
    val darkThemePref by viewModel.darkThemeOption.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val defaultColorHex by viewModel.defaultNoteColor.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val context = LocalContext.current

    // Google integration collections
    val isGoogleConnected by viewModel.isGoogleConnected.collectAsState()
    val googleEmail by viewModel.googleEmail.collectAsState()
    val activeNotes by viewModel.activeNotes.collectAsState(emptyList())

    val isSyncingNotes by viewModel.isSyncingNotes.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(userName) }

    val neverBackupStr = stringResource(R.string.never_backed_up)
    val justNowStr = stringResource(R.string.just_now)

    // Simulated backup state
    var isBackingUp by remember { mutableStateOf(false) }
    var backupProgress by remember { mutableFloatStateOf(0f) }
    var backupStatusMsg by remember { mutableStateOf("") }
    var lastBackupTime by remember(neverBackupStr) { mutableStateOf(neverBackupStr) }

    var showAccountChooser by remember { mutableStateOf(false) }
    // Note: showAccountChooser is now unused (fake chooser removed), kept as
    // a placeholder for future re-introduction of an in-app picker.
    var isConnectingGoogle by remember { mutableStateOf(false) }
    var customEmailInput by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val accountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                viewModel.connectAndSyncGoogleAccount(accountName)
            }
        }
    }

    val triggerGooglePicker = {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            accountChooserLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                context,
                "Impossibile aprire il selettore account",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_hdr),
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.settings_sub),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 1. App Theme Card selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.app_theme_header),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark)
                ).forEach { (opt, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDarkThemeOption(opt) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = desc, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        RadioButton(
                            selected = darkThemePref == opt,
                            onClick = { viewModel.setDarkThemeOption(opt) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // Accent color picker
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Colore accento",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                val accentArgb by viewModel.accentColorArgb.collectAsState()
                val accentPalette = listOf(
                    0xFFFFCA28L, // Keep yellow (default)
                    0xFF3B82F6L, // blue
                    0xFFEC4899L, // pink
                    0xFF10B981L, // green
                    0xFF8B5CF6L, // purple
                    0xFFF97316L, // orange
                    0xFF06B6D4L, // cyan
                    0xFF6B7280L  // gray
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(accentPalette) { argb ->
                        val color = Color(argb)
                        val isSelected = argb == accentArgb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setAccentColor(argb) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF1A1A1A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Default Card Color selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.default_note_color_header),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(KeepColors.colors) { item ->
                        val itemColor = Color(android.graphics.Color.parseColor(item.lightHex))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(itemColor)
                                .border(
                                    width = if (defaultColorHex == item.lightHex || defaultColorHex == item.darkHex) 3.dp else 1.dp,
                                    color = if (defaultColorHex == item.lightHex || defaultColorHex == item.darkHex) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    viewModel.setDefaultNoteColor(item.lightHex)
                                }
                        )
                    }
                }
            }
        }

        // 3. Edit Username
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.explorer_profile_header),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (editingName) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.primary_user, userName),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (editingName) {
                            if (nameInput.isNotBlank()) {
                                viewModel.setUserName(nameInput)
                            }
                        } else {
                            nameInput = userName
                        }
                        editingName = !editingName
                    }
                ) {
                    Icon(
                        imageVector = if (editingName) Icons.Default.CheckCircle else Icons.Default.Edit,
                        contentDescription = "Edit profiles",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4. Google Account Management Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.google_account_header),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isGoogleConnected) {
                    // Derive avatar initial from the email itself — no more
                    // hardcoded "Eugenio Casale" fallback that hijacked the UI.
                    val initial = googleEmail.firstOrNull()?.uppercase() ?: "?"
                    val avatarColor = MaterialTheme.colorScheme.primary

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Just the email, no fake display name
                            Text(
                                text = googleEmail,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Separated right-aligned TextButton to prevent clipping
                        TextButton(
                            onClick = { viewModel.disconnectGoogleAccount() },
                            modifier = Modifier.align(Alignment.End),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Disconnect icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.disconnect_google_account),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Sign In flow (Spacious, modern, and perfectly robust)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Nessun account connesso",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )

                        Button(
                            onClick = { triggerGooglePicker() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = "Google account icon",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.connect_google_account),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Cloud Backup Card (Requires Google Connected)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.google_backup_header),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = stringResource(R.string.google_backup_sub),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isGoogleConnected) {
                    AnimatedVisibility(visible = isBackingUp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { backupProgress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                            Text(
                                text = backupStatusMsg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    AnimatedVisibility(visible = !isBackingUp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.last_sync_label, lastBackupTime),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )

                            // Read autoUpload first since the sync button reacts to it
                            val autoUploadEnabled by viewModel.autoUploadEnabled.collectAsState()

                            Button(
                                onClick = {
                                    isBackingUp = true
                                    backupProgress = 0f
                                    viewModel.syncNotesToCloud(
                                        email = googleEmail,
                                        onProgress = { progress, msg ->
                                            backupProgress = progress
                                            backupStatusMsg = msg
                                        },
                                        onComplete = {
                                            isBackingUp = false
                                            lastBackupTime = justNowStr
                                        }
                                    )
                                },
                                enabled = !autoUploadEnabled && !isBackingUp,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (autoUploadEnabled) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (autoUploadEnabled)
                                            stringResource(R.string.sync_auto_active)
                                        else
                                            stringResource(R.string.sync_now),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Auto-upload toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Caricamento automatico su Drive",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Ogni modifica a una nota viene caricata su Drive. Allegati inclusi.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                                Switch(
                                    checked = autoUploadEnabled,
                                    onCheckedChange = { viewModel.setAutoUploadEnabled(it) }
                                )
                            }
                        }
                    }
                } else {
                    // Disabled Backup Warning
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Connetti il tuo account Google qui sopra per abilitare il backup sul cloud.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ─── Security / App Lock card ───
        val appLockEnabled by viewModel.appLockEnabled.collectAsState()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sicurezza",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Richiedi sblocco all'avvio",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Impronta o codice del telefono ogni volta che apri Keeper",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Check that the device actually has a credential set up
                                val biometricManager = androidx.biometric.BiometricManager.from(context)
                                val canAuth = biometricManager.canAuthenticate(
                                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                )
                                if (canAuth == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                                    viewModel.setAppLockEnabled(true)
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Imposta prima un PIN, codice o impronta sul telefono",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                viewModel.setAppLockEnabled(false)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "☕", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.support_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = stringResource(R.string.support_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    lineHeight = 18.sp
                )
                Button(
                    onClick = {
                        com.secondream.keeper.util.FileOpener.openUrl(
                            context = context,
                            url = "https://buymeacoffee.com/M12oPyJwty"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFCA28),
                        contentColor = Color(0xFF1A1A1A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.support_button),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── About / version card ───
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.about_version),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = com.secondream.keeper.BuildConfig.VERSION_NAME,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Build",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = com.secondream.keeper.BuildConfig.VERSION_CODE.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.about_author),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Generous bottom spacer to scroll fully past any bottom system/gesture bars
        Spacer(modifier = Modifier.height(100.dp))
    }

    // Fake account chooser removed — use the real system picker only.

    // Beautiful dynamic Cloud Sync Full-Screen dialog state
    if (isSyncingNotes) {
        AlertDialog(
            onDismissRequest = { },
            title = null,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sincronizzazione",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Sincronizzazione in Corso",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = syncMessage,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { },
            shape = RoundedCornerShape(28.dp)
        )
    }
}
