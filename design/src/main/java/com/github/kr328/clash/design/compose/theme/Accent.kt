package com.github.kr328.clash.design.compose.theme

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.github.kr328.clash.design.R
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Settings → Visual → Accent Color, as on iOS: the active profile's colour by
 * default; the app's green; one of the five colours of the desktop app; or a
 * colour of one's own (Personalisation).
 */
enum class AccentChoice(@StringRes val title: Int, private val rgb: Int?) {
    Profile(R.string.accent_profile, null),
    Green(R.string.accent_green, AccentColours.GREEN),
    Indigo(R.string.accent_indigo, 0x5B67E8),
    Emerald(R.string.accent_emerald, 0x1F9E7A),
    Red(R.string.accent_red, 0xC9484F),
    Amber(R.string.accent_amber, 0xC98A2E),
    Blue(R.string.accent_blue, 0x2F7FBF),
    Custom(R.string.accent_personalisation, null);

    /** The colour of a fixed choice; null for the profile's, which changes with the profile, and for Custom. */
    val fixed: AccentRGB? get() = rgb?.let(::AccentRGB)

    companion object {
        /** The choices with a row of their own; Personalisation is the last row, a screen of its own. */
        val listed: List<AccentChoice> get() = entries.filter { it != Custom }

        const val DEFAULT_CUSTOM_HEX = "E84A8F"
    }
}

/** A colour as 0–255 channels. */
data class AccentRGB(val r: Int, val g: Int, val b: Int) {
    constructor(rgb: Int) : this((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)

    init {
        require(r in 0..255 && g in 0..255 && b in 0..255)
    }

    val hex: String get() = "%02X%02X%02X".format(r, g, b)

    val colour: Color get() = Color(r, g, b)

    /** Light enough that white text on it would not read. */
    val isLight: Boolean get() = 0.299 * r + 0.587 * g + 0.114 * b > 170

    /** Hue, saturation and value, each 0…1. */
    val hsv: Triple<Double, Double, Double>
        get() {
            val rd = r / 255.0
            val gd = g / 255.0
            val bd = b / 255.0
            val mx = maxOf(rd, gd, bd)
            val mn = minOf(rd, gd, bd)
            val d = mx - mn
            var h = 0.0
            if (d > 0) {
                h = when (mx) {
                    rd -> (gd - bd) / d
                    gd -> 2 + (bd - rd) / d
                    else -> 4 + (rd - gd) / d
                } / 6
                if (h < 0) h += 1
            }
            return Triple(h, if (mx == 0.0) 0.0 else d / mx, mx)
        }

    /** `this` with [other] laid over it at [amount]. */
    fun mixed(other: AccentRGB, amount: Double): AccentRGB {
        fun m(a: Int, b: Int) = (a * (1 - amount) + b * amount).roundToInt().coerceIn(0, 255)
        return AccentRGB(m(r, other.r), m(g, other.g), m(b, other.b))
    }

    companion object {
        val WHITE = AccentRGB(255, 255, 255)
        val BLACK = AccentRGB(0, 0, 0)

        /** "RRGGBB" or "#RRGGBB"; null for anything else. */
        fun parse(text: String?): AccentRGB? {
            val t = text?.trim()?.removePrefix("#") ?: return null
            if (t.length != 6 || !t.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) return null
            return AccentRGB(t.toInt(16))
        }

        fun clamped(r: Int, g: Int, b: Int) = AccentRGB(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

        /** From hue, saturation and value, each 0…1. */
        fun hsv(h: Double, s: Double, v: Double): AccentRGB {
            val h6 = (h - floor(h)) * 6
            val f = h6 - floor(h6)
            val p = v * (1 - s)
            val q = v * (1 - f * s)
            val t = v * (1 - (1 - f) * s)
            val (r, g, b) = when (h6.toInt() % 6) {
                0 -> Triple(v, t, p)
                1 -> Triple(q, v, p)
                2 -> Triple(p, v, t)
                3 -> Triple(p, q, v)
                4 -> Triple(t, p, v)
                else -> Triple(v, p, q)
            }
            return clamped((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
        }
    }
}

/**
 * The colour work behind the accent and the profile cards, the same as on
 * iOS (ProfileCardColor / ProfileAccent).
 */
object AccentColours {
    /** The app's green: the accent without a colourful logo, and Settings' switches. */
    const val GREEN = 0x2FB257

    /** Card of a local file without a theme colour. */
    const val FILE = 0x4D534E

    /** A `pxa-theme` card colour: as given, only darkened when it is too light for the card's white text. */
    fun themed(hex: String): AccentRGB {
        val c = AccentRGB.parse(hex) ?: return AccentRGB(GREEN)
        val luma = (0.299 * c.r + 0.587 * c.g + 0.114 * c.b) / 255
        val scale = if (luma > 0.62) 0.62 / luma else 1.0
        return AccentRGB.clamped((c.r * scale).roundToInt(), (c.g * scale).roundToInt(), (c.b * scale).roundToInt())
    }

    /** Saturated enough to be a colour, dark enough for white text. */
    fun readable(c: AccentRGB): AccentRGB {
        val (h, s, v) = c.hsv
        return AccentRGB.hsv(h, max(s, 0.45), min(max(v, 0.45), 0.72))
    }

    /**
     * A card colour made to read as an accent: darker on a light screen,
     * lighter on a dark one (a navy or wine card colour would vanish on
     * black otherwise).
     */
    fun adapted(base: AccentRGB, dark: Boolean): AccentRGB {
        val (h, s, v) = base.hsv
        return if (dark) {
            AccentRGB.hsv(h, min(s, 0.8), max(v, 0.82))
        } else {
            AccentRGB.hsv(h, max(s, 0.55), min(max(v, 0.42), 0.62))
        }
    }

    /**
     * Average of the logo's coloured pixels, weighted by saturation; null for a
     * grey, black or white logo.
     */
    fun mainColour(bitmap: Bitmap): AccentRGB? {
        val side = 24
        val small = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        small.getPixels(pixels, 0, side, 0, 0, side, side)
        if (small !== bitmap) small.recycle()
        return mainColour(pixels)
    }

    /** [mainColour] of ARGB pixels (not premultiplied, as Bitmap.getPixels gives them). */
    fun mainColour(pixels: IntArray): AccentRGB? {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var weight = 0.0
        for (p in pixels) {
            val a = ((p ushr 24) and 0xFF) / 255.0
            if (a <= 0.5) continue
            val pr = ((p shr 16) and 0xFF) / 255.0
            val pg = ((p shr 8) and 0xFF) / 255.0
            val pb = (p and 0xFF) / 255.0
            val maxC = maxOf(pr, pg, pb)
            val minC = minOf(pr, pg, pb)
            val saturation = if (maxC == 0.0) 0.0 else (maxC - minC) / maxC
            if (saturation <= 0.2 || maxC <= 0.15) continue
            r += pr * saturation
            g += pg * saturation
            b += pb * saturation
            weight += saturation
        }
        if (weight <= 3) return null
        return AccentRGB.clamped((r / weight * 255).roundToInt(), (g / weight * 255).roundToInt(), (b / weight * 255).roundToInt())
    }
}
