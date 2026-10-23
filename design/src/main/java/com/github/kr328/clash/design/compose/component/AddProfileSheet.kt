package com.github.kr328.clash.design.compose.component

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R

/** Add-profile actions, mirroring the View-based add sheet. */
enum class AddProfileAction { Clipboard, ScanQr, File, Manually, TvImport }

/**
 * Bottom sheet for adding a profile, kept visually close to the View-based
 * `design_sheet_add_profile_profiles.xml`. The "from another device" card is
 * shown only on TV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfileSheet(
    isTv: Boolean,
    onAction: (AddProfileAction) -> Unit,
    onDismiss: () -> Unit,
) {
    // Open fully expanded so every option (incl. manual / from file) is visible
    // immediately without dragging — important on TV where there is no drag.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tvFocus = remember { FocusRequester() }
    // On TV, land the D-pad on the first option rather than the drag handle.
    LaunchedEffect(Unit) { if (isTv) runCatching { tvFocus.requestFocus() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            if (isTv) {
                TvImportCard(
                    onClick = { onAction(AddProfileAction.TvImport) },
                    modifier = Modifier.focusRequester(tvFocus),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                SquareActionCard(
                    icon = R.drawable.ic_mdi_clipboard_outline,
                    label = stringResource(R.string.add_from_clipboard),
                    onClick = { onAction(AddProfileAction.Clipboard) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 1.dp),
                )
                SquareActionCard(
                    icon = R.drawable.ic_mdi_qrcode_scan,
                    label = stringResource(R.string.scan_qr_code),
                    onClick = { onAction(AddProfileAction.ScanQr) },
                    leading = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 1.dp),
                )
            }

            // The other two as a segmented group, as Settings lists rows.
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CompositionLocalProvider(LocalSegment provides Segment(0, 2)) {
                    PreferenceRow(
                        title = stringResource(R.string.add_from_file),
                        icon = R.drawable.ic_mdi_file_cog_outline,
                        onClick = { onAction(AddProfileAction.File) },
                    )
                }
                CompositionLocalProvider(LocalSegment provides Segment(1, 2)) {
                    PreferenceRow(
                        title = stringResource(R.string.add_manually),
                        icon = R.drawable.ic_mdi_plus_thick,
                        onClick = { onAction(AddProfileAction.Manually) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvImportCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mdi_home_import_outline),
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.add_from_tv),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onPrimaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )
            Text(
                text = stringResource(R.string.add_from_tv_badge),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

/** One of the two large actions, side by side: the outer corners round, the inner ones less, as a button group. */
@Composable
private fun SquareActionCard(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Pressed, the whole tile rounds: Material 3 Expressive's shape morph.
    val inner by animateDpAsState(if (pressed) 28.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "inner")
    val shape = if (leading) {
        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = inner, bottomEnd = inner)
    } else {
        RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = 28.dp, bottomEnd = 28.dp)
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = scheme.surfaceBright,
        interactionSource = source,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
