package com.github.kr328.clash

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import com.github.kr328.clash.design.compose.screen.PartialResetScreen
import com.github.kr328.clash.design.compose.screen.ResetGroup
import com.github.kr328.clash.design.compose.screen.ResetRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Debug → Partial Reset: tick what to reset, then confirm. */
class PartialResetActivity : BaseActivity() {
    private val sizes = MutableStateFlow<Map<PartialReset.Item, Long>>(emptyMap())
    private val selected = MutableStateFlow<Set<PartialReset.Item>>(emptySet())
    private val running = MutableStateFlow(false)
    private val resetting = MutableStateFlow(false)

    override suspend fun main() {
        running.value = clashRunning

        setContent {
            val sizes by sizes.collectAsStateWithLifecycle()
            val selected by selected.collectAsStateWithLifecycle()
            val running by running.collectAsStateWithLifecycle()
            val resetting by resetting.collectAsStateWithLifecycle()
            AppTheme {
                PartialResetScreen(
                    groups = PartialReset.Group.entries.map { group ->
                        ResetGroup(group.title, group.items.map { item ->
                            ResetRow(
                                key = item.name,
                                title = item.title,
                                detail = item.detail,
                                icon = item.icon,
                                size = sizes[item]?.let { PartialReset.formattedSize(this, it) },
                                available = item.isAvailable(running),
                            )
                        })
                    },
                    selected = selected.map { it.name }.toSet(),
                    resetting = resetting,
                    onToggle = { key ->
                        val item = PartialReset.Item.valueOf(key)
                        this.selected.value = if (item in this.selected.value) this.selected.value - item else this.selected.value + item
                    },
                    onSelectAll = { all ->
                        this.selected.value = if (all) available() else emptySet()
                    },
                    onReset = { launch { confirmAndReset() } },
                    onBack = { finish() },
                )
            }
        }

        measure()

        while (isActive) {
            when (events.receive()) {
                Event.ClashStart, Event.ClashStop -> {
                    running.value = clashRunning
                    // A running tunnel keeps the core cache: never reset it from a stale tick.
                    selected.value = selected.value.filter { it.isAvailable(clashRunning) }.toSet()
                }
                Event.ActivityStart -> measure()
                else -> Unit
            }
        }
    }

    private fun available(): Set<PartialReset.Item> =
        PartialReset.Item.entries.filter { it.isAvailable(running.value) }.toSet()

    /** Off the main thread: a large log or provider folder takes a moment. */
    private suspend fun measure() {
        sizes.value = withContext(Dispatchers.IO) {
            PartialReset.Item.entries.mapNotNull { item ->
                PartialReset.size(this@PartialResetActivity, item)?.let { item to it }
            }.toMap()
        }
    }

    private suspend fun confirmAndReset() {
        val items = selected.value
        if (items.isEmpty() || !confirm(items)) return
        resetting.value = true
        try {
            PartialReset.reset(this, items)
        } finally {
            measure()
            selected.value = emptySet()
            resetting.value = false
        }
        ExpressiveDialog.Builder(this)
            .setTitle(R.string.reset_done_title)
            .setMessage(R.string.reset_done_message)
            .setPositiveButton(R.string.ok) { _, _ -> }
            .show()
    }

    private suspend fun confirm(items: Set<PartialReset.Item>): Boolean = suspendCancellableCoroutine { ctx ->
        val message = PartialReset.Item.entries.filter { it in items }.joinToString("\n") { "• " + getString(it.title) }
        val dialog = ExpressiveDialog.Builder(this)
            .setTitle(R.string.reset_confirm_title)
            .setMessage(message)
            .setDestructiveButton(R.string.reset_action) { _, _ -> if (ctx.isActive) ctx.resume(true) }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
        dialog.setOnDismissListener { if (ctx.isActive) ctx.resume(false) }
        ctx.invokeOnCancellation { dialog.dismiss() }
    }
}
