package com.github.kr328.clash.design.compose.screen

import com.github.kr328.clash.design.compose.dialog.ExpressiveAlert
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.ConnectedChoice
import com.github.kr328.clash.design.compose.component.EmptyState
import com.github.kr328.clash.design.compose.component.ExpressiveScaffold
import com.github.kr328.clash.design.compose.component.PreferenceList
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.SearchField
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.design.util.toBytesString

/**
 * Live connections grouped by app (1:1 with ConnectionsDesign, restyled to
 * MD3E). Active/Closed tabs, a filter field, a total-speed readout and toolbar
 * actions for pause/resume, closed-connection caching and kill-all. Tapping a
 * group opens its per-connection list. Snapshot polling, grouping, filtering
 * and caching live in ConnectionsActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    activeGroups: List<ConnectionGroup>,
    closedGroups: List<ConnectionGroup>,
    speed: String,
    paused: Boolean,
    caching: Boolean,
    onBack: (() -> Unit)?,
    onTogglePause: () -> Unit,
    onToggleCache: () -> Unit,
    onKillAll: () -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenGroup: (ConnectionGroup, Boolean) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showKillConfirm by remember { mutableStateOf(false) }

    ExpressiveScaffold(
        title = stringResource(R.string.connections),
        subtitle = speed,
        onBack = onBack,
        actions = {
            IconButton(onClick = onTogglePause) {
                Icon(
                    painter = painterResource(
                        if (paused) R.drawable.ic_baseline_play_arrow
                        else R.drawable.ic_baseline_pause
                    ),
                    contentDescription = stringResource(
                        if (paused) R.string.connections_resume else R.string.connections_pause
                    ),
                )
            }
            // Caching closed connections: on as a filled button.
            FilledIconToggleButton(checked = caching, onCheckedChange = { onToggleCache() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_restore),
                    contentDescription = null,
                )
            }
            IconButton(onClick = { showKillConfirm = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_clear_all),
                    contentDescription = null,
                )
            }
        },
    ) { inner ->
        val direction = LocalLayoutDirection.current
        Column(modifier = Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchField(query = query, onQueryChange = { query = it; onFilterChange(it) })
                ConnectedChoice(
                    options = listOf(
                        0 to stringResource(R.string.connections_active),
                        1 to stringResource(R.string.connections_closed),
                    ),
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }

            val groups = if (selectedTab == 1) closedGroups else activeGroups
            if (groups.isEmpty()) {
                EmptyState(stringResource(R.string.empty), PaddingValues(bottom = inner.calculateBottomPadding()))
            } else {
                PreferenceList(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = inner.calculateStartPadding(direction) + 16.dp,
                        end = inner.calculateEndPadding(direction) + 16.dp,
                        bottom = inner.calculateBottomPadding(),
                    ),
                ) {
                    items(groups, key = { it.packageName }) { group ->
                        ConnectionGroupRow(
                            group = group,
                            onClick = { onOpenGroup(group, selectedTab == 1) },
                        )
                    }
                }
            }
        }
    }

    if (showKillConfirm) {
        ExpressiveAlert(
            onDismissRequest = { showKillConfirm = false },
            title = stringResource(R.string.connections_kill_all_confirm_title),
            text = stringResource(R.string.connections_kill_all_confirm_msg),
            confirmLabel = stringResource(R.string.yes),
            onConfirm = { showKillConfirm = false; onKillAll() },
            dismissLabel = stringResource(R.string.no),
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionGroupRow(group: ConnectionGroup, onClick: () -> Unit) {
    val painter = remember(group.icon) {
        group.icon?.let { BitmapPainter(it.toBitmap(96, 96).asImageBitmap()) }
    }
    PreferenceRow(
        title = group.appName,
        summary = "↑ ${group.uploadSpeed.toBytesString()}/s  ↓ ${group.downloadSpeed.toBytesString()}/s",
        onClick = onClick,
        leading = {
            if (painter != null) {
                Image(painter = painter, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.Unspecified,
                )
            }
        },
        trailing = { Text(group.connections.size.toString(), style = MaterialTheme.typography.labelLarge) },
    )
}
