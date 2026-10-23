package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.EmptyState
import com.github.kr328.clash.design.compose.component.ExpressiveScaffold
import com.github.kr328.clash.design.compose.component.PreferenceList
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.listPadding
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.design.util.format

/**
 * Logs file list (1:1 with LogsDesign, restyled to MD3E). Toolbar actions:
 * start live logcat (adb icon) and delete all (clear-all icon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    files: List<LogFile>,
    onBack: (() -> Unit)?,
    onStartLogcat: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpenFile: (LogFile) -> Unit,
) {
    ExpressiveScaffold(
        title = stringResource(R.string.logs),
        onBack = onBack,
        actions = {
            IconButton(onClick = onStartLogcat) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_adb),
                    contentDescription = stringResource(R.string.logcat),
                )
            }
            IconButton(onClick = onDeleteAll) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_clear_all),
                    contentDescription = stringResource(R.string.delete_all_logs),
                )
            }
        },
    ) { inner ->
        if (files.isEmpty()) {
            EmptyState(stringResource(R.string.empty), inner)
        } else {
            PreferenceList(modifier = Modifier.fillMaxSize(), contentPadding = listPadding(inner)) {
                items(files, key = { it.fileName }) { file ->
                    LogFileRow(file = file, onClick = { onOpenFile(file) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun LogFileRow(file: LogFile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val subtitle = remember(file) { file.date.format(context) }
    PreferenceRow(title = file.fileName, summary = subtitle, onClick = onClick, modifier = modifier)
}
