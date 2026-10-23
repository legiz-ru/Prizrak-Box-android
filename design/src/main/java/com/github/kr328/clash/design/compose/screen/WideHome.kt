package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.WideLayout
import com.github.kr328.clash.design.compose.component.EmptyState
import com.github.kr328.clash.design.compose.component.ProxyGroupPane
import com.github.kr328.clash.design.compose.deck.OpenedProfileActions
import com.github.kr328.clash.design.compose.deck.OpenedProfilePane
import com.github.kr328.clash.service.model.Profile

/**
 * Home in a wide window, as on iPad and the inner display of iPhone Duo:
 * Home's own column on the left ([body]) and [detail] on the right — on a
 * foldable split at the fold, so nothing lies on it; on a tablet the tabs
 * at the top ([topBar]) and the Profiles tab ([profilesTab]) in place of
 * Home when chosen. Without profiles a tablet keeps one column under its tabs.
 */
@Composable
internal fun WideHome(
    wide: WideLayout,
    tablet: Boolean,
    tab: SettingsNavTarget,
    twoPane: Boolean,
    body: @Composable (Modifier) -> Unit,
    detail: @Composable (bottomInset: Dp) -> Unit,
    profilesTab: @Composable (leftWidth: Dp, gap: Dp, top: Dp, bottom: Dp) -> Unit,
    topBar: @Composable () -> Unit,
    /** Half-open with the fold across the screen: the columns one above the other, split at the fold. */
    tabletop: Boolean = false,
    /** The bar at the bottom edge in [tabletop]: the half lying on the table. */
    bottomBar: @Composable () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val direction = LocalLayoutDirection.current
        // The columns split at a fold across the width; otherwise Home takes
        // a little under half, within what its cards need.
        val fold = wide.foldStart?.takeIf { it > 0.dp && it < maxWidth }
        val leftWidth = fold ?: (maxWidth * 0.42f).coerceIn(360.dp, 520.dp)
        val gap = if (fold != null) wide.foldThickness else 0.dp
        val top = insets.calculateTopPadding() + if (tablet) TabletTopBarHeight else 0.dp
        val bottom = insets.calculateBottomPadding()

        val foldTop = wide.foldTop
        when {
            tabletop && foldTop != null -> Column(Modifier.fillMaxSize()) {
                // Above the fold: Home's own column, as wide as on a phone.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(foldTop),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    body(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxHeight(),
                    )
                }
                Spacer(Modifier.height(wide.foldThickness))
                // Below it, on the table: the right column, then the bar.
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(
                                start = insets.calculateStartPadding(direction) + 12.dp,
                                end = insets.calculateEndPadding(direction) + 12.dp,
                                top = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            detail(0.dp)
                        }
                    }
                    bottomBar()
                }
            }
            tablet && tab == SettingsNavTarget.Profiles -> profilesTab(leftWidth, gap, top, bottom)
            twoPane -> Row(Modifier.fillMaxSize()) {
                body(
                    Modifier
                        .width(leftWidth)
                        .fillMaxHeight(),
                )
                Spacer(Modifier.width(gap))
                // The right column: a pane of its own over Home's background.
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            top = top + if (tablet) 0.dp else 8.dp,
                            end = insets.calculateEndPadding(direction) + 12.dp,
                            bottom = bottom + 12.dp,
                            start = if (fold != null) 12.dp else 0.dp,
                        ),
                ) {
                    detail(0.dp)
                }
            }
            else -> body(Modifier.fillMaxSize())
        }
        if (tablet) {
            Box(Modifier.align(Alignment.TopCenter)) { topBar() }
        }
    }
}

/**
 * Home's right column: the profile opened from the deck; while disconnected,
 * what will be here and the profile's announcement in full; connected, the
 * chosen group's nodes (simple mode: the first group's).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeDetailPane(
    opened: Profile?,
    actions: OpenedProfileActions,
    onCloseProfile: () -> Unit,
    clashRunning: Boolean,
    activeProfile: Profile?,
    groupName: String?,
    groupMap: Map<String, ProxyGroup>,
    hasGroups: Boolean,
    useDots: Boolean,
    testingProxies: Set<String>,
    onSelectProxy: (String, String) -> Unit,
    onUrlTest: (String) -> Unit,
    onTestProxy: (String, String) -> Unit,
    bottomInset: Dp,
    /** The connect button, at the top of the column while disconnected. */
    power: @Composable () -> Unit,
) {
    val kind = when {
        opened != null -> "profile:${opened.uuid}"
        !clashRunning -> "disconnected"
        groupName != null && groupMap[groupName] != null -> "group:$groupName"
        !hasGroups -> "loading"
        else -> "empty"
    }
    Crossfade(targetState = kind, label = "homeDetail") { shown ->
        when {
            shown.startsWith("profile:") && opened != null -> OpenedProfilePane(
                profile = opened,
                actions = actions,
                onClose = onCloseProfile,
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomInset),
            )
            shown == "disconnected" -> DisconnectedDetail(activeProfile, power)
            shown.startsWith("group:") && groupName != null -> {
                val group = groupMap[groupName]
                if (group != null) {
                    ProxyGroupPane(
                        groupName = groupName,
                        group = group,
                        groupMap = groupMap,
                        useDots = useDots,
                        testingProxies = testingProxies,
                        onSelect = { onSelectProxy(groupName, it) },
                        onUrlTest = { onUrlTest(groupName) },
                        onTestProxy = { onTestProxy(groupName, it) },
                        fill = true,
                        bottomPadding = bottomInset + 16.dp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            shown == "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(48.dp))
            }
            else -> EmptyState(stringResource(R.string.proxy_empty_tips), PaddingValues())
        }
    }
}

/** Disconnected: the servers come after connecting, the announcement in full — there is room for it — then the connect button. */
@Composable
private fun DisconnectedDetail(activeProfile: Profile?, power: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.wide_groups_after_connect),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val announce = activeProfile?.announce?.replace("\\n", "\n").orEmpty()
        if (announce.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceBright,
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
            ) {
                // The text centred; the megaphone stays in the top left corner.
                Box(Modifier.padding(16.dp)) {
                    Text(
                        announce,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_mdi_bullhorn_variant_outline),
                        contentDescription = stringResource(R.string.opened_announcement),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
        }
        // The connect button under what there is to read, on its own: the cards on the left are free to be handled.
        power()
    }
}
