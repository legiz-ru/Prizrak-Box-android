package com.github.kr328.clash

import com.github.kr328.clash.BaseActivity.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.runtime.remember
import com.github.kr328.clash.zashboard.ZashboardFiles
import com.github.kr328.clash.zashboard.ZashboardSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.HelpScreen
import com.github.kr328.clash.update.UpdateChecker
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HelpActivity : SectionActivity() {
    override fun createSection() = HelpSection(this, ::finish)
}

class HelpSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {
    private val appVersionFlow = MutableStateFlow("")
    private val appIconFlow = MutableStateFlow<ImageBitmap?>(null)

    override suspend fun run(events: SharedFlow<Event>) {
        val info = withContext(Dispatchers.IO) {
            runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0) }.getOrNull()
        }
        appVersionFlow.value = info?.versionName.orEmpty()
        // The home screen icon as a square for the header (rounded there, as
        // on iOS), not cut to the launcher's circle.
        appIconFlow.value = withContext(Dispatchers.IO) {
            runCatching { squareAppIcon(288).asImageBitmap() }.getOrNull()
        }
        events.collect { }
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val appVersion by appVersionFlow.collectAsStateWithLifecycle()
        val appIcon by appIconFlow.collectAsStateWithLifecycle()
        HelpScreen(
            appIcon = appIcon,
            appVersion = appVersion,
            // When this build was made, in UTC: 261023-0124.
            appBuild = BuildConfig.BUILD_STAMP,
            coreVersion = BuildConfig.CORE_VERSION,
            onBack = onBack,
            onDebug = { activity.startActivity(DebugActivity::class.intent) },
            zashboardVersion = remember {
                if (ZashboardSession.isEnabled(activity)) ZashboardFiles.version(activity) else null
            },
            onCheckUpdate = { activity.launch { runUpdateCheck() } },
            onOpenLink = {
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                }
            },
        )
    }

    /**
     * The app's icon, [side] pixels square. An adaptive icon is drawn from its
     * layers, the visible 72 of their 108 dp filling the square, as a
     * launcher shows it but without its mask.
     */
    private fun squareAppIcon(side: Int): Bitmap {
        val icon = activity.packageManager.getApplicationIcon(activity.packageName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || icon !is AdaptiveIconDrawable) {
            return icon.toBitmap(side, side)
        }
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bleed = side / 4
        for (layer in listOfNotNull(icon.background, icon.foreground)) {
            layer.setBounds(-bleed, -bleed, side + bleed, side + bleed)
            layer.draw(canvas)
        }
        return bitmap
    }

    private suspend fun runUpdateCheck() {
        when (val result = UpdateChecker.check(activity)) {
            is UpdateChecker.CheckResult.UpdateAvailable ->
                showUpdateAvailableDialog(result.tagName, result.downloadUrl)
            is UpdateChecker.CheckResult.UpToDate ->
                simpleDialog(R.string.update_not_found_title, activity.getString(R.string.update_not_found_message))
            is UpdateChecker.CheckResult.Error ->
                simpleDialog(R.string.update_error_title, result.message)
        }
    }

    private fun showUpdateAvailableDialog(tagName: String, downloadUrl: String) {
        ExpressiveDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(activity.getString(R.string.update_available_message, tagName))
            .setPositiveButton(R.string.update_download) { _, _ ->
                UpdateChecker.startDownload(activity, downloadUrl, tagName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun simpleDialog(titleRes: Int, message: String) {
        ExpressiveDialog.Builder(activity)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
