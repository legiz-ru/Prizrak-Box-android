package com.github.kr328.clash

import com.github.kr328.clash.BaseActivity.Event
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setFileName
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.LogsScreen
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.util.logsDir
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LogsActivity : SectionActivity() {
    override fun createSection() = LogsSection(this, ::finish)
}

class LogsSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {
    private val filesFlow = MutableStateFlow<List<LogFile>>(emptyList())

    override suspend fun run(events: SharedFlow<Event>) {
        reload()
        events.collect {
            if (it == Event.ActivityStart) reload()
        }
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val files by filesFlow.collectAsStateWithLifecycle()
        LogsScreen(
            files = files,
            onBack = onBack,
            onStartLogcat = {
                activity.startActivity(LogcatActivity::class.intent)
                close()
            },
            onDeleteAll = {
                activity.launch {
                    if (requestDeleteAll()) {
                        withContext(Dispatchers.IO) { deleteAllLogs() }
                        reload()
                    }
                }
            },
            onOpenFile = {
                activity.startActivity(LogcatActivity::class.intent.setFileName(it.fileName))
            },
        )
    }

    private suspend fun reload() {
        filesFlow.value = withContext(Dispatchers.IO) { loadFiles() }
    }

    private suspend fun requestDeleteAll(): Boolean {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { ctx ->
                ExpressiveDialog.Builder(activity)
                    .setTitle(R.string.delete_all_logs)
                    .setMessage(R.string.delete_all_logs_warn)
                    .setDestructiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .show()
                    .setOnDismissListener { if (!ctx.isCompleted) ctx.resume(false) }
            }
        }
    }

    private fun loadFiles(): List<LogFile> {
        val list = activity.cacheDir.resolve("logs").listFiles()?.toList() ?: emptyList()
        return list.mapNotNull { LogFile.parseFromFileName(it.name) }
    }

    private fun deleteAllLogs() {
        activity.logsDir.deleteRecursively()
    }
}
