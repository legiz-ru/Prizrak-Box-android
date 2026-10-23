package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.EmptyState
import com.github.kr328.clash.design.compose.deck.CardShape
import com.github.kr328.clash.design.compose.deck.DeckUpdates
import com.github.kr328.clash.design.compose.deck.OpenedProfileActions
import com.github.kr328.clash.design.compose.deck.OpenedProfilePane
import com.github.kr328.clash.design.compose.deck.ProfileStrip
import com.github.kr328.clash.design.store.ProfileRecency
import com.github.kr328.clash.service.model.Profile
import java.util.UUID

/**
 * A tablet's Profiles tab, as iPad's: every profile as a strip on the left —
 * the active one first, then the one used last, as Home's deck — with ↻ and
 * ＋ above; the profile tapped, the active one until then, on the right.
 */
@Composable
fun ProfilesTabPane(
    profiles: List<Profile>,
    activeProfile: Profile?,
    selected: UUID?,
    onSelect: (UUID?) -> Unit,
    deckUpdates: DeckUpdates,
    onUpdateAll: () -> Unit,
    onAdd: () -> Unit,
    actions: OpenedProfileActions,
    leftWidth: Dp,
    gap: Dp,
    top: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imported = profiles.filter { it.imported }
    val ordered = listOfNotNull(activeProfile?.takeIf { it.imported }) +
        ProfileRecency.ordered(context, imported.filter { it.uuid != activeProfile?.uuid })
    val shown = imported.firstOrNull { it.uuid == selected } ?: activeProfile

    Row(modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(leftWidth)
                .fillMaxHeight()
                .padding(top = top),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.wide_profiles_count, ordered.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ProfilesHeaderButtons(
                    updatingAll = deckUpdates.updating.isNotEmpty(),
                    onUpdateAll = onUpdateAll,
                    onAdd = onAdd,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ordered, key = { it.uuid }) { profile ->
                    val open = profile.uuid == shown?.uuid
                    Box(
                        // The open one outlined, as iPad marks it.
                        Modifier.then(
                            if (open) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardShape) else Modifier,
                        ),
                    ) {
                        ProfileStrip(
                            profile = profile,
                            updating = profile.uuid in deckUpdates.updating,
                            failure = deckUpdates.failures[profile.uuid],
                            tucked = false,
                            onClick = { onSelect(profile.uuid) },
                        )
                        // The active one: a dot after the strip's name, as on iPad.
                        if (profile.uuid == activeProfile?.uuid) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color.White),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(gap))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = top, end = 12.dp, bottom = bottom + 12.dp),
        ) {
            if (shown != null) {
                OpenedProfilePane(
                    profile = shown,
                    actions = actions,
                    onClose = { onSelect(null) },
                    contentPadding = PaddingValues(top = 8.dp),
                )
            } else {
                EmptyState(stringResource(R.string.wide_select_profile), PaddingValues())
            }
        }
    }
}
