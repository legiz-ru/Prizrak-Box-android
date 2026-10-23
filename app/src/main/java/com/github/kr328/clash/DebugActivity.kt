package com.github.kr328.clash

import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.compose.screen.DebugScreen
import com.github.kr328.clash.design.compose.screen.DebugState
import com.github.kr328.clash.design.store.UiStore.Companion.mainActivityAlias
import com.github.kr328.clash.service.TemplateManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.GetContentCompat
import com.github.kr328.clash.util.applyDynamicShortcuts
import com.github.kr328.clash.util.showAgeKeyPairDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import com.github.kr328.clash.zashboard.ZashboardSession

/** Debug: ten taps on the version in About open it, as on iOS. */
class DebugActivity : BaseActivity() {
    private val serviceStore by lazy { ServiceStore(this) }
    private val stateFlow = MutableStateFlow(
        DebugState(
            sendHwid = true,
            tunStack = "mips",
            congestion = "",
            disconnectOnNodeSwitch = true,
            zashboard = false,
            hideIcon = false,
            hideRecents = false,
            running = false,
        ),
    )

    override suspend fun main() {
        read()

        setContent {
            val state by stateFlow.collectAsStateWithLifecycle()
            AppTheme {
                DebugScreen(
                    state = state,
                    onBack = { finish() },
                    onAgeKeyGenerator = { showAgeKeyPairDialog() },
                    onSendHwid = { uiStore.sendHwid = it; read() },
                    onMetaFeatures = { startActivity(MetaFeatureSettingsActivity::class.intent) },
                    onOverride = { startActivity(OverrideSettingsActivity::class.intent) },
                    onTunStack = { serviceStore.tunStackMode = it; read() },
                    onCongestion = { serviceStore.tunCongestionController = it; read() },
                    onDisconnectOnNodeSwitch = { serviceStore.disconnectOnNodeSwitch = it; read() },
                    onZashboard = { launch { setZashboard(it) } },
                    onPartialReset = { startActivity(PartialResetActivity::class.intent) },
                    onHideIcon = {
                        uiStore.hideAppIcon = it
                        onHideIconChange(it)
                        read()
                    },
                    onHideRecents = {
                        uiStore.hideFromRecents = it
                        read()
                        ApplicationObserver.createdActivities.forEach { activity -> activity.recreate() }
                    },
                    onCustomTemplate = { launch { selectCustomTemplate() } },
                )
            }
        }

        while (isActive) {
            when (events.receive()) {
                Event.ClashStart, Event.ClashStop, Event.ActivityStart -> read()
                else -> Unit
            }
        }
    }

    private fun read() {
        stateFlow.value = DebugState(
            sendHwid = uiStore.sendHwid,
            tunStack = serviceStore.tunStackMode,
            congestion = serviceStore.tunCongestionController,
            disconnectOnNodeSwitch = serviceStore.disconnectOnNodeSwitch,
            zashboard = serviceStore.zashboardEnabled,
            hideIcon = uiStore.hideAppIcon,
            hideRecents = uiStore.hideFromRecents,
            running = clashRunning,
        )
    }

    /**
     * Debug → Zashboard. On, after saying what changes, when anything does
     * beyond the switch: the controller settings of Override cleared, a
     * running VPN reloading its configuration.
     */
    private suspend fun setZashboard(on: Boolean) {
        if (on) {
            val clears = ZashboardSession.overrideHasController()
            if ((clears || clashRunning) && !confirmZashboard(clears)) return
        }
        ZashboardSession.setEnabled(this, on)
        read()
    }

    private suspend fun confirmZashboard(clears: Boolean): Boolean = suspendCancellableCoroutine { ctx ->
        val message = listOfNotNull(
            getString(R.string.zashboard_enable_clears).takeIf { clears },
            getString(R.string.zashboard_enable_reloads).takeIf { clashRunning },
        ).joinToString("\n\n")
        val dialog = ExpressiveDialog.Builder(this)
            .setTitle(R.string.zashboard_enable_title)
            .setMessage(message)
            .setPositiveButton(R.string.zashboard_turn_on) { _, _ -> if (ctx.isActive) ctx.resume(true) }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
        dialog.setOnDismissListener { if (ctx.isActive) ctx.resume(false) }
        ctx.invokeOnCancellation { dialog.dismiss() }
    }

    private fun onHideIconChange(hide: Boolean) {
        // Hiding the icon also disables any copy dragged onto the home screen —
        // a pinned shortcut isn't dynamic, so it survives removeAllDynamicShortcuts()
        // on its own. Un-hiding restores both.
        applyDynamicShortcuts(hide)
        packageManager.setComponentEnabledSetting(
            mainActivityAlias,
            if (hide) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private suspend fun selectCustomTemplate() {
        val uri = startActivityForResult(GetContentCompat(), "*/*") ?: return
        val content = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        if (!content.isNullOrBlank()) {
            val customFile = filesDir.resolve("custom_template.yaml")
            withContext(Dispatchers.IO) {
                customFile.writeText(content, Charsets.UTF_8)
            }
            TemplateManager.setCustomTemplatePath(this, customFile.absolutePath)
        }
    }
}
