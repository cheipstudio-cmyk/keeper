package com.secondream.keeper.ui.screens

import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.secondream.keeper.R
import com.secondream.keeper.viewmodel.NoteViewModel

/**
 * Minimal first-launch onboarding: real Keeper icon (the launcher artwork
 * rendered from its vector foreground over the brand-dark background) plus
 * one short tagline. No feature bullets, no marketing copy — the screen
 * needs room to breathe.
 */
@Composable
fun OnboardingDialog(
    viewModel: NoteViewModel
) {
    // Guard against double-tap launching two account chooser intents
    var hasLaunchedPicker by remember { mutableStateOf(false) }

    val accountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hasLaunchedPicker = false
        val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!accountName.isNullOrBlank()) {
            viewModel.connectAndSyncGoogleAccount(accountName)
        }
    }

    val triggerGooglePicker: () -> Unit = {
        if (!hasLaunchedPicker) {
            hasLaunchedPicker = true
            try {
                viewModel.markSystemPickerAboutToOpen()
                val intent = AccountManager.newChooseAccountIntent(
                    null, null, arrayOf("com.google"), null, null, null, null
                )
                accountChooserLauncher.launch(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                hasLaunchedPicker = false
            }
        }
    }

    Dialog(
        onDismissRequest = { /* not dismissible by tap-outside */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // 3-row layout: flexible top spacer, centered icon + tagline,
            // flexible bottom spacer pushing CTA + skip near the bottom.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Real Keeper launcher icon: dark blue rounded square with
                // the lightbulb vector foreground on top. Avoids loading the
                // adaptive-icon XML (which Compose can't decode as a Painter).
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color(0xFF0B1528)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(140.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = stringResource(R.string.onboarding_tagline),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    lineHeight = 26.sp,
                    letterSpacing = (-0.2).sp
                )

                Spacer(modifier = Modifier.weight(1f))

                // Primary CTA: connect Drive
                Button(
                    onClick = { triggerGooglePicker() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_connect_drive),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { viewModel.completeOnboarding() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
