package com.github.kr328.clash.design.compose.tv

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.SimpleModeProxyList
import com.github.kr328.clash.design.compose.component.ProxyGroupPane
import androidx.compose.foundation.layout.fillMaxHeight
import com.github.kr328.clash.design.compose.deck.CardFace
import com.github.kr328.clash.design.compose.deck.CardFigures
import com.github.kr328.clash.design.compose.deck.CardInk
import com.github.kr328.clash.design.compose.deck.CardLogo
import com.github.kr328.clash.design.compose.deck.rememberCardColour
import com.github.kr328.clash.service.model.Profile
import java.util.UUID
import kotlinx.coroutines.delay

// Android TV's Home and Profiles, as on Apple TV: big round buttons, the
// profile's card in its own colour, rows to move between with the remote.

/**
 * A round button for the remote, as Apple TV's: muted glass, or filled with
 * [fill]; larger and lighter while focused.
 */
@Composable
fun TvRoundButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    fill: Color? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    busy: Boolean = false,
    enabled: Boolean = true,
    onFocused: (Boolean) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(150), label = "scale")
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .onFocusChanged { onFocused(it.isFocused) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 12.dp else 0.dp, CircleShape)
            .size(size)
            .background(fill ?: onSurface.copy(alpha = if (focused) 0.24f else 0.09f), CircleShape)
            .then(if (focused && fill != null) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(size * 0.45f), strokeWidth = 3.dp, color = iconTint)
        } else {
            Icon(painterResource(icon), contentDescription = label, tint = iconTint, modifier = Modifier.size(size * 0.46f))
        }
    }
}

/** A profile's card for the TV: its colour, logo, name and figures; nothing on it is focusable. */
@Composable
fun TvProfileFace(
    profile: Profile,
    modifier: Modifier = Modifier,
    /** Profiles: a dot after the active profile's name. */
    marked: Boolean = false,
    showsAnnouncement: Boolean = true,
    /** Profiles: the cards other than the current one while its actions are focused. */
    dimmed: Boolean = false,
) {
    val colour = rememberCardColour(profile)
    val scrim by animateFloatAsState(if (dimmed) 0.55f else 0f, tween(200), label = "scrim")
    CardFace(colour = colour, modifier = modifier, shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardLogo(profile, colour, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CardInk.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (marked) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(9.dp).background(Color.White, CircleShape))
                }
            }
            Column(modifier = Modifier.padding(top = 12.dp)) {
                CardFigures(profile)
            }
            val announce = profile.announce.replace("\\n", "\n")
            if (showsAnnouncement && announce.isNotEmpty()) {
                Text(
                    announce,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardInk.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                )
            }
        }
        // Darkened, not see-through: the cards under it stay hidden.
        if (scrim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = scrim)))
    }
}

/** A row of round buttons, centred, with the focused one's name under it. */
@Composable
private fun TvButtonRow(
    buttons: List<TvButton>,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    /** Given to the first button: where "down" from above lands. */
    first: FocusRequester? = null,
    /** Right from the last button goes to the first, left from the first to the last: the focus stays in the row. */
    wrap: Boolean = false,
) {
    var focused by remember { mutableStateOf<String?>(null) }
    val firstButton = buttons.firstOrNull { !it.divider }
    val real = buttons.filter { !it.divider }
    val rowFocus = remember(real.size) { List(real.size) { FocusRequester() } }
    Column(modifier = modifier.fillMaxWidth().focusGroup(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            for (button in buttons) {
                if (button.divider) {
                    Box(Modifier.width(2.dp).height(28.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                    continue
                }
                val at = real.indexOf(button)
                TvRoundButton(
                    icon = button.icon,
                    label = button.label,
                    onClick = button.onClick,
                    busy = button.busy,
                    iconTint = button.tint ?: MaterialTheme.colorScheme.onSurface,
                    modifier = buttonModifier
                        .then(if (first != null && button === firstButton) Modifier.focusRequester(first) else Modifier)
                        .then(
                            if (wrap && real.size > 1) {
                                Modifier
                                    .focusRequester(rowFocus[at])
                                    .focusProperties {
                                        left = rowFocus[(at - 1 + real.size) % real.size]
                                        right = rowFocus[(at + 1) % real.size]
                                    }
                            } else {
                                Modifier
                            },
                        ),
                    onFocused = { has ->
                        if (has) focused = button.label else if (focused == button.label) focused = null
                    },
                )
            }
        }
        // Keeps its line when nothing here is focused: the rows below do not jump.
        Text(
            focused ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private data class TvButton(
    val icon: Int = 0,
    val label: String = "",
    val busy: Boolean = false,
    val tint: Color? = null,
    val divider: Boolean = false,
    val onClick: () -> Unit = {},
)

private val Divider = TvButton(divider = true)

/** Play/Pause on the remote toggles the VPN, as on Apple TV: on Home's whole screen, the tabs too. */
fun Modifier.tvPlayPause(onToggle: () -> Unit) = onPreviewKeyEvent { event ->
    val key = event.nativeKeyEvent
    if (key.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
        key.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
    ) {
        if (key.action == KeyEvent.ACTION_UP) onToggle()
        true
    } else {
        false
    }
}

/**
 * Android TV's Home, as Apple TV's: the power circle with the active
 * profile's card beside it, the profile's round buttons under them, then —
 * while connected — the proxy groups as a shelf, or a simple-mode profile's
 * nodes. Opens with the power circle focused; Play/Pause connects.
 */
@Composable
fun TvHome(
    profile: Profile,
    clashRunning: Boolean,
    connecting: Boolean,
    updating: Boolean,
    onPowerToggle: () -> Unit,
    onUpdate: () -> Unit,
    onModeSelector: () -> Unit,
    onConnections: () -> Unit,
    onProviders: () -> Unit,
    onSupport: (String) -> Unit,
    onWebPage: (String) -> Unit,
    onRenew: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val power = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Once the screen is laid out: earlier the request found nothing to focus.
        delay(150)
        runCatching { power.requestFocus() }
    }

    // The notes stay at the bottom of the screen, outside the scroll: under the buttons they went
    // off a TV's screen while the power circle had the focus, seen only once the focus went down.
    // Nothing scrolls: on a TV whose screen is shorter (a larger density, or a card with an
    // announcement) the whole block is made smaller to fit — scrolled to the buttons, the card
    // went under the tabs and was cut off.
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .fitHeight()
            .padding(horizontal = 40.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // The power circle and the card: one block in the middle.
        Row(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TvRoundButton(
                    icon = if (clashRunning) R.drawable.ic_mdi_power else R.drawable.ic_prizrak_ghost,
                    label = stringResource(if (clashRunning) R.string.disconnect else R.string.tap_to_start),
                    onClick = onPowerToggle,
                    size = 116.dp,
                    fill = if (clashRunning) Color(0xFFE5393B) else null,
                    iconTint = if (clashRunning) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    busy = connecting,
                    modifier = Modifier.focusRequester(power),
                )
                Text(
                    stringResource(
                        when {
                            connecting -> R.string.connecting
                            clashRunning -> R.string.vpn_connected
                            else -> R.string.tap_to_start
                        },
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (clashRunning) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            TvProfileFace(profile, modifier = Modifier.width(460.dp).shadow(16.dp, RoundedCornerShape(24.dp)))
        }

        // The profile's buttons: ↻ (and the simple mode's latency test), then the tunnel's, then the provider's links.
        val buttons = buildList {
            if (profile.type != Profile.Type.File) {
                add(TvButton(R.drawable.ic_baseline_sync, stringResource(R.string.update), busy = updating, onClick = onUpdate))
            }
            val tunnel = buildList {
                if (clashRunning && profile.globalModeMp) add(TvButton(R.drawable.ic_arrow_decision_outline, stringResource(R.string.mode), onClick = onModeSelector))
                if (clashRunning && profile.rpMp) add(TvButton(R.drawable.ic_mdi_database, stringResource(R.string.providers), onClick = onProviders))
                if (clashRunning && profile.connsViewMp) add(TvButton(R.drawable.ic_mdi_transit_connection_variant, stringResource(R.string.connections), onClick = onConnections))
            }
            val links = buildList {
                if (profile.renewUrl.isNotEmpty()) add(TvButton(R.drawable.ic_mdi_credit_card_outline, stringResource(R.string.renew_subscription)) { onRenew(profile.renewUrl) })
                if (profile.supportUrl.isNotEmpty()) {
                    val telegram = profile.supportUrl.contains("t.me") || profile.supportUrl.contains("telegram")
                    add(TvButton(if (telegram) R.drawable.ic_outline_send else R.drawable.ic_mdi_help_circle_outline, stringResource(R.string.contact_support)) { onSupport(profile.supportUrl) })
                }
                if (profile.profileWebPageUrl.isNotEmpty()) add(TvButton(R.drawable.ic_mdi_web, stringResource(R.string.provider_website)) { onWebPage(profile.profileWebPageUrl) })
            }
            for (group in listOf(tunnel, links)) {
                if (group.isEmpty()) continue
                if (isNotEmpty()) add(Divider)
                addAll(group)
            }
        }
        if (buttons.isNotEmpty()) TvButtonRow(buttons)
    }

        // The servers have their own tab while the profile runs.
        if (!clashRunning) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 40.dp, end = 40.dp, bottom = 18.dp),
            ) {
                Text(
                    stringResource(R.string.wide_groups_after_connect),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.tv_play_pause_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Android TV's Servers tab, while a profile runs: the proxy groups down the
 * left, the chosen group's nodes on the right with its sort and latency test;
 * a simple-mode profile — its nodes alone, with the latency test above them.
 */
@Composable
fun TvServers(
    simpleMode: Boolean,
    groups: List<Pair<String, ProxyGroup>>,
    groupMap: Map<String, ProxyGroup>,
    useDots: Boolean,
    testingProxies: Set<String>,
    latencyTesting: Boolean,
    onLatencyTest: () -> Unit,
    onSelectProxy: (String, String) -> Unit,
    onUrlTest: (String) -> Unit,
    onTestProxy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (simpleMode) {
        val (name, group) = groups.first()
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvButtonRow(listOf(TvButton(R.drawable.ic_baseline_speed, stringResource(R.string.delay_test), busy = latencyTesting, onClick = onLatencyTest)))
            SimpleModeProxyList(
                group = group,
                groupMap = groupMap,
                useDots = useDots,
                testingProxies = testingProxies,
                onSelect = { onSelectProxy(name, it) },
                onTestProxy = { onTestProxy(name, it) },
                modifier = Modifier.width(720.dp),
            )
        }
        return
    }
    var chosen by remember { mutableStateOf(groups.first().first) }
    val shown = groups.firstOrNull { it.first == chosen } ?: groups.first()
    Row(modifier = modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 12.dp)) {
        // The groups: moving the focus over one shows its nodes.
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for ((name, group) in groups) {
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                LaunchedEffect(focused) { if (focused) chosen = name }
                val surface = MaterialTheme.colorScheme.onSurface
                val current = name == shown.first
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            surface.copy(alpha = if (focused) 0.22f else if (current) 0.12f else 0.06f),
                            RoundedCornerShape(18.dp),
                        )
                        .then(if (focused) Modifier.border(2.dp, surface.copy(alpha = 0.8f), RoundedCornerShape(18.dp)) else Modifier)
                        .clickable(interactionSource = interaction, indication = null) { chosen = name }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        group.now,
                        style = MaterialTheme.typography.bodyMedium,
                        color = surface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        ProxyGroupPane(
            groupName = shown.first,
            group = shown.second,
            groupMap = groupMap,
            useDots = useDots,
            testingProxies = testingProxies,
            onSelect = { onSelectProxy(shown.first, it) },
            onUrlTest = { onUrlTest(shown.first) },
            onTestProxy = { onTestProxy(shown.first, it) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            fill = true,
        )
    }
}

/** Update all and Add, for the right end of the tab bar over [TvProfiles]. */
@Composable
fun TvProfilesActions(updatable: Boolean, allUpdating: Boolean, onUpdateAll: () -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (updatable) {
            TvRoundButton(R.drawable.ic_baseline_sync, stringResource(R.string.deck_update_all), onUpdateAll, size = 48.dp, busy = allUpdating)
            Spacer(Modifier.width(16.dp))
        }
        TvRoundButton(R.drawable.ic_baseline_add, stringResource(R.string.add_profile), onAdd, size = 48.dp)
    }
}

/**
 * Android TV's Profiles, as Apple TV's: the cards lie left to right as a
 * fanned deck, the focused one whole and raised above its neighbours; its
 * actions are round buttons under it. Left/right — another profile, Select —
 * make it active, down — its actions.
 */
@Composable
fun TvProfiles(
    profiles: List<Profile>,
    updatingUuids: Set<UUID>,
    onActivate: (Profile) -> Unit,
    onUpdate: (Profile) -> Unit,
    onAnnounce: (Profile) -> Unit,
    onSupport: (Profile) -> Unit,
    onWebPage: (Profile) -> Unit,
    onRenew: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    modifier: Modifier = Modifier,
    /** On the current card: where Down from the tabs lands. */
    entry: FocusRequester = remember { FocusRequester() },
    /** Up from the cards: the tab bar's Profiles. */
    up: FocusRequester? = null,
) {
    var currentUuid by remember { mutableStateOf(profiles.firstOrNull { it.active }?.uuid) }
    val current = profiles.firstOrNull { it.uuid == currentUuid } ?: profiles.firstOrNull { it.active } ?: profiles.firstOrNull()
    val currentIndex = profiles.indexOfFirst { it.uuid == current?.uuid }.coerceAtLeast(0)
    val requesters = remember { mutableStateMapOf<UUID, FocusRequester>() }
    val listState = rememberLazyListState()
    var inActions by remember { mutableStateOf(false) }
    var deckFocused by remember { mutableStateOf(false) }
    val actionsFirst = remember { FocusRequester() }

    // The current card comes to the middle of the screen. The focus opens on the Profiles tab
    // (see ProfilesScreen): the cards are one press down, starting at the active one.
    var placedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex, profiles.size) {
        if (profiles.isEmpty()) return@LaunchedEffect
        if (placedOnce) listState.animateScrollToItem(currentIndex) else listState.scrollToItem(currentIndex)
        placedOnce = true
    }

    Column(modifier = modifier.fillMaxSize().padding(top = 4.dp, bottom = 16.dp)) {
        if (profiles.isEmpty()) return@Column

        // The deck. Margins of half the screen less half a card: scrolled to
        // the current card, it stands in the middle.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val margin = ((maxWidth - CardWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { deckFocused = it.hasFocus }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(CardStep - CardWidth),
            contentPadding = PaddingValues(start = margin, end = margin, top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            itemsIndexed(profiles, key = { _, p -> p.uuid }) { index, profile ->
                val requester = requesters.getOrPut(profile.uuid) { FocusRequester() }
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val lift by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "lift")
                LaunchedEffect(focused) { if (focused) currentUuid = profile.uuid }
                TvProfileFace(
                    profile = profile,
                    marked = profile.active,
                    showsAnnouncement = false,
                    dimmed = inActions && index != currentIndex,
                    modifier = Modifier
                        .padding(start = if (index == currentIndex + 1) CardWidth - CardStep + 24.dp else 0.dp)
                        .zIndex(if (index == currentIndex) 1000f else index.toFloat())
                        .graphicsLayer {
                            val s = 1f + 0.06f * lift
                            scaleX = s
                            scaleY = s
                            translationY = -14.dp.toPx() * lift
                        }
                        .shadow((8 + 16 * lift).dp, RoundedCornerShape(24.dp))
                        .width(CardWidth)
                        .height(CardHeight)
                        .focusRequester(requester)
                        .then(if (index == currentIndex) Modifier.focusRequester(entry) else Modifier)
                        // Down: the actions of this card (the search did not find the row under the deck);
                        // up: the Profiles tab (not Update all or Add beside the tabs).
                        .focusProperties {
                            down = actionsFirst
                            if (up != null) this.up = up
                        }
                        .clickable(interactionSource = interaction, indication = null) { onActivate(profile) },
                )
                if (profile.uuid in updatingUuids) {
                    Box(Modifier.width(CardWidth).height(CardHeight).padding(18.dp), contentAlignment = Alignment.TopEnd) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                }
            }
        }
        }

        // Where the current card is among them.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            if (profiles.size > 9) {
                Text("${currentIndex + 1} / ${profiles.size}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else {
                profiles.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 5.dp)
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == currentIndex) 0.9f else 0.3f),
                                CircleShape,
                            ),
                    )
                }
            }
        }

        // The current profile's actions, as round buttons.
        current?.let { profile ->
            val buttons = buildList {
                if (!profile.active) add(TvButton(R.drawable.ic_outline_check_circle, stringResource(R.string.opened_make_active)) { onActivate(profile) })
                if (profile.type != Profile.Type.File) add(TvButton(R.drawable.ic_baseline_sync, stringResource(R.string.update), busy = profile.uuid in updatingUuids) { onUpdate(profile) })
                if (profile.renewUrl.isNotEmpty()) add(TvButton(R.drawable.ic_mdi_credit_card_outline, stringResource(R.string.renew_subscription)) { onRenew(profile) })
                if (profile.supportUrl.isNotEmpty()) add(TvButton(R.drawable.ic_mdi_help_circle_outline, stringResource(R.string.contact_support)) { onSupport(profile) })
                if (profile.profileWebPageUrl.isNotEmpty()) add(TvButton(R.drawable.ic_mdi_web, stringResource(R.string.provider_website)) { onWebPage(profile) })
                if (profile.announce.isNotEmpty()) add(TvButton(R.drawable.ic_mdi_bullhorn_variant_outline, stringResource(R.string.opened_announcement)) { onAnnounce(profile) })
                add(TvButton(R.drawable.ic_baseline_edit, stringResource(R.string.edit)) { onEdit(profile) })
                add(TvButton(R.drawable.ic_baseline_delete, stringResource(R.string.delete), tint = Color(0xFFE5393B)) { onDelete(profile) })
            }
            TvButtonRow(
                buttons,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .onFocusChanged { inActions = it.hasFocus },
                // Up goes back to the card these actions belong to.
                buttonModifier = Modifier.focusProperties { this.up = requesters[profile.uuid] ?: FocusRequester.Default },
                first = actionsFirst,
                wrap = true,
            )
        }

        Spacer(Modifier.weight(1f))
        // What the remote does from where its focus is: the tabs, the cards or a card's actions.
        Text(
            stringResource(
                when {
                    inActions -> R.string.tv_profiles_hint_actions
                    deckFocused && current?.active == true -> R.string.tv_profiles_hint_deck_active
                    deckFocused -> R.string.tv_profiles_hint_deck
                    else -> R.string.tv_profiles_hint_tabs
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val CardWidth = 340.dp
private val CardHeight = 200.dp
/** What shows of a card under its neighbour. */
private val CardStep = 150.dp

/**
 * Lays its content out at its own height and, when that is more than there is, draws it smaller
 * to fit, centred; when it fits, centres it in the space.
 */
private fun Modifier.fitHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val height = constraints.maxHeight
    val scale = if (placeable.height > height) height.toFloat() / placeable.height else 1f
    layout(constraints.maxWidth, height) {
        val y = ((height - placeable.height) / 2)
        placeable.placeWithLayer((constraints.maxWidth - placeable.width) / 2, y) {
            scaleX = scale
            scaleY = scale
        }
    }
}

/** Android TV with no profile yet: the app's ghost, big, in the middle; ＋ under it adds the first one. */
@Composable
fun TvWelcome(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val add = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            delay(100)
            if (runCatching { add.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    Column(
        modifier = modifier.fillMaxSize().fitHeight().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_prizrak_ghost),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.size(150.dp),
        )
        Text(
            stringResource(R.string.welcome),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            stringResource(R.string.add_first_profile),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp),
        )
        TvRoundButton(
            icon = R.drawable.ic_baseline_add,
            label = stringResource(R.string.add_profile),
            onClick = onAdd,
            size = 96.dp,
            modifier = Modifier.padding(top = 32.dp).focusRequester(add),
        )
        Text(
            stringResource(R.string.add_profile),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
