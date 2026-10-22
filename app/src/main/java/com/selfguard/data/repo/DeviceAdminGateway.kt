package com.selfguard.data.repo

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.selfguard.service.deviceadmin.SelfGuardDeviceAdminReceiver

/**
 * Thin wrapper over [DevicePolicyManager]. Every call is best-effort and
 * never destructive: there is no wipe-data policy and no wipe call here
 * (FR-4.1 / FR-4.2).
 *
 * Plain Device Admin (activated in settings) only supports lockNow().
 * Blocking uninstall requires the Device Owner role (adb command).
 */
class DeviceAdminGateway(context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, SelfGuardDeviceAdminReceiver::class.java)
    val packageName: String = context.packageName

    val adminActive: Boolean
        get() = runCatching { dpm.isAdminActive(componentName) }.getOrDefault(false)

    val deviceOwner: Boolean
        get() = runCatching { dpm.isDeviceOwnerApp(packageName) }.getOrDefault(false)

    /**
     * Requires the Device Owner role; a plain Device Admin is refused by the
     * system. Returns whether the call succeeded.
     */
    fun setUninstallBlocked(blocked: Boolean): Boolean {
        if (!deviceOwner) return false
        return runCatching {
            dpm.setUninstallBlocked(componentName, packageName, blocked)
        }.onSuccess {
            Log.i(TAG, "setUninstallBlocked($blocked) ok")
        }.onFailure {
            Log.w(TAG, "setUninstallBlocked($blocked) failed: ${it.message}")
        }.isSuccess
    }

    fun lockNow() {
        runCatching { dpm.lockNow() }
            .onFailure { Log.w(TAG, "lockNow failed: ${it.message}") }
    }

    /** Only works while the app is the Device Owner; used by the release flow. */
    fun clearDeviceOwner() {
        runCatching { dpm.clearDeviceOwnerApp(packageName) }
            .onSuccess { Log.i(TAG, "clearDeviceOwnerApp ok") }
            .onFailure { Log.w(TAG, "clearDeviceOwnerApp failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "SelfGuard"
    }
}
