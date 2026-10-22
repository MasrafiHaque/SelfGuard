package com.selfguard.domain.app

/**
 * Why a package can never be blocked (FR-2.5 / FR-2.6).
 * The launcher and the keyboard are resolved at runtime, everything else is
 * covered by a static list of system packages the phone needs to stay usable.
 */
enum class ProtectionReason {
    SELF,
    LAUNCHER,
    IME,
    CRITICAL;

    fun userMessage(packageName: String): String = when (this) {
        SELF -> "SelfGuard cannot block itself"
        LAUNCHER -> "Your launcher must stay unblocked"
        IME -> "Your keyboard must stay unblocked"
        CRITICAL -> "$packageName keeps the phone usable and cannot be blocked"
    }
}

object CriticalApps {

    val STATIC_CRITICAL: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.contacts",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.incallui",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.samsung.android.app.clockpackage",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.gms",
        "com.android.vending",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.emergency",
        "com.android.documentsui",
        "com.android.keychain",
        "com.android.networkstack",
        "com.android.networkstack.tethering",
        "com.sec.android.app.launcher",
        "com.sec.android.app.twlauncher",
    )

    fun protectReason(
        packageName: String,
        selfPackage: String,
        launcherPackages: Set<String>,
        imePackage: String?,
    ): ProtectionReason? = when {
        packageName == selfPackage -> ProtectionReason.SELF
        packageName.isNotEmpty() && packageName in launcherPackages -> ProtectionReason.LAUNCHER
        packageName.isNotEmpty() && packageName == imePackage -> ProtectionReason.IME
        packageName in STATIC_CRITICAL -> ProtectionReason.CRITICAL
        else -> null
    }

    fun isProtected(
        packageName: String,
        selfPackage: String,
        launcherPackages: Set<String>,
        imePackage: String?,
    ): Boolean = protectReason(packageName, selfPackage, launcherPackages, imePackage) != null
}
