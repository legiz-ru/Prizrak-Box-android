@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.github.kr328.clash.design.compose.component

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.LoadingIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.theme.ClashTheme
import kotlinx.coroutines.launch

/*
 * Material 3 Expressive building blocks for every settings-like screen: a
 * large flexible top bar over the surface container, rows in segmented
 * groups (large corners at a group's ends, small ones between, 2 dp apart),
 * switches with a check in their thumb, choices in a bottom sheet. A row
 * learns its place in its group from [LocalSegment], set by [PreferenceList].
 */

/** Where a row sits in its group: index from 0, out of count. */
data class Segment(val index: Int, val count: Int)

/** The row's place in its group; null for a row on its own. */
val LocalSegment = compositionLocalOf<Segment?> { null }

/**
 * What a settings screen lists: rows ([item], [items]) grouped by [category]
 * headers, [footnote]s under a group, and [free] items with a look of their
 * own. Consecutive rows form one segmented group.
 */
class PreferenceListScope internal constructor() {
    internal sealed interface Entry {
        class Row(val key: Any?, val content: @Composable LazyItemScope.() -> Unit) : Entry
        class Category(val title: @Composable () -> String) : Entry
        class Footnote(val text: @Composable () -> String, val error: Boolean) : Entry
        class Free(val key: Any?, val content: @Composable LazyItemScope.() -> Unit) : Entry
        object Gap : Entry
    }

    internal val entries = mutableListOf<Entry>()

    /** A row of the current group. */
    fun item(key: Any? = null, contentType: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
        entries += Entry.Row(key, content)
    }

    fun items(count: Int, key: ((Int) -> Any)? = null, itemContent: @Composable LazyItemScope.(Int) -> Unit) {
        repeat(count) { index -> entries += Entry.Row(key?.invoke(index)) { itemContent(index) } }
    }

    fun <T> items(items: List<T>, key: ((T) -> Any)? = null, itemContent: @Composable LazyItemScope.(T) -> Unit) {
        items.forEach { value -> entries += Entry.Row(key?.invoke(value)) { itemContent(value) } }
    }

    fun <T> itemsIndexed(items: List<T>, key: ((Int, T) -> Any)? = null, itemContent: @Composable LazyItemScope.(Int, T) -> Unit) {
        items.forEachIndexed { index, value -> entries += Entry.Row(key?.invoke(index, value)) { itemContent(index, value) } }
    }

    /** A header: the rows after it are a new group. */
    fun category(@StringRes title: Int) {
        entries += Entry.Category { stringResource(title) }
    }

    fun category(title: String) {
        entries += Entry.Category { title }
    }

    /** A note under the group above. */
    fun footnote(@StringRes text: Int, error: Boolean = false) {
        entries += Entry.Footnote({ stringResource(text) }, error)
    }

    fun footnote(text: String, error: Boolean = false) {
        entries += Entry.Footnote({ text }, error)
    }

    /** An item with a look of its own (a preview, a picker): not a row, it ends the group. */
    fun free(key: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
        entries += Entry.Free(key, content)
    }

    /** Ends the group: the rows after it start a group of their own. */
    fun gap() {
        entries += Entry.Gap
    }

    /** Puts the entries into the lazy list, each row told its place in its group. */
    internal fun emit(list: LazyListScope) {
        var i = 0
        var first = true
        while (i < entries.size) {
            when (val entry = entries[i]) {
                is Entry.Row -> {
                    var end = i
                    while (end + 1 < entries.size && entries[end + 1] is Entry.Row) end++
                    val count = end - i + 1
                    if (!first && entries.getOrNull(i - 1) !is Entry.Category) {
                        list.item { Spacer(Modifier.height(14.dp)) }
                    }
                    for (index in 0 until count) {
                        val row = entries[i + index] as Entry.Row
                        list.item(key = row.key) {
                            CompositionLocalProvider(LocalSegment provides Segment(index, count)) {
                                row.content(this)
                            }
                        }
                    }
                    i = end
                }
                is Entry.Category -> list.item { SettingsCategory(entry.title()) }
                is Entry.Footnote -> list.item { PreferenceFootnote(entry.text(), entry.error) }
                is Entry.Free -> {
                    if (!first) list.item { Spacer(Modifier.height(14.dp)) }
                    list.item(key = entry.key) { entry.content(this) }
                }
                Entry.Gap -> Unit
            }
            first = false
            i++
        }
    }
}

/** A note under a group, in the label colour (or the error colour). */
@Composable
fun PreferenceFootnote(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
    )
}

/** The rows of a settings screen, grouped and spaced as Material 3 Expressive lists them. */
@Composable
fun PreferenceList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    content: PreferenceListScope.() -> Unit,
) {
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        state = state,
        modifier = modifier.verticalScrollbar(state, scrollbarColor),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        PreferenceListScope().apply(content).emit(this)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * A settings screen: a large flexible title that collapses as the list
 * scrolls, back at its left, [actions] at its right, the list below on the
 * surface container. Without [onBack] there is no back button (a top-level
 * screen).
 */
@Composable
fun PreferenceScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: PreferenceListScope.() -> Unit,
) {
    ExpressiveScaffold(title = title, onBack = onBack, actions = actions, bottomBar = bottomBar) { inner ->
        PreferenceList(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listPadding(inner),
            content = content,
        )
    }
}

/**
 * A screen as Material 3 Expressive has it: the container colour behind, a
 * large title that collapses into the bar as the content scrolls.
 */
@Composable
fun ExpressiveScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // In a column of a wide screen: the column's colour shows, the system bars are the screen's business.
    val inPane = com.github.kr328.clash.design.compose.LocalInPane.current
    val background = if (inPane) Color.Transparent else scheme.surfaceContainer
    // The content runs up under the title (it collapses over it), so a remote's
    // ↓ from the bar finds nothing below it: it is pointed at the content.
    val contentFocus = remember { FocusRequester() }
    val downIntoContent = Modifier.focusProperties { down = contentFocus }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        contentWindowInsets = if (inPane) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            LargeFlexibleTopAppBar(
                windowInsets = if (inPane) WindowInsets(0) else TopAppBarDefaults.windowInsets,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        titleIcon?.invoke()
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = downIntoContent) {
                            Icon(painterResource(R.drawable.ic_baseline_arrow_back), contentDescription = stringResource(R.string.close))
                        }
                    }
                },
                actions = { Row(downIntoContent, verticalAlignment = Alignment.CenterVertically) { actions() } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
    ) { inner ->
        Box(
            Modifier
                .focusRequester(contentFocus)
                .focusGroup(),
        ) {
            content(inner)
        }
    }
}

/** A search field as a pill on the bright surface, the magnifier at its start. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search),
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = CircleShape,
        leadingIcon = { Icon(painterResource(R.drawable.ic_baseline_search), contentDescription = null) },
        trailingIcon = trailing,
        placeholder = { Text(placeholder) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceBright,
            unfocusedContainerColor = scheme.surfaceBright,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A sheet of actions: segmented rows on the container colour, as Settings lists
 * them; an optional title above.
 */
@Composable
fun ActionSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: PreferenceListScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
        }
        PreferenceList(contentPadding = PaddingValues(horizontal = 16.dp), content = content)
    }
}

/** Work under way: Material 3 Expressive's morphing indicator, in the accent. */
@Composable
fun BusyIndicator(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    LoadingIndicator(modifier = modifier.size(size))
}

/** Nothing to list: a quiet line in the middle of the screen. */
@Composable
fun EmptyState(text: String, inner: PaddingValues, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(inner),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The scaffold's insets with the side margins inside the list: its scrollbar at the screen's edge, clear of the rows. */
@Composable
fun listPadding(inner: PaddingValues, horizontal: androidx.compose.ui.unit.Dp = 16.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = inner.calculateStartPadding(direction) + horizontal,
        top = inner.calculateTopPadding(),
        end = inner.calculateEndPadding(direction) + horizontal,
        bottom = inner.calculateBottomPadding(),
    )
}

/** The segmented shapes of a row at [segment] (a single shape alone). */
@Composable
fun segmentShapes(segment: Segment?): ListItemShapes {
    val shapes = MaterialTheme.shapes
    return if (segment == null || segment.count == 1) {
        ListItemDefaults.shapes(shape = shapes.large, pressedShape = shapes.extraLarge)
    } else {
        ListItemDefaults.segmentedShapes(
            segment.index,
            segment.count,
            ListItemDefaults.shapes(shape = shapes.extraSmall, pressedShape = shapes.extraLarge),
        )
    }
}

/** A row's colours: the bright surface over the container behind the list. */
@Composable
fun segmentColors(destructive: Boolean = false, container: Color = MaterialTheme.colorScheme.surfaceBright): ListItemColors {
    val scheme = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = container,
        disabledContainerColor = container,
        selectedContainerColor = container,
        contentColor = if (destructive) scheme.error else Color.Unspecified,
        leadingContentColor = if (destructive) scheme.error else Color.Unspecified,
    )
}

@Composable
private fun LeadingIcon(@DrawableRes icon: Int?) {
    if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
}

/** A row that opens something or does something. */
@Composable
fun PreferenceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceBright,
) {
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = segmentShapes(LocalSegment.current),
        colors = segmentColors(destructive, container),
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        leadingContent = leading ?: icon?.let { { LeadingIcon(it) } },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
    ) {
        Text(title)
    }
}

/** Content with a look of its own (sliders, a field, swatches) on a row's surface, in its group. */
@Composable
fun PreferenceCard(
    modifier: Modifier = Modifier,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = segmentShapes(LocalSegment.current).shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        border = border,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/**
 * One of a few, as Material 3 Expressive's connected button group: the chosen
 * one rounds fully. Neutral, not the accent: a mode, not a colour.
 */
@Composable
fun <T> ConnectedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { onSelect(value) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.onSurface,
                    checkedContentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

/** A row that only shows a value at its end: nothing to open. Spaced as the rows that open something. */
@Composable
fun ValuePreference(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = segmentShapes(LocalSegment.current).shape,
        color = scheme.surfaceBright,
        contentColor = scheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val start = leading ?: icon?.let { { LeadingIcon(it) } }
            if (start != null) {
                CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) { start() }
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * A switch row: the whole row toggles. Green whatever the accent, a check in
 * the thumb while on.
 */
@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    // Opens that notification's own system channel settings (sound, vibration,
    // importance) — only meaningful for a toggle backed by its own channel.
    onOpenChannelSettings: (() -> Unit)? = null,
) {
    // Self-updating: seeded from [checked], re-seeded when the caller passes a new
    // value, but toggled locally on tap so lazy rows reflect it at once.
    var current by remember(checked) { mutableStateOf(checked) }
    val change = { value: Boolean -> current = value; onCheckedChange(value) }
    SegmentedListItem(
        checked = current,
        onCheckedChange = change,
        shapes = segmentShapes(LocalSegment.current),
        colors = segmentColors(),
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        leadingContent = icon?.let { { LeadingIcon(it) } },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenChannelSettings != null) {
                    IconButton(onClick = onOpenChannelSettings, enabled = enabled) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_settings),
                            contentDescription = stringResource(R.string.open_channel_settings),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                ExpressiveSwitch(checked = current, enabled = enabled)
            }
        },
    ) {
        Text(title)
    }
}

/** The switch of a row: shown, the row itself takes the touch. */
@Composable
fun ExpressiveSwitch(checked: Boolean, enabled: Boolean = true, onCheckedChange: ((Boolean) -> Unit)? = null) {
    val green = ClashTheme.extraColors.settingsGreen
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(painterResource(R.drawable.ic_baseline_check), null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedTrackColor = green,
            checkedBorderColor = green,
            checkedThumbColor = Color.White,
            checkedIconColor = green,
        ),
    )
}

/**
 * A choice among options, in a bottom sheet: the options as a segmented
 * list, the chosen one marked. Picking one closes it.
 */
@Composable
fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
            )
            options.forEachIndexed { index, (value, label) ->
                SegmentedListItem(
                    selected = value == selected,
                    onClick = {
                        onSelect(value)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    shapes = segmentShapes(Segment(index, options.size)),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    trailingContent = if (value == selected) {
                        { Icon(painterResource(R.drawable.ic_baseline_check), null, tint = ClashTheme.extraColors.settingsGreen) }
                    } else {
                        null
                    },
                ) {
                    Text(label)
                }
            }
        }
    }
}

/** A row dimmed to say it is not available now. */
fun Modifier.dimmed(dim: Boolean): Modifier = if (dim) alpha(0.45f) else this
