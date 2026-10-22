package com.selfguard.domain.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalAppsTest {

    private val self = "com.selfguard"
    private val launchers = setOf("com.launcher", "com.vivo.simplelauncher")

    @Test
    fun `self is protected`() {
        assertEquals(
            ProtectionReason.SELF,
            CriticalApps.protectReason(self, self, launchers, "com.ime"),
        )
    }

    @Test
    fun `launcher is protected`() {
        assertEquals(
            ProtectionReason.LAUNCHER,
            CriticalApps.protectReason("com.launcher", self, launchers, "com.ime"),
        )
    }

    @Test
    fun `every launcher package is protected`() {
        for (launcher in launchers) {
            assertEquals(
                ProtectionReason.LAUNCHER,
                CriticalApps.protectReason(launcher, self, launchers, "com.ime"),
            )
        }
    }

    @Test
    fun `ime is protected`() {
        assertEquals(
            ProtectionReason.IME,
            CriticalApps.protectReason("com.ime", self, launchers, "com.ime"),
        )
    }

    @Test
    fun `static critical packages are protected`() {
        for (pkg in CriticalApps.STATIC_CRITICAL) {
            assertTrue(pkg, CriticalApps.isProtected(pkg, self, emptySet(), null))
            assertEquals(ProtectionReason.CRITICAL, CriticalApps.protectReason(pkg, self, emptySet(), null))
        }
    }

    @Test
    fun `normal apps are not protected`() {
        assertNull(CriticalApps.protectReason("com.someapp", self, launchers, "com.ime"))
        assertFalse(CriticalApps.isProtected("com.someapp", self, launchers, "com.ime"))
    }

    @Test
    fun `empty launcher and ime do not protect anything`() {
        assertNull(CriticalApps.protectReason("com.someapp", self, emptySet(), null))
        assertNull(CriticalApps.protectReason("", self, setOf(""), ""))
    }

    @Test
    fun `user message names the reason`() {
        assertEquals("SelfGuard cannot block itself", ProtectionReason.SELF.userMessage(self))
        assertTrue(
            ProtectionReason.CRITICAL.userMessage("com.foo")
                .contains("com.foo"),
        )
    }
}
