package com.github.kr328.clash.design.compose.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold

/** One row of Partial Reset: what it is, what resetting it does, its size on disk. */
data class ResetRow(
    val key: String,
    @StringRes val title: Int,
    @StringRes val detail: Int,
    @DrawableRes val icon: Int,
    /** "12.4 MB" for data; null for settings. */
    val size: String?,
    /** False while the VPN holds it (the core cache, the downloads). */
    val available: Boolean,
)

/** A group of rows under its header. */
data class ResetGroup(@StringRes val title: Int, val rows: List<ResetRow>)

/**
 * Debug → Partial Reset, as on iOS: tick what to reset, then confirm with the
 * red button at the bottom. Rows held by the running VPN are dimmed.
 */
@Composable
fun PartialResetScreen(
    groups: List<ResetGroup>,
    selected: Set<String>,
    resetting: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val available = groups.flatMap { it.rows }.filter { it.available }.map { it.key }
    val allSelected = available.isNotEmpty() && selected.containsAll(available)

    PreferenceScaffold(
        title = stringResource(R.string.partial_reset),
        onBack = onBack,
        actions = {
            TextButton(onClick = { onSelectAll(!allSelected) }, enabled = !resetting) {
                Text(stringResource(if (allSelected) R.string.reset_deselect_all else R.string.reset_select_all))
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = onReset,
                    enabled = selected.isNotEmpty() && !resetting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (resetting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                        )
                    }
                    Text(
                        if (selected.isEmpty()) stringResource(R.string.reset_select_hint)
                        else stringResource(R.string.reset_selected_count, selected.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) {
        for (group in groups) {
            category(group.title)
            for (row in group.rows) {
                item(key = row.key) {
                    val checked = row.key in selected
                    PreferenceRow(
                        title = stringResource(row.title),
                        summary = stringResource(row.detail),
                        icon = row.icon,
                        enabled = row.available && !resetting,
                        onClick = { onToggle(row.key) },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (row.size != null) {
                                    Text(
                                        row.size,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    enabled = row.available && !resetting,
                                    colors = CheckboxDefaults.colors(),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
