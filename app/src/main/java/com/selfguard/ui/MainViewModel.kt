package com.selfguard.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selfguard.SelfGuardApp
import com.selfguard.data.local.InstalledApp
import com.selfguard.data.repo.AddResult
import com.selfguard.domain.model.BlockedApp
import com.selfguard.domain.model.BlockedKeyword
import com.selfguard.domain.model.BlockedSite
import com.selfguard.service.accessibility.SelfGuardAccessibilityService
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

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _installedAppsLoaded = MutableStateFlow(false)
    val installedAppsLoaded: StateFlow<Boolean> = _installedAppsLoaded.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = container.appCatalog.loadInstalledApps()
            _installedAppsLoaded.value = true
        }
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

    fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "${container.appContext.packageName}/" +
            SelfGuardAccessibilityService::class.java.name
        val enabled = Settings.Secure.getString(
            container.appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    companion object {
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

        fun openAppSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
