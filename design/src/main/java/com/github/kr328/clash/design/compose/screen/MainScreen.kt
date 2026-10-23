package com.github.kr328.clash.design.compose.screen

import com.github.kr328.clash.design.compose.tv.TvHome
import com.github.kr328.clash.design.compose.tv.TvServers
import com.github.kr328.clash.design.compose.tv.tvPlayPause
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withTimeoutOrNull
import com.github.kr328.clash.design.compose.rememberWideLayout
import com.github.kr328.clash.design.compose.WideLayout
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import com.github.kr328.clash.design.compose.deck.deckHero
import com.github.kr328.clash.design.compose.deck.OpenedProfileActions
import com.github.kr328.clash.design.compose.deck.OpenedProfile
import com.github.kr328.clash.design.compose.deck.LocalDeckHero
import com.github.kr328.clash.design.compose.deck.DeckHero
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.github.kr328.clash.design.compose.deck.StripHeight
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import com.github.kr328.clash.design.compose.deck.CardFigures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.github.kr328.clash.design.store.ProfileRecency
import com.github.kr328.clash.design.compose.deck.ProfileDeck
import com.github.kr328.clash.design.compose.tv.TvWelcome
import com.github.kr328.clash.design.compose.deck.DeckUpdates
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import com.github.kr328.clash.design.compose.deck.rememberCardColour
import com.github.kr328.clash.design.compose.deck.CardTitle
import com.github.kr328.clash.design.compose.deck.CardLogo
import com.github.kr328.clash.design.compose.deck.CardInk
import com.github.kr328.clash.design.compose.deck.CardFace
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.compose.theme.Appearance
import com.github.kr328.clash.design.compose.background.HomeBackgroundStyle
import com.github.kr328.clash.design.compose.background.HomeBackground
import androidx.compose.foundation.BorderStroke
import com.github.kr328.clash.design.compose.background.homeGlass
import com.github.kr328.clash.design.compose.background.HomeGlassBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.AddProfileAction
import com.github.kr328.clash.design.compose.component.AddProfileSheet
import com.github.kr328.clash.design.compose.component.AppNavigationRail
import com.github.kr328.clash.design.compose.component.GhostPowerButton
import com.github.kr328.clash.design.compose.component.ProxyGroupCard
import com.github.kr328.clash.design.compose.component.ProxySelectionSheet
import com.github.kr328.clash.design.compose.component.RemoteIcon
import com.github.kr328.clash.design.compose.component.SimpleModeProxyList
import com.github.kr328.clash.design.compose.component.verticalScrollbar
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.subscription.SubscriptionAlerts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Home screen (stage 5a). Mirrors the static `design_main.xml`: header, loading,
 * onboarding, the active-profile card, the ghost power button (disconnected) and,
 * while running, a floating toolbar (disconnect + latency test) plus an entry to
 * the proxy selector. The inline proxy-group accordion is handled in a follow-up.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    expanded: Boolean,
    clashRunning: Boolean,
    /** Between the tap on the ghost and the tunnel running (or failing). */
    connecting: Boolean,
    /** The active profile's ↻ is turning: its update is under way. */
    updatingProfile: Boolean,
    isLoading: Boolean,
    hasProfiles: Boolean,
    activeProfile: Profile?,
    /** Every profile: the deck shows the others above the active card. */
    profiles: List<Profile>,
    /** "Update all": which profile updates now, which ones failed. */
    deckUpdates: DeckUpdates,
    onUpdateAll: () -> Unit,
    /** The opened profile's "Edit Profile": the Android profile editor. */
    onEditProfile: (Profile) -> Unit,
    /** The opened profile's "Update Subscription". */
    onUpdateProfileOf: (Profile) -> Unit,
    /** The opened profile's "Delete Profile", confirmed. */
    onDeleteProfile: (Profile) -> Unit,
    /** Another profile made active from the deck. */
    onActivateProfile: (Profile) -> Unit,
    latencyTesting: Boolean,
    proxyGroups: List<Pair<String, ProxyGroup>>,
    useDots: Boolean,
    testingProxies: Set<String>,
    onPowerToggle: () -> Unit,
    onUpdateProfile: () -> Unit,
    onManageProfiles: () -> Unit,
    onModeSelector: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSupport: (String) -> Unit,
    onOpenWebPage: (String) -> Unit,
    onOpenRenew: (String) -> Unit,
    onAdd: (AddProfileAction) -> Unit,
    onNavigate: (SettingsNavTarget) -> Unit,
    onLatencyTest: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectProxy: (String, String) -> Unit,
    onUrlTest: (String) -> Unit,
    onTestProxy: (String, String) -> Unit,
    isTv: Boolean = false,
    /** A tab asked for from outside (Settings' Profiles tab on a tablet); [onTabShown] once taken. */
    requestedTab: SettingsNavTarget? = null,
    onTabShown: () -> Unit = {},
) {
    var showAddSheet by remember { mutableStateOf(false) }
    // Kept across a change of width (folding, unfolding, resizing): the same
    // group is a sheet on a phone and the right column on a tablet.
    var openedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val simpleMode = activeProfile?.simpleMode == true
    val groupMap = remember(proxyGroups) { proxyGroups.toMap() }
    val visibleGroups = remember(proxyGroups) { proxyGroups.filter { !it.second.hidden } }
    val homeScroll = rememberScrollState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current

    // The deck: the others top to bottom, the one used last nearest the active card.
    var deckExpanded by rememberSaveable { mutableStateOf(false) }
    // The profile opened from the deck: its card flies up over Home.
    var openedUuid by rememberSaveable { mutableStateOf<java.util.UUID?>(null) }
    var lastOpened by remember { mutableStateOf<Profile?>(null) }
    profiles.firstOrNull { it.uuid == openedUuid }?.let { lastOpened = it }
    // Deleted while open: nothing left to show.
    if (openedUuid != null && profiles.none { it.uuid == openedUuid }) openedUuid = null
    val stack = remember(profiles, activeProfile?.uuid) {
        // The profile left behind goes nearest the card first: noted here, before the order is
        // read. Noted later (by the accent's refresh), it was read before and lay at the end.
        ProfileRecency.noteActive(context, activeProfile?.uuid)
        ProfileRecency.ordered(context, profiles.filter { it.uuid != activeProfile?.uuid && it.imported }).reversed()
    }
    val foldable = stack.size > 3
    val unfolded = deckExpanded && foldable
    // A new active profile, however chosen: the deck folds, the connect button is back in reach.
    LaunchedEffect(activeProfile?.uuid) { deckExpanded = false }
    // As in Wallet: unfolding slides the active card down to the bottom of
    // the screen, the strips nearest it above and the far ones piled up at
    // the top; scrolling the card back up to its folded place (the connect
    // button under it, as on the folded Home) folds the deck right there —
    // the strips gather back into the stack while the card stays.
    val deckFold = remember { DeckFold() }
    val foldDensity = androidx.compose.ui.platform.LocalDensity.current
    var deckFoldRoom by remember { mutableFloatStateOf(0f) }
    // Folding where the card reached its place: the strips go at once (see the card's fold below).
    var deckFoldInstant by remember { mutableStateOf(false) }
    val foldScope = rememberCoroutineScope()
    // A scroll asked from a layout reaches Home's layout only after that frame is drawn: what it
    // still has to move is drawn as this shift meanwhile (and dropped on the next layout).
    var deckDrawShift by remember { mutableFloatStateOf(0f) }

    // Unfolding, as on iOS: the strips take their whole places at once — none squeezed over
    // another (growing, they covered each other's rounded lower edges and showed through each
    // other) — and Home scrolls by what they add above the card before that frame is drawn, the
    // card staying where it is; then one glide takes it down to the bottom of the screen, the
    // strips coming in from the top at their spacing.
    fun unfoldDeck() {
        if (deckExpanded) return
        if (foldable) {
            deckFold.holdAt = deckFold.cardBottom
            deckFold.holdPending = true
            // Room under the deck for that scroll; trimmed once the card is down.
            val added = with(foldDensity) { (StripHeight * stack.size).toPx() }
            deckFoldRoom = maxOf(deckFoldRoom, added + deckFold.viewportBottom)
        }
        deckExpanded = true
    }

    // Folded where the card is at its folded place: the strips go and Home goes back to its top
    // in one frame (see the card's layout below), the card staying where it is.
    fun foldHere(byDrag: Boolean) {
        deckFold.foldingByCard = true
        deckFold.swallowDrag = byDrag
        deckFold.scrollToTopPending = true
        deckFoldInstant = true
        deckExpanded = false
        // Asked from a layout: the fold is composed in the very next frame, not one later.
        Snapshot.sendApplyNotifications()
    }

    // "Collapse": the unfolded deck moves up whole, at its spacing, until the card is at its
    // folded place, and folds there as when the card is scrolled there by hand.
    fun foldDeck() {
        if (!deckExpanded) return
        val folded = deckFold.foldedCardBottom
        if (!unfolded || folded == null) {
            deckExpanded = false
            return
        }
        foldScope.launch {
            deckFold.gliding = true
            try {
                val target = (homeScroll.value + deckFold.cardBottom - folded).roundToInt().coerceIn(0, homeScroll.maxValue)
                homeScroll.glideTo(target)
                foldHere(byDrag = false)
            } catch (_: CancellationException) {
                // A touch took the scroll over: the deck stays unfolded.
            } finally {
                deckFold.gliding = false
            }
        }
    }

    var deckWasUnfolded by remember { mutableStateOf(unfolded) }
    LaunchedEffect(unfolded) {
        if (unfolded == deckWasUnfolded) return@LaunchedEffect
        deckWasUnfolded = unfolded
        if (unfolded) {
            // A pull that unfolded the deck: once the finger is off.
            snapshotFlow { homeScroll.isScrollInProgress }.first { !it }
            deckFold.unfoldedAt = System.currentTimeMillis()
            deckFold.armed = false
            // The card held at its place first, then down to the bottom of the screen.
            withFrameNanos { }
            val margin = with(foldDensity) { 12.dp.toPx() }
            // Never up: a card longer than the screen stays where it is.
            val target = (homeScroll.value + deckFold.cardBottom - (deckFold.viewportBottom - margin))
                .roundToInt().coerceIn(0, homeScroll.value)
            try {
                homeScroll.glideTo(target)
            } catch (_: CancellationException) {
                // A touch took the scroll over.
            }
            // Room under the deck for the card to reach its folded place, and a little more.
            // Measured again once laid out: with few profiles Home was shorter than the screen
            // and the room first filled that gap — the card could not reach its place.
            deckFold.foldedCardBottom?.let { folded ->
                val target = folded - with(foldDensity) { 40.dp.toPx() }
                repeat(4) {
                    val lowestReach = deckFold.cardBottom - (homeScroll.maxValue - homeScroll.value)
                    val room = (deckFoldRoom + lowestReach - target).coerceAtLeast(0f)
                    if (abs(room - deckFoldRoom) < 1f) return@let
                    deckFoldRoom = room
                    withFrameNanos { }
                    withFrameNanos { }
                }
            }
        } else if (deckFold.foldingByCard) {
            deckFold.foldingByCard = false
            withFrameNanos { }
            deckFoldInstant = false
        } else {
            try {
                snapshotFlow { homeScroll.isScrollInProgress }.first { !it }
                foldToTop(homeScroll)
            } catch (_: CancellationException) {
                // A new touch took the scroll over.
            }
        }
    }

    // Two columns by the window's width, as iOS's regular width; Android TV keeps its rail.
    val wide = if (isTv || expanded) WideLayout.Phone else rememberWideLayout()
    val twoPane = wide.twoPane && hasProfiles && !isLoading
    // Half-open with the fold across the screen ("laptop"): the columns above and below the fold.
    val tabletop = twoPane && wide.foldTop != null
    val tablet = wide.tablet && !isLoading
    // A tablet's Profiles tab; anywhere narrower it is Home with the deck unfolded, as iOS opens its profiles sheet.
    var tab by rememberSaveable { mutableStateOf(SettingsNavTarget.Home) }
    // Android TV: Home or Servers.
    var tvTab by rememberSaveable { mutableStateOf(SettingsNavTarget.Home) }
    LaunchedEffect(clashRunning) { if (!clashRunning) tvTab = SettingsNavTarget.Home }
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            if (isTv) {
                if (requestedTab == SettingsNavTarget.Home || requestedTab == SettingsNavTarget.Servers) tvTab = requestedTab
            } else if (requestedTab == SettingsNavTarget.Profiles && !wide.tablet) unfoldDeck() else tab = requestedTab
            onTabShown()
        }
    }
    LaunchedEffect(wide.tablet) {
        if (!wide.tablet && tab == SettingsNavTarget.Profiles) {
            tab = SettingsNavTarget.Home
            unfoldDeck()
        }
    }
    // The profile open beside the Profiles tab's strips (null: the active one).
    var tabProfile by rememberSaveable { mutableStateOf<java.util.UUID?>(null) }
    val profileActions = OpenedProfileActions(
        onActivate = { openedUuid = null; onActivateProfile(it) },
        onUpdate = onUpdateProfileOf,
        onEdit = onEditProfile,
        onDelete = { openedUuid = null; onDeleteProfile(it) },
        onLink = onOpenWebPage,
    )
    // Home tapped while on Home folds the deck.
    val navigate: (SettingsNavTarget) -> Unit = { target ->
        when {
            target == SettingsNavTarget.Home && tab != SettingsNavTarget.Home -> tab = SettingsNavTarget.Home
            target == SettingsNavTarget.Home && deckExpanded -> foldDeck()
            target == SettingsNavTarget.Profiles && wide.tablet -> tab = SettingsNavTarget.Profiles
            else -> onNavigate(target)
        }
    }
    // A pull down past the top unfolds the deck, as pulling a stack of cards
    // apart (Wallet); it folds where the active card is scrolled back up to
    // its place (`deckFold`).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pull = remember(foldable) {
        object : NestedScrollConnection {
            // How far this touch has pulled past the top (+) or pushed past the end (−).
            var dragged = 0f
            // This pull unfolded the deck.
            var unfoldedNow = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || unfoldedNow) return Offset.Zero
                dragged += available.y
                if (!deckExpanded && foldable && dragged > with(density) { 44.dp.toPx() }) {
                    unfoldedNow = true
                    unfoldDeck()
                }
                return Offset.Zero
            }

            // Once the pull has unfolded the deck, the rest of it moves
            // nothing: the deck settles, then goes to its end.
            // The same once the card folded the deck at its place — the drag
            // or the fling that brought it there stops: a fling went on over
            // the folded Home and the cards flew past.
            // And a scroll that would carry the card past its folded place
            // goes only as far as that place, where the deck folds: a quick
            // swipe moved it past in one frame, and it jumped back.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (unfoldedNow && source == NestedScrollSource.UserInput) return available
                if (deckFold.swallowDrag) return available
                val folded = deckFold.foldedCardBottom
                if (deckExpanded && deckFold.armed && folded != null && available.y < 0f) {
                    val room = folded - deckFold.cardBottom
                    if (available.y < room) return Offset(0f, available.y - room)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val handled = unfoldedNow || deckFold.swallowDrag
                dragged = 0f
                unfoldedNow = false
                return if (handled) available else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                deckFold.swallowDrag = false
                return Velocity.Zero
            }
        }
    }

    // The right column's group: the one picked, else the first; simple mode: always the first.
    val shownGroup = if (simpleMode) visibleGroups.firstOrNull()?.first
    else openedGroup?.takeIf { groupMap.containsKey(it) } ?: visibleGroups.firstOrNull()?.first
    // Below a tablet's tabs at the top.
    val bodyTop = if (tablet) TabletTopBarHeight else 0.dp
    val body: @Composable (Modifier) -> Unit = { contentModifier ->
        Scaffold(
            modifier = contentModifier,
            // The background shows through (Settings → Visual → Home Background).
            containerColor = Color.Transparent,
            bottomBar = {
                if (!expanded && !tablet && !tabletop) {
                    AppBottomBar(
                        selected = SettingsNavTarget.Home,
                        onNavigate = navigate,
                        clashRunning = clashRunning,
                        onDisconnect = onDisconnect,
                        showLatencyTest = clashRunning && simpleMode && !unfolded,
                        latencyTesting = latencyTesting,
                        onLatencyTest = onLatencyTest,
                    )
                }
            },
        ) { inner ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            deckFold.viewportBottom = it.boundsInWindow().bottom -
                                with(density) { inner.calculateBottomPadding().toPx() }
                        }
                        .nestedScroll(pull)
                        // The unfolded deck scrolls as the cards themselves, without a bar at the side.
                        .then(if (unfolded) Modifier else Modifier.verticalScrollbar(homeScroll, scrollbarColor))
                        .verticalScroll(homeScroll)
                        .drawWithContent {
                            val shift = deckDrawShift
                            if (shift == 0f) drawContent() else translate(top = shift) { this@drawWithContent.drawContent() }
                        }
                        .padding(
                            top = inner.calculateTopPadding() + bodyTop,
                            bottom = inner.calculateBottomPadding(),
                        )
                        .padding(horizontal = 16.dp),
                ) {
                    Header(
                        // Welcome has its own big ＋: none in the corner then.
                        showAdd = hasProfiles && !isLoading,
                        unfolded = unfolded,
                        updatingAll = deckUpdates.updating.isNotEmpty(),
                        onUpdateAll = onUpdateAll,
                        onAdd = { showAddSheet = true },
                    )

                    AnimatedVisibility(
                        visible = isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(vertical = 64.dp)
                                .size(48.dp),
                        )
                    }

                    if (!hasProfiles && !clashRunning && !isLoading) {
                        Onboarding(onAdd = { showAddSheet = true })
                    }

                    if (hasProfiles && !isLoading) {
                        ProfileDeck(
                            stack = stack,
                            expanded = unfolded,
                            onExpanded = { if (it) unfoldDeck() else foldDeck() },
                            updates = deckUpdates,
                            onOpen = { openedUuid = it.uuid },
                            onActivate = onActivateProfile,
                            scroll = homeScroll,
                            instantFold = deckFoldInstant,
                            compact = clashRunning,
                            drawShift = { deckDrawShift },
                            // The deck's bottom is the active card's.
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                // Not the visible bounds: a card longer than the screen was cut at its edge.
                                val bottom = coordinates.positionInWindow().y + coordinates.size.height
                                deckFold.cardBottom = bottom
                                // Laid out with the scroll asked last frame: no shift to draw any more.
                                if (deckDrawShift != 0f) {
                                    deckDrawShift = 0f
                                    Snapshot.sendApplyNotifications()
                                }
                                if (unfolded && deckFold.holdPending) {
                                    // The unfolded deck's first layout: Home scrolls by what the strips added
                                    // above the card, and this frame draws it shifted by as much — the card
                                    // stays where it was (it was drawn a frame where the strips pushed it).
                                    deckFold.holdPending = false
                                    val pushed = bottom - deckFold.holdAt
                                    homeScroll.dispatchRawDelta(pushed)
                                    deckDrawShift = -pushed
                                    deckFold.cardBottom = deckFold.holdAt
                                    Snapshot.sendApplyNotifications()
                                } else if (!unfolded && deckFold.scrollToTopPending) {
                                    // The folded deck's first layout: Home back at its top, drawn so already
                                    // in this frame — the card stays at its place.
                                    deckFold.scrollToTopPending = false
                                    val scrolled = homeScroll.value.toFloat()
                                    homeScroll.dispatchRawDelta(-scrolled)
                                    deckDrawShift = scrolled
                                    deckFold.cardBottom = bottom + scrolled
                                    Snapshot.sendApplyNotifications()
                                } else if (!unfolded) {
                                    if (homeScroll.value == 0) deckFold.foldedCardBottom = bottom
                                    deckFold.armed = false
                                } else if (!deckFold.gliding && deckFold.cardReachedFold(bottom, with(density) { 4.dp.toPx() })) {
                                    // The card is where the folded Home has it, Home scrolled by just the
                                    // height of the strips above it. They go and the scroll goes back to the
                                    // top in the same frame: the card does not move. Animated, the strips
                                    // shrank over several frames while the scroll caught up a frame late —
                                    // the cards jumped down and back up. Not scrolled here: a scroll takes
                                    // effect before this frame is drawn, the fold only in the next one —
                                    // the whole unfolded deck showed for a frame from Home's top.
                                    foldHere(byDrag = true)
                                }
                            },
                        ) {
                            if (activeProfile != null) MainProfileCard(
                                profile = activeProfile,
                                clashRunning = clashRunning,
                                updating = updatingProfile || activeProfile.uuid in deckUpdates.updating,
                                onOpen = { openedUuid = activeProfile.uuid },
                                onUpdate = onUpdateProfile,
                                onModeSelector = onModeSelector,
                                onConnections = onOpenConnections,
                                onProviders = onOpenProviders,
                                onSupport = onOpenSupport,
                                onWebPage = onOpenWebPage,
                                onRenew = onOpenRenew,
                            )
                        }
                    }

                    // The unfolded deck has the screen to itself.
                    // In two columns the button is the right column's (HomeDetailPane): the cards have this one
                    // to themselves — unless a profile opened from the deck takes the right column.
                    // Under the active card, folded or not: scrolling the unfolded deck up to
                    // the folded look folds it there.
                    AnimatedVisibility(visible = hasProfiles && !clashRunning && !isLoading && (!twoPane || openedUuid != null)) {
                        PowerSection(connecting = connecting, onToggle = onPowerToggle)
                    }

                    AnimatedVisibility(
                        visible = clashRunning && !unfolded,
                        // Drop in from the top (like the power button), instead of
                        // the default bottom-anchored expand that read diagonally. Folded at the
                        // card's place they are simply there, in the same frame as the strips
                        // went, as on iOS: fading in under the card they flickered.
                        enter = if (deckFoldInstant) EnterTransition.None else fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        // Unfolding they go at once, as on iOS: the card glides down over where they were.
                        exit = if (unfolded) ExitTransition.None else fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        Column {
                            if (simpleMode && twoPane) {
                                // Its nodes are the right column.
                            } else if (simpleMode && visibleGroups.isNotEmpty()) {
                                val (firstName, firstGroup) = visibleGroups.first()
                                SimpleModeProxyList(
                                    group = firstGroup,
                                    groupMap = groupMap,
                                    useDots = useDots,
                                    testingProxies = testingProxies,
                                    onSelect = { proxy -> onSelectProxy(firstName, proxy) },
                                    onTestProxy = { proxy -> onTestProxy(firstName, proxy) },
                                )
                            } else {
                                for ((name, group) in visibleGroups) {
                                    ProxyGroupCard(
                                        name = name,
                                        group = group,
                                        groupMap = groupMap,
                                        onClick = { openedGroup = name },
                                        selected = twoPane && name == shownGroup,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (unfolded && deckFoldRoom > 0f) {
                        Spacer(Modifier.height(with(density) { deckFoldRoom.toDp() }))
                    }
                }
            }
        }
    }

    // Home's background: the profile theme's or the user's; Android TV has none, as Apple TV.
    val appearance by Appearance.state.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
      HomeBackground(
        style = if (isTv) HomeBackgroundStyle.Clean else appearance.background,
        connected = clashRunning,
        modifier = Modifier.fillMaxSize(),
      ) {
       SharedTransitionLayout {
        val heroScope = this
        // In two columns the opened profile is the right column: no card flies.
        CompositionLocalProvider(LocalDeckHero provides DeckHero(heroScope, if (twoPane) null else openedUuid)) {
        if (twoPane || tablet) {
            WideHome(
                wide = wide,
                tablet = tablet,
                tab = tab,
                twoPane = twoPane,
                body = body,
                detail = { bottomInset ->
                    HomeDetailPane(
                        opened = profiles.firstOrNull { it.uuid == openedUuid },
                        actions = profileActions,
                        onCloseProfile = { openedUuid = null },
                        clashRunning = clashRunning,
                        activeProfile = activeProfile,
                        groupName = shownGroup,
                        groupMap = groupMap,
                        hasGroups = proxyGroups.isNotEmpty(),
                        useDots = useDots,
                        testingProxies = testingProxies,
                        onSelectProxy = onSelectProxy,
                        onUrlTest = onUrlTest,
                        onTestProxy = onTestProxy,
                        bottomInset = bottomInset,
                        power = { PowerSection(connecting = connecting, onToggle = onPowerToggle) },
                    )
                },
                profilesTab = { leftWidth, gap, top, bottom ->
                    ProfilesTabPane(
                        profiles = profiles,
                        activeProfile = activeProfile,
                        selected = tabProfile,
                        onSelect = { tabProfile = it },
                        deckUpdates = deckUpdates,
                        onUpdateAll = onUpdateAll,
                        onAdd = { showAddSheet = true },
                        actions = profileActions,
                        leftWidth = leftWidth,
                        gap = gap,
                        top = top,
                        bottom = bottom,
                    )
                },
                topBar = {
                    TabletTopBar(
                        selected = tab,
                        onNavigate = navigate,
                        clashRunning = clashRunning,
                        onDisconnect = onDisconnect,
                    )
                },
                tabletop = tabletop,
                bottomBar = {
                    AppBottomBar(
                        selected = SettingsNavTarget.Home,
                        onNavigate = navigate,
                        clashRunning = clashRunning,
                        onDisconnect = onDisconnect,
                        showLatencyTest = clashRunning && simpleMode && !unfolded,
                        latencyTesting = latencyTesting,
                        onLatencyTest = onLatencyTest,
                    )
                },
            )
        } else if (isTv) {
            // Android TV: the tabs at the top, as on Apple TV and iPad — Servers between Home and
            // Profiles while a profile runs; Home as Apple TV's, the power circle and the card.
            val servers = tvTab == SettingsNavTarget.Servers && clashRunning
            Column(modifier = Modifier.fillMaxSize().tvPlayPause(onPowerToggle)) {
                TabletTopBar(
                    selected = if (servers) SettingsNavTarget.Servers else SettingsNavTarget.Home,
                    onNavigate = { target ->
                        when (target) {
                            SettingsNavTarget.Home, SettingsNavTarget.Servers -> tvTab = target
                            else -> onNavigate(target)
                        }
                    },
                    clashRunning = clashRunning,
                    onDisconnect = onDisconnect,
                    tabs = tvTabs(clashRunning, hasProfiles || isLoading),
                    showDisconnect = false,
                )
                if (servers) {
                    TvServers(
                        simpleMode = simpleMode,
                        groups = visibleGroups,
                        groupMap = groupMap,
                        useDots = useDots,
                        testingProxies = testingProxies,
                        latencyTesting = latencyTesting,
                        onLatencyTest = onLatencyTest,
                        onSelectProxy = onSelectProxy,
                        onUrlTest = onUrlTest,
                        onTestProxy = onTestProxy,
                        modifier = Modifier.weight(1f),
                    )
                } else if (hasProfiles && !isLoading && activeProfile != null) {
                    TvHome(
                        profile = activeProfile,
                        clashRunning = clashRunning,
                        connecting = connecting,
                        updating = updatingProfile || activeProfile.uuid in deckUpdates.updating,
                        onPowerToggle = onPowerToggle,
                        onUpdate = onUpdateProfile,
                        onModeSelector = onModeSelector,
                        onConnections = onOpenConnections,
                        onProviders = onOpenProviders,
                        onSupport = onOpenSupport,
                        onWebPage = onOpenWebPage,
                        onRenew = onOpenRenew,
                        modifier = Modifier.weight(1f),
                    )
                } else if (!hasProfiles && !isLoading && !clashRunning) {
                    TvWelcome(onAdd = { showAddSheet = true }, modifier = Modifier.weight(1f))
                } else {
                    Box(modifier = Modifier.weight(1f)) { body(Modifier.fillMaxSize()) }
                }
            }
        } else if (expanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(
                    selected = SettingsNavTarget.Home,
                    clashRunning = clashRunning,
                    onNavigate = onNavigate,
                    onToggleStatus = onPowerToggle,
                    hasProfiles = hasProfiles,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    body(Modifier.fillMaxSize())
                }
            }
        } else {
            body(Modifier.fillMaxSize())
        }
        }

        // The opened profile, over Home and its bar.
        AnimatedVisibility(
            visible = openedUuid != null && !twoPane,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            val shown = lastOpened
            if (shown != null) {
                val close = { openedUuid = null }
                OpenedProfile(
                    scope = heroScope,
                    profile = shown,
                    others = ProfileRecency.ordered(context, profiles.filter { it.uuid != shown.uuid && it.imported }),
                    actions = profileActions,
                    onClose = close,
                )
            }
        }
       }
      }
    }

    if (showAddSheet) {
        AddProfileSheet(
            isTv = isTv,
            onAction = {
                showAddSheet = false
                onAdd(it)
            },
            onDismiss = { showAddSheet = false },
        )
    }

    val opened = openedGroup
    val openedData = opened?.let { groupMap[it] }
    if (!twoPane && opened != null && openedData != null) {
        ProxySelectionSheet(
            groupName = opened,
            group = openedData,
            groupMap = groupMap,
            useDots = useDots,
            isTv = isTv,
            testingProxies = testingProxies,
            onSelect = { proxy -> onSelectProxy(opened, proxy) },
            onUrlTest = { onUrlTest(opened) },
            onTestProxy = { proxy -> onTestProxy(opened, proxy) },
            onDismiss = { openedGroup = null },
        )
    }
}

/**
 * The app's ghost and name at the left, as on iOS — the profile's own logo
 * and title are on its card — on the line of ＋ (add a profile) at the right.
 */
@Composable
private fun Header(showAdd: Boolean, unfolded: Boolean, updatingAll: Boolean, onUpdateAll: () -> Unit, onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // As tall with ＋ as without it: the title does not move.
            .heightIn(min = 40.dp)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_clash),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Prizrak-Box",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            // ↻ updates every profile while the deck is unfolded.
            AnimatedVisibility(visible = unfolded, enter = fadeIn(), exit = fadeOut()) {
                Row {
                    HeaderButton(
                        icon = R.drawable.ic_baseline_sync,
                        label = stringResource(R.string.deck_update_all),
                        busy = updatingAll,
                        onClick = onUpdateAll,
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (showAdd) {
                HeaderButton(R.drawable.ic_baseline_add, stringResource(R.string.add_profile), onClick = onAdd)
            }
        }
    }
}

/** ↻ and ＋, as in Home's header: for the tablet's Profiles tab. */
@Composable
internal fun ProfilesHeaderButtons(updatingAll: Boolean, onUpdateAll: () -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeaderButton(
            icon = R.drawable.ic_baseline_sync,
            label = stringResource(R.string.deck_update_all),
            busy = updatingAll,
            onClick = onUpdateAll,
        )
        Spacer(Modifier.width(8.dp))
        HeaderButton(R.drawable.ic_baseline_add, stringResource(R.string.add_profile), onClick = onAdd)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderButton(icon: Int, label: String, busy: Boolean = false, onClick: () -> Unit) {
    // Material 3 Expressive: a tonal icon button whose shape gives under the finger.
    FilledTonalIconButton(
        onClick = onClick,
        enabled = !busy,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(40.dp),
    ) {
        if (busy) {
            LoadingIndicator(Modifier.size(28.dp))
        } else {
            Icon(painter = painterResource(icon), contentDescription = label)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Onboarding(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.welcome),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.add_first_profile),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 12.dp),
        )
        // Same muted circular style as the (stopped) ghost power button; its shape gives under the finger.
        FilledTonalIconButton(
            onClick = onAdd,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .padding(top = 48.dp)
                .size(120.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_add),
                contentDescription = stringResource(R.string.add_profile),
                modifier = Modifier.size(64.dp),
            )
        }
        Text(
            text = stringResource(R.string.add_profile),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
internal fun PowerSection(connecting: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GhostPowerButton(busy = connecting, onClick = onToggle)
        Text(
            text = stringResource(if (connecting) R.string.connecting else R.string.tap_to_start),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}


/**
 * The active profile's card, as on iOS: its colour (from its logo or its
 * `pxa-theme`) and white text; the logo, the name and ↻ in its header; its
 * figures; the row of icons; the announcement; Renew in white when the
 * subscription is about to run out. A tap outside its buttons opens it.
 */
@Composable
private fun MainProfileCard(
    profile: Profile,
    clashRunning: Boolean,
    updating: Boolean,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onModeSelector: () -> Unit,
    onConnections: () -> Unit,
    onProviders: () -> Unit,
    onSupport: (String) -> Unit,
    onWebPage: (String) -> Unit,
    onRenew: (String) -> Unit,
) {
    val colour = rememberCardColour(profile)
    CardFace(
        colour = colour,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .deckHero(profile.uuid)
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header: logo, name, and ↻ at the right edge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardLogo(profile, colour)
                Spacer(Modifier.width(10.dp))
                CardTitle(profile.name, modifier = Modifier.weight(1f))
                if (profile.type != Profile.Type.File) {
                    val spin = rememberInfiniteTransition(label = "update")
                    val angle by spin.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
                        label = "angle",
                    )
                    MainCardIcon(
                        R.drawable.ic_baseline_sync,
                        stringResource(R.string.update),
                        onClick = { if (!updating) onUpdate() },
                        modifier = Modifier.graphicsLayer { rotationZ = if (updating) angle else 0f },
                    )
                }
            }

            // Figures: only the rows that have a value.
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                CardFigures(profile)
            }

            // Icons: the tunnel's (while it runs) at the left, the provider's at the right.
            val supportUrl = profile.supportUrl
            val webPageUrl = profile.profileWebPageUrl
            val renewUrl = profile.renewUrl
            val showShortcuts = clashRunning && (profile.globalModeMp || profile.connsViewMp || profile.rpMp)
            if (supportUrl.isNotEmpty() || webPageUrl.isNotEmpty() || renewUrl.isNotEmpty() || showShortcuts) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (clashRunning && profile.globalModeMp) {
                        MainCardIcon(R.drawable.ic_arrow_decision_outline, stringResource(R.string.mode), onModeSelector)
                    }
                    if (clashRunning && profile.rpMp) {
                        MainCardIcon(R.drawable.ic_mdi_database, stringResource(R.string.providers), onProviders)
                    }
                    if (clashRunning && profile.connsViewMp) {
                        MainCardIcon(R.drawable.ic_mdi_transit_connection_variant, stringResource(R.string.connections), onConnections)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (renewUrl.isNotEmpty()) {
                        MainCardIcon(R.drawable.ic_mdi_credit_card_outline, stringResource(R.string.renew_subscription), { onRenew(renewUrl) })
                    }
                    if (supportUrl.isNotEmpty()) {
                        val telegram = supportUrl.contains("t.me") || supportUrl.contains("telegram")
                        MainCardIcon(
                            if (telegram) R.drawable.ic_outline_send else R.drawable.ic_mdi_help_circle_outline,
                            stringResource(R.string.contact_support),
                            { onSupport(supportUrl) },
                        )
                    }
                    if (webPageUrl.isNotEmpty()) {
                        MainCardIcon(R.drawable.ic_mdi_web, stringResource(R.string.provider_website), { onWebPage(webPageUrl) })
                    }
                }
            }

            val announce = profile.announce.replace("\\n", "\n")
            if (announce.isNotEmpty()) {
                Text(
                    text = announce,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardInk.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(8.dp),
                )
            }

            // Renew nudge — independent of the "Subscription alerts" notification
            // switch (this is a UI element, not a push notification): it reflects
            // whether a threshold is crossed right now. Expiry uses the panel's own
            // notify-expire-days when present, else the built-in defaults; traffic
            // only evaluates when the panel sent notify-traffic-percent.
            if (renewUrl.isNotEmpty() && shouldShowRenew(profile)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .clickable { onRenew(renewUrl) }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // White on the card, in the card's own colour.
                    Icon(
                        painter = painterResource(R.drawable.ic_mdi_credit_card_outline),
                        contentDescription = null,
                        tint = colour.colour,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.renew_subscription), color = colour.colour, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Whether a renewal threshold is crossed right now: expiry soon, or the traffic used past a panel's percent. */
private fun shouldShowRenew(profile: Profile): Boolean {
    val nowMillis = System.currentTimeMillis() + profile.clockSkewMillis
    val expireDays = profile.notifyExpireDays ?: SubscriptionAlerts.DEFAULT_EXPIRE_DAYS
    val expireSoon = profile.expire != 0L && expireDays.isNotEmpty() &&
        (profile.expire - nowMillis).let { remaining ->
            remaining <= 0L || expireDays.any { remaining <= it * DAY_MILLIS }
        }
    val trafficThresholds = profile.notifyTrafficPercent
    val trafficSoon = SubscriptionAlerts.hasQuota(profile.total) && !trafficThresholds.isNullOrEmpty() &&
        (profile.upload + profile.download).coerceAtLeast(0).let { used ->
            trafficThresholds.any { threshold ->
                val whole = used / profile.total * 100
                val rest = used % profile.total * 100 / profile.total
                whole + rest >= threshold
            }
        }
    return expireSoon || trafficSoon
}


private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)


@Composable
private fun MainCardIcon(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = CardInk.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Where the active card is on screen, frame by frame: plain fields, no recomposition. */
private class DeckFold {
    var cardBottom = 0f
    /** The card's bottom on the folded Home at its top. */
    var foldedCardBottom: Float? = null
    var viewportBottom = 0f
    var unfoldedAt = 0L
    /** The card has been below its folded place since the deck unfolded. */
    var armed = false
    var foldingByCard = false
    /** Folded at the card's place: Home goes back to its top once the folded deck is laid out. */
    var scrollToTopPending = false
    /** Just unfolded: Home keeps the card at [holdAt] once the unfolded deck is laid out. */
    var holdPending = false
    var holdAt = 0f
    /** "Collapse" moving the deck up to the card's folded place: it folds when there. */
    var gliding = false
    /** The rest of the drag that folded the deck moves nothing. */
    var swallowDrag = false

    /** The unfolded deck scrolled so the card is back at its folded place. */
    fun cardReachedFold(bottom: Float, margin: Float): Boolean {
        val folded = foldedCardBottom ?: return false
        if (System.currentTimeMillis() - unfoldedAt < 500) return false
        if (bottom > folded + margin) armed = true
        if (!armed || bottom > folded + 0.5f) return false
        armed = false
        return true
    }
}

/**
 * The deck's one glide, unfolding or folding, as iOS's: critically damped, no bounce, on frames —
 * at most a 60th of a second a step, so a late first frame (the unfolded deck being made) does
 * not leap — and done within a pixel and a half (a spring's tail crept on unseen for a third
 * of a second before the deck could fold).
 */
private suspend fun ScrollState.glideTo(target: Int) {
    val w = 14f
    var x = value.toFloat()
    var v = 0f
    var last = -1L
    scroll {
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last < 0) 1f / 60f else ((now - last) / 1e9f).coerceAtMost(1f / 60f)
            last = now
            val d = x - target
            val e = kotlin.math.exp(-w * dt)
            val next = target + (d + (v + w * d) * dt) * e
            v = (v - (v + w * d) * w * dt) * e
            val done = abs(next - target) < 1.5f
            val to = if (done) target.toFloat() else next
            scrollBy(to - x)
            x = to
            if (done) break
        }
    }
}

/** The deck folding: Home back at its top with the cards. */
private suspend fun foldToTop(scroll: ScrollState) {
    scroll.animateScrollTo(0, spring(stiffness = Spring.StiffnessMediumLow))
}
