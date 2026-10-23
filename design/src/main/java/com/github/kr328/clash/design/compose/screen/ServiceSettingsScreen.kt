package com.github.kr328.clash.design.compose.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SwitchPreference

/** What Service shows, read from the stores and the system by its section. */
data class ServiceState(
    /** The VPN comes back after the device restarts. */
    val autoRestart: Boolean = false,
    /** The app's screens are closed while it is in the background and the VPN runs. */
    val releaseUi: Boolean = false,
    /** The system does not hold the app back to save battery. */
    val batteryUnrestricted: Boolean = false,
)

/**
 * Settings → Device → Service: how the app keeps running — auto restart
 * after a reboot, the screens let go in the background and battery
 * restrictions.
 */
@Composable
fun ServiceSettingsScreen(
    state: ServiceState,
    onBack: (() -> Unit)?,
    onAutoRestart: (Boolean) -> Unit,
    onReleaseUi: (Boolean) -> Unit,
    onBattery: () -> Unit,
) {
    PreferenceScaffold(title = stringResource(R.string.service_settings), onBack = onBack) {
        item {
            SwitchPreference(
                title = stringResource(R.string.auto_restart),
                summary = stringResource(R.string.allow_clash_auto_restart),
                icon = R.drawable.ic_baseline_restore,
                checked = state.autoRestart,
                onCheckedChange = onAutoRestart,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.release_ui_background),
                summary = stringResource(R.string.release_ui_background_summary),
                icon = R.drawable.ic_baseline_memory,
                checked = state.releaseUi,
                onCheckedChange = onReleaseUi,
            )
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.battery_unrestricted),
                summary = stringResource(
                    if (state.batteryUnrestricted) R.string.battery_unrestricted_on else R.string.battery_unrestricted_off,
                ),
                icon = R.drawable.ic_baseline_battery_full,
                onClick = onBattery,
            )
        }
        footnote(R.string.battery_unrestricted_footer)
    }
}
