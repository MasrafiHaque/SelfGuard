package com.selfguard.service.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.util.Log

class SelfGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: android.content.Context, intent: android.content.Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: android.content.Context, intent: android.content.Intent) {
        super.onDisabled(context, intent)
        // Recorded as a tamper event once the event log exists (Phase 5);
        // for now the logcat line is the record.
        Log.w(TAG, "Device admin disabled (tamper attempt)")
    }
}

private const val TAG = "SelfGuard"
