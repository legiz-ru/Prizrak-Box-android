package com.github.kr328.clash

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.compat.versionCodeCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.compose.screen.AppCrashedScreen
import com.github.kr328.clash.log.SystemLogcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AppCrashedActivity : BaseActivity() {
    private val logsFlow = MutableStateFlow("")

    override suspend fun main() {
        setContent {
            AppTheme {
                val logs by logsFlow.collectAsStateWithLifecycle()
                AppCrashedScreen(logs = logs, onBack = { finish() })
            }
        }

        val packageInfo = withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0)
        }

        Log.i("App version: versionName = ${packageInfo.versionName} versionCode = ${packageInfo.versionCodeCompat}")

        logsFlow.value = withContext(Dispatchers.IO) {
            SystemLogcat.dumpCrash()
        }

        while (isActive) {
            events.receive()
        }
    }
}
