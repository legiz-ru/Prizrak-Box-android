package com.github.kr328.clash.design.dialog

import android.content.Context
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelProgressBarConfigure {
    var isIndeterminate: Boolean
    var text: String?
    var progress: Int
    var max: Int
}

/**
 * Maps a fetch [status] reported during profile commit onto the progress dialog,
 * so callers (import flow, properties screen) show live per-provider progress
 * instead of a static indeterminate message.
 */
fun ModelProgressBarConfigure.applyFetchStatus(context: Context, status: FetchStatus) {
    when (status.action) {
        FetchStatus.Action.FetchConfiguration -> {
            text = context.getString(R.string.format_fetching_configuration, status.args[0])
            isIndeterminate = true
        }
        FetchStatus.Action.FetchProviders -> {
            text = context.getString(R.string.format_fetching_provider, status.args[0])
            isIndeterminate = false
            max = status.max
            progress = status.progress
        }
        FetchStatus.Action.FetchIcons -> {
            text = context.getString(R.string.fetching_icons)
            isIndeterminate = false
            max = status.max
            progress = status.progress
        }
        FetchStatus.Action.Verifying -> {
            text = context.getString(R.string.verifying)
            isIndeterminate = false
            max = status.max
            progress = status.progress
        }
    }
}

interface ModelProgressBarScope {
    suspend fun configure(block: suspend ModelProgressBarConfigure.() -> Unit)
}

/**
 * A dialog that cannot be dismissed while [block] runs: a line of text over
 * Material 3 Expressive's wavy progress — moving on its own until there is a
 * count, then filling with it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
suspend fun Context.withModelProgressBar(block: suspend ModelProgressBarScope.() -> Unit) {
    var indeterminate by mutableStateOf(true)
    var label by mutableStateOf<String?>(null)
    var value by mutableIntStateOf(0)
    var total by mutableIntStateOf(100)

    val dialog = withContext(Dispatchers.Main) {
        ExpressiveDialog.Builder(this@withModelProgressBar)
            .setCancelable(false)
            .setContent {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    if (indeterminate) {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        val shown by animateFloatAsState(
                            if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f,
                            label = "progress",
                        )
                        LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            .show()
    }

    val configureImpl = object : ModelProgressBarConfigure {
        override var isIndeterminate: Boolean
            get() = indeterminate
            set(v) {
                indeterminate = v
            }
        override var text: String?
            get() = label
            set(v) {
                label = v
            }
        override var progress: Int
            get() = value
            set(v) {
                value = v
            }
        override var max: Int
            get() = total
            set(v) {
                total = v
            }
    }

    val scopeImpl = object : ModelProgressBarScope {
        override suspend fun configure(block: suspend ModelProgressBarConfigure.() -> Unit) {
            withContext(Dispatchers.Main) {
                configureImpl.block()
            }
        }
    }

    try {
        scopeImpl.block()
    } finally {
        withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) { dialog.dismiss() }
    }
}
