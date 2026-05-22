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

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoteApp(viewModel = viewModel)
                    if (!onboardingCompleted) {
                        OnboardingDialog(viewModel = viewModel)
                    }
                    // Loading overlay while Drive auth + initial import runs
                    ConnectingDialog(viewModel = viewModel)
                }
            }
        }
    }
}
