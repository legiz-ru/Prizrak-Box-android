package com.github.kr328.clash.design.compose.component

import com.github.kr328.clash.design.compose.dialog.ExpressiveAlert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.FilledTonalIconButton
import com.github.kr328.clash.design.compose.background.homeGlass
import com.github.kr328.clash.design.compose.background.HomeGlassBackdrop
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.ProxyTypeIcons
import java.util.Locale

private val DelayGreen = Color(0xFF4CAF50)
private val DelayOrange = Color(0xFFFF9800)
private val DelayRed = Color(0xFFF44336)

enum class ProxySheetSort { Default, Name, Delay }

/**
 * What mihomo reports as the delay of a proxy with no usable measurement — it
 * was never tested, or the last test failed. [Proxy.tested] tells the two apart.
 */
internal const val DELAY_TIMEOUT = 65535

/** What a row's delay badge shows. */
internal sealed interface DelayState {
    /** No test has ever run for this proxy. */
    data object Untested : DelayState

    /** A test is running right now. */
    data object Testing : DelayState

    /** A test ran and the proxy did not answer. */
    data object Timeout : DelayState

    /** A test ran and the proxy answered in [ms]. */
    data class Value(val ms: Int) : DelayState
}

private fun delayColor(delay: Int): Color = when {
    delay <= 0 -> Color.Transparent
    delay < 200 -> DelayGreen
    delay < 500 -> DelayOrange
    else -> DelayRed
}

private fun proxyDelayState(proxy: Proxy): DelayState = when {
    !proxy.tested -> DelayState.Untested
    proxy.delay <= 0 || proxy.delay >= DELAY_TIMEOUT -> DelayState.Timeout
    else -> DelayState.Value(proxy.delay)
}

/**
 * The best of a load-balance group: its fastest live member, or — when nothing
 * is live — a timeout if anything in it was tested at all.
 */
private fun bestDelayState(proxies: List<Proxy>): DelayState {
    val best = proxies
        .filter { it.tested && it.delay in 1 until DELAY_TIMEOUT }
        .minOfOrNull { it.delay }

    return when {
        best != null -> DelayState.Value(best)
        proxies.any { it.tested } -> DelayState.Timeout
        else -> DelayState.Untested
    }
}

private fun resolveDelayState(
    groupMap: Map<String, ProxyGroup>,
    groupName: String,
    now: String,
    visited: MutableSet<String> = mutableSetOf(),
): DelayState {
    if (!visited.add(groupName)) return DelayState.Untested
    val group = groupMap[groupName] ?: return DelayState.Untested
    val selected = group.proxies.find { it.name == now } ?: return DelayState.Untested
    if (!selected.isGroup) return proxyDelayState(selected)
    val nested = groupMap[selected.name] ?: return proxyDelayState(selected)
    if (nested.type == "LoadBalance") return bestDelayState(nested.proxies)
    return resolveDelayState(groupMap, selected.name, nested.now, visited)
}

fun selectedProxyName(groupMap: Map<String, ProxyGroup>, groupName: String, now: String): String {
    val group = groupMap[groupName] ?: return now
    val selected = group.proxies.find { it.name == now } ?: return now
    return selected.title.ifEmpty { selected.name }
}

internal fun rowDelayState(
    groupMap: Map<String, ProxyGroup>,
    proxy: Proxy,
    testing: Boolean,
): DelayState {
    if (testing) return DelayState.Testing
    if (!proxy.isGroup) return proxyDelayState(proxy)
    val nested = groupMap[proxy.name] ?: return proxyDelayState(proxy)
    return if (proxy.type == "LoadBalance") {
        bestDelayState(nested.proxies)
    } else {
        resolveDelayState(groupMap, proxy.name, nested.now)
    }
}

/**
 * Sort key for [ProxySheetSort.Delay]: live proxies by their delay, then the
 * ones that timed out, then the ones never tested. [List.sortedBy] is stable, so
 * within each band the original order survives.
 */
private fun delaySortKey(proxy: Proxy): Int = when {
    proxy.tested && proxy.delay in 1 until DELAY_TIMEOUT -> proxy.delay
    proxy.tested -> DELAY_TIMEOUT
    else -> Int.MAX_VALUE
}

private fun sortProxies(proxies: List<Proxy>, sort: ProxySheetSort): List<Proxy> = when (sort) {
    ProxySheetSort.Default -> proxies
    ProxySheetSort.Name -> proxies.sortedBy { it.title.ifEmpty { it.name }.lowercase(Locale.getDefault()) }
    ProxySheetSort.Delay -> proxies.sortedBy(::delaySortKey)
}

/** Accordion-style card for one proxy group on the Home screen. */
@Composable
fun ProxyGroupCard(
    name: String,
    group: ProxyGroup,
    groupMap: Map<String, ProxyGroup>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Open in the right column (a tablet, an unfolded foldable): outlined in the accent. */
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = homeGlass()
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        // Over Mushroomia, glass: the forest blurred behind it, a light rim.
        colors = CardDefaults.cardColors(containerColor = if (glass) Color.Transparent else scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = when {
            selected -> BorderStroke(2.dp, scheme.primary)
            glass -> BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
            else -> null
        },
    ) {
        Box {
            HomeGlassBackdrop()
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (group.icon.isNotEmpty()) {
                    RemoteIcon(
                        url = group.icon,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(28.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_vpn_lock),
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(28.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = selectedProxyName(groupMap, name, group.now),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_mdi_chevron_right),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Inline proxy list for simple-mode profiles (first group's nodes, no group card). */
@Composable
fun SimpleModeProxyList(
    group: ProxyGroup,
    groupMap: Map<String, ProxyGroup>,
    useDots: Boolean,
    testingProxies: Set<String>,
    onSelect: (String) -> Unit,
    onTestProxy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelector = group.type == "Selector"
    val isSmart = group.type == "Smart"
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        for (proxy in group.proxies) {
            ProxyRow(
                proxy = proxy,
                selected = proxy.name == group.now,
                delayState = rowDelayState(groupMap, proxy, proxy.name in testingProxies),
                useDots = useDots,
                enabled = isSelector,
                parentIsSmart = isSmart,
                groupMap = groupMap,
                onClick = { if (isSelector) onSelect(proxy.name) },
                onTestDelay = { onTestProxy(proxy.name) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySelectionSheet(
    groupName: String,
    group: ProxyGroup,
    groupMap: Map<String, ProxyGroup>,
    useDots: Boolean,
    isTv: Boolean,
    testingProxies: Set<String>,
    onSelect: (String) -> Unit,
    onUrlTest: () -> Unit,
    onTestProxy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val content: @Composable () -> Unit = {
        ProxyGroupPane(
            groupName = groupName,
            group = group,
            groupMap = groupMap,
            useDots = useDots,
            testingProxies = testingProxies,
            onSelect = {
                onSelect(it)
                onDismiss()
            },
            onUrlTest = onUrlTest,
            onTestProxy = onTestProxy,
            fill = isTv,
        )
    }

    if (isTv) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                content()
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    }
}

/**
 * A group's nodes: sort at the start, the name, the latency test at the end,
 * then the nodes as a segmented list. In the proxy sheet on a phone, and the
 * right column of Home on a tablet or an unfolded foldable.
 *
 * @param fill take the height given (TV, the right column); otherwise the
 *   list wraps its nodes up to a sheet's height.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProxyGroupPane(
    groupName: String,
    group: ProxyGroup,
    groupMap: Map<String, ProxyGroup>,
    useDots: Boolean,
    testingProxies: Set<String>,
    onSelect: (String) -> Unit,
    onUrlTest: () -> Unit,
    onTestProxy: (String) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val isSelector = group.type == "Selector"
    val isSmart = group.type == "Smart"
    // Kept per group: another group starts from the default order.
    var sort by remember(groupName) { mutableStateOf(ProxySheetSort.Default) }
    var showAutoDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fill) Modifier.fillMaxSize() else Modifier),
    ) {
        // Top bar: sort | title | url-test
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showSortDialog = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_sort),
                    contentDescription = stringResource(R.string.sort),
                )
            }
            Text(
                text = groupName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            // The whole group under test: the same morphing shape as the
            // simple-mode test circle on Home, until the results are in.
            // The group's own name is there until its last node is measured (testGroupLatency).
            val testing = groupName in testingProxies
            FilledTonalIconButton(onClick = { if (!testing) onUrlTest() }) {
                if (testing) {
                    LoadingIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_speed),
                        contentDescription = stringResource(R.string.delay),
                    )
                }
            }
        }

        val sorted = remember(group, sort) { sortProxies(group.proxies, sort) }
        PreferenceList(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fill) Modifier.weight(1f) else Modifier.heightIn(max = 520.dp)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        ) {
            items(sorted, key = { it.name }) { proxy ->
                ProxyRow(
                    proxy = proxy,
                    selected = proxy.name == group.now,
                    delayState = rowDelayState(groupMap, proxy, proxy.name in testingProxies),
                    useDots = useDots,
                    enabled = true,
                    parentIsSmart = isSmart,
                    groupMap = groupMap,
                    onTestDelay = { onTestProxy(proxy.name) },
                    onClick = {
                        if (isSelector) onSelect(proxy.name) else showAutoDialog = true
                    },
                    segmented = true,
                )
            }
        }
    }

    if (showAutoDialog) {
        ExpressiveAlert(
            onDismissRequest = { showAutoDialog = false },
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { showAutoDialog = false },
            text = stringResource(R.string.proxy_group_auto_no_select),
        )
    }

    if (showSortDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.sort),
            options = listOf(
                ProxySheetSort.Default to stringResource(R.string.default_),
                ProxySheetSort.Name to stringResource(R.string.name),
                ProxySheetSort.Delay to stringResource(R.string.delay),
            ),
            selected = sort,
            onSelect = { sort = it },
            onDismiss = { showSortDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProxyRow(
    proxy: Proxy,
    selected: Boolean,
    delayState: DelayState,
    useDots: Boolean,
    enabled: Boolean,
    parentIsSmart: Boolean,
    groupMap: Map<String, ProxyGroup>,
    onClick: () -> Unit,
    onTestDelay: (() -> Unit)? = null,
    // In a sheet or the Proxy screen: a Material 3 Expressive segmented row.
    // Home's own list keeps its look.
    segmented: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val name = proxy.title.ifEmpty { proxy.name }

    val nestedSmart = if (!parentIsSmart && proxy.type == "Smart") groupMap[proxy.name] else null
    val hasSmartData = nestedSmart?.proxies?.any { it.rank.isNotEmpty() && it.weight > 0.0 } == true

    val weightMessage: String? = remember(proxy, parentIsSmart, nestedSmart, hasSmartData) {
        when {
            parentIsSmart -> {
                val weight = (proxy.weight * 100).toInt()
                when (proxy.rank) {
                    "MostUsed" -> context.getString(R.string.proxies_smart_most_used_tip, weight)
                    "OccasionalUsed" -> context.getString(R.string.proxies_smart_occasional_used_tip, weight)
                    "RarelyUsed" -> context.getString(R.string.proxies_smart_rarely_used_tip, weight)
                    else -> context.getString(R.string.proxies_smart_no_data)
                }
            }
            nestedSmart != null -> {
                if (!hasSmartData) {
                    context.getString(R.string.proxies_smart_no_data)
                } else {
                    nestedSmart.proxies.filter { it.rank.isNotEmpty() }.joinToString("\n") { p ->
                        val label = when (p.rank) {
                            "MostUsed" -> context.getString(R.string.proxies_smart_most_used)
                            "OccasionalUsed" -> context.getString(R.string.proxies_smart_occasional_used)
                            "RarelyUsed" -> context.getString(R.string.proxies_smart_rarely_used)
                            else -> p.rank
                        }
                        "${p.title.ifEmpty { p.name }}: $label (${(p.weight * 100).toInt()})"
                    }.ifEmpty { context.getString(R.string.proxies_smart_no_data) }
                }
            }
            else -> null
        }
    }
    var showWeight by remember { mutableStateOf(false) }

    val typeIconRes = remember(proxy.type, proxy.isGroup) { ProxyTypeIcons.icon(proxy.type, proxy.isGroup) }
    val typeTooltip = remember(proxy.type, proxy.isGroup) { ProxyTypeIcons.tooltip(context, proxy.type, proxy.isGroup) }
    // subtitle defaults to the type itself; only worth printing as text when
    // ui-subtitle-pattern extracted something else from the node's name — the
    // type is always shown by the icon above regardless.
    //
    // A description from the profile (serverDescription / description) beats it.
    val typeDescription = proxy.description.takeIf { it.isNotEmpty() }
        ?: proxy.subtitle.takeIf {
            it.isNotEmpty() && !it.equals(proxy.type, ignoreCase = true)
        }
    var showTypeTooltip by remember { mutableStateOf(false) }

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val subtitleTint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                    Icon(
                        painter = painterResource(typeIconRes),
                        contentDescription = typeTooltip,
                        tint = subtitleTint,
                        modifier = Modifier
                            .size(14.dp)
                            // The row (Card) is already the D-pad focus/activation
                            // target on TV; without this, this icon becomes a second,
                            // separate stop a few pixels wide that D-pad nav can land
                            // on and get stuck on. Touch taps don't need focus, so
                            // this only removes the D-pad/keyboard path, not the tap.
                            .focusProperties { canFocus = false }
                            .clickable { showTypeTooltip = true },
                    )
                    if (typeDescription != null) {
                        Text(
                            text = typeDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = 4.dp),
                        )
                    }
                }
            }

            val tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
            if (parentIsSmart) {
                val iconRes = when (proxy.rank) {
                    "MostUsed" -> R.drawable.ic_mdi_shield
                    "OccasionalUsed" -> R.drawable.ic_mdi_shield_half_full
                    "RarelyUsed" -> R.drawable.ic_mdi_shield_outline
                    else -> R.drawable.ic_mdi_timelapse
                }
                ShieldIcon(iconRes, tint) { showWeight = true }
            } else if (nestedSmart != null) {
                val iconRes = if (hasSmartData) R.drawable.ic_mdi_shield_check_outline
                else R.drawable.ic_mdi_timelapse
                ShieldIcon(iconRes, tint) { showWeight = true }
            }

            DelayBadge(
                state = delayState,
                useDots = useDots,
                // A test already running is not a second tap target.
                onTest = onTestDelay.takeIf { delayState != DelayState.Testing },
            )
        }
    }

    if (segmented) {
        // The chosen one rounds fully and takes the accent's container.
        val shape = if (selected) MaterialTheme.shapes.extraLarge else segmentShapes(LocalSegment.current).shape
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = if (selected) scheme.secondaryContainer else scheme.surfaceBright,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) { rowContent() }
        }
    } else {
        Card(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) scheme.secondaryContainer else Color.Transparent,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = if (selected) BorderStroke(1.dp, scheme.primary) else null,
        ) {
            rowContent()
        }
    }

    if (showWeight && weightMessage != null) {
        ExpressiveAlert(
            onDismissRequest = { showWeight = false },
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { showWeight = false },
            text = weightMessage,
        )
    }

    if (showTypeTooltip) {
        ExpressiveAlert(
            onDismissRequest = { showTypeTooltip = false },
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { showTypeTooltip = false },
            text = typeTooltip,
        )
    }
}

@Composable
private fun ShieldIcon(iconRes: Int, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The delay readout of one row, and the tap target that re-measures it.
 *
 * Four states, told apart on purpose: a proxy that was never tested used to look
 * exactly like one that timed out, because mihomo reports 0xffff for both.
 *
 *  * [DelayState.Testing] — a spinner, in both display modes.
 *  * [DelayState.Untested] — a hollow ring / an em dash: no data, nothing claimed.
 *  * [DelayState.Timeout] — a filled red dot with a bar across it / "timeout".
 *  * [DelayState.Value] — a filled dot / the number, coloured by the delay.
 *
 * The badge keeps a fixed footprint so the rows do not shift as states change,
 * and the tap target around it is larger than the mark itself. Only this badge
 * is clickable — tapping the row still selects the proxy.
 */
@Composable
private fun DelayBadge(
    state: DelayState,
    useDots: Boolean,
    onTest: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val testLabel = stringResource(R.string.delay_test)
    val slot = Modifier
        .padding(start = 4.dp)
        .clip(RoundedCornerShape(50))
        .then(
            if (onTest != null) {
                Modifier.clickable(onClickLabel = testLabel, onClick = onTest)
            } else {
                Modifier
            },
        )

    if (useDots) {
        Box(
            modifier = slot.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                DelayState.Testing -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = scheme.onSurfaceVariant,
                )
                DelayState.Untested -> Box(
                    modifier = Modifier
                        .size(10.dp)
                        .border(1.5.dp, scheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                )
                DelayState.Timeout -> Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(DelayRed),
                    contentAlignment = Alignment.Center,
                ) {
                    // The bar is what separates a timeout from a merely slow
                    // proxy — both are red, only this one is struck through.
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 1.5.dp)
                            .background(Color.White),
                    )
                }
                is DelayState.Value -> Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(delayColor(state.ms)),
                )
            }
        }

        return
    }

    Box(
        modifier = slot
            .widthIn(min = 52.dp)
            .heightIn(min = 28.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            DelayState.Testing -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = scheme.onSurfaceVariant,
            )
            DelayState.Untested -> Text(
                text = "—",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            DelayState.Timeout -> Text(
                text = stringResource(R.string.timeout),
                style = MaterialTheme.typography.labelMedium,
                color = DelayRed,
            )
            is DelayState.Value -> Text(
                text = stringResource(R.string.format_delay_ms, state.ms),
                style = MaterialTheme.typography.labelMedium,
                color = delayColor(state.ms),
            )
        }
    }
}
