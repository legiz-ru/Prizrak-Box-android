package com.github.kr328.clash.design.compose.screen

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.background.HomeBackgroundStyle
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.ConnectedChoice
import com.github.kr328.clash.design.compose.component.PreferenceCard
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.SingleChoiceDialog
import com.github.kr328.clash.design.compose.component.SwitchPreference
import com.github.kr328.clash.design.compose.theme.AccentChoice
import com.github.kr328.clash.design.compose.theme.AccentRGB
import com.github.kr328.clash.design.compose.theme.Appearance
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.service.model.ProfileTheme

/** The screens of Settings → Visual, one over another. */
enum class VisualPage { Main, Accent, Custom, Palette, Background, BackgroundPreview }

/**
 * Settings → Visual, as on iOS: the look of the app in one place — theme,
 * accent (with Personalisation and its palette), how a host's latency is
 * shown — and, while the active profile brings a `pxa-theme`, the switch
 * that applies it.
 */
@Composable
fun VisualSettingsScreen(
    page: VisualPage,
    onPage: (VisualPage) -> Unit,
    onBack: (() -> Unit)?,
    appearance: Appearance.State,
    darkTheme: Boolean,
    darkMode: DarkMode,
    onDarkMode: (DarkMode) -> Unit,
    delayDots: Boolean,
    /** The active profile's own choice (`pxa-latency-dots`), which locks the row; null when it has none. */
    profileDelayDots: Boolean?,
    onDelayDots: (Boolean) -> Unit,
    useProfileTheme: Boolean,
    onUseProfileTheme: (Boolean) -> Unit,
    accentChoice: AccentChoice,
    onAccentChoice: (AccentChoice) -> Unit,
    customHex: String,
    onSaveCustom: (String) -> Unit,
    paletteSpectrum: Boolean,
    onPaletteSpectrum: (Boolean) -> Unit,
    /** The user's own background, as stored (the profile's theme may show another). */
    homeBackground: HomeBackgroundStyle,
    onHomeBackground: (HomeBackgroundStyle) -> Unit,
    /** The app's language, as the system's per-app language screen has it. */
    languageSummary: String = "",
    onLanguage: () -> Unit = {},
    /** Android TV: no Home background row — the TV's Home has none (light work for its processor). */
    showsHomeBackground: Boolean = true,
) {
    // Personalisation's draft: kept while the palette is open over it,
    // made afresh from the saved colour each time it is opened.
    var draft by remember { mutableStateOf(AccentRGB.parse(customHex) ?: AccentRGB(0xE84A8F)) }

    BackHandler(enabled = page != VisualPage.Main) {
        onPage(
            when (page) {
                VisualPage.Palette -> VisualPage.Custom
                VisualPage.Custom -> VisualPage.Accent
                VisualPage.BackgroundPreview -> VisualPage.Background
                else -> VisualPage.Main
            },
        )
    }

    when (page) {
        VisualPage.Main -> VisualMain(
            onBack = onBack,
            languageSummary = languageSummary,
            onLanguage = onLanguage,
            appearance = appearance,
            darkTheme = darkTheme,
            darkMode = darkMode,
            onDarkMode = onDarkMode,
            delayDots = delayDots,
            profileDelayDots = profileDelayDots,
            onDelayDots = onDelayDots,
            useProfileTheme = useProfileTheme,
            onUseProfileTheme = onUseProfileTheme,
            accentChoice = accentChoice,
            onAccent = { onPage(VisualPage.Accent) },
            homeBackground = homeBackground,
            onBackground = { onPage(VisualPage.Background) },
            showsHomeBackground = showsHomeBackground,
        )
        VisualPage.Accent -> AccentColorPage(
            onBack = { onPage(VisualPage.Main) },
            appearance = appearance,
            darkTheme = darkTheme,
            accentChoice = accentChoice,
            onAccentChoice = onAccentChoice,
            customHex = customHex,
            onCustom = {
                draft = AccentRGB.parse(customHex) ?: AccentRGB(0xE84A8F)
                onPage(VisualPage.Custom)
            },
        )
        VisualPage.Custom -> CustomAccentPage(
            onBack = { onPage(VisualPage.Accent) },
            draft = draft,
            onDraft = { draft = it },
            saved = accentChoice == AccentChoice.Custom && customHex.equals(draft.hex, ignoreCase = true),
            onSave = {
                onSaveCustom(draft.hex)
                onPage(VisualPage.Accent)
            },
            onPalette = { onPage(VisualPage.Palette) },
        )
        VisualPage.Background -> HomeBackgroundPage(
            onBack = { onPage(VisualPage.Main) },
            appearance = appearance,
            darkTheme = darkTheme,
            selected = homeBackground,
            onSelect = onHomeBackground,
            onFullScreen = { onPage(VisualPage.BackgroundPreview) },
        )
        VisualPage.BackgroundPreview -> HomeBackgroundPreview(
            start = appearance.themeBackground ?: homeBackground,
            applied = homeBackground,
            accent = appearance.accent(darkTheme),
            onApply = {
                onHomeBackground(it)
                onPage(VisualPage.Background)
            },
            onClose = { onPage(VisualPage.Background) },
        )
        VisualPage.Palette -> AccentPalettePage(
            onBack = { onPage(VisualPage.Custom) },
            colour = draft,
            onColour = { draft = it },
            spectrum = paletteSpectrum,
            onSpectrum = onPaletteSpectrum,
        )
    }
}

// MARK: Visual

@Composable
private fun VisualMain(
    onBack: (() -> Unit)?,
    languageSummary: String,
    onLanguage: () -> Unit,
    appearance: Appearance.State,
    darkTheme: Boolean,
    darkMode: DarkMode,
    onDarkMode: (DarkMode) -> Unit,
    delayDots: Boolean,
    profileDelayDots: Boolean?,
    onDelayDots: (Boolean) -> Unit,
    useProfileTheme: Boolean,
    onUseProfileTheme: (Boolean) -> Unit,
    accentChoice: AccentChoice,
    onAccent: () -> Unit,
    homeBackground: HomeBackgroundStyle,
    onBackground: () -> Unit,
    showsHomeBackground: Boolean = true,
) {
    var themeDialog by remember { mutableStateOf(false) }
    var delayDialog by remember { mutableStateOf(false) }
    val applied = appearance.appliedTheme
    val setBy = stringResource(R.string.set_by_profile)

    val themeOptions = listOf(
        DarkMode.Auto to stringResource(R.string.theme_auto),
        DarkMode.ForceDark to stringResource(R.string.theme_dark),
        DarkMode.ForceLight to stringResource(R.string.theme_light),
    )
    // The profile's mode while its theme sets one: shown, and the row locked.
    val forcedMode = applied?.takeIf { it.setsMode }?.mode?.let {
        when (it) {
            ProfileTheme.Mode.Dark -> DarkMode.ForceDark
            ProfileTheme.Mode.Light -> DarkMode.ForceLight
            else -> DarkMode.Auto
        }
    }
    val delayOptions = listOf(
        true to stringResource(R.string.delay_display_dots),
        false to stringResource(R.string.delay_display_ms),
    )

    PreferenceScaffold(title = stringResource(R.string.visual), onBack = onBack) {
        // First: the app's language (the system's per-app language screen).
        item {
            PreferenceRow(
                title = stringResource(R.string.language),
                summary = languageSummary,
                icon = R.drawable.ic_baseline_language,
                onClick = onLanguage,
            )
        }
        gap()

        // Only while the active profile brings a theme.
        val theme = appearance.activeTheme
        if (theme != null) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.profile_theme),
                    summary = profileThemeSummary(theme, appearance.activeThemeOwner),
                    icon = R.drawable.ic_baseline_palette,
                    checked = useProfileTheme,
                    onCheckedChange = onUseProfileTheme,
                )
            }
            footnote(R.string.profile_theme_footer)
        }

        item {
            PreferenceRow(
                title = stringResource(R.string.visual_theme),
                summary = locked(themeOptions.first { it.first == (forcedMode ?: darkMode) }.second, forcedMode != null, setBy),
                icon = R.drawable.ic_baseline_brightness_4,
                enabled = forcedMode == null,
                onClick = { themeDialog = true },
            )
        }
        item {
            val accentSummary = if (applied?.setsAccent == true) {
                stringResource(R.string.from_profile)
            } else {
                stringResource(accentChoice.title)
            }
            SwatchRow(
                title = stringResource(R.string.accent_color),
                summary = locked(accentSummary, applied?.setsAccent == true, setBy),
                icon = R.drawable.ic_baseline_brush,
                swatch = appearance.accent(darkTheme),
                onClick = onAccent,
            )
        }
        item {
            val shown = profileDelayDots ?: delayDots
            PreferenceRow(
                title = stringResource(R.string.delay_display),
                summary = locked(delayOptions.first { it.first == shown }.second, profileDelayDots != null, setBy),
                icon = R.drawable.ic_baseline_speed,
                enabled = profileDelayDots == null,
                onClick = { delayDialog = true },
            )
        }
        if (showsHomeBackground) item {
            // One row here; the backgrounds, with a look at each, on their own screen.
            val shown = appearance.themeBackground ?: homeBackground
            PreferenceRow(
                title = stringResource(R.string.home_background),
                summary = locked(stringResource(shown.title), appearance.themeBackground != null, setBy),
                icon = R.drawable.ic_baseline_wallpaper,
                onClick = onBackground,
            )
        }
    }

    if (themeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.visual_theme),
            options = themeOptions,
            selected = darkMode,
            onSelect = onDarkMode,
            onDismiss = { themeDialog = false },
        )
    }
    if (delayDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.delay_display),
            options = delayOptions,
            selected = delayDots,
            onSelect = onDelayDots,
            onDismiss = { delayDialog = false },
        )
    }
}

/** "Dark · Set by the profile" when the profile decides. */
private fun locked(value: String, byProfile: Boolean, setBy: String) =
    if (byProfile) "$value · $setBy" else value

/** "«Name» sets: accent, card, theme, background." */
@Composable
private fun profileThemeSummary(theme: ProfileTheme, owner: String): String {
    val parts = buildList {
        if (theme.setsAccent) add(stringResource(R.string.profile_theme_part_accent))
        if (theme.setsCard) add(stringResource(R.string.profile_theme_part_card))
        if (theme.setsMode) add(stringResource(R.string.profile_theme_part_theme))
        if (HomeBackgroundStyle.of(theme.background) != null) add(stringResource(R.string.profile_theme_part_background))
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.profile_theme_leaves, owner)
    } else {
        stringResource(R.string.profile_theme_sets, owner, parts.joinToString(", "))
    }
}

@Composable
private fun Footnote(text: String, colour: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colour,
        modifier = Modifier.padding(start = 64.dp, end = 20.dp, bottom = 8.dp),
    )
}

/** A settings row with a round swatch at its end. */
@Composable
private fun SwatchRow(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    swatch: Color,
    onClick: () -> Unit,
) {
    PreferenceRow(
        title = title,
        summary = summary,
        icon = icon,
        onClick = onClick,
        trailing = { Swatch(swatch, size = 24) },
    )
}

@Composable
private fun Swatch(colour: Color, size: Int = 28, @DrawableRes badge: Int? = null) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(colour)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (badge != null) {
            Icon(
                painter = painterResource(badge),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

// MARK: Accent Color

@Composable
private fun AccentColorPage(
    onBack: () -> Unit,
    appearance: Appearance.State,
    darkTheme: Boolean,
    accentChoice: AccentChoice,
    onAccentChoice: (AccentChoice) -> Unit,
    customHex: String,
    onCustom: () -> Unit,
) {
    // The active profile's `pxa-theme` sets the accent.
    val locked = appearance.appliedTheme?.setsAccent == true

    PreferenceScaffold(title = stringResource(R.string.accent_color), onBack = onBack) {
        if (locked) {
            free("locked") {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Swatch(appearance.accent(darkTheme))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.accent_locked, appearance.activeThemeOwner),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        for (choice in AccentChoice.listed) {
            item(key = choice.name) {
                ChoiceRow(
                    swatch = choice.fixed?.colour ?: appearance.profileColour(darkTheme),
                    badge = if (choice == AccentChoice.Profile) R.drawable.ic_baseline_account_box else null,
                    title = stringResource(choice.title),
                    selected = accentChoice == choice,
                    enabled = !locked,
                    onClick = { onAccentChoice(choice) },
                )
            }
        }
        // Last: any colour, on a screen of its own, whatever is chosen above;
        // nothing changes until it is saved there.
        gap()
        item(key = AccentChoice.Custom.name) {
            ChoiceRow(
                swatch = (AccentRGB.parse(customHex) ?: AccentRGB(0xE84A8F)).colour,
                badge = R.drawable.ic_baseline_palette,
                title = stringResource(AccentChoice.Custom.title),
                selected = accentChoice == AccentChoice.Custom,
                enabled = !locked,
                chevron = true,
                onClick = onCustom,
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    swatch: Color,
    @DrawableRes badge: Int?,
    title: String,
    selected: Boolean,
    enabled: Boolean,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    PreferenceRow(
        title = title,
        enabled = enabled,
        onClick = onClick,
        leading = { Swatch(swatch, badge = badge) },
        trailing = {
            when {
                // Green as the rest of Settings.
                selected -> Icon(
                    painter = painterResource(R.drawable.ic_baseline_check),
                    contentDescription = null,
                    tint = ClashTheme.extraColors.settingsGreen,
                )
                chevron -> Icon(
                    painter = painterResource(R.drawable.ic_baseline_chevron_right),
                    contentDescription = null,
                )
            }
        },
    )
}

// MARK: Personalisation

/** Ten colours none of the listed accents is. */
private val SAMPLES = listOf(
    0xE84A8F, 0x8E5BE8, 0xB04FD9, 0x1FB5C9, 0x138F8F,
    0x8DBA2A, 0xD9B21F, 0xE8742E, 0x8A6240, 0x5E6670,
)

/**
 * Settings → Visual → Accent Color → Personalisation: any colour — a palette
 * (grid and spectrum), R G B, HEX, and ten ready colours the list does not
 * have. A draft: the accent changes only on Save.
 */
@Composable
private fun CustomAccentPage(
    onBack: () -> Unit,
    draft: AccentRGB,
    onDraft: (AccentRGB) -> Unit,
    saved: Boolean,
    onSave: () -> Unit,
    onPalette: () -> Unit,
) {
    var hexText by remember { mutableStateOf(draft.hex) }
    var hexFocused by remember { mutableStateOf(false) }
    // The draft changed elsewhere (palette, sliders, examples): the field follows unless being typed in.
    LaunchedEffect(draft) { if (!hexFocused) hexText = draft.hex }
    val hexProblem = AccentRGB.parse(hexText) == null
    val green = ClashTheme.extraColors.settingsGreen

    PreferenceScaffold(
        title = stringResource(R.string.accent_personalisation),
        onBack = onBack,
        actions = {
            TextButton(onClick = onSave, enabled = !saved && !hexProblem) {
                Text(
                    stringResource(R.string.save),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (!saved && !hexProblem) 1f else 0.38f),
                )
            }
        },
    ) {
        free("preview") {
            ColourPreview(draft, height = 72, modifier = Modifier.padding(vertical = 4.dp))
        }
        item {
            SwatchRow(
                title = stringResource(R.string.accent_palette),
                summary = "#" + draft.hex,
                icon = R.drawable.ic_baseline_palette,
                swatch = draft.colour,
                onClick = onPalette,
            )
        }
        footnote(R.string.accent_saved_hint)

        category("RGB")
        item { PreferenceCard { ChannelRow("R", Color(0xFFE53935), draft.r) { onDraft(draft.copy(r = it)) } } }
        item { PreferenceCard { ChannelRow("G", Color(0xFF43A047), draft.g) { onDraft(draft.copy(g = it)) } } }
        item { PreferenceCard { ChannelRow("B", Color(0xFF1E88E5), draft.b) { onDraft(draft.copy(b = it)) } } }

        category("HEX")
        item {
            PreferenceCard {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = hexText,
                    onValueChange = { text ->
                        // Only 0–9 and A–F, six at most; "#" and spaces of a pasted code dropped.
                        val clean = text.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
                        hexText = clean
                        AccentRGB.parse(clean)?.let { if (it != draft) onDraft(it) }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            hexFocused = it.isFocused
                            // Left unfinished: back to the colour it is.
                            if (!it.isFocused && AccentRGB.parse(hexText) == null) hexText = draft.hex
                        },
                )
                Icon(
                    painter = painterResource(if (hexProblem) R.drawable.ic_baseline_error else R.drawable.ic_outline_check_circle),
                    contentDescription = null,
                    tint = if (hexProblem) MaterialTheme.colorScheme.error else green,
                )
            }
            }
        }
        if (hexProblem) {
            footnote(R.string.accent_hex_hint, error = true)
        }

        category(R.string.accent_examples)
        item {
            PreferenceCard {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                for (value in SAMPLES) {
                    val sample = AccentRGB(value)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDraft(sample) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // A ring in the text colour around the chosen one.
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    if (sample == draft) 2.dp else 0.dp,
                                    if (sample == draft) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    CircleShape,
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(sample.colour),
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ColourPreview(colour: AccentRGB, height: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colour.colour),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "#" + colour.hex,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            color = if (colour.isLight) Color.Black else Color.White,
        )
    }
}

/** One channel: a slider, and its number, typed in when tapped. */
@Composable
private fun ChannelRow(name: String, tint: Color, value: Int, onValue: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) text = value.toString() }
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontFamily = FontFamily.Monospace, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint,
                // The rest of the track the channel's own colour, faint — not the accent.
                inactiveTrackColor = tint.copy(alpha = 0.22f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text,
            onValueChange = { new ->
                // Digits only, 255 at most.
                val digits = new.filter { it.isDigit() }.take(3)
                val n = digits.toIntOrNull()
                text = if (n != null && n > 255) "255" else digits
                text.toIntOrNull()?.let { if (it != value) onValue(it) }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = if (focused) scheme.onSurface else scheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            cursorBrush = SolidColor(scheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.onSurface.copy(alpha = if (focused) 0.12f else 0.06f))
                .padding(vertical = 8.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) text = value.toString()
                },
        )
    }
}

// MARK: Palette

private const val COLUMNS = 12
private const val ROWS = 10

/** The top row greys, white to black; below it twelve hues, dark and deep at the top down to pale. */
private fun gridColour(row: Int, column: Int): AccentRGB {
    if (row == 0) {
        val v = Math.round(255 * (1 - column.toDouble() / (COLUMNS - 1))).toInt()
        return AccentRGB(v, v, v)
    }
    val hue = (0.55 + column.toDouble() / COLUMNS) % 1.0
    val t = row - 1
    val s = if (t < 5) 1.0 else 1.0 - (t - 4) * 0.18
    val v = if (t < 5) 0.35 + t * 0.16 else 1.0
    return AccentRGB.hsv(hue, s, v)
}

/** Hue across; down the middle the pure hue, white above it, black below. */
private fun spectrumColour(x: Double, y: Double): AccentRGB {
    val pure = AccentRGB.hsv(x, 1.0, 1.0)
    return if (y < 0.5) AccentRGB.WHITE.mixed(pure, y * 2) else pure.mixed(AccentRGB.BLACK, (y - 0.5) * 2)
}

/** Where the colour sits on the spectrum, near enough. */
private fun spectrumPoint(c: AccentRGB): Offset {
    val (h, s, v) = c.hsv
    val y = if (v >= 0.995) s / 2 else 0.5 + (1 - v) / 2
    return Offset(h.toFloat(), y.toFloat())
}

/** Personalisation → Palette: a grid of colours or the whole spectrum; a tap or a drag across either picks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccentPalettePage(
    onBack: () -> Unit,
    colour: AccentRGB,
    onColour: (AccentRGB) -> Unit,
    spectrum: Boolean,
    onSpectrum: (Boolean) -> Unit,
) {
    // Where the finger left the spectrum's marker; worked back from the colour otherwise.
    var point by remember { mutableStateOf<Offset?>(null) }
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    PreferenceScaffold(title = stringResource(R.string.accent_palette), onBack = onBack) {
        free("palette") {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ConnectedChoice(
                    options = listOf(
                        false to stringResource(R.string.accent_grid),
                        true to stringResource(R.string.accent_spectrum),
                    ),
                    selected = spectrum,
                    onSelect = onSpectrum,
                )

                val shape = RoundedCornerShape(14.dp)
                val area = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COLUMNS.toFloat() / ROWS)
                    .clip(shape)
                    // Black and white cells stay visible on either theme.
                    .border(1.dp, outline, shape)

                if (spectrum) {
                    val p = point ?: spectrumPoint(colour)
                    fun pick(at: Offset, size: Size) {
                        val x = (at.x / size.width).coerceIn(0f, 0.9999f)
                        val y = (at.y / size.height).coerceIn(0f, 1f)
                        point = Offset(x, y)
                        onColour(spectrumColour(x.toDouble(), y.toDouble()))
                    }
                    Canvas(
                        modifier = area
                            .pointerInput(Unit) {
                                detectTapGestures { pick(it, Size(size.width.toFloat(), size.height.toFloat())) }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    pick(change.position, Size(size.width.toFloat(), size.height.toFloat()))
                                }
                            },
                    ) {
                        drawRect(Brush.horizontalGradient((0..12).map { AccentRGB.hsv(it / 12.0, 1.0, 1.0).colour }))
                        drawRect(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), endY = size.height / 2))
                        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black), startY = size.height / 2))
                        val centre = Offset(p.x * size.width, p.y * size.height)
                        val r = 14.dp.toPx()
                        drawCircle(Color.Black.copy(alpha = 0.3f), radius = r + 1.5f, center = centre)
                        drawCircle(colour.colour, radius = r, center = centre)
                        drawCircle(Color.White, radius = r - 1.5.dp.toPx(), center = centre, style = Stroke(3.dp.toPx()))
                    }
                } else {
                    fun pick(at: Offset, size: Size) {
                        val column = (at.x / (size.width / COLUMNS)).toInt().coerceIn(0, COLUMNS - 1)
                        val row = (at.y / (size.height / ROWS)).toInt().coerceIn(0, ROWS - 1)
                        point = null
                        val c = gridColour(row, column)
                        if (c != colour) onColour(c)
                    }
                    Canvas(
                        modifier = area
                            .pointerInput(Unit) {
                                detectTapGestures { pick(it, Size(size.width.toFloat(), size.height.toFloat())) }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    pick(change.position, Size(size.width.toFloat(), size.height.toFloat()))
                                }
                            },
                    ) {
                        val cw = size.width / COLUMNS
                        val ch = size.height / ROWS
                        for (row in 0 until ROWS) for (column in 0 until COLUMNS) {
                            val c = gridColour(row, column)
                            val topLeft = Offset(column * cw, row * ch)
                            drawRect(c.colour, topLeft, Size(cw + 0.5f, ch + 0.5f))
                            if (c == colour) {
                                val w = 3.dp.toPx()
                                drawRect(
                                    Color.White,
                                    Offset(topLeft.x + w / 2, topLeft.y + w / 2),
                                    Size(cw - w, ch - w),
                                    style = Stroke(w),
                                )
                            }
                        }
                    }
                }

                ColourPreview(colour, height = 64)
            }
        }
    }
}
