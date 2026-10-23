package com.github.kr328.clash.design.compose.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.FilledTonalIconButton
import com.github.kr328.clash.design.compose.dialog.ExpressiveAlert
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.NullableTextAdapter
import com.github.kr328.clash.design.compose.component.TextAdapter

/**
 * Compose equivalents of the View-based editable preferences
 * (editableText / editableTextList / editableTextMap), restyled to MD3 Expressive.
 */

@Composable
fun <T> EditableTextPreference(
    title: String,
    value: T,
    adapter: NullableTextAdapter<T>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    empty: String? = null,
    numeric: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    // Self-updating display state: seeded from [value] and re-seeded when the caller
    // passes a new value (e.g. hoisted/flow-driven screens), but updated locally on edit
    // so LazyColumn rows reflect changes immediately without an external recompose trigger.
    var current by remember(value) { mutableStateOf(value) }
    val display = adapter.from(current)
    val summary = when {
        display == null -> placeholder
        display.isEmpty() -> empty
        else -> display
    }
    PreferenceRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        TextInputDialog(
            title = title,
            initial = adapter.from(current) ?: "",
            numeric = numeric,
            onConfirm = {
                val nv = adapter.to(it)
                current = nv
                onValueChange(nv)
                showDialog = false
            },
            onReset = {
                val nv = adapter.to(null)
                current = nv
                onValueChange(nv)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    numeric: Boolean,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    ExpressiveAlert(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = stringResource(R.string.ok),
        onConfirm = { onConfirm(text) },
        dismissLabel = stringResource(R.string.cancel),
        neutralLabel = stringResource(R.string.reset),
        onNeutral = onReset,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = if (numeric) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions.Default
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun <T> EditableListPreference(
    title: String,
    value: List<T>?,
    adapter: TextAdapter<T>,
    onValueChange: (List<T>?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    // Self-updating display state (see EditableTextPreference) so edits show immediately.
    var current by remember(value) { mutableStateOf(value) }
    val summary = when {
        current == null -> placeholder
        current!!.isEmpty() -> stringResource(R.string.empty)
        else -> stringResource(R.string.format_elements, current!!.size)
    }
    PreferenceRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        ListEditorDialog(
            title = title,
            initial = current?.map { adapter.from(it) } ?: emptyList(),
            onApply = {
                val nv = it.map { item -> adapter.to(item) }
                current = nv
                onValueChange(nv)
                showDialog = false
            },
            onReset = {
                current = null
                onValueChange(null)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun ListEditorDialog(
    title: String,
    initial: List<String>,
    onApply: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val items = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var newItem by remember { mutableStateOf("") }
    ExpressiveAlert(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = stringResource(R.string.ok),
        onConfirm = { onApply(items.toList()) },
        dismissLabel = stringResource(R.string.cancel),
        neutralLabel = stringResource(R.string.reset),
        onNeutral = onReset,
    ) {
        // The entries as a segmented list; a field to add one below.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                CompositionLocalProvider(LocalSegment provides Segment(index, items.size)) {
                    EntryRow(item) { items.removeAt(index) }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = {
                    if (newItem.isNotBlank()) {
                        items.add(newItem.trim())
                        newItem = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_baseline_add), contentDescription = null)
            }
        }
    }
}

@Composable
fun <K, V> EditableMapPreference(
    title: String,
    value: Map<K, V>?,
    keyAdapter: TextAdapter<K>,
    valueAdapter: TextAdapter<V>,
    onValueChange: (Map<K, V>?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    // Self-updating display state (see EditableTextPreference) so edits show immediately.
    var current by remember(value) { mutableStateOf(value) }
    val summary = when {
        current == null -> placeholder
        current!!.isEmpty() -> stringResource(R.string.empty)
        else -> stringResource(R.string.format_elements, current!!.size)
    }
    PreferenceRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        MapEditorDialog(
            title = title,
            initial = current?.map { keyAdapter.from(it.key) to valueAdapter.from(it.value) }
                ?: emptyList(),
            onApply = { pairs ->
                val nv = pairs.associate { keyAdapter.to(it.first) to valueAdapter.to(it.second) }
                current = nv
                onValueChange(nv)
                showDialog = false
            },
            onReset = {
                current = null
                onValueChange(null)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun MapEditorDialog(
    title: String,
    initial: List<Pair<String, String>>,
    onApply: (List<Pair<String, String>>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pairs = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(initial) } }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    ExpressiveAlert(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = stringResource(R.string.ok),
        onConfirm = { onApply(pairs.toList()) },
        dismissLabel = stringResource(R.string.cancel),
        neutralLabel = stringResource(R.string.reset),
        onNeutral = onReset,
    ) {
        // The entries as a segmented list; a field to add one below.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            pairs.forEachIndexed { index, (k, v) ->
                CompositionLocalProvider(LocalSegment provides Segment(index, pairs.size)) {
                    EntryRow("$k → $v") { pairs.removeAt(index) }
                }
            }
        }
        OutlinedTextField(
            value = newKey,
            onValueChange = { newKey = it },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = newValue,
                onValueChange = { newValue = it },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = {
                    if (newKey.isNotBlank() && newValue.isNotBlank()) {
                        pairs.add(newKey.trim() to newValue.trim())
                        newKey = ""
                        newValue = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_baseline_add), contentDescription = null)
            }
        }
    }
}

/** One entry of a list or map being edited: a segmented row with Delete at its end. */
@Composable
private fun EntryRow(text: String, onDelete: () -> Unit) {
    PreferenceCard(modifier = Modifier.heightIn(min = 48.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_delete),
                    contentDescription = stringResource(R.string.delete),
                )
            }
        }
    }
}
