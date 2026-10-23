package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Height of the bar: the tabs and the circles beside them. */
private val BarHeight = 62.dp

/**
 * The app's bar, as on iOS: the Home and Settings tabs in a floating capsule
 * in the middle — icon over label, the chosen one in the accent — and, at
 * the screen's corners on the same line, a red Disconnect circle while the
 * VPN runs and, on Home with a simple-mode profile, the latency test. The
 * tabs stay centred whether the circles show or not.
 */
@Composable
fun AppBottomBar(
    selected: SettingsNavTarget,
    onNavigate: (SettingsNavTarget) -> Unit,
    clashRunning: Boolean,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    showLatencyTest: Boolean = false,
    latencyTesting: Boolean = false,
    onLatencyTest: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        NavPill(selected = selected, onNavigate = onNavigate, modifier = Modifier.align(Alignment.Center))

        AnimatedVisibility(
            visible = showLatencyTest,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            BarCircle(
                icon = R.drawable.ic_baseline_speed,
                label = stringResource(R.string.delay_test),
                busy = latencyTesting,
                onClick = { if (!latencyTesting) onLatencyTest() },
            )
        }
        AnimatedVisibility(
            visible = clashRunning,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            BarCircle(
                icon = R.drawable.ic_mdi_power,
                label = stringResource(R.string.disconnect),
                tint = Color(0xFFE5393B),
                onClick = onDisconnect,
            )
        }
    }
}

/**
 * The tabs — Home and Settings, and Profiles on a tablet or a TV — in a
 * floating capsule, as iOS's tab bar: the chosen one sits on a lens that
 * springs from tab to tab, swells under the finger and stretches as it
 * travels. A finger can slide the lens across the bar (the tab under it is
 * chosen when it lets go, past either end the lens gives like rubber). The
 * lens and the capsule are concentric. Opening another screen, the lens
 * slides over from the tab it came from.
 */
@Composable
fun NavPill(
    selected: SettingsNavTarget,
    onNavigate: (SettingsNavTarget) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<SettingsNavTarget> = PhoneTabs,
    /** Android TV: also on the chosen tab, for the screen under the bar to come back up to it. */
    selectedFocus: FocusRequester? = null,
    /** Android TV: the remote's focus starts on the chosen tab (Profiles: the deck is one press down). */
    focusSelectedOnStart: Boolean = false,
    /** Android TV: where Down from a tab goes, when the screen has a place for it. */
    downFocus: FocusRequester? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val count = tabs.size
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    val itemPx = with(density) { PillItemWidth.toPx() }

    // Where the lens is, in tabs: from the tab the last screen showed, sliding to this one.
    val lens = remember(tabs) {
        val from = lastShownTab?.let { tabs.indexOf(it) }?.takeIf { it >= 0 } ?: selectedIndex
        Animatable(from.toFloat())
    }
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val swell by animateFloatAsState(
        if (pressed || dragging) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "swell",
    )
    LaunchedEffect(selectedIndex, tabs) {
        lastShownTab = selected
        if (!dragging) lens.animateTo(selectedIndex.toFloat(), LensTravel)
    }
    // Back on this screen (another one's bar was used in between): the lens is put back on this
    // screen's tab. It had slid away to the tab tapped here before the screen changed, and the
    // screen kept it there — Home came back with Settings marked, or seemed to jump back.
    LifecycleResumeEffect(selectedIndex, tabs) {
        val job = scope.launch {
            val from = lastShownTab?.let { tabs.indexOf(it) }?.takeIf { it >= 0 && it != selectedIndex }
            dragging = false
            pressed = false
            if (from != null) lens.snapTo(from.toFloat())
            lastShownTab = selected
            lens.animateTo(selectedIndex.toFloat(), LensTravel)
        }
        onPauseOrDispose { job.cancel() }
    }

    // The remote's focus on the tabs. Back on this screen with it still on the tab chosen before
    // the screen changed (Settings, Profiles), it goes to this screen's own tab — it stayed on the
    // other one, as if that were open.
    val itemFocus = remember(tabs) { List(tabs.size) { FocusRequester() } }
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    LifecycleResumeEffect(selectedIndex, tabs) {
        val stale = focusedIndex
        if (stale != null && stale != selectedIndex) runCatching { itemFocus[selectedIndex].requestFocus() }
        onPauseOrDispose { }
    }
    if (focusSelectedOnStart) {
        LaunchedEffect(Unit) {
            // The window hands its first focus out after the screen is shown: asked again until the tab has it.
            repeat(30) {
                delay(100)
                if (focusedIndex == selectedIndex) return@LaunchedEffect
                runCatching { itemFocus[selectedIndex].requestFocus() }
            }
        }
    }

    // A tab chosen by tap or drop: the lens goes there first, then the screen changes.
    // Another tab tapped in those 140 ms: the first choice stands.
    var navigating by remember { mutableStateOf(false) }
    fun choose(index: Int) {
        val target = index.coerceIn(0, count - 1)
        scope.launch {
            if (tabs[target] != selected) {
                if (navigating) return@launch
                navigating = true
                lastShownTab = tabs[target]
                launch { lens.animateTo(target.toFloat(), LensTravel) }
                delay(140)
                onNavigate(tabs[target])
                navigating = false
            } else {
                lens.animateTo(target.toFloat(), LensTravel)
                onNavigate(tabs[target])
            }
        }
    }

    Surface(
        modifier = modifier.graphicsLayer {
            // The bar itself swells a little under the finger.
            val s = 1f + 0.03f * swell
            scaleX = s
            scaleY = s
        },
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .padding(PillInset)
                .size(height = BarHeight - PillInset * 2, width = PillItemWidth * count)
                .pointerInput(tabs, selected) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            val raw = lens.value + dx / itemPx
                            // Past either end: rubber band.
                            val limit = 0.35f
                            val bounded = when {
                                raw < 0f -> -limit * (1f - 1f / (-raw / limit + 1f))
                                raw > count - 1f -> (count - 1f) + limit * (1f - 1f / ((raw - count + 1f) / limit + 1f))
                                else -> raw
                            }
                            scope.launch { lens.snapTo(bounded) }
                        },
                        onDragEnd = {
                            dragging = false
                            choose(lens.value.roundToInt())
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { lens.animateTo(selectedIndex.toFloat(), LensTravel) }
                        },
                    )
                },
        ) {
            // The lens: as tall as the tabs, its corners concentric with the capsule's.
            Box(
                Modifier
                    .size(width = PillItemWidth, height = BarHeight - PillInset * 2)
                    .graphicsLayer {
                        // Stretched along its path while it travels, squashed a little across it.
                        val stretch = (abs(lens.velocity) * 0.06f).coerceAtMost(0.3f)
                        translationX = lens.value * itemPx
                        scaleX = 1f + stretch + 0.08f * swell
                        scaleY = 1f - stretch * 0.35f + 0.08f * swell
                    }
                    .clip(CircleShape)
                    .background(scheme.onSurface.copy(alpha = 0.10f + 0.05f * swell)),
            )
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    val (icon, label) = when (tab) {
                        SettingsNavTarget.Home -> R.drawable.ic_mdi_home to R.string.home
                        SettingsNavTarget.Servers -> R.drawable.ic_mdi_earth to R.string.servers
                        SettingsNavTarget.Profiles -> R.drawable.ic_baseline_view_list to R.string.profiles
                        SettingsNavTarget.Settings -> R.drawable.ic_mdi_cog to R.string.settings
                    }
                    NavPillItem(
                        // 1 on the lens, 0 a tab away: the accent follows the lens as it moves.
                        onLens = { (1f - abs(lens.value - index)).coerceIn(0f, 1f) },
                        icon = icon,
                        label = stringResource(label),
                        onClick = { choose(index) },
                        onPress = { pressed = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onFocusChanged {
                                if (it.isFocused) focusedIndex = index else if (focusedIndex == index) focusedIndex = null
                            }
                            .focusRequester(itemFocus[index])
                            .then(if (selectedFocus != null && index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier)
                            .then(if (downFocus != null) Modifier.focusProperties { down = downFocus } else Modifier),
                    )
                }
            }
        }
    }
}

/** The tab shown last, on any screen: the next bar's lens slides over from it. */
private var lastShownTab: SettingsNavTarget? = null

private val PillItemWidth = 84.dp
private val PillInset = 4.dp
/** A light bounce, as iOS's tab bar. */
private val LensTravel = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)

val PhoneTabs = listOf(SettingsNavTarget.Home, SettingsNavTarget.Settings)
val TabletTabs = listOf(SettingsNavTarget.Home, SettingsNavTarget.Profiles, SettingsNavTarget.Settings)

/** Android TV: Servers between Home and Profiles while a profile runs; no Profiles before the first one. */
fun tvTabs(clashRunning: Boolean, hasProfiles: Boolean = true) = when {
    clashRunning -> listOf(SettingsNavTarget.Home, SettingsNavTarget.Servers, SettingsNavTarget.Profiles, SettingsNavTarget.Settings)
    hasProfiles -> TabletTabs
    else -> listOf(SettingsNavTarget.Home, SettingsNavTarget.Settings)
}

/**
 * A tablet's bar, as iPad's: the three tabs at the top in the middle, the red
 * Disconnect circle at their right while the VPN runs. The tabs stay centred.
 */
@Composable
fun TabletTopBar(
    selected: SettingsNavTarget,
    onNavigate: (SettingsNavTarget) -> Unit,
    clashRunning: Boolean,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    /** At the right edge of the bar (Android TV's Profiles: Update all and Add); the tabs stay centred. */
    trailing: (@Composable () -> Unit)? = null,
    tabs: List<SettingsNavTarget> = TabletTabs,
    /** Android TV: no Disconnect circle — Home's own power circle and Play/Pause do it. */
    showDisconnect: Boolean = true,
    selectedFocus: FocusRequester? = null,
    focusSelectedOnStart: Boolean = false,
    downFocus: FocusRequester? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A place on each side, so the tabs stay in the middle.
        if (showDisconnect) Box(Modifier.size(BarHeight))
        NavPill(
            selected = selected,
            onNavigate = onNavigate,
            tabs = tabs,
            selectedFocus = selectedFocus,
            focusSelectedOnStart = focusSelectedOnStart,
            downFocus = downFocus,
        )
        if (showDisconnect) Box(Modifier.size(BarHeight)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = clashRunning,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                BarCircle(
                    icon = R.drawable.ic_mdi_power,
                    label = stringResource(R.string.disconnect),
                    tint = Color(0xFFE5393B),
                    onClick = onDisconnect,
                )
            }
        }
    }
    if (trailing != null) {
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 40.dp)) { trailing() }
    }
    }
}

/** Height of [TabletTopBar] below the status bar. */
val TabletTopBarHeight = BarHeight + 12.dp

@Composable
private fun NavPillItem(
    onLens: () -> Float,
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isPressed by interaction.collectIsPressedAsState()
    LaunchedEffect(isPressed) { onPress(isPressed) }
    val fg = lerp(scheme.onSurface, scheme.primary, onLens())
    Column(
        modifier = modifier
            .clip(CircleShape)
            // The remote's focus: a ring of its own, the lens stays on the chosen tab.
            .then(if (focused) Modifier.border(2.dp, scheme.onSurface.copy(alpha = 0.8f), CircleShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 80.dp),
        )
    }
}

/**
 * A round button beside the tabs, as tall as they are: tinted (Disconnect,
 * white icon) or plain (the latency test, icon in the text colour).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BarCircle(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
    busy: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tint ?: scheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.size(BarHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                LoadingIndicator(modifier = Modifier.size(32.dp), color = scheme.onSurface)
            } else {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    tint = if (tint != null) Color.White else scheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
