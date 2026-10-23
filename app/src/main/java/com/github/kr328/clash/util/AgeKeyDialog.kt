package com.github.kr328.clash.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.ConnectedChoice
import com.github.kr328.clash.design.compose.component.PreferenceCard
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog

private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
}

private val KEY_TYPES = listOf("MLKEM768-X25519", "X25519")

/** Debug → Age Key Generator: a new age key pair, its type to choose, both keys to copy. */
fun Context.showAgeKeyPairDialog() {
    val context = this
    ExpressiveDialog.Builder(this)
        .setTitle(R.string.age_keypair_result_title)
        .setPositiveButton(R.string.ok, null)
        .setContent {
            var type by remember { mutableStateOf(KEY_TYPES.first()) }
            // (secret, public), made again on a new type or ↻.
            var pair by remember { mutableStateOf(Clash.generateAgeKeyPair(type)) }
            fun regenerate() {
                Clash.generateAgeKeyPair(type)?.let { pair = it }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectedChoice(
                        options = KEY_TYPES.map { it to it },
                        selected = type,
                        onSelect = {
                            type = it
                            regenerate()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(onClick = ::regenerate, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(painterResource(R.drawable.ic_baseline_sync), stringResource(R.string.age_regenerate))
                    }
                }
                KeyCard(stringResource(R.string.age_public_key), pair?.second.orEmpty()) {
                    context.copyToClipboard(context.getString(R.string.age_public_key), it)
                }
                KeyCard(stringResource(R.string.age_private_key), pair?.first.orEmpty()) {
                    context.copyToClipboard(context.getString(R.string.age_private_key), it)
                }
            }
        }
        .show()
}

@Composable
private fun KeyCard(title: String, key: String, onCopy: (String) -> Unit) {
    PreferenceCard {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { if (key.isNotEmpty()) onCopy(key) }) {
                    Icon(painterResource(R.drawable.ic_baseline_content_copy), stringResource(R.string.age_copy_key))
                }
            }
            SelectionContainer {
                Text(
                    key,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp),
                )
            }
        }
    }
}
