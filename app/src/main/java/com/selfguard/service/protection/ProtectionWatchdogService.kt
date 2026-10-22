package com.selfguard.service.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.selfguard.R
import com.selfguard.SelfGuardApp
import com.selfguard.core.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground watchdog started while protection is armed. It watches the
 * secure setting that lists enabled accessibility services and asks the
 * system to re-enable SelfGuard when the service is switched off
 * (FR-2.11 recovery path). No polling: a ContentObserver wakes it only on
 * an actual change, plus one check after boot.
 *
 * enableSelf() is the documented API-26 way for a service to re-enable
 * itself; it is best-effort and may be ignored by some OEM skins — that
 * limit is documented in docs/DEVICE_OWNER.md.
 */
class ProtectionWatchdogService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var container: AppContainer? = null
    private var observer: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as? SelfGuardApp
        val c = app?.container
        if (c == null) {
            stopSelf()
            return
        }
        container = c
        startInForeground()
        registerObserver()

        scope.launch {
            c.protectionStore.protection.collectLatest { snapshot ->
                if (!snapshot.armed) {
                    Log.i(TAG, "Protection disarmed, stopping watchdog")
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
        scope.launch { recoverIfPossible() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground is idempotent; onCreate already called it, but a
        // restart intent must never leave the service without a notification.
        if (container != null) startInForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observer?.let {
            runCatching { contentResolver.unregisterContentObserver(it) }
        }
        observer = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.protection_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.protection_notification_title))
            .setContentText(getString(R.string.protection_notification_text))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun registerObserver() {
        val object_ = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch { recoverIfPossible() }
            }
        }
        observer = object_
        runCatching {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                object_,
            )
        }.onFailure { Log.w(TAG, "registerContentObserver failed: ${it.message}") }
    }

    /** Re-enables the accessibility service when protection is armed and it is off. */
    private suspend fun recoverIfPossible() {
        val c = container ?: return
        val armed = c.protectionStore.protection.first().armed
        if (!armed) return
        if (c.isAccessibilityServiceEnabled()) return
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        // AccessibilityManager.enableSelf() is @hide in the public SDK but is
        // the only self-recovery path that needs no privileged permission.
        // It is greylist reflection: works on stock Android, best-effort on
        // OEM skins, and failures are logged, never fatal (FR-2.11).
        runCatching {
            AccessibilityManager::class.java.getMethod("enableSelf").invoke(manager)
        }.onSuccess { Log.i(TAG, "enableSelf() requested") }
            .onFailure { Log.w(TAG, "enableSelf() failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "SelfGuard"
        private const val CHANNEL_ID = "selfguard_protection"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, ProtectionWatchdogService::class.java),
                )
            }.onFailure { Log.w(TAG, "start watchdog failed: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, ProtectionWatchdogService::class.java))
            }
        }
    }
}
