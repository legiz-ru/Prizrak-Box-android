package com.github.kr328.clash

import androidx.activity.compose.setContent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.OverrideSettingsScreen
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import kotlinx.coroutines.channels.Channel
import com.github.kr328.clash.zashboard.ZashboardSession
import com.github.kr328.clash.zashboard.clearController
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OverrideSettingsActivity : BaseActivity() {
    private val resetRequests = Channel<Unit>(Channel.CONFLATED)

    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }

        // Debug → Zashboard holds the controller: its settings stay cleared.
        val zashboard = ZashboardSession.isEnabled(this)

        defer {
            if (ZashboardSession.isEnabled(this)) configuration.clearController()
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        setContent {
            AppTheme {
                OverrideSettingsScreen(
                    configuration = configuration,
                    onBack = { finish() },
                    onReset = { resetRequests.trySend(Unit) },
                    zashboard = zashboard,
                )
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                resetRequests.onReceive {
                    if (requestResetConfirm()) {
                        defer {
                            withClash {
                                clearOverride(Clash.OverrideSlot.Persist)
                            }
                        }
                        finish()
                    }
                }
            }
        }
    }

    private suspend fun requestResetConfirm(): Boolean {
        return suspendCancellableCoroutine { ctx ->
            val dialog = ExpressiveDialog.Builder(this)
                .setTitle(R.string.reset_override_settings)
                .setMessage(R.string.reset_override_settings_message)
                .setDestructiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .show()

            dialog.setOnDismissListener {
                if (!ctx.isCompleted) ctx.resume(false)
            }

            ctx.invokeOnCancellation { dialog.dismiss() }
        }
    }
}
