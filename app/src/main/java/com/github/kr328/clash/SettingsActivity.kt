package com.github.kr328.clash

import com.github.kr328.clash.design.compose.screen.VisualPage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.compose.screen.SettingsDestination
import com.github.kr328.clash.design.compose.screen.SettingsInfo
import com.github.kr328.clash.design.compose.screen.SettingsNavTarget
import com.github.kr328.clash.design.compose.screen.SettingsScreen
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.github.kr328.clash.util.appLanguageSummary
import com.github.kr328.clash.zashboard.ZashboardSession

class SettingsActivity : BaseActivity() {
    private val running = MutableStateFlow(clashRunning)
    private val hasProfilesFlow = MutableStateFlow(true)
    private val infoFlow = MutableStateFlow(SettingsInfo())
    /** The section beside the list (a wide window), marked in it; kept when the screen is made again. */
    // Lazy: what a recreation hands back is there only once the activity is attached.
    private val selectedFlow by lazy { retainedStateFlow<SettingsDestination?>("settings.section") { null } }

    private val tv by lazy { TvUtils.isTv(this) }

    override suspend fun main() {
        // Android TV: the window is see-through (SettingsTheme), the screen
        // before stays behind the side panel.
        if (tv) {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            // With alpha: an opaque surface drew the dim over the screen behind as solid black.
            window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            // The screen behind dimmed by the system, as under the TV's own settings
            // (a dim drawn in the window itself came out solid black).
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.55f)
        }
        // setContent() first, data after: querying profiles is a suspend
        // IPC call, and running it before setContent() left the activity's
        // window visible with nothing drawn into it yet — a blank flash
        // between window-shown and first Compose frame. Filling in the flow
        // once content is already on screen just recomposes, same as it
        // already does on ProfileChanged/ActivityStart below.
        setContent {
            val isRunning by running.collectAsStateWithLifecycle()
            val hasProfiles by hasProfilesFlow.collectAsStateWithLifecycle()
            val info by infoFlow.collectAsStateWithLifecycle()
            val selected by selectedFlow.collectAsStateWithLifecycle()

            AppTheme {
                SettingsScreen(
                    // The side rail is Android TV's only.
                    expanded = com.github.kr328.clash.common.util.TvUtils.isTv(this),
                    clashRunning = isRunning,
                    onOpen = ::openDestination,
                    onNavigate = ::navigate,
                    onToggleStatus = { toggleStatus() },
                    hasProfiles = hasProfiles,
                    info = info,
                    selected = selected,
                    onSelect = ::select,
                    pane = { destination ->
                        // One section at a time: made when chosen, its work stops when another is.
                        val section = remember(destination) { sectionFor(destination) }
                        if (section != null) {
                            LaunchedEffect(section) { section.run(eventFlow) }
                            // Android TV: in the side panel, with its way back to the list.
                            section.Content(onBack = if (tv) ({ selectedFlow.value = null }) else null)
                        }
                    },
                )
            }
        }

        if (tv) {
            // After setContent: every view between the window and Compose without a background.
            window.decorView.post {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                var view: android.view.View? = findViewById(android.R.id.content)
                while (view != null) {
                    view.background = null
                    view = view.parent as? android.view.View
                }
                (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))?.background = null
            }
        }

        val appVersion = withContext(Dispatchers.IO) {
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        }
        infoFlow.value = SettingsInfo(
            appVersion = appVersion,
            coreVersion = BuildConfig.CORE_VERSION,
            languageSummary = appLanguageSummary(),
            zashboard = ZashboardSession.isEnabled(this),
        )
        hasProfilesFlow.value = withProfile { queryAll().isNotEmpty() }

        while (isActive) {
            when (events.receive()) {
                Event.ClashStart, Event.ClashStop -> {
                    running.value = clashRunning
                    // The tunnel's own sections leave the list with it, and the right column with them.
                    if (!clashRunning && selectedFlow.value in TUNNEL_SECTIONS) selectedFlow.value = null
                }
                Event.ActivityStart -> {
                    infoFlow.value = infoFlow.value.copy(
                        languageSummary = appLanguageSummary(),
                        zashboard = ZashboardSession.isEnabled(this),
                    )
                    hasProfilesFlow.value = withProfile { queryAll().isNotEmpty() }
                }
                Event.ProfileChanged, Event.ProfileLoaded ->
                    hasProfilesFlow.value = withProfile { queryAll().isNotEmpty() }
                else -> Unit
            }
        }
    }

    /** A section picked in a wide window: beside the list when it can be, its own screen otherwise. */
    private fun select(destination: SettingsDestination?) {
        if (destination == null) {
            selectedFlow.value = null
            return
        }
        if (sectionFor(destination) == null) {
            openDestination(destination)
            return
        }
        // Visual opens at its first page, as when it is opened on a phone.
        if (destination == SettingsDestination.Visual) {
            retainedStateFlow("visual.page") { VisualPage.Main }.value = VisualPage.Main
        }
        selectedFlow.value = destination
    }

    /** The section a destination shows beside the list; null where it opens elsewhere (the system's language screen, live logs). */
    private fun sectionFor(destination: SettingsDestination): SettingsSection? {
        val close = { selectedFlow.value = null }
        return when (destination) {
            SettingsDestination.Visual -> VisualSection(this, close)
            SettingsDestination.Notifications -> NotificationSection(this, close)
            SettingsDestination.Network -> NetworkSection(this, close)
            SettingsDestination.Service -> ServiceSection(this, close)
            SettingsDestination.Rules -> RulesSection(this, close)
            SettingsDestination.Providers -> ProvidersSection(this, close)
            SettingsDestination.Connections -> ConnectionsSection(this, close)
            SettingsDestination.Zashboard -> null
            SettingsDestination.Logs -> if (LogcatService.running) null else LogsSection(this, close)
            SettingsDestination.About -> HelpSection(this, close)
        }
    }

    private fun openDestination(destination: SettingsDestination) {
        val target = when (destination) {
            SettingsDestination.Visual -> VisualSettingsActivity::class.intent
            SettingsDestination.Notifications -> NotificationSettingsActivity::class.intent
            SettingsDestination.Network -> NetworkSettingsActivity::class.intent
            SettingsDestination.Service -> ServiceSettingsActivity::class.intent
            SettingsDestination.Rules -> RulesActivity::class.intent
            SettingsDestination.Providers -> ProvidersActivity::class.intent
            SettingsDestination.Connections -> ConnectionsActivity::class.intent
            SettingsDestination.Zashboard -> return ZashboardSession.open(this)
            SettingsDestination.Logs ->
                if (LogcatService.running) LogcatActivity::class.intent else LogsActivity::class.intent
            SettingsDestination.About -> HelpActivity::class.intent
        }
        startActivity(target)
    }

    private fun navigate(target: SettingsNavTarget) {
        when (target) {
            // Android TV: Home's Servers tab.
            SettingsNavTarget.Servers -> {
                startActivity(
                    MainActivity::class.intent
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra(MainActivity.EXTRA_TAB, SettingsNavTarget.Servers.name),
                )
                finish()
                overridePendingTransition(0, 0)
            }
            SettingsNavTarget.Home -> {
                startActivity(
                    MainActivity::class.intent
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
                finish()
                overridePendingTransition(0, 0)
            }
            // Android TV: Profiles is a screen of its own over Home (Home's tabs do not show it,
            // and Home came up with the focus still on Settings). Back from it goes Home.
            SettingsNavTarget.Profiles -> if (tv) {
                startActivities(
                    arrayOf(
                        MainActivity::class.intent
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION),
                        ProfilesActivity::class.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    ),
                )
                finish()
                overridePendingTransition(0, 0)
            } else startActivity(
                MainActivity::class.intent
                    .putExtra(MainActivity.EXTRA_TAB, SettingsNavTarget.Profiles.name)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
            SettingsNavTarget.Settings -> Unit // already here
        }
    }

    private fun toggleStatus() {
        if (clashRunning) {
            stopClashService()
        } else {
            launch { toggleClashOn() }
        }
    }

    private suspend fun toggleClashOn() {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) {
            Toast.makeText(
                this,
                com.github.kr328.clash.design.R.string.no_profile_selected,
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val vpnRequest = startClashService()
        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )
                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                com.github.kr328.clash.design.R.string.unable_to_start_vpn,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Mirrors the side-drawer condition used across the View-based screens. */
    private fun useDrawerNav(): Boolean {
        if (TvUtils.isTv(this)) return true
        val cfg = resources.configuration
        return cfg.smallestScreenWidthDp >= 600 &&
            cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private companion object {
        val TUNNEL_SECTIONS = setOf(
            SettingsDestination.Rules,
            SettingsDestination.Providers,
            SettingsDestination.Connections,
        )
    }
}
