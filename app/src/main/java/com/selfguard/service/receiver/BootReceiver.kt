package com.selfguard.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.selfguard.SelfGuardApp
import com.selfguard.service.protection.ProtectionWatchdogService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> startWatchdogIfArmed(context)

            else -> Unit
        }
    }

    /**
     * Re-arms the protection watchdog after a reboot or an app update, but
     * only while protection is armed so an unarmed install stays silent.
     */
    private fun startWatchdogIfArmed(context: Context) {
        val app = context.applicationContext as? SelfGuardApp ?: return
        val armed = runBlocking {
            runCatching { app.container.protectionStore.protection.first().armed }
                .getOrDefault(false)
        }
        if (armed) ProtectionWatchdogService.start(context)
    }
}
