package com.github.kr328.clash.design.compose.deck

import com.github.kr328.clash.design.compose.dialog.ExpressiveAlert
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.service.model.theme
import com.github.kr328.clash.service.model.ProfileTheme
import com.github.kr328.clash.design.compose.theme.AccentRGB
import com.github.kr328.clash.design.compose.theme.Appearance
import com.github.kr328.clash.design.store.ProfileRecency
import com.github.kr328.clash.design.compose.theme.AccentColours
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.LayoutCoordinates
import com.github.kr328.clash.design.compose.theme.ProfileCardColours
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.background.HomeGlassBackdrop
import com.github.kr328.clash.design.compose.background.homeGlass
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.service.model.Profile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.VisibilityThreshold
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Visible part of a strip; the rest is under the card below it. */
val StripHeight = 42.dp

/** A folded strip's visible part while a profile runs: room for the groups below. */
val CompactStripHeight = 30.dp
val Tuck = 20.dp

/** Lays a strip out [tuck] shorter than it draws: the next card covers its lower part. */
fun Modifier.tucked(tuck: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val cut = tuck.roundToPx()
    layout(placeable.width, (placeable.height - cut).coerceAtLeast(0)) { placeable.place(0, 0) }
}

/** What "Update all" is doing: the strips show which one updates and which one failed. */
data class DeckUpdates(
    val updating: Set<UUID> = emptySet(),
    val failures: Map<UUID, String> = emptyMap(),
)

/**
 * Home's profiles as a stack of cards, as in Wallet and on iOS: the active
 * profile in full at the bottom ([card]), above it the others, the one used
 * last nearest. With more than three others only the two nearest show, the
 * rest folded under "N more profiles"; a tap there unfolds the same stack in
 * place, "Collapse" folds it again.
 *
 * @param stack the other profiles, top to bottom (the one used last at the bottom).
 */
@Composable
fun ProfileDeck(
    stack: List<Profile>,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    updates: DeckUpdates,
    onOpen: (Profile) -> Unit,
    /** A strip dropped onto the active card, and Yes: that profile becomes active. */
    onActivate: (Profile) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Home's scroll: the unfolded deck piles up at its top, as in Wallet, and
     * a strip dragged to its edge scrolls it.
     */
    scroll: ScrollState? = null,
    /** Folding at the card's place: the strips go at once, with the scroll (see MainScreen). */
    instantFold: Boolean = false,
    /** A profile runs: the folded strips and the bar over them are thinner, as on iOS. */
    compact: Boolean = false,
    /** What Home draws its content shifted by this frame (a scroll not laid out yet, see MainScreen). */
    drawShift: () -> Float = { 0f },
    card: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The stack as last arranged by hand; the caller's order again once it changes.
    var order by remember(stack.map { it.uuid }) { mutableStateOf(stack) }
    val foldable = order.size > 3
    val open = expanded && foldable
    val shown = if (open || !foldable) order else order.takeLast(2)

    // A long press drags a strip: to another place (the order stays, folded
    // or not) or onto the active card (asks to make it active).
    var dragging by remember { mutableStateOf<UUID?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The strip let go, sliding onto its place; the others still make way for it.
    var settling by remember { mutableStateOf<UUID?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var overCard by remember { mutableStateOf(false) }
    var pendingActivate by remember { mutableStateOf<Profile?>(null) }
    // The finger, in window pixels: near an edge of the screen Home scrolls.
    var fingerY by remember { mutableFloatStateOf(0f) }
    val slot = with(density) { StripHeight.toPx() }
    val full = with(density) { (StripHeight + Tuck).toPx() }
    // Where the strips pile up: under the status bar.
    val pinTop = WindowInsets.statusBars.getTop(density).toFloat()
    // Each strip's top on screen as last laid out (for the drag) and where it is laid out now (for the
    // pile, read as it is drawn). Not from the scroll: one asked during a layout reaches it a frame
    // later. Nor from what the last layout reported: it came after the frame it was needed in, and
    // a strip with no report yet was taken as deep in the pile — unfolding, every strip was missing
    // for a frame and some for half a second.
    val screenTops = remember { mutableStateMapOf<UUID, Float>() }
    val placed = remember { HashMap<UUID, LayoutCoordinates>() }

    // The gesture keeps the callbacks of the composition it started in: what
    // they read must be state, never a value of that composition (the deck
    // folded then, and no strip of the unfolded one could move).
    val openNow by rememberUpdatedState(open)

    fun dragBy(uuid: UUID, dy: Float) {
        dragOffset += dy
        val visible = (if (openNow || order.size <= 3) order else order.takeLast(2)).map { it.uuid }
        val from = visible.indexOf(uuid)
        if (from < 0) return
        // Past the last strip, over the card: not a place, the card's question.
        overCard = from == visible.lastIndex && dragOffset > slot * 0.75f
        // Half way over the next strip is enough to take its place.
        val steps = (dragOffset / slot).roundToInt().coerceIn(-from, visible.lastIndex - from)
        if (steps != 0 && !overCard) {
            val target = visible[from + steps]
            val list = order.toMutableList()
            val a = list.indexOfFirst { it.uuid == uuid }
            val b = list.indexOfFirst { it.uuid == target }
            val moved = list.removeAt(a)
            list.add(b, moved)
            order = list
            dragOffset -= steps * slot
        }
    }

    fun dragEnd() {
        // Only a drag that was under way: a gesture cancelled because the deck was made again
        // (another profile made active) wrote its stale order over the new one, and the profile
        // just left behind went to the far end instead of nearest the card.
        val uuid = dragging ?: return
        dragging = null
        if (uuid != null && overCard) {
            pendingActivate = order.firstOrNull { it.uuid == uuid }
        } else {
            // The stack as dragged, kept as the deck's order (nearest first).
            ProfileRecency.set(context, order.reversed().map { it.uuid })
        }
        overCard = false
        // The strip slides onto its place instead of jumping there.
        settling = uuid
        settleJob = scope.launch {
            animate(dragOffset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                dragOffset = value
            }
            settling = null
        }
    }

    fun dragStart(uuid: UUID, touchY: Float) {
        settleJob?.cancel()
        settling = null
        dragging = uuid
        dragOffset = 0f
        fingerY = (screenTops[uuid] ?: 0f) + touchY
    }


    // A strip held near the top or the bottom of the screen scrolls Home under it.
    LaunchedEffect(dragging, scroll) {
        val uuid = dragging ?: return@LaunchedEffect
        val home = scroll ?: return@LaunchedEffect
        val edge = with(density) { 96.dp.toPx() }
        val fastest = with(density) { 14.dp.toPx() }
        while (isActive) {
            withFrameNanos { }
            val top = pinTop + edge
            val bottom = view.height - with(density) { 120.dp.toPx() } - edge
            val speed = when {
                fingerY < top -> -fastest * ((top - fingerY) / edge).coerceIn(0f, 1f)
                fingerY > bottom -> fastest * ((fingerY - bottom) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                val moved = home.scrollBy(speed)
                if (moved != 0f) dragBy(uuid, moved)
            }
        }
    }

    // The folded strips' colours (and logos) worked out ahead: unfolding shows every strip at
    // once, and one whose logo was first read then showed the plain colour for a few frames.
    LaunchedEffect(stack.map { it.profileLogo }) {
        for (profile in stack) {
            profile.profileLogo?.takeIf { it.isNotEmpty() }?.let { ProfileCardColours.logoColour(context, it) }
        }
    }

    // Thinner folded strips while a profile runs; the unfolded deck keeps its full ones.
    val stripHeight = if (compact && !open) CompactStripHeight else StripHeight
    Column(modifier) {
        if (foldable) {
            if (open) {
                DeckBar(stringResource(R.string.deck_collapse), R.drawable.ic_mdi_chevron_down, upward = true) { onExpanded(false) }
            } else {
                DeckBar(
                    pluralStringResource(R.plurals.deck_more_profiles, stack.size - 2, stack.size - 2),
                    R.drawable.ic_mdi_chevron_down,
                    edges = true,
                    height = stripHeight,
                ) { onExpanded(true) }
            }
        }
        val reordering = dragging != null || settling != null
        for (profile in order) {
            key(profile.uuid) {
            val lifted = dragging == profile.uuid || settling == profile.uuid
            AnimatedVisibility(
                visible = profile in shown,
                // Unfolding, a strip takes its whole place at once (Home holds the card and glides,
                // see Home): growing from nothing, the strips lay over each other, showed through
                // each other and covered each other's rounded lower edges. Not clipped either: a
                // strip draws its tucked lower part and its shadow outside its place.
                enter = EnterTransition.None,
                exit = if (instantFold) {
                    ExitTransition.None
                } else {
                    shrinkVertically(DeckSpring, shrinkTowards = Alignment.Top, clip = false) + fadeOut(DeckFade)
                },
                modifier = Modifier
                    .onPlaced { placed[profile.uuid] = it }
                    .onGloballyPositioned { coordinates ->
                        val top = coordinates.positionInWindow().y
                        if (screenTops[profile.uuid] != top) screenTops[profile.uuid] = top
                    }
                    // Folded at the card's place: gone in this very frame, as on iOS. AnimatedVisibility
                    // keeps a leaving strip one more frame even with no exit — the whole unfolded deck
                    // showed for a frame over Home already back at its top (cards flashed by the card).
                    .then(if (instantFold && profile !in shown) Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, 0) {}
                    } else Modifier)
                    .zIndex(if (lifted) 1f else 0f)
                    // The others make way for the dragged strip, as on iOS.
                    .animatePlacement(enabled = reordering && !lifted),
            ) {
                ProfileStrip(
                    profile = profile,
                    updating = profile.uuid in updates.updating,
                    failure = updates.failures[profile.uuid],
                    height = stripHeight,
                    modifier = Modifier
                        .tucked(Tuck)
                        .deckHero(profile.uuid)
                        .graphicsLayer {
                            // Scrolled past the top of the unfolded deck: it stays
                            // there, smaller, and the next strip slides over it.
                            val gone = if (open && scroll != null && !lifted) {
                                // Read so the pile follows the scroll and the layout; the place itself
                                // is the one laid out now.
                                scroll.value
                                screenTops[profile.uuid]
                                val at = placed[profile.uuid]?.takeIf { it.isAttached }?.positionInWindow()?.y
                                if (at == null) 0f else (pinTop - (at + drawShift())).coerceAtLeast(0f)
                            } else {
                                0f
                            }
                            val share = (gone / full).coerceAtMost(1f)
                            translationY = (if (lifted) dragOffset else 0f) + gone
                            val scale = (if (dragging == profile.uuid) 1.03f else 1f) * (1f - 0.08f * share)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            // Deep in the pile it is under the others anyway.
                            alpha = if (gone > full * 1.5f) 0f else 1f - 0.4f * share
                        }
                        .pointerInput(profile.uuid) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragStart(profile.uuid, it.y) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    fingerY += amount.y
                                    dragBy(profile.uuid, amount.y)
                                },
                                onDragEnd = { dragEnd() },
                                onDragCancel = {
                                    overCard = false
                                    dragEnd()
                                },
                            )
                        },
                    onClick = { onOpen(profile) },
                )
            }
            }
        }
        // A strip over the card: the card answers with a white rim.
        Box(
            Modifier
                .graphicsLayer {
                    val scale = if (overCard) 0.98f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (overCard) Modifier.border(3.dp, Color.White, CardShape) else Modifier),
        ) { card() }
    }

    pendingActivate?.let { profile ->
        ActivateProfileSheet(
            profile = profile,
            onConfirm = {
                pendingActivate = null
                onActivate(profile)
            },
            onCancel = { pendingActivate = null },
        )
    }
}

/** Unfolding and folding the deck: one soft spring for every strip, no bounce. */
private val DeckSpring = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)
private val DeckFade = spring<Float>(stiffness = Spring.StiffnessMediumLow)

/**
 * Slides to a new place in the column instead of jumping there, while
 * [enabled]; otherwise it simply follows the layout.
 */
private fun Modifier.animatePlacement(enabled: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    // A plain field: written as state while placing, it asked for another placement every frame
    // and every strip was laid out again and again with nothing moving.
    val holder = remember { arrayOfNulls<Animatable<IntOffset, AnimationVector2D>>(1) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val anim = holder[0]
            when {
                !enabled || anim == null -> {
                    if (anim == null || anim.value != target) holder[0] = Animatable(target, IntOffset.VectorConverter)
                    IntOffset.Zero
                }
                else -> {
                    if (anim.targetValue != target) {
                        scope.launch { anim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
                    }
                    anim.value - target
                }
            }
        }
}

/**
 * A folded card: the profile's colour, logo, name and one figure — the
 * traffic left, or the date it runs out when that is near.
 */
@Composable
fun ProfileStrip(
    profile: Profile,
    modifier: Modifier = Modifier,
    updating: Boolean = false,
    failure: String? = null,
    height: Dp = StripHeight,
    tucked: Boolean = true,
    onClick: () -> Unit,
) {
    val colour = rememberCardColour(profile)
    var showFailure by remember { mutableStateOf(false) }
    CardFace(
        colour = colour,
        modifier = modifier
            .fillMaxWidth()
            // Its edge over the card above, even when both share a colour.
            .shadow(5.dp, CardShape, clip = false)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thin = height < StripHeight
            CardLogo(profile, colour, size = if (thin) 22.dp else 30.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                profile.name,
                color = CardInk.primary,
                fontWeight = FontWeight.SemiBold,
                style = if (thin) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            when {
                // In a box as wide as the card's ↻, so the spinner turns right under it.
                updating -> Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                failure != null -> Box(
                    Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { showFailure = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_error),
                        contentDescription = stringResource(R.string.deck_update_failed),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                else -> stripSummary(profile)?.let {
                    Text(it, color = CardInk.primary.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
        if (tucked) Spacer(Modifier.height(height + Tuck))
    }
    if (showFailure && failure != null) {
        ExpressiveAlert(
            onDismissRequest = { showFailure = false },
            title = stringResource(R.string.deck_update_failed),
            text = failure,
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { showFailure = false },
        )
    }
}

/** A strip's figure: "file", "Expired", "until 3 Nov" in its last week, else the traffic left. */
@Composable
private fun stripSummary(profile: Profile): String? {
    if (profile.type == Profile.Type.File) return stringResource(R.string.deck_file)
    val now = System.currentTimeMillis() + profile.clockSkewMillis
    if (profile.expire > 0 && profile.expire <= now) return stringResource(R.string.deck_expired)
    val until = if (profile.expire > 0) {
        stringResource(R.string.deck_until, SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(profile.expire)))
    } else {
        null
    }
    if (profile.expire > 0 && profile.expire - now < TimeUnit.DAYS.toMillis(7)) return until
    if (profile.total > 1) return (profile.total - profile.upload - profile.download).coerceAtLeast(0).toBytesString()
    return until
}

/**
 * The top of the deck: "N more profiles" (with the edges of the folded cards
 * showing above it) or "Collapse".
 */
@Composable
private fun DeckBar(
    text: String,
    icon: Int,
    upward: Boolean = false,
    edges: Boolean = false,
    height: Dp = StripHeight,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = homeGlass()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .tucked(Tuck),
    ) {
        if (edges) {
            // Edges of the folded cards.
            Box(
                Modifier
                    .padding(horizontal = 22.dp)
                    .layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { p.place(0, (-10).dp.roundToPx()) } }
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(CardShape)
                    .background(scheme.outlineVariant),
            )
            Box(
                Modifier
                    .padding(horizontal = 11.dp)
                    .layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { p.place(0, (-5).dp.roundToPx()) } }
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(CardShape)
                    .background(scheme.surfaceVariant),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height + Tuck)
                .shadow(2.dp, CardShape, clip = false)
                .clip(CardShape)
                .background(if (glass) Color.Transparent else scheme.surfaceContainerHigh),
        ) {
            // Over Mushroomia, frosted glass like the groups' cards.
            HomeGlassBackdrop()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height + Tuck)
                .clickable(onClick = onClick)
                .padding(bottom = Tuck),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = if (height < StripHeight) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .then(if (upward) Modifier.layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { p.placeWithLayer(0, 0) { rotationZ = 180f } } } else Modifier),
            )
        }
        }
    }
}

/**
 * "Make the profile active?" after a strip was dropped onto the active card:
 * the strip itself, the question, and the two answers — Yes in that
 * profile's own colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivateProfileSheet(profile: Profile, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colour = rememberCardColour(profile)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val appearance by Appearance.state.collectAsStateWithLifecycle()
    // The accent the profile would bring: its theme's own, else its card colour made an accent.
    val themed = (if (appearance.usesProfileTheme) profile.theme?.accent else null) as? ProfileTheme.Colour.Hex
    val yes = (themed?.let { AccentRGB.parse(it.value) } ?: AccentColours.adapted(colour, dark)).colour
    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The strip on its own: no part to tuck under a next card.
            ProfileStrip(profile = profile, height = 56.dp, tucked = false, onClick = {})
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.deck_make_active, profile.name),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        .clickable(onClick = onCancel)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(yes)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.deck_yes), fontWeight = FontWeight.SemiBold, color = Color.White) }
            }
        }
    }
}
