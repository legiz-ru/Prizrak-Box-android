package com.github.kr328.clash.design.compose.screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.ActionSheet
import com.github.kr328.clash.design.compose.component.EmptyState
import com.github.kr328.clash.design.compose.component.ExpressiveScaffold
import com.github.kr328.clash.design.compose.component.PreferenceList
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.listPadding
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString

/**
 * In-app file browser (1:1 with FilesDesign, restyled to MD3E). The toolbar "add"
 * action imports a new file and is shown only outside the profile base directory.
 * Per-file operations live in a bottom-sheet menu, mirroring dialog_files_menu.xml.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    files: List<File>,
    inBaseDir: Boolean,
    configurationEditable: Boolean,
    now: Long,
    onBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenDirectory: (File) -> Unit,
    onNew: () -> Unit,
    onRename: (File) -> Unit,
    onImport: (File) -> Unit,
    onExport: (File) -> Unit,
    onDelete: (File) -> Unit,
) {
    var menuFile by remember { mutableStateOf<File?>(null) }

    ExpressiveScaffold(
        title = stringResource(R.string.browse_files),
        onBack = onBack,
        actions = {
            if (!inBaseDir) {
                IconButton(onClick = onNew) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_add),
                        contentDescription = stringResource(R.string._new),
                    )
                }
            }
        },
    ) { inner ->
        if (files.isEmpty()) {
            EmptyState(stringResource(R.string.empty), inner)
        } else {
            PreferenceList(modifier = Modifier.fillMaxSize(), contentPadding = listPadding(inner)) {
                items(files, key = { it.id }) { file ->
                    FileRow(
                        file = file,
                        now = now,
                        onOpen = {
                            if (file.isDirectory) onOpenDirectory(file) else onOpenFile(file)
                        },
                        onMore = { menuFile = file },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    menuFile?.let { file ->
        FileMenuSheet(
            file = file,
            inBaseDir = inBaseDir,
            configurationEditable = configurationEditable,
            onDismiss = { menuFile = null },
            onRename = { menuFile = null; onRename(file) },
            onImport = { menuFile = null; onImport(file) },
            onExport = { menuFile = null; onExport(file) },
            onDelete = { menuFile = null; onDelete(file) },
        )
    }
}

@Composable
private fun FileRow(
    file: File,
    now: Long,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    PreferenceRow(
        title = file.name,
        summary = if (file.isDirectory) null else {
            file.size.toBytesString() + " · " + (now - file.lastModified).elapsedIntervalString(context)
        },
        icon = if (file.isDirectory) R.drawable.ic_outline_folder else R.drawable.ic_outline_article,
        onClick = onOpen,
        onLongClick = onMore,
        modifier = modifier,
        trailing = {
            IconButton(onClick = onMore) {
                Icon(painter = painterResource(R.drawable.ic_baseline_more_vert), contentDescription = null)
            }
        },
    )
}

@Composable
private fun FileMenuSheet(
    file: File,
    inBaseDir: Boolean,
    configurationEditable: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    ActionSheet(onDismiss = onDismiss, title = file.name) {
        if (!file.isDirectory && (!inBaseDir || configurationEditable)) {
            item { PreferenceRow(stringResource(R.string.import_), onImport, icon = R.drawable.ic_baseline_get_app) }
        }
        if (!file.isDirectory && file.size > 0) {
            item { PreferenceRow(stringResource(R.string.export), onExport, icon = R.drawable.ic_baseline_publish) }
        }
        if (!inBaseDir) {
            item { PreferenceRow(stringResource(R.string.rename), onRename, icon = R.drawable.ic_baseline_edit) }
            gap()
            item { PreferenceRow(stringResource(R.string.delete), onDelete, icon = R.drawable.ic_outline_delete, destructive = true) }
        }
    }
}
