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
        enableEdgeToEdge()
        setContent {
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
            // The Compose-side equivalent of WindowInsetsControllerCompat for the
            // status bar appearance (the "icons black" the user expects on light).
            val view = androidx.compose.ui.platform.LocalView.current
            LaunchedEffect(useDarkTheme, view) {
                val window = (view.context as android.app.Activity).window
                androidx.core.view.WindowCompat
                    .getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !useDarkTheme
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
