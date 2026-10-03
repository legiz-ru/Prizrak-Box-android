package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentTest {
    @Test
    fun includesVersionAndCore() {
        assertEquals(
            "prizrak-box/1.0.21-beta06 (Android Build; Prizrak-Core v1.19.32-r1)",
            buildUserAgent("1.0.21-beta06", "v1.19.32-r1"),
        )
    }

    @Test
    fun leavesOutBlankCore() {
        assertEquals("prizrak-box/1.0.21-beta06 (Android Build)", buildUserAgent("1.0.21-beta06", ""))
    }

    @Test
    fun keepsTheSlashWhenVersionIsUnknown() {
        assertEquals("prizrak-box/unknown (Android Build)", buildUserAgent(null, ""))
        assertEquals("prizrak-box/unknown (Android Build)", buildUserAgent(" ", ""))
    }

    // Remnawave sends serverDescription only to clients matching ^prizrak-box/
    @Test
    fun matchesRemnawaveExtendedClientRegex() {
        val extended = Regex("^prizrak-box/")

        assertTrue(extended.containsMatchIn(buildUserAgent("1.0.21-beta06", "v1.19.32-r1")))
        assertTrue(extended.containsMatchIn(buildUserAgent(null, "")))
    }
}
