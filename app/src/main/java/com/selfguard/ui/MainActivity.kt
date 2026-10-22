package com.selfguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.selfguard.ui.blocklists.BlocklistsScreen
import com.selfguard.ui.dashboard.DashboardScreen
import com.selfguard.ui.onboarding.OnboardingScreen
import com.selfguard.ui.settings.SettingsScreen
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

private data class NavTab(val label: String, val icon: ImageVector)

private val NAV_TABS = listOf(
    NavTab("Home", Icons.Default.Home),
    NavTab("Blocklist", Icons.AutoMirrored.Filled.ListAlt),
    NavTab("Settings", Icons.Default.Settings),
)

@Composable
private fun SelfGuardRoot(viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val onboardingComplete = settings?.onboardingComplete ?: false
    if (!onboardingComplete) {
        showOnboarding = true
    }

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) showOnboarding = false
    }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            viewModel.consumeMessage()
        }
    }

    if (showOnboarding) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinished = { viewModel.completeOnboarding() },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NAV_TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onOpenBlocklists = { selectedTab = 1 },
                )

                1 -> BlocklistsScreen(viewModel = viewModel)

                else -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
