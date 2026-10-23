package com.github.kr328.clash

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.BaseActivity.Event
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.ServiceSettingsScreen
import com.github.kr328.clash.design.compose.screen.ServiceState
import com.github.kr328.clash.util.XiaomiAutoStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow

class ServiceSettingsActivity : SectionActivity() {
    override fun createSection() = ServiceSection(this, ::finish)
}

/**
 * Settings → Device → Service: auto restart, the screens let go in the
 * background and battery restrictions.
 */
class ServiceSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {
    private val stateFlow = MutableStateFlow(read())

    override suspend fun run(events: SharedFlow<Event>) {
        // Back from the system's battery or autostart screen: what changed there shows.
        events.collect { if (it == Event.ActivityStart) stateFlow.value = read() }
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val state by stateFlow.collectAsStateWithLifecycle()
        ServiceSettingsScreen(
            state = state,
            onBack = onBack,
            onAutoRestart = ::setAutoRestart,
            onReleaseUi = { activity.uiStore.releaseUiInBackground = it; stateFlow.value = read() },
            onBattery = ::requestUnrestrictedBattery,
        )
    }

    private fun read() = ServiceState(
        autoRestart = activity.packageManager.getComponentEnabledSetting(RestartReceiver::class.componentName) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        releaseUi = activity.uiStore.releaseUiInBackground,
        batteryUnrestricted = activity.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(activity.packageName) == true,
    )

    /** The receiver that brings the VPN back after the device restarts. */
    private fun setAutoRestart(on: Boolean) {
        activity.packageManager.setComponentEnabledSetting(
            RestartReceiver::class.componentName,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        stateFlow.value = read()
        // MIUI / HyperOS deliver no boot broadcast without their own
        // Autostart switch: turning this on sends the user there.
        if (on && XiaomiAutoStart.isXiaomi && XiaomiAutoStart.state(activity) != XiaomiAutoStart.State.Enabled) {
            Toast.makeText(activity, R.string.xiaomi_autostart_hint, Toast.LENGTH_LONG).show()
            XiaomiAutoStart.openSettings(activity)
        }
    }

    /** The system's request to stop optimizing the app's battery use; its list where the request is not offered. */
    @SuppressLint("BatteryLife")
    private fun requestUnrestrictedBattery() {
        if (stateFlow.value.batteryUnrestricted) {
            Toast.makeText(activity, R.string.battery_unrestricted_already, Toast.LENGTH_SHORT).show()
            return
        }
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
        runCatching { activity.startActivity(request) }.onFailure {
            runCatching { activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
}
