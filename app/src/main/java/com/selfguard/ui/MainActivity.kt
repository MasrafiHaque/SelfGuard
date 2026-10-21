package com.selfguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.selfguard.ui.blocklists.BlocklistsScreen
import com.selfguard.ui.dashboard.DashboardScreen
import com.selfguard.ui.onboarding.OnboardingScreen
import com.selfguard.ui.theme.SelfGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SelfGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SelfGuardRoot()
                }
            }
        }
    }
}

private enum class Screen { DASHBOARD, BLOCKLISTS }

@Composable
private fun SelfGuardRoot(viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }

    val onboardingComplete = settings?.onboardingComplete ?: false
    if (!onboardingComplete) {
        showOnboarding = true
    }

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) showOnboarding = false
    }

    if (showOnboarding) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinished = { viewModel.completeOnboarding() },
        )
        return
    }

    when (screen) {
        Screen.DASHBOARD -> DashboardScreen(
            viewModel = viewModel,
            onOpenBlocklists = { screen = Screen.BLOCKLISTS },
        )

        Screen.BLOCKLISTS -> BlocklistsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.DASHBOARD },
        )
    }
}
