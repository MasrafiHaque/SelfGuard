package com.selfguard.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.selfguard.SelfGuardApp
import com.selfguard.core.AppContainer
import com.selfguard.data.local.prefs.SettingsSnapshot
import com.selfguard.domain.match.Candidate
import com.selfguard.domain.match.HostNormalizer
import com.selfguard.domain.match.MatchedRule
import com.selfguard.domain.match.RuleKind
import com.selfguard.domain.model.BlockedApp
import com.selfguard.domain.model.BlockedKeyword
import com.selfguard.domain.model.BlockedSite
import com.selfguard.domain.protection.SettingsShield
import com.selfguard.ui.blocked.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelfGuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var container: AppContainer? = null
    private var probe: BrowserProbe? = null

    @Volatile
    private var sites: List<BlockedSite> = emptyList()

    @Volatile
    private var keywords: List<BlockedKeyword> = emptyList()

    @Volatile
    private var apps: List<BlockedApp> = emptyList()

    @Volatile
    private var settings = SettingsSnapshot()

    @Volatile
    private var armed = false

    private var selfLabel: String = "SelfGuard"

    private val lastContentCheck = HashMap<String, Long>()

    @Volatile
    private var lastBlockAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        val app = application as? SelfGuardApp
        val c = app?.container ?: return
        container = c
        probe = BrowserProbe(this)

        scope.launch { c.blocklistRepository.sites.collectLatest { sites = it } }
        scope.launch { c.blocklistRepository.keywords.collectLatest { keywords = it } }
        scope.launch { c.blocklistRepository.apps.collectLatest { apps = it } }
        scope.launch { c.settingsStore.settings.collectLatest { settings = it } }
        scope.launch { c.protectionStore.protection.collectLatest { armed = it.armed } }
        selfLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault("SelfGuard")
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!settings.blockingEnabled) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // The settings shield runs first so a protected screen can
                // never be reached while protection is armed.
                if (checkShield(e)) return
                // An app launch is only visible here, so check it next.
                if (checkAppBlock(pkg)) return
                if (BrowserProfiles.isBrowser(pkg)) handleWindowState(pkg)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (BrowserProfiles.isBrowser(pkg)) handleViewText(e)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (BrowserProfiles.isBrowser(pkg)) handleContentChanged(pkg)
            }

            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isConnected = false
        scope.cancel()
        workScope.cancel()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        scope.cancel()
        workScope.cancel()
        return super.onUnbind(intent)
    }

    /**
     * Returns true when the package is on the app blocklist and a block was
     * triggered. Critical packages are refused here as well as at entry, so a
     * hand-edited database cannot lock the owner out of the phone (FR-2.5).
     */
    private fun checkAppBlock(pkg: String): Boolean {
        val rule = apps.firstOrNull { it.packageName == pkg && it.isEnabled } ?: return false
        val c = container ?: return false
        val protection = c.protectReason(pkg)
        if (protection != null) {
            Log.w(TAG, "Refusing to block $pkg: ${protection.name}")
            return false
        }
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < BLOCK_DEBOUNCE_MS) return true
        lastBlockAt = now
        Log.i(TAG, "Blocked APP rule=$pkg")
        val matched = MatchedRule(
            kind = RuleKind.APP,
            ruleId = rule.id,
            label = rule.label.ifBlank { pkg },
            matchedText = pkg,
            host = pkg,
        )
        workScope.launch { triggerBlock(matched) }
        return true
    }

    /**
     * Blocks protected settings screens while protection is armed (FR-4.3).
     * Detection is best-effort — class names vary across OEMs — and covers
     * Accessibility, Device admin and the app-info page of SelfGuard itself.
     */
    private fun checkShield(event: AccessibilityEvent): Boolean {
        if (!armed) return false
        val title = event.text?.firstOrNull()?.toString()
            ?: event.contentDescription?.toString()
        val kind = SettingsShield.classify(
            className = event.className?.toString(),
            title = title,
            selfLabel = selfLabel,
        ) ?: return false
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < BLOCK_DEBOUNCE_MS) return true
        lastBlockAt = now
        Log.i(TAG, "Blocked SHIELD rule=${kind.name}")
        val matched = MatchedRule(
            kind = RuleKind.SHIELD,
            ruleId = 0L,
            label = kind.userMessage(selfLabel),
            matchedText = event.className?.toString().orEmpty(),
            host = null,
        )
        workScope.launch { triggerBlock(matched) }
        return true
    }

    private fun handleWindowState(packageName: String) {
        val p = probe ?: return
        val root = runCatching { rootInActiveWindow }.getOrNull()
        val extraction = p.extract(root, packageName, settings.matchKeywordsInPageText)
        if (extraction.host == null && extraction.urlText == null && extraction.pageText.isNullOrBlank()) {
            return
        }
        evaluate(
            Candidate(
                host = extraction.host,
                urlText = extraction.urlText,
                title = extraction.title,
                pageText = extraction.pageText,
            )
        )
    }

    private fun handleViewText(event: AccessibilityEvent) {
        val text = event.text?.firstOrNull()?.toString() ?: return
        val host = HostNormalizer.normalizeHost(text) ?: return
        evaluate(
            Candidate(
                host = host,
                urlText = text,
                title = null,
                pageText = null,
            )
        )
    }

    private fun handleContentChanged(packageName: String) {
        val now = System.currentTimeMillis()
        val last = lastContentCheck[packageName] ?: 0L
        if (now - last < CONTENT_THROTTLE_MS) return
        lastContentCheck[packageName] = now

        val p = probe ?: return
        val root = runCatching { rootInActiveWindow }.getOrNull()
        val extraction = p.extract(root, packageName, includePageText = false)
        if (extraction.host == null && extraction.urlText == null) return
        evaluate(
            Candidate(
                host = extraction.host,
                urlText = extraction.urlText,
                title = extraction.title,
                pageText = null,
            )
        )
    }

    private fun evaluate(candidate: Candidate) {
        val c = container ?: return
        workScope.launch {
            val rule: MatchedRule = c.ruleMatcher.match(candidate, sites, keywords) ?: return@launch
            val now = System.currentTimeMillis()
            if (now - lastBlockAt < BLOCK_DEBOUNCE_MS) return@launch
            lastBlockAt = now
            Log.i(TAG, "Blocked ${rule.kind} rule=${rule.label}")
            triggerBlock(rule)
        }
    }

    private suspend fun triggerBlock(rule: MatchedRule) {
        withContext(Dispatchers.Main) {
            val intent = Intent(this@SelfGuardAccessibilityService, BlockedActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                .putExtra(BlockedActivity.EXTRA_RULE_KIND, rule.kind.name)
                .putExtra(BlockedActivity.EXTRA_RULE_LABEL, rule.label)
                .putExtra(BlockedActivity.EXTRA_RULE_MATCH, rule.matchedText)
                .putExtra(BlockedActivity.EXTRA_RULE_HOST, rule.host)
            runCatching { startActivity(intent) }

            // If the blocked screen did not take over (some devices refuse an
            // activity start from the background), fall back to sending the
            // browser to the home screen so the page never stays open.
            delay(BLOCK_CONFIRM_DELAY_MS)
            val top = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (top != packageName) performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    companion object {
        const val TAG = "SelfGuard"

        @Volatile
        var isConnected: Boolean = false
            private set

        private const val CONTENT_THROTTLE_MS = 1200L
        private const val BLOCK_DEBOUNCE_MS = 750L
        private const val BLOCK_CONFIRM_DELAY_MS = 1000L
    }
}
