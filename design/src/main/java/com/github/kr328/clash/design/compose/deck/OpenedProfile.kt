@file:OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.kr328.clash.design.compose.deck

import com.github.kr328.clash.design.compose.dialog.ExpressiveAlert
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.Segment
import com.github.kr328.clash.design.compose.component.LocalSegment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.theme.AccentColours
import com.github.kr328.clash.design.compose.theme.AccentRGB
import com.github.kr328.clash.design.compose.theme.Appearance
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.ProfileTheme
import com.github.kr328.clash.service.model.theme
import java.util.UUID

/** The flight of a card between Home's deck and the opened profile. */
class DeckHero(val scope: SharedTransitionScope, val opened: UUID?)

val LocalDeckHero = compositionLocalOf<DeckHero?> { null }

/** On Home: the card that flies to the opened profile, hidden while it is up there. */
@Composable
fun Modifier.deckHero(uuid: UUID): Modifier {
    val hero = LocalDeckHero.current ?: return this
    return with(hero.scope) {
        this@deckHero.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState("card-$uuid"),
            visible = hero.opened != uuid,
        )
    }
}

/** What an opened profile can do; each closes the screen when it has to. */
class OpenedProfileActions(
    val onActivate: (Profile) -> Unit,
    val onUpdate: (Profile) -> Unit,
    val onEdit: (Profile) -> Unit,
    val onDelete: (Profile) -> Unit,
    val onLink: (String) -> Unit,
)

/**
 * A profile opened from Home's deck, as on iOS: its card on top (flown up
 * from the deck), "Make Active", the profile's links as rows — with the
 * Android editor among them — and "Delete Profile". The other profiles wait
 * at the bottom, their edges showing. ✕, a tap on the card, a pull down or
 * Back close it: the card flies back onto its place.
 */
@Composable
fun AnimatedVisibilityScope.OpenedProfile(
    scope: SharedTransitionScope,
    profile: Profile,
    others: List<Profile>,
    actions: OpenedProfileActions,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var pulled by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    // A pull down past the top closes, as ✕ does.
    val pullToClose = remember {
        object : NestedScrollConnection {
            var dragged = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    dragged += available.y
                    if (!pulled && dragged > with(density) { 64.dp.toPx() }) {
                        pulled = true
                        onClose()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                dragged = 0f
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The background fades; the card flies (no fade); the rows come in after it.
        Box(
            Modifier
                .fillMaxSize()
                .animateEnterExit(enter = fadeIn(), exit = fadeOut())
                .background(scheme.background)
                .clickable(enabled = false) {},
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .nestedScroll(pullToClose)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 96.dp),
        ) {
            OpenedTopRow(
                onClose = onClose,
                onEdit = { actions.onEdit(profile) },
                modifier = Modifier.animateEnterExit(enter = fadeIn(), exit = fadeOut()),
            )

            OpenedCard(scope, profile, onClose)

            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .animateEnterExit(
                        enter = fadeIn() + slideInVertically { it / 8 },
                        exit = fadeOut() + slideOutVertically { it / 8 },
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OpenedActions(profile, actions)
            }
        }

        // The other profiles wait at the bottom, the one used last in front.
        WaitingCards(
            others = others.take(3),
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .animateEnterExit(
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ),
        )
    }
}

/** The card as on Home, without its row of icons: the links are below. A tap closes it. */
@Composable
private fun AnimatedVisibilityScope.OpenedCard(scope: SharedTransitionScope, profile: Profile, onClose: () -> Unit) {
    val hero = with(scope) {
        Modifier.sharedElement(rememberSharedContentState("card-${profile.uuid}"), animatedVisibilityScope = this@OpenedCard)
    }
    CardContent(profile, Modifier.then(hero).clickable(onClick = onClose))
}

/** The opened card's face: logo, name — large for the active profile, as on Home — and the figures. */
@Composable
private fun CardContent(profile: Profile, modifier: Modifier = Modifier) {
    val colour = rememberCardColour(profile)
    CardFace(colour = colour, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardLogo(profile, colour)
                Spacer(Modifier.width(10.dp))
                if (profile.active) {
                    CardTitle(profile.name, Modifier.weight(1f))
                } else {
                    Text(
                        profile.name,
                        color = CardInk.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Column(Modifier.padding(top = 10.dp)) {
                CardFigures(profile)
            }
        }
    }
}

/** ✕ at the left, the Android profile editor at the right. */
@Composable
private fun OpenedTopRow(onClose: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilledTonalIconButton(
            onClick = onClose,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(44.dp),
        ) {
            Icon(painterResource(R.drawable.ic_baseline_close), stringResource(R.string.close))
        }
        FilledTonalIconButton(
            onClick = onEdit,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(44.dp),
        ) {
            Icon(painterResource(R.drawable.ic_baseline_edit), stringResource(R.string.opened_edit))
        }
    }
}

/** Make Active, the links and Delete, with Delete's confirmation and the announcement. */
@Composable
private fun OpenedActions(profile: Profile, actions: OpenedProfileActions) {
    val scheme = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    var showAnnouncement by remember { mutableStateOf(false) }

    ActivateButton(profile, actions)
    Links(profile, actions, onAnnouncement = { showAnnouncement = true })
    CompositionLocalProvider(LocalSegment provides Segment(0, 1)) {
        PreferenceRow(
            title = stringResource(R.string.opened_delete),
            icon = R.drawable.ic_outline_delete,
            onClick = { confirmDelete = true },
            destructive = true,
            container = scheme.surfaceContainerHigh,
        )
    }

    if (confirmDelete) {
        ExpressiveAlert(
            onDismissRequest = { confirmDelete = false },
            title = stringResource(R.string.opened_delete_title),
            text = stringResource(R.string.opened_delete_message, profile.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                actions.onDelete(profile)
            },
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    if (showAnnouncement) {
        ExpressiveAlert(
            onDismissRequest = { showAnnouncement = false },
            title = profile.name,
            text = profile.announce.replace("\\n", "\n"),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { showAnnouncement = false },
        )
    }
}

/**
 * A profile opened in a column of its own (a tablet, an unfolded foldable):
 * what the opened profile shows on a phone, without the card's flight and
 * the cards waiting below — the deck stays in sight beside it.
 */
@Composable
fun OpenedProfilePane(
    profile: Profile,
    actions: OpenedProfileActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        OpenedTopRow(onClose = onClose, onEdit = { actions.onEdit(profile) })
        CardContent(profile)
        Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OpenedActions(profile, actions)
        }
    }
}

/** "Make Active" in that profile's own accent; "Active" in grey when it already is. */
@Composable
private fun ActivateButton(profile: Profile, actions: OpenedProfileActions) {
    val scheme = MaterialTheme.colorScheme
    val height = ButtonDefaults.MediumContainerHeight
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = height)
    if (profile.active) {
        // Nothing to do: a tonal button that does not press.
        FilledTonalButton(
            onClick = {},
            enabled = false,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            colors = ButtonDefaults.filledTonalButtonColors(
                disabledContainerColor = scheme.surfaceContainerHigh,
                disabledContentColor = scheme.onSurfaceVariant,
            ),
            modifier = modifier,
        ) {
            Icon(painterResource(R.drawable.ic_baseline_check), null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.opened_active), style = ButtonDefaults.textStyleFor(height))
        }
    } else {
        val colour = rememberCardColour(profile)
        val dark = scheme.background.luminance() < 0.5f
        val appearance by Appearance.state.collectAsStateWithLifecycle()
        val themed = (if (appearance.usesProfileTheme) profile.theme?.accent else null) as? ProfileTheme.Colour.Hex
        val accent = (themed?.let { AccentRGB.parse(it.value) } ?: AccentColours.adapted(colour, dark)).colour
        Button(
            onClick = { actions.onActivate(profile) },
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            modifier = modifier,
        ) {
            Text(stringResource(R.string.opened_make_active), style = ButtonDefaults.textStyleFor(height))
        }
    }
}

/** Only the links the profile has: a segmented list, as in Settings. The editor is the round button at the top right. */
@Composable
private fun Links(profile: Profile, actions: OpenedProfileActions, onAnnouncement: () -> Unit) {
    data class Link(@DrawableRes val icon: Int, val title: String, val action: () -> Unit)

    val rows = buildList {
        if (profile.type != Profile.Type.File) {
            add(Link(R.drawable.ic_baseline_sync, stringResource(R.string.opened_update)) { actions.onUpdate(profile) })
        }
        if (profile.renewUrl.isNotEmpty()) {
            add(Link(R.drawable.ic_mdi_credit_card_outline, stringResource(R.string.opened_renew)) { actions.onLink(profile.renewUrl) })
        }
        if (profile.supportUrl.isNotEmpty()) {
            val telegram = profile.supportUrl.contains("t.me") || profile.supportUrl.contains("telegram")
            add(Link(if (telegram) R.drawable.ic_outline_send else R.drawable.ic_mdi_help_circle_outline, stringResource(R.string.opened_support)) {
                actions.onLink(profile.supportUrl)
            })
        }
        if (profile.profileWebPageUrl.isNotEmpty()) {
            add(Link(R.drawable.ic_mdi_web, stringResource(R.string.opened_website)) { actions.onLink(profile.profileWebPageUrl) })
        }
        if (profile.announce.isNotEmpty()) {
            add(Link(R.drawable.ic_mdi_bullhorn_variant_outline, stringResource(R.string.opened_announcement), onAnnouncement))
        }
    }
    if (rows.isEmpty()) return
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row ->
            CompositionLocalProvider(LocalSegment provides Segment(index, rows.size)) {
                PreferenceRow(title = row.title, icon = row.icon, onClick = row.action, container = container)
            }
        }
    }
}

/** Up to three other profiles at the bottom, only their top edges showing. A tap closes. */
@Composable
private fun WaitingCards(others: List<Profile>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .padding(horizontal = 16.dp)
            .height(StripHeight + 14.dp * (others.size - 1).coerceAtLeast(0) + 8.dp),
    ) {
        // The one used last in front, lowest.
        others.reversed().forEachIndexed { index, profile ->
            ProfileStrip(
                profile = profile,
                tucked = true,
                modifier = Modifier
                    .offset(y = 14.dp * index)
                    .zIndex(index.toFloat()),
                onClick = onClose,
            )
        }
    }
}
