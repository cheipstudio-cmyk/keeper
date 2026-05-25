package com.secondream.keeper.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.secondream.keeper.viewmodel.NoteViewModel
import androidx.compose.ui.res.stringResource
import com.secondream.keeper.R

/**
 * Full-screen lock overlay shown when the app is launched / resumed while
 * "Require unlock" is enabled. Uses BiometricPrompt with device-credential
 * fallback (PIN / pattern / password).
 */
@Composable
fun LockScreen(viewModel: NoteViewModel) {
    val context = LocalContext.current
    var authError by remember { mutableStateOf<String?>(null) }
    var isPrompting by remember { mutableStateOf(false) }

    val showPrompt: () -> Unit = remember {
        {
            val activity = context as? FragmentActivity
            if (activity == null) {
                authError = "Errore interno (Activity non disponibile)"
            } else {
                val executor = ContextCompat.getMainExecutor(context)
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Sblocca Keeper")
                    .setSubtitle("Usa l'impronta o il PIN del telefono")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()

                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            isPrompting = false
                            viewModel.unlockApp()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            isPrompting = false
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                errorCode != BiometricPrompt.ERROR_CANCELED) {
                                authError = errString.toString()
                            }
                        }
                        override fun onAuthenticationFailed() {}
                    }
                )
                isPrompting = true
                try {
                    prompt.authenticate(info)
                } catch (e: Exception) {
                    isPrompting = false
                    authError = e.message ?: "Errore di autenticazione"
                }
            }
        }
    }

    // Re-trigger the prompt every time the screen enters foreground and
    // reset isPrompting so the button isn't stuck disabled if Android
    // silently cancels the prompt while we're in background.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isPrompting = false
                authError = null
                showPrompt()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                // Prompt is dismissed by the system when activity pauses
                isPrompting = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Keeper è bloccato",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lock_screen_message),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
            authError?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    authError = null
                    // Reset isPrompting in case it got stuck (e.g. Android
                    // silently cancelled the prompt). Force-relaunch.
                    isPrompting = false
                    showPrompt()
                },
                enabled = true,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sblocca", fontWeight = FontWeight.Bold)
            }
        }
    }
}
