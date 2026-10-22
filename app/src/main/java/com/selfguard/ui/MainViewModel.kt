package com.selfguard.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selfguard.SelfGuardApp
import com.selfguard.data.local.InstalledApp
import com.selfguard.data.local.prefs.ProtectionSnapshot
import com.selfguard.data.repo.AddResult
import com.selfguard.data.repo.ReleaseOutcome
import com.selfguard.domain.model.BlockedApp
import com.selfguard.domain.model.BlockedKeyword
import com.selfguard.domain.model.BlockedSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val container = (application as SelfGuardApp).container

    val settings = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sites: StateFlow<List<BlockedSite>> = container.blocklistRepository.sites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val keywords: StateFlow<List<BlockedKeyword>> = container.blocklistRepository.keywords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedApps: StateFlow<List<BlockedApp>> = container.blocklistRepository.apps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val protection: StateFlow<ProtectionSnapshot?> = container.protectionRepository.protection
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _deviceAdminActive = MutableStateFlow(false)
    val deviceAdminActive: StateFlow<Boolean> = _deviceAdminActive.asStateFlow()

    private val _deviceOwner = MutableStateFlow(false)
    val deviceOwner: StateFlow<Boolean> = _deviceOwner.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _installedAppsLoaded = MutableStateFlow(false)
    val installedAppsLoaded: StateFlow<Boolean> = _installedAppsLoaded.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = container.appCatalog.loadInstalledApps()
            _installedAppsLoaded.value = true
        }
        refreshAdminStatus()
    }

    /** Packages that can never be blocked (launcher, IME, SelfGuard, critical system apps). */
    val protectedPackages: StateFlow<Set<String>> = flow {
        emit(
            installedApps.value
                .map { it.packageName }
                .filter { container.protectReason(it) != null }
                .toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val serviceEnabled: StateFlow<Boolean> = flow {
        while (true) {
            emit(isAccessibilityServiceEnabled())
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setBlockingEnabled(enabled) }
    }

    fun setMatchKeywordsInPageText(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setMatchKeywordsInPageText(enabled) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { container.settingsStore.setOnboardingComplete(true) }
    }

    fun addSite(domain: String) {
        viewModelScope.launch {
            _message.value = when (val result = container.blocklistRepository.addSite(domain)) {
                is AddResult.Added -> "Blocked $domain"
                is AddResult.Duplicate -> "$domain is already blocked"
                is AddResult.Rejected -> result.reason
            }
        }
    }

    fun addKeyword(keyword: String) {
        viewModelScope.launch {
            _message.value = when (val result = container.blocklistRepository.addKeyword(keyword)) {
                is AddResult.Added -> "Keyword \"$keyword\" added"
                is AddResult.Duplicate -> "\"$keyword\" is already blocked"
                is AddResult.Rejected -> result.reason
            }
        }
    }

    fun deleteSite(id: Long) {
        viewModelScope.launch { container.blocklistRepository.deleteSite(id) }
    }

    fun deleteKeyword(id: Long) {
        viewModelScope.launch { container.blocklistRepository.deleteKeyword(id) }
    }

    fun toggleApp(packageName: String, label: String, blocked: Boolean) {
        viewModelScope.launch {
            if (blocked) {
                _message.value = when (val result = container.blocklistRepository.addApp(packageName, label)) {
                    is AddResult.Added -> "$label is blocked"
                    is AddResult.Duplicate -> "$label is already blocked"
                    is AddResult.Rejected -> result.reason
                }
            } else {
                container.blocklistRepository.deleteAppByPackage(packageName)
            }
        }
    }

    fun deleteApp(id: Long) {
        viewModelScope.launch { container.blocklistRepository.deleteApp(id) }
    }

    fun toggleSite(id: Long, enabled: Boolean) {
        viewModelScope.launch { container.blocklistRepository.setSiteEnabled(id, enabled) }
    }

    fun toggleKeyword(id: Long, enabled: Boolean) {
        viewModelScope.launch { container.blocklistRepository.setKeywordEnabled(id, enabled) }
    }

    fun restoreDefaults() {
        viewModelScope.launch {
            container.blocklistRepository.restoreDefaults(container.currentDefaults())
            _message.value = "Blocklist reset to the default list"
        }
    }

    fun refreshAdminStatus() {
        _deviceAdminActive.value = container.deviceAdminGateway.adminActive
        _deviceOwner.value = container.deviceAdminGateway.deviceOwner
    }

    fun armProtection() {
        viewModelScope.launch {
            container.protectionRepository.arm()
            refreshAdminStatus()
            _message.value = "Protection armed"
        }
    }

    fun beginRelease() {
        viewModelScope.launch {
            _message.value = releaseMessage(container.protectionRepository.beginRelease())
            refreshAdminStatus()
        }
    }

    fun releaseWithPassphrase(phrase: String) {
        viewModelScope.launch {
            _message.value = releaseMessage(
                container.protectionRepository.releaseWithPassphrase(phrase),
            )
            refreshAdminStatus()
        }
    }

    fun setPassphrase(phrase: String) {
        viewModelScope.launch {
            val trimmed = phrase.trim()
            if (trimmed.isEmpty()) {
                _message.value = "Passphrase must not be blank"
                return@launch
            }
            container.protectionRepository.setPassphrase(trimmed)
            _message.value = "Passphrase saved"
        }
    }

    private fun releaseMessage(outcome: ReleaseOutcome): String = when (outcome) {
        is ReleaseOutcome.Started -> "Cooldown started — release available in 24 hours"
        is ReleaseOutcome.InProgress -> "Cooldown running: ${formatRemaining(outcome.remainingMs)} left"
        is ReleaseOutcome.Completed -> "Protection released"
        is ReleaseOutcome.WrongPassphrase ->
            "Wrong passphrase (${outcome.attemptsLeft} attempts left)"
        is ReleaseOutcome.LockedOut ->
            "Too many attempts — locked for ${formatRemaining(outcome.remainingMs)}"
        is ReleaseOutcome.NoPassphrase -> "No passphrase is set yet"
    }

    fun isAccessibilityServiceEnabled(): Boolean = container.isAccessibilityServiceEnabled()

    companion object {
        fun formatRemaining(ms: Long): String {
            val totalMinutes = (ms + 59_999L) / 60_000L
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            return if (hours > 0) "$hours h $minutes min" else "$minutes min"
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
        }

        fun openDeviceAdminSettings(context: Context) {
            // Settings.ACTION_DEVICE_ADMIN_SETTINGS is not in the public SDK
            // surface of current compileSdk versions; the action string is
            // stable ("android.settings.DEVICE_ADMIN_SETTINGS").
            val intent = Intent("android.settings.DEVICE_ADMIN_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_SECURITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
        }

        fun openAppSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
