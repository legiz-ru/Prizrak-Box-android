package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.SingleChoiceDialog
import com.github.kr328.clash.design.compose.component.SwitchPreference

/** The state of the Debug screen, read from the stores by its activity. */
data class DebugState(
    val sendHwid: Boolean,
    val tunStack: String,
    val congestion: String,
    val disconnectOnNodeSwitch: Boolean,
    /** Debug → Zashboard: the core's controller for the dashboard in Settings → Active Tunnel. */
    val zashboard: Boolean = false,
    val hideIcon: Boolean,
    val hideRecents: Boolean,
    /** The VPN is up: the stack and its controller change only while it is down. */
    val running: Boolean,
)

/**
 * Debug, opened by ten taps on the version in About, as on iOS: tools and
 * settings that should not sit in plain sight — the core's own settings
 * (Meta Features, Override, the network stack), the subscription's HWID and
 * the app's hidden switches.
 */
@Composable
fun DebugScreen(
    state: DebugState,
    onBack: () -> Unit,
    onAgeKeyGenerator: () -> Unit,
    onSendHwid: (Boolean) -> Unit,
    onMetaFeatures: () -> Unit,
    onOverride: () -> Unit,
    onTunStack: (String) -> Unit,
    onCongestion: (String) -> Unit,
    onDisconnectOnNodeSwitch: (Boolean) -> Unit,
    onZashboard: (Boolean) -> Unit,
    onPartialReset: () -> Unit,
    onHideIcon: (Boolean) -> Unit,
    onHideRecents: (Boolean) -> Unit,
    onCustomTemplate: () -> Unit,
) {
    var stackDialog by remember { mutableStateOf(false) }
    var congestionDialog by remember { mutableStateOf(false) }

    val stacks = listOf(
        "system" to stringResource(R.string.tun_stack_system),
        "gvisor" to stringResource(R.string.tun_stack_gvisor),
        "mixed" to stringResource(R.string.tun_stack_mixed),
        "mips" to stringResource(R.string.tun_stack_mips),
    )
    val controllers = listOf(
        "" to stringResource(R.string.congestion_default),
        "cubic" to "CUBIC",
        "reno" to "Reno",
        "bbr" to "BBR",
        "bbr3" to "BBR3",
    )

    PreferenceScaffold(title = stringResource(R.string.debug), onBack = onBack) {
        item {
            PreferenceRow(
                title = stringResource(R.string.age_keypair_generate),
                summary = stringResource(R.string.age_keypair_generate_desc),
                icon = R.drawable.ic_baseline_key,
                onClick = onAgeKeyGenerator,
            )
        }

        category(R.string.debug_subscription)
        item {
            SwitchPreference(
                title = stringResource(R.string.send_hwid_title),
                summary = stringResource(R.string.send_hwid_desc),
                icon = R.drawable.ic_baseline_fingerprint,
                checked = state.sendHwid,
                onCheckedChange = onSendHwid,
            )
        }

        category(R.string.debug_core)
        item {
            PreferenceRow(
                title = stringResource(R.string.meta_features),
                icon = R.drawable.ic_baseline_meta,
                onClick = onMetaFeatures,
            )
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.override),
                icon = R.drawable.ic_baseline_extension,
                onClick = onOverride,
            )
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.tun_stack_mode),
                summary = stacks.firstOrNull { it.first == state.tunStack }?.second,
                icon = R.drawable.ic_baseline_stack,
                enabled = !state.running,
                onClick = { stackDialog = true },
            )
        }
        if (state.tunStack == "mips") {
            item {
                PreferenceRow(
                    title = stringResource(R.string.congestion_controller),
                    summary = controllers.firstOrNull { it.first == state.congestion }?.second,
                    icon = R.drawable.ic_baseline_speed,
                    enabled = !state.running,
                    onClick = { congestionDialog = true },
                )
            }
        }
        footnote(R.string.debug_stack_footer)
        item {
            SwitchPreference(
                title = stringResource(R.string.auto_disconnect),
                icon = R.drawable.ic_baseline_swap_vert,
                checked = state.disconnectOnNodeSwitch,
                onCheckedChange = onDisconnectOnNodeSwitch,
            )
        }
        footnote(R.string.auto_disconnect_footer)

        // The core's controller on 127.0.0.1 for the dashboard (Settings → Active Tunnel → Zashboard).
        item {
            SwitchPreference(
                title = "Zashboard",
                icon = R.drawable.ic_zashboard,
                checked = state.zashboard,
                onCheckedChange = onZashboard,
            )
        }
        footnote(R.string.zashboard_footer)

        item {
            PreferenceRow(
                title = stringResource(R.string.partial_reset),
                icon = R.drawable.ic_baseline_checklist,
                onClick = onPartialReset,
            )
        }
        footnote(R.string.partial_reset_footer)

        category(R.string.debug_app)
        item {
            SwitchPreference(
                title = stringResource(R.string.hide_app_icon_title),
                summary = stringResource(R.string.hide_app_icon_desc),
                icon = R.drawable.ic_baseline_hide,
                checked = state.hideIcon,
                onCheckedChange = onHideIcon,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.hide_from_recents_title),
                summary = stringResource(R.string.hide_from_recents_desc),
                icon = R.drawable.ic_baseline_view_list,
                checked = state.hideRecents,
                onCheckedChange = onHideRecents,
            )
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.custom_template),
                summary = stringResource(R.string.custom_template_desc),
                icon = R.drawable.ic_baseline_attach_file,
                onClick = onCustomTemplate,
            )
        }
    }

    if (stackDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.tun_stack_mode),
            options = stacks,
            selected = state.tunStack,
            onSelect = onTunStack,
            onDismiss = { stackDialog = false },
        )
    }
    if (congestionDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.congestion_controller),
            options = controllers,
            selected = state.congestion,
            onSelect = onCongestion,
            onDismiss = { congestionDialog = false },
        )
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 64.dp, end = 20.dp, bottom = 8.dp),
    )
}
