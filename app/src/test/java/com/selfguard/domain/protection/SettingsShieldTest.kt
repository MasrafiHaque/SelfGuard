package com.selfguard.domain.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsShieldTest {

    private val self = "SelfGuard"

    @Test
    fun `accessibility settings activity is shielded`() {
        assertEquals(
            ShieldKind.ACCESSIBILITY,
            SettingsShield.classify(
                className = "com.android.settings.Settings\$AccessibilitySettingsActivity",
                title = "Accessibility",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `device admin settings activity is shielded`() {
        assertEquals(
            ShieldKind.DEVICE_ADMIN,
            SettingsShield.classify(
                className = "com.android.settings.Settings\$DeviceAdminSettingsActivity",
                title = "Device admin apps",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `app info of self is shielded`() {
        assertEquals(
            ShieldKind.APP_INFO_SELF,
            SettingsShield.classify(
                className = "com.android.settings.Settings\$InstalledAppDetailsActivity",
                title = "SelfGuard",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `app info of another app is not shielded`() {
        assertNull(
            SettingsShield.classify(
                className = "com.android.settings.Settings\$InstalledAppDetailsActivity",
                title = "Drive",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `subsettings with accessibility heading is shielded`() {
        assertEquals(
            ShieldKind.ACCESSIBILITY,
            SettingsShield.classify(
                className = "com.android.settings.SubSettings",
                title = "Accessibility",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `subsettings with non-sensitive heading is not shielded`() {
        assertNull(
            SettingsShield.classify(
                className = "com.android.settings.SubSettings",
                title = "Wi-Fi",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `subsettings app info of app named accessibility scanner is not shielded`() {
        assertNull(
            SettingsShield.classify(
                className = "com.android.settings.SubSettings",
                title = "Accessibility Scanner",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `wifi settings is not shielded`() {
        assertNull(
            SettingsShield.classify(
                className = "com.android.settings.Settings\$WifiSettingsActivity",
                title = "Wi-Fi",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `display settings is not shielded`() {
        assertNull(
            SettingsShield.classify(
                className = "com.android.settings.Settings\$DisplaySettingsActivity",
                title = "Display",
                selfLabel = self,
            ),
        )
    }

    @Test
    fun `null class name is not shielded`() {
        assertNull(SettingsShield.classify(className = null, title = "SelfGuard", selfLabel = self))
    }

    @Test
    fun `user messages are full sentences`() {
        assertEquals(
            "SelfGuard cannot be opened while SelfGuard is armed",
            ShieldKind.APP_INFO_SELF.userMessage(self),
        )
    }
}
