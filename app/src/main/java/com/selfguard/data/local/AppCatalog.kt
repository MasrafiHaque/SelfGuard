package com.selfguard.data.local

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val icon: Bitmap?,
)

/** Loads the installed apps once, off the main thread. */
class AppCatalog(private val context: Context) {

    fun loadInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val appInfos = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return appInfos
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || hasLauncherActivity(pm, it.packageName) }
            .mapNotNull { appInfo ->
                val packageName = appInfo.packageName
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(packageName)
                val icon = runCatching {
                    scaleIcon(pm.getApplicationIcon(appInfo))
                }.getOrNull()
                InstalledApp(
                    packageName = packageName,
                    label = label,
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    icon = icon,
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<InstalledApp> { !it.isSystem }.thenBy { it.label.lowercase() })
            .toList()
    }

    private fun hasLauncherActivity(pm: PackageManager, packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        return intent.resolveActivity(pm) != null
    }

    private fun scaleIcon(drawable: Drawable): Bitmap? {
        val full = drawable.toBitmap(width = 96, height = 96)
        return Bitmap.createScaledBitmap(full, ICON_PX, ICON_PX, true)
    }

    private companion object {
        const val ICON_PX = 96
    }
}
