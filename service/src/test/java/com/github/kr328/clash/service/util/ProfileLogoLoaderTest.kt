package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ProfileLogoLoaderTest {
    @Test
    fun onlyHttpAndHttpsAreRemote() {
        assertTrue(ProfileLogoLoader.isRemoteUrl("https://example.com/logo.png"))
        assertTrue(ProfileLogoLoader.isRemoteUrl("HTTP://example.com/logo.png"))
        assertFalse(ProfileLogoLoader.isRemoteUrl("ftp://example.com/logo.png"))
        assertFalse(ProfileLogoLoader.isRemoteUrl("file:///sdcard/logo.png"))
        assertFalse(ProfileLogoLoader.isRemoteUrl("data:image/png;base64,AAAA"))
        assertFalse(ProfileLogoLoader.isRemoteUrl(""))
    }

    @Test
    fun sampleSizeKeepsBothSidesAtOrAboveTarget() {
        assertEquals(1, ProfileLogoLoader.sampleSizeFor(100, 100, 192))
        assertEquals(1, ProfileLogoLoader.sampleSizeFor(300, 300, 192))
        assertEquals(2, ProfileLogoLoader.sampleSizeFor(400, 400, 192))
        assertEquals(4, ProfileLogoLoader.sampleSizeFor(1024, 1024, 192))
        // the short side decides
        assertEquals(1, ProfileLogoLoader.sampleSizeFor(4096, 200, 192))
        assertEquals(1, ProfileLogoLoader.sampleSizeFor(1024, 1024, 0))
    }

    @Test
    fun readCappedReturnsDataWithinLimit() {
        val data = ByteArray(20_000) { it.toByte() }

        val read = ProfileLogoLoader.readCapped(ByteArrayInputStream(data), 20_000)

        assertNotNull(read)
        assertEquals(data.toList(), read!!.toList())
    }

    @Test
    fun readCappedRejectsDataOverLimit() {
        val data = ByteArray(20_001)

        assertNull(ProfileLogoLoader.readCapped(ByteArrayInputStream(data), 20_000))
    }
}
