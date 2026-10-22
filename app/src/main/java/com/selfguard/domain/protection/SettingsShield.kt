package com.selfguard.domain.protection

/**
 * Which protected settings screen was recognised (FR-4.3). Detection is
 * best-effort: class names come from AOSP and the OEM may rename or nest
 * the activities, so a SubSettings title fallback is used as well.
 */
enum class ShieldKind {
    ACCESSIBILITY,
    DEVICE_ADMIN,
    APP_INFO_SELF;

    fun userMessage(selfLabel: String): String = when (this) {
        ACCESSIBILITY -> "Accessibility settings are protected while SelfGuard is armed"
        DEVICE_ADMIN -> "Device admin settings are protected while SelfGuard is armed"
        APP_INFO_SELF -> "$selfLabel cannot be opened while SelfGuard is armed"
    }
}

object SettingsShield {

    /**
     * @param className the accessibility event's window class name
     * @param title the window title, if the event carries one
     * @param selfLabel the user-visible label of this app, used to recognise
     *   the app-info screen of SelfGuard itself without blocking app info
     *   for every other package
     */
    fun classify(
        className: String?,
        title: String?,
        selfLabel: String,
    ): ShieldKind? {
        if (className.isNullOrEmpty()) return null
        val simple = className.substringAfterLast('.')
        return when {
            simple.endsWith("AccessibilitySettingsActivity") ||
                simple == "AccessibilitySettings" -> ShieldKind.ACCESSIBILITY

            simple.endsWith("DeviceAdminSettingsActivity") ||
                simple == "DeviceAdminSettings" -> ShieldKind.DEVICE_ADMIN

            simple.endsWith("InstalledAppDetailsActivity") ||
                simple == "InstalledAppDetails" ->
                if (titleMatchesSelf(title, selfLabel)) ShieldKind.APP_INFO_SELF else null

            // Fragments host several screens in one activity; match on the
            // exact visible heading only, so app info for an unrelated app
            // whose name merely contains "Accessibility" stays usable.
            simple == "SubSettings" -> classifySubSettings(title, selfLabel)

            else -> null
        }
    }

    private fun classifySubSettings(title: String?, selfLabel: String): ShieldKind? {
        val heading = title?.trim().orEmpty()
        if (heading.isEmpty()) return null
        return when {
            titleMatchesSelf(heading, selfLabel) -> ShieldKind.APP_INFO_SELF
            heading.equals("Accessibility", ignoreCase = true) -> ShieldKind.ACCESSIBILITY
            heading.equals("Device admin", ignoreCase = true) ||
                heading.equals("Device administrator", ignoreCase = true) -> ShieldKind.DEVICE_ADMIN
            else -> null
        }
    }

    private fun titleMatchesSelf(title: String?, selfLabel: String): Boolean =
        !title.isNullOrBlank() && selfLabel.isNotBlank() &&
            title.trim().contains(selfLabel, ignoreCase = true)
}
