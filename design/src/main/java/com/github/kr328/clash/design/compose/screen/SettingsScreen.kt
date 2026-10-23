package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.statusBars
import com.github.kr328.clash.design.compose.rememberWideLayout
import com.github.kr328.clash.design.compose.component.ValuePreference
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.PreferenceListScope
import com.github.kr328.clash.design.compose.component.PreferenceList
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import androidx.compose.runtime.remember
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.SettingsItem
import com.github.kr328.clash.design.compose.theme.ClashTheme

/** Sub-screens reachable from the settings hub. */
enum class SettingsDestination {
    Visual, Notifications, Network, Service,
    Rules, Providers, Connections, Zashboard,
    Logs, About,
}

/** What the Settings hub shows besides its links: the About values. */
data class SettingsInfo(
    val appVersion: String = "",
    val coreVersion: String = "",
    val languageSummary: String = "",
    /** Debug → Zashboard is on: the dashboard under the Active Tunnel. */
    val zashboard: Boolean = false,
)

/** Top-level navigation targets (bottom bar on phones, side rail on tablets/TV). */
/** The app's tabs. Servers: Android TV only, while a profile runs. */
enum class SettingsNavTarget { Home, Servers, Profiles, Settings }

/**
 * Settings hub. Sections that are only meaningful while a profile is running
 * (Rules / Providers / Connections) are grouped under the "Active profile" header
 * and shown only when [clashRunning].
 *
 * @param expanded when true (tablet/TV) a side navigation rail is shown instead of
 *   the bottom navigation bar — matching the current TvNavigationDrawer behaviour.
 */
@Composable
fun SettingsScreen(
    expanded: Boolean,
    clashRunning: Boolean,
    onOpen: (SettingsDestination) -> Unit,
    onNavigate: (SettingsNavTarget) -> Unit,
    onToggleStatus: () -> Unit,
    modifier: Modifier = Modifier,
    hasProfiles: Boolean = true,
    info: SettingsInfo = SettingsInfo(),
    /** The section open beside the list (a wide window). */
    selected: SettingsDestination? = null,
    /** A section picked in a wide window (null: none); the activity decides whether it goes beside the list. */
    onSelect: (SettingsDestination?) -> Unit = { it?.let(onOpen) },
    /** The section's screen in the right column. */
    pane: @Composable (SettingsDestination) -> Unit = {},
) {
    val wide = if (expanded) com.github.kr328.clash.design.compose.WideLayout.Phone else rememberWideLayout()
    // In a wide window a section goes beside the list; otherwise it has a screen of its own.
    val open: (SettingsDestination) -> Unit = { if (wide.twoPane) onSelect(it) else onOpen(it) }
    val about: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        IconButton(onClick = { open(SettingsDestination.About) }) {
            Icon(painterResource(R.drawable.ic_outline_info), contentDescription = stringResource(R.string.about_the_app))
        }
    }
    if (expanded) {
        // Android TV: a panel at the side, as the TV's own settings and its launchers'.
        TvSettings(
            clashRunning = clashRunning,
            hasProfiles = hasProfiles,
            info = info,
            selected = selected,
            onSelect = onSelect,
            onNavigate = onNavigate,
            pane = pane,
            modifier = modifier,
        )
    } else if (wide.twoPane) {
        WideSettings(
            wide = wide,
            clashRunning = clashRunning,
            info = info,
            selected = selected,
            onOpen = open,
            onNavigate = onNavigate,
            onToggleStatus = onToggleStatus,
            about = about,
            pane = pane,
            modifier = modifier,
        )
    } else {
        // Narrowed with a section open beside the list (folded, a smaller window): it gets its own screen.
        LaunchedEffect(Unit) {
            selected?.let {
                onSelect(null)
                onOpen(it)
            }
        }
        PreferenceScaffold(
            title = stringResource(R.string.settings),
            onBack = null,
            actions = about,
            bottomBar = {
                AppBottomBar(
                    selected = SettingsNavTarget.Settings,
                    onNavigate = onNavigate,
                    clashRunning = clashRunning,
                    onDisconnect = onToggleStatus,
                )
            },
        ) {
            settingsEntries(null, clashRunning, info, open)
        }
    }
}

/**
 * Android TV's Settings, as the TV's own settings and its launchers': the
 * screen it came from stays behind, dimmed (the window is see-through on a
 * TV), the tabs at the top, and the settings in a rounded panel sliding in
 * at the right — the same entries as everywhere, About as a row under the
 * versions. A section opens in the panel, Back returns to the
 * list; one that has a screen of its own opens there.
 */
@Composable
private fun TvSettings(
    clashRunning: Boolean,
    hasProfiles: Boolean,
    info: SettingsInfo,
    selected: SettingsDestination?,
    onSelect: (SettingsDestination?) -> Unit,
    onNavigate: (SettingsNavTarget) -> Unit,
    pane: @Composable (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
    val panel = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(selected) {
        // Into the panel, on its first entry: the list when it opens, the section when one does.
        repeat(20) {
            kotlinx.coroutines.delay(60)
            if (runCatching { panel.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    androidx.activity.compose.BackHandler(enabled = selected != null) { onSelect(null) }

    Box(modifier = modifier.fillMaxSize()) {
        // What was on screen before stays behind, dimmed by the window (SettingsActivity).
        Box(Modifier.align(Alignment.TopCenter)) {
            TabletTopBar(
                selected = SettingsNavTarget.Settings,
                onNavigate = onNavigate,
                clashRunning = clashRunning,
                onDisconnect = {},
                tabs = tvTabs(clashRunning, hasProfiles),
                showDisconnect = false,
            )
        }
        AnimatedVisibility(
            visibleState = shown,
            enter = androidx.compose.animation.slideInHorizontally(
                androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f),
            ) { it } + androidx.compose.animation.fadeIn(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = TabletTopBarHeight + 6.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                color = scheme.surfaceContainerLow,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight(),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(panel)
                        .focusGroup(),
                ) {
                    if (selected == null) {
                        PreferenceList(
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                        ) {
                            settingsEntries(null, clashRunning, info, onOpen = { onSelect(it) }, aboutRow = true)
                        }
                    } else {
                        Box(Modifier.weight(1f)) { pane(selected) }
                    }
                }
            }
        }
    }
}

/**
 * Settings in a wide window, as iOS's split view: the list on the left, the
 * chosen section on the right ("Select a section" until then). A tablet has
 * its tabs at the top in the middle, as on Home; a foldable keeps the bar at
 * the bottom of the list, and the columns meet at its fold.
 */
@Composable
private fun WideSettings(
    wide: com.github.kr328.clash.design.compose.WideLayout,
    clashRunning: Boolean,
    info: SettingsInfo,
    selected: SettingsDestination?,
    onOpen: (SettingsDestination) -> Unit,
    onNavigate: (SettingsNavTarget) -> Unit,
    onToggleStatus: () -> Unit,
    about: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    pane: @Composable (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(scheme.surfaceContainer),
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
        val fold = wide.foldStart?.takeIf { it > 0.dp && it < maxWidth }
        val leftWidth = fold ?: (maxWidth * 0.42f).coerceIn(360.dp, 520.dp)
        val gap = if (fold != null) wide.foldThickness else 0.dp
        val tabs = if (wide.tablet) TabletTopBarHeight else 0.dp
        val foldTop = wide.foldTop

        if (foldTop != null) {
            // Half-open with the fold across the screen: the list above it, the section below, the bar at the bottom.
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(foldTop),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxHeight(),
                    ) {
                        PreferenceScaffold(
                            title = stringResource(R.string.settings),
                            onBack = null,
                            actions = about,
                        ) {
                            settingsEntries(selected, clashRunning, info, onOpen)
                        }
                    }
                }
                Spacer(Modifier.height(wide.foldThickness))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = scheme.surfaceContainerLow,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(
                                start = insets.calculateStartPadding(direction) + 12.dp,
                                end = insets.calculateEndPadding(direction) + 12.dp,
                                top = 12.dp,
                            ),
                    ) {
                        CompositionLocalProvider(com.github.kr328.clash.design.compose.LocalInPane provides true) {
                            Crossfade(targetState = selected, label = "settingsSection") { shown ->
                                if (shown != null) pane(shown) else SettingsPlaceholderScreen()
                            }
                        }
                    }
                    AppBottomBar(
                        selected = SettingsNavTarget.Settings,
                        onNavigate = onNavigate,
                        clashRunning = clashRunning,
                        onDisconnect = onToggleStatus,
                    )
                }
            }
            return@BoxWithConstraints
        }

        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(leftWidth)
                    .fillMaxHeight()
                    .padding(top = tabs),
            ) {
                PreferenceScaffold(
                    title = stringResource(R.string.settings),
                    onBack = null,
                    actions = about,
                    bottomBar = {
                        // A tablet has its tabs at the top; a foldable at the bottom of the list.
                        if (!wide.tablet) {
                            AppBottomBar(
                                selected = SettingsNavTarget.Settings,
                                onNavigate = onNavigate,
                                clashRunning = clashRunning,
                                onDisconnect = onToggleStatus,
                            )
                        }
                    },
                ) {
                    settingsEntries(selected, clashRunning, info, onOpen)
                }
            }
            Spacer(Modifier.width(gap))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.surfaceContainerLow,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        top = insets.calculateTopPadding() + tabs + if (wide.tablet) 0.dp else 8.dp,
                        end = insets.calculateEndPadding(direction) + 12.dp,
                        bottom = insets.calculateBottomPadding() + 12.dp,
                        start = if (fold != null) 12.dp else 0.dp,
                    ),
            ) {
                CompositionLocalProvider(com.github.kr328.clash.design.compose.LocalInPane provides true) {
                    Crossfade(targetState = selected, label = "settingsSection") { shown ->
                        if (shown != null) pane(shown) else SettingsPlaceholderScreen()
                    }
                }
            }
        }
        if (wide.tablet) {
            Box(Modifier.align(Alignment.TopCenter)) {
                TabletTopBar(
                    selected = SettingsNavTarget.Settings,
                    onNavigate = onNavigate,
                    clashRunning = clashRunning,
                    onDisconnect = onToggleStatus,
                )
            }
        }
    }
}

/**
 * The settings hub, as on iOS: Device, the Active Tunnel (while it runs),
 * About and Diagnostics — Material 3 Expressive groups.
 */
private fun PreferenceListScope.settingsEntries(
    selected: SettingsDestination?,
    clashRunning: Boolean,
    info: SettingsInfo,
    onOpen: (SettingsDestination) -> Unit,
    /** Android TV: About as a row of its own under the versions (no ⓘ in a bar there). */
    aboutRow: Boolean = false,
) {
    category(R.string.settings_device)
    item { SettingsItem(R.drawable.ic_baseline_palette, stringResource(R.string.visual), selected = selected == SettingsDestination.Visual) { onOpen(SettingsDestination.Visual) } }
    item { SettingsItem(R.drawable.ic_baseline_notifications, stringResource(R.string.notifications), selected = selected == SettingsDestination.Notifications) { onOpen(SettingsDestination.Notifications) } }
    item { SettingsItem(R.drawable.ic_baseline_dns, stringResource(R.string.network), selected = selected == SettingsDestination.Network) { onOpen(SettingsDestination.Network) } }
    item { SettingsItem(R.drawable.ic_baseline_settings_applications, stringResource(R.string.service_settings), selected = selected == SettingsDestination.Service) { onOpen(SettingsDestination.Service) } }

    // Only while the tunnel runs.
    if (clashRunning) {
        category(R.string.settings_active_tunnel)
        item { SettingsItem(R.drawable.ic_arrow_decision_outline, stringResource(R.string.rules), selected = selected == SettingsDestination.Rules) { onOpen(SettingsDestination.Rules) } }
        item { SettingsItem(R.drawable.ic_baseline_swap_vertical_circle, stringResource(R.string.providers), selected = selected == SettingsDestination.Providers) { onOpen(SettingsDestination.Providers) } }
        item { SettingsItem(R.drawable.ic_baseline_view_list, stringResource(R.string.connections), selected = selected == SettingsDestination.Connections) { onOpen(SettingsDestination.Connections) } }
        // Debug → Zashboard: the dashboard over the whole app.
        if (info.zashboard) {
            item { SettingsItem(R.drawable.ic_zashboard, "Zashboard") { onOpen(SettingsDestination.Zashboard) } }
        }
    }

    category(R.string.settings_about_app)
    item { ValuePreference(stringResource(R.string.app_version), info.appVersion, icon = R.drawable.ic_prizrak_ghost) }
    item { ValuePreference(stringResource(R.string.core_version), info.coreVersion, icon = R.drawable.ic_baseline_meta) }
    if (aboutRow) {
        item { SettingsItem(R.drawable.ic_outline_info, stringResource(R.string.about_more), selected = selected == SettingsDestination.About) { onOpen(SettingsDestination.About) } }
    }

    category(R.string.settings_diagnostics)
    item { SettingsItem(R.drawable.ic_baseline_assignment, stringResource(R.string.session_logs), selected = selected == SettingsDestination.Logs) { onOpen(SettingsDestination.Logs) } }
}

@Composable
private fun SettingsRail(
    clashRunning: Boolean,
    onNavigate: (SettingsNavTarget) -> Unit,
    onToggleStatus: () -> Unit,
    hasProfiles: Boolean,
) {
    NavigationRail(
        header = {
            if (hasProfiles || clashRunning) {
                FilledIconButton(
                    onClick = onToggleStatus,
                    colors = if (clashRunning) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        IconButtonDefaults.filledIconButtonColors()
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mdi_power),
                        contentDescription = stringResource(
                            if (clashRunning) R.string.stop else R.string.start,
                        ),
                    )
                }
            }
        },
    ) {
        NavigationRailItem(
            selected = false,
            onClick = { onNavigate(SettingsNavTarget.Home) },
            icon = {
                Icon(painter = painterResource(R.drawable.ic_mdi_home), contentDescription = null)
            },
            label = { Text(stringResource(R.string.home)) },
        )
        NavigationRailItem(
            selected = false,
            onClick = { onNavigate(SettingsNavTarget.Profiles) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_mdi_arrange_bring_forward),
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(R.string.profiles)) },
        )
        NavigationRailItem(
            selected = true,
            onClick = { onNavigate(SettingsNavTarget.Settings) },
            icon = {
                Icon(painter = painterResource(R.drawable.ic_mdi_cog), contentDescription = null)
            },
            label = { Text(stringResource(R.string.settings)) },
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPhonePreview() {
    ClashTheme(darkTheme = false) {
        SettingsScreen(
            expanded = false,
            clashRunning = true,
            onOpen = {},
            onNavigate = {},
            onToggleStatus = {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 420)
@Composable
private fun SettingsScreenExpandedPreview() {
    ClashTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                expanded = true,
                clashRunning = false,
                onOpen = {},
                onNavigate = {},
                onToggleStatus = {},
            )
        }
    }
}

/** Settings' right column with no section chosen yet (a wide window). */
@Composable
fun SettingsPlaceholderScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painterResource(R.drawable.ic_mdi_cog),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.wide_select_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
