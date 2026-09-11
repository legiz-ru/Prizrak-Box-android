package com.github.kr328.clash.design.compose.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.kr328.clash.design.R

/**
 * TV substitute for opening [url] in a browser: a link typed with a D-pad is
 * painful, and many TV boxes have no browser to open it in at all. Shows the
 * link as a QR code to scan with a phone, the URL itself for anyone who'd
 * rather copy it (a PC next to the TV, no working camera), and nothing else —
 * this only ever replaces an ACTION_VIEW that would otherwise fire silently
 * into whatever (if anything) handles it.
 */
@Composable
fun UrlQrDialog(
    title: String,
    url: String,
    qr: Bitmap,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    // Same reasoning as TvImportScreen's cancel button: give the remote a
    // guaranteed, immediate target rather than leaving focus wherever the
    // triggering click left it.
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 680.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Image(
                        bitmap = remember(qr) { qr.asImageBitmap() },
                        contentDescription = title,
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White)
                            .padding(12.dp),
                    )
                    Column(modifier = Modifier.widthIn(max = 340.dp)) {
                        Text(
                            text = stringResource(R.string.tv_url_qr_scan_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            SelectionContainer {
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 28.dp),
                ) {
                    OutlinedButton(onClick = onCopyLink) {
                        Text(stringResource(R.string.tv_url_qr_copy))
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeFocus),
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
