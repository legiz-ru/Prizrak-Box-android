package com.github.kr328.clash

import com.github.kr328.clash.BaseActivity.Event
import com.github.kr328.clash.remote.Remote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import android.widget.Toast
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.NetworkSettingsScreen
import com.github.kr328.clash.service.store.ServiceStore

class NetworkSettingsActivity : SectionActivity() {
    override fun createSection() = NetworkSection(this, ::finish)
}

class NetworkSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {
    private val srvStore = ServiceStore(activity)
    /** The options lock while the tunnel runs; the screen is made again when it starts or stops. */
    private val runningFlow = MutableStateFlow(Remote.broadcasts.clashRunning)

    override suspend fun run(events: SharedFlow<Event>) {
        if (runningFlow.value) {
            Toast.makeText(activity, R.string.options_unavailable, Toast.LENGTH_LONG).show()
        }
        events.collect {
            when (it) {
                Event.ClashStart, Event.ClashStop, Event.ServiceRecreated ->
                    runningFlow.value = Remote.broadcasts.clashRunning
                else -> Unit
            }
        }
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val running by runningFlow.collectAsStateWithLifecycle()
        key(running) {
            NetworkSettingsScreen(
                uiStore = activity.uiStore,
                srvStore = srvStore,
                running = running,
                onBack = onBack,
                onAccessControlPackages = {
                    activity.startActivity(AccessControlActivity::class.intent)
                },
            )
        }
    }
}
