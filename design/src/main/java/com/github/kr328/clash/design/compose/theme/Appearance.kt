package com.github.kr328.clash.design.compose.theme

import com.github.kr328.clash.design.compose.dialog.ExpressiveDialogHost
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.compose.background.HomeBackgroundStyle
import com.github.kr328.clash.design.compose.component.IconLoader
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.ProfileRecency
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.ProfileTheme
import com.github.kr328.clash.service.model.theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * The look of the whole app, as on iOS (ProfileAccent): the accent — the
 * active profile's card colour, a chosen colour or the profile's `pxa-theme`
 * — and the theme the profile brings. Every screen's [AppTheme] reads it;
 * [refresh] works it out again on a change of profile or setting.
 */
object Appearance {
    data class State(
        val accentLight: Color = AppGreen,
        val accentDark: Color = AppGreen,
        /** The active profile's `pxa-theme`, applied or not: Visual offers its switch only while there is one. */
        val activeTheme: ProfileTheme? = null,
        /** The name of the profile it comes from. */
        val activeThemeOwner: String = "",
        /** The theme in effect: the active profile's, unless switched off. */
        val appliedTheme: ProfileTheme? = null,
        /** The active profile's own colour, whatever the setting: the swatch of "Profile's Colour". */
        val profileColourLight: Color = AppGreen,
        val profileColourDark: Color = AppGreen,
        /** Home's background: the profile theme's, else the user's. */
        val background: HomeBackgroundStyle = HomeBackgroundStyle.DEFAULT,
        /** The background the applied theme sets; null when it leaves it to the user. */
        val themeBackground: HomeBackgroundStyle? = null,
        /** Settings → Visual → Profile Theme: every profile's `pxa-theme` card colour applies. */
        val usesProfileTheme: Boolean = true,
    ) {
        fun accent(dark: Boolean) = if (dark) accentDark else accentLight
        fun profileColour(dark: Boolean) = if (dark) profileColourDark else profileColourLight
    }

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable

    /** Re-reads the settings for [active], the active profile (null for none). */
    suspend fun refresh(context: Context, active: Profile?) {
        val store = UiStore(context)
        // Home's deck: the profile left behind goes nearest the card.
        ProfileRecency.noteActive(context, active?.uuid)
        val theme = active?.theme
        val applied = if (store.useProfileTheme) theme else null
        val base = ProfileCardColours.cardBase(context, active, applied)

        val chosen = when (val choice = store.accentChoice) {
            AccentChoice.Custom -> AccentRGB.parse(store.accentCustomHex) ?: AccentRGB.parse(AccentChoice.DEFAULT_CUSTOM_HEX)
            else -> choice.fixed
        }
        val green = AccentRGB(AccentColours.GREEN)

        // A colour as given (a fixed choice, Personalisation, the theme's hex)
        // is the same in light and dark; a card colour is adapted to each.
        val (light, dark) = when (val accent = applied?.accent) {
            is ProfileTheme.Colour.Hex -> AccentRGB.parse(accent.value).let { it to it }
            ProfileTheme.Colour.Auto -> adaptedPair(base)
            else -> when {
                store.accentChoice == AccentChoice.Profile && base != null -> adaptedPair(base)
                else -> chosen to chosen
            }
        }
        val (ownLight, ownDark) = adaptedPair(base)

        mutable.value = State(
            accentLight = (light ?: green).colour,
            accentDark = (dark ?: green).colour,
            activeTheme = theme,
            activeThemeOwner = active?.name.orEmpty(),
            appliedTheme = applied,
            profileColourLight = (ownLight ?: green).colour,
            profileColourDark = (ownDark ?: green).colour,
            background = HomeBackgroundStyle.effective(store.homeBackground, applied?.background),
            themeBackground = HomeBackgroundStyle.of(applied?.background),
            usesProfileTheme = store.useProfileTheme,
        )
    }

    private fun adaptedPair(base: AccentRGB?): Pair<AccentRGB?, AccentRGB?> =
        base?.let { AccentColours.adapted(it, dark = false) to AccentColours.adapted(it, dark = true) } ?: (null to null)

    /**
     * Whether the app is dark: the profile's `mode` while its theme applies
     * and sets one, else Settings → Visual → Theme.
     */
    fun isDark(store: UiStore, systemDark: Boolean): Boolean {
        val applied = state.value.appliedTheme
        return when (applied?.mode?.takeIf { applied.setsMode }) {
            ProfileTheme.Mode.Light -> false
            ProfileTheme.Mode.Dark -> true
            ProfileTheme.Mode.Auto -> systemDark
            else -> when (store.darkMode) {
                DarkMode.Auto -> systemDark
                DarkMode.ForceLight -> false
                DarkMode.ForceDark -> true
            }
        }
    }
}

/** Every screen's theme: the accent of [Appearance] for this light or dark look. */
@Composable
fun AppTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val state by Appearance.state.collectAsStateWithLifecycle()
    ClashTheme(darkTheme = darkTheme, accent = state.accent(darkTheme)) {
        content()
        // The dialogs asked for outside Compose (MaterialAlertDialogBuilder's place), over the screen.
        ExpressiveDialogHost()
    }
}

/**
 * The colour of a profile's card, as on iOS (ProfileCardColor): the
 * `pxa-theme` card colour, else the main colour of its `profile-logo`.
 */
object ProfileCardColours {
    private val cache = ConcurrentHashMap<String, AccentRGB>()

    /** Logos that loaded but have no colour of their own (grey, black, white). */
    private val colourless: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** A subscription's card without a colourful logo: the app's green. */
    val SUBSCRIPTION = AccentRGB(AccentColours.GREEN)

    /** A local file's card: graphite. */
    val FILE = AccentRGB(AccentColours.FILE)

    /**
     * The colour of [profile]'s card: its `pxa-theme` card colour while
     * profile themes are on, else its logo's, else green for a subscription
     * and graphite for a file.
     */
    suspend fun cardColour(context: Context, profile: Profile, usesTheme: Boolean): AccentRGB {
        val theme = if (usesTheme) profile.theme else null
        return cardBase(context, profile, theme) ?: fallback(profile)
    }

    /** The same without waiting: only what is already worked out, for a card's first frame. */
    fun cachedCardColour(profile: Profile, usesTheme: Boolean): AccentRGB {
        val themed = (if (usesTheme) profile.theme else null)?.cardHex?.let(AccentColours::themed)
        return themed ?: cache[profile.profileLogo] ?: fallback(profile)
    }

    private fun fallback(profile: Profile) = if (profile.type == Profile.Type.File) FILE else SUBSCRIPTION

    /** The card colour the theme fixes, else the logo's; null for a colourless logo or none. */
    suspend fun cardBase(context: Context, profile: Profile?, theme: ProfileTheme?): AccentRGB? {
        theme?.cardHex?.let { return AccentColours.themed(it) }
        val logo = profile?.profileLogo.orEmpty()
        if (logo.isEmpty()) return null
        return logoColour(context, logo)
    }

    /** The card colour of a logo that has one; null for a colourless logo or one that will not load. */
    suspend fun logoColour(context: Context, logo: String): AccentRGB? {
        cache[logo]?.let { return it }
        if (logo in colourless) return null
        val bitmap = IconLoader.load(context, logo) ?: return null
        val main = AccentColours.mainColour(bitmap)
        if (main == null) {
            colourless += logo
            return null
        }
        return AccentColours.readable(main).also { cache[logo] = it }
    }
}
