package com.github.kr328.clash.service.model

import com.github.kr328.clash.service.model.ProfileTheme.Colour
import com.github.kr328.clash.service.model.ProfileTheme.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ProfileThemeTest {
    private fun parse(raw: String?) = ProfileTheme.parse(raw) { text ->
        try {
            String(Base64.getDecoder().decode(text), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @Test
    fun everyKey() {
        assertEquals(
            ProfileTheme(Colour.Hex("3b2a6b"), Colour.Hex("8e5be8"), Mode.Dark, "mushroomia"),
            parse("accent=#8e5be8; card=#3b2a6b; mode=dark; background=mushroomia"),
        )
    }

    @Test
    fun caseAndSpacesAreIgnored() {
        assertEquals(
            ProfileTheme(Colour.Hex("aabbcc"), Colour.Auto, Mode.Light, "cucumber"),
            parse("  CARD = #AABBCC ;Accent=AUTO;  mode =Light ; Background= Cucumber ;"),
        )
    }

    @Test
    fun base64() {
        val encoded = Base64.getEncoder().encodeToString("card=auto;accent=user;mode=user;background=user".toByteArray())
        assertEquals(
            ProfileTheme(Colour.Auto, Colour.User, Mode.User, "user"),
            parse("base64:$encoded"),
        )
        assertEquals(ProfileTheme(mode = Mode.Auto), parse("BASE64: " + Base64.getEncoder().encodeToString("mode=auto".toByteArray())))
        assertNull(parse("base64:%%%"))
    }

    @Test
    fun invalidValuesAreSkipped() {
        assertEquals(
            ProfileTheme(accent = Colour.Hex("00ff00")),
            parse("card=#12345; card=user; accent=#00ff00; mode=night; background=my-bg; colour=#ffffff"),
        )
        assertNull(parse("card=red; accent=#ggg000; mode=; background="))
        assertNull(parse("accent"))
    }

    @Test
    fun nothingIsNothing() {
        assertNull(parse(null))
        assertNull(parse(""))
        assertNull(parse("   "))
    }

    @Test
    fun whatTheProfileTakesOver() {
        val user = parse("card=auto; accent=user; mode=user; background=user")!!
        assertFalse(user.setsCard)
        assertFalse(user.setsAccent)
        assertFalse(user.setsMode)
        assertFalse(user.setsBackground)
        assertNull(user.cardHex)

        val all = parse("card=#101010; accent=auto; mode=auto; background=signal")!!
        assertTrue(all.setsCard)
        assertTrue(all.setsAccent)
        assertTrue(all.setsMode)
        assertTrue(all.setsBackground)
        assertEquals("101010", all.cardHex)
    }
}
