package com.github.kr328.clash

import com.github.kr328.clash.util.appLanguageSummary
import com.github.kr328.clash.util.openAppLanguageSettings

import com.github.kr328.clash.BaseActivity.Event
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.compose.background.HomeBackgroundStyle
import com.github.kr328.clash.design.compose.screen.VisualPage
import com.github.kr328.clash.design.compose.screen.VisualSettingsScreen
import com.github.kr328.clash.design.compose.theme.AccentChoice
import com.github.kr328.clash.design.compose.theme.Appearance
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow

/** Settings → Visual: theme, accent color (with Personalisation), delay display, profile theme. */
class VisualSettingsActivity : SectionActivity() {
    override fun createSection() = VisualSection(this, ::finish)
}

class VisualSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {
    private val uiStore get() = activity.uiStore

    // Which of Visual's screens is open survives the recreation a change of theme makes.
    private val pageFlow = activity.retainedStateFlow("visual.page") { VisualPage.Main }
    private val darkModeFlow = MutableStateFlow(uiStore.darkMode)
    private val delayDotsFlow = MutableStateFlow(uiStore.delayDisplayDots)
    private val profileDelayDotsFlow = MutableStateFlow<Boolean?>(null)
    private val useProfileThemeFlow = MutableStateFlow(uiStore.useProfileTheme)
    private val accentChoiceFlow = MutableStateFlow(uiStore.accentChoice)
    private val customHexFlow = MutableStateFlow(uiStore.accentCustomHex)
    private val paletteSpectrumFlow = MutableStateFlow(uiStore.accentPaletteSpectrum)
    private val languageFlow = MutableStateFlow(activity.appLanguageSummary())
    private val homeBackgroundFlow = MutableStateFlow(
        HomeBackgroundStyle.of(uiStore.homeBackground) ?: HomeBackgroundStyle.DEFAULT,
    )

    override suspend fun run(events: SharedFlow<Event>) {
        readProfileDelayDots()
        events.collect {
            when (it) {
                Event.ProfileChanged, Event.ProfileLoaded -> readProfileDelayDots()
                // Back from the system's language screen too.
                Event.ActivityStart -> {
                    readProfileDelayDots()
                    languageFlow.value = activity.appLanguageSummary()
                }
                else -> Unit
            }
        }
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val page by pageFlow.collectAsStateWithLifecycle()
        val appearance by Appearance.state.collectAsStateWithLifecycle()
        val darkMode by darkModeFlow.collectAsStateWithLifecycle()
        val delayDots by delayDotsFlow.collectAsStateWithLifecycle()
        val profileDelayDots by profileDelayDotsFlow.collectAsStateWithLifecycle()
        val useProfileTheme by useProfileThemeFlow.collectAsStateWithLifecycle()
        val accentChoice by accentChoiceFlow.collectAsStateWithLifecycle()
        val customHex by customHexFlow.collectAsStateWithLifecycle()
        val paletteSpectrum by paletteSpectrumFlow.collectAsStateWithLifecycle()
        val homeBackground by homeBackgroundFlow.collectAsStateWithLifecycle()
        val language by languageFlow.collectAsStateWithLifecycle()

        VisualSettingsScreen(
            page = page,
            showsHomeBackground = !com.github.kr328.clash.common.util.TvUtils.isTv(activity),
            onPage = { pageFlow.value = it },
            onBack = onBack,
            appearance = appearance,
            darkTheme = activity.isNightTheme,
            darkMode = darkMode,
            onDarkMode = ::setDarkMode,
            delayDots = delayDots,
            profileDelayDots = profileDelayDots,
            onDelayDots = { uiStore.delayDisplayDots = it; delayDotsFlow.value = it },
            useProfileTheme = useProfileTheme,
            onUseProfileTheme = {
                uiStore.useProfileTheme = it
                useProfileThemeFlow.value = it
                activity.refreshAppearance()
            },
            accentChoice = accentChoice,
            onAccentChoice = {
                uiStore.accentChoice = it
                accentChoiceFlow.value = it
                activity.refreshAppearance()
            },
            customHex = customHex,
            onSaveCustom = {
                uiStore.accentCustomHex = it
                uiStore.accentChoice = AccentChoice.Custom
                customHexFlow.value = it
                accentChoiceFlow.value = AccentChoice.Custom
                activity.refreshAppearance()
            },
            paletteSpectrum = paletteSpectrum,
            onPaletteSpectrum = { uiStore.accentPaletteSpectrum = it; paletteSpectrumFlow.value = it },
            homeBackground = homeBackground,
            onHomeBackground = {
                uiStore.homeBackground = it.id
                homeBackgroundFlow.value = it
                activity.refreshAppearance()
            },
            languageSummary = language,
            onLanguage = { activity.openAppLanguageSettings() },
        )
    }

    /** The active profile's `pxa-latency-dots` / `ping-result`, which decides the delay display for it. */
    private suspend fun readProfileDelayDots() {
        val dots = try {
            withProfile { queryActive() }?.latencyDots ?: -1
        } catch (_: Exception) {
            -1
        }
        profileDelayDotsFlow.value = if (dots >= 0) dots == 1 else null
    }

    private fun setDarkMode(mode: DarkMode) {
        uiStore.darkMode = mode
        darkModeFlow.value = mode
        ApplicationObserver.createdActivities.forEach { it.recreate() }
    }
}
