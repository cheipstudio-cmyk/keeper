package com.secondream.keeper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.secondream.keeper.ui.screens.AccountSwitchDialog
import com.secondream.keeper.ui.screens.ConnectingDialog
import com.secondream.keeper.ui.screens.NoteApp
import com.secondream.keeper.ui.screens.OnboardingDialog
import com.secondream.keeper.ui.theme.MyApplicationTheme
import com.secondream.keeper.viewmodel.NoteViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("KeeperLifecycle", "MainActivity.onCreate START")

        // Surface previous crash & break onboarding-related crash loops.
        var skipOnboarding = false
        try {
            val crashFile = java.io.File(filesDir, "last_crash.txt")
            if (crashFile.exists()) {
                val content = crashFile.readText()
                android.util.Log.e("KeeperPriorCrash", content)
                val mentionsOnboarding = content.contains("Onboarding", ignoreCase = true) ||
                                         content.contains("painterResource", ignoreCase = true) ||
                                         content.contains("mipmap", ignoreCase = true) ||
                                         content.contains("adaptive", ignoreCase = true)
                if (mentionsOnboarding) {
                    // Force-skip onboarding so we don't crash again on the same path
                    val prefs = getSharedPreferences("keep_notes_prefs", MODE_PRIVATE)
                    prefs.edit().putBoolean("onboarding_completed", true).apply()
                    skipOnboarding = true
                    android.widget.Toast.makeText(
                        this,
                        "Skipping onboarding: precedente crash rilevato (vedi log)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    val summary = content.lines()
                        .firstOrNull { it.contains("Exception") || it.contains("Error:") }
                        ?: "Crash precedente"
                    android.widget.Toast.makeText(
                        this,
                        summary.take(180),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                crashFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.w("KeeperPriorCrash", "Could not read crash file", e)
        }
        android.util.Log.i("KeeperLifecycle", "skipOnboarding=$skipOnboarding")

        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            android.util.Log.w("KeeperLifecycle", "enableEdgeToEdge failed", e)
        }
        setContent {
            android.util.Log.i("KeeperLifecycle", "setContent composing")
            val darkThemePref by viewModel.darkThemeOption.collectAsState()
            val useDarkTheme = when (darkThemePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            // Handle first-time Drive consent prompt
            val pendingAuthIntent by viewModel.pendingAuthIntent.collectAsState()
            val authLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                viewModel.clearPendingAuthIntent()
                val email = viewModel.googleEmail.value
                if (result.resultCode == android.app.Activity.RESULT_OK && email.isNotBlank()) {
                    viewModel.connectAndSyncGoogleAccount(email)
                }
            }
            LaunchedEffect(pendingAuthIntent) {
                pendingAuthIntent?.let { intent ->
                    try {
                        authLauncher.launch(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        viewModel.clearPendingAuthIntent()
                    }
                }
            }

            // First-launch onboarding
            val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()

            // Status bar: light icons on dark theme, dark icons on light theme.
            val view = androidx.compose.ui.platform.LocalView.current
            LaunchedEffect(useDarkTheme, view) {
                try {
                    val activity = view.context as? android.app.Activity
                    if (activity != null) {
                        androidx.core.view.WindowCompat
                            .getInsetsController(activity.window, view)
                            .isAppearanceLightStatusBars = !useDarkTheme
                    }
                } catch (e: Exception) {
                    android.util.Log.w("KeeperLifecycle", "Status bar config failed", e)
                }
            }

            val accentArgb by viewModel.accentColorArgb.collectAsState()

            MyApplicationTheme(
                darkTheme = useDarkTheme,
                accentColor = androidx.compose.ui.graphics.Color(accentArgb)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoteApp(viewModel = viewModel)
                    if (!onboardingCompleted) {
                        OnboardingDialog(viewModel = viewModel)
                    }
                    // Loading overlay while Drive auth + initial import runs
                    ConnectingDialog(viewModel = viewModel)
                    // Confirmation dialog when switching to a different Google account
                    AccountSwitchDialog(viewModel = viewModel)
                }
            }
        }
    }
}
