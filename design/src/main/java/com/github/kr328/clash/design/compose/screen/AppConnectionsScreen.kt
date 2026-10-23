package com.github.kr328.clash.design.compose.screen

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SegmentedListItem
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.EmptyState
import com.github.kr328.clash.design.compose.component.ExpressiveScaffold
import com.github.kr328.clash.design.compose.component.LocalSegment
import com.github.kr328.clash.design.compose.component.PreferenceList
import com.github.kr328.clash.design.compose.component.listPadding
import com.github.kr328.clash.design.compose.component.segmentColors
import com.github.kr328.clash.design.compose.component.segmentShapes
import com.github.kr328.clash.design.util.toBytesString
import java.time.Duration
import java.time.Instant

/**
 * Per-app connection list (1:1 with AppConnectionsDesign, restyled to MD3E).
 * Lists a single app's connections (host, network/chain, traffic + duration);
 * tapping opens the shared detail sheet, and active connections expose a close
 * button. Polling/closing lives in AppConnectionsActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConnectionsScreen(
    appName: String,
    appIcon: Drawable?,
    connections: List<Connection>,
    closable: Boolean,
    onBack: () -> Unit,
    onConnectionClick: (Connection) -> Unit,
    onClose: (Connection) -> Unit,
) {
    val iconPainter = remember(appIcon) {
        appIcon?.let { BitmapPainter(it.toBitmap(96, 96).asImageBitmap()) }
    }
    ExpressiveScaffold(
        title = appName,
        onBack = onBack,
        titleIcon = iconPainter?.let {
            { Image(painter = it, contentDescription = null, modifier = Modifier.size(36.dp)) }
        },
    ) { inner ->
        if (connections.isEmpty()) {
            EmptyState(stringResource(R.string.empty), inner)
        } else {
            PreferenceList(modifier = Modifier.fillMaxSize(), contentPadding = listPadding(inner)) {
                items(connections, key = { it.id }) { conn ->
                    ConnectionRow(
                        connection = conn,
                        closable = closable,
                        onClick = { onConnectionClick(conn) },
                        onClose = { onClose(conn) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionRow(
    connection: Connection,
    closable: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val meta = connection.metadata
    val host = remember(connection) {
        val h = meta.host.ifEmpty { meta.destinationIP }
        if (meta.destinationPort.isNotEmpty()) "$h:${meta.destinationPort}" else h
    }
    val typeChain = remember(connection) {
        "${meta.network.uppercase()}  ·  ${connection.chains.reversed().joinToString(" → ")}"
    }
    val traffic = remember(connection) {
        "↑ ${connection.upload.toBytesString()}  ↓ ${connection.download.toBytesString()}    ${formatDuration(connection.start)}"
    }
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentShapes(LocalSegment.current),
        colors = segmentColors(),
        modifier = Modifier.fillMaxWidth(),
        supportingContent = {
            Column {
                Text(typeChain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(traffic, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = if (closable) {
            {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_clear_all),
                        contentDescription = null,
                    )
                }
            }
        } else null,
    ) {
        Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatDuration(startIso: String): String {
    return try {
        val start = Instant.parse(startIso)
        val seconds = Duration.between(start, Instant.now()).seconds.coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    } catch (_: Exception) {
        "00:00"
    }
}
