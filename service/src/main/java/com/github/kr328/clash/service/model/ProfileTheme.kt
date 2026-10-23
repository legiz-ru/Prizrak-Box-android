package com.github.kr328.clash.service.model

import java.util.Locale

/**
 * `pxa-theme`: the provider's look for the app — the profile card's colour,
 * the accent, light or dark, and Home's background. The mobile counterpart of
 * the desktop's `pxd-theme`, the same as on iOS:
 *
 *     pxa-theme: accent=#8e5be8; card=#3b2a6b; mode=dark; background=mushroomia
 *
 * The whole value may also come as `base64:<…>`. Keys and values ignore case
 * and the spaces around `=` and `;`. Every key is optional; one left out — or
 * set to `user` — is the user's own setting. Unknown keys and invalid values
 * are skipped, the rest applies. [parse] gives null when nothing valid is
 * left, so a fetch without the header clears the profile's theme.
 */
data class ProfileTheme(
    val card: Colour? = null,
    val accent: Colour? = null,
    val mode: Mode? = null,
    /**
     * A Home background id in lower case (`mushroomia`), or `user`. Which ids
     * exist is the app's business: an unknown one is the user's own.
     */
    val background: String? = null,
) {
    sealed interface Colour {
        /** `#rrggbb`, kept as the six digits in lower case. */
        data class Hex(val value: String) : Colour

        /** `card=auto`: from the logo; `accent=auto`: the card's colour. */
        data object Auto : Colour

        /** The user's own setting (`accent` only). */
        data object User : Colour
    }

    enum class Mode { Auto, Light, Dark, User }

    /** The card colour the profile fixes, as six hex digits; null when the card follows the logo. */
    val cardHex: String? get() = (card as? Colour.Hex)?.value

    /** What the profile takes over from the user's settings (`user` and a card from the logo take nothing). */
    val setsCard: Boolean get() = cardHex != null
    val setsAccent: Boolean get() = accent != null && accent != Colour.User
    val setsMode: Boolean get() = mode != null && mode != Mode.User
    val setsBackground: Boolean get() = background != null && background != USER

    companion object {
        const val USER = "user"
        private const val BASE64 = "base64:"

        /**
         * The header's value → its theme; null when it is absent or nothing in
         * it is valid. [decodeBase64] turns the text after `base64:` into the
         * value (null when it is not base64).
         */
        fun parse(
            raw: String?,
            decodeBase64: (String) -> String? = ::decodeAndroidBase64,
        ): ProfileTheme? {
            var value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (value.lowercase(Locale.ROOT).startsWith(BASE64)) {
                value = decodeBase64(value.substring(BASE64.length).trim())?.trim() ?: return null
            }

            var card: Colour? = null
            var accent: Colour? = null
            var mode: Mode? = null
            var background: String? = null
            for (part in value.split(';')) {
                val pair = part.split('=', limit = 2)
                if (pair.size != 2) continue
                val key = pair[0].trim().lowercase(Locale.ROOT)
                val v = pair[1].trim().lowercase(Locale.ROOT)
                when (key) {
                    "card" -> if (v == "auto") card = Colour.Auto else hex(v)?.let { card = Colour.Hex(it) }
                    "accent" -> when (v) {
                        "auto" -> accent = Colour.Auto
                        USER -> accent = Colour.User
                        else -> hex(v)?.let { accent = Colour.Hex(it) }
                    }
                    "mode" -> Mode.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == v }?.let { mode = it }
                    "background" -> if (v.isNotEmpty() && v.all { it.isLetterOrDigit() }) background = v
                }
            }
            if (card == null && accent == null && mode == null && background == null) return null
            return ProfileTheme(card, accent, mode, background)
        }

        /** `#rrggbb` → `rrggbb`; null for anything else. */
        private fun hex(value: String): String? {
            if (!value.startsWith("#")) return null
            val digits = value.substring(1)
            return digits.takeIf { it.length == 6 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        }

        private fun decodeAndroidBase64(text: String): String? = try {
            String(android.util.Base64.decode(text, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** The active theme this profile's last fetch brought, if any. */
val Profile.theme: ProfileTheme? get() = ProfileTheme.parse(pxaTheme)
