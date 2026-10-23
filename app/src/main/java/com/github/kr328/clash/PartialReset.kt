package com.github.kr328.clash

import android.app.NotificationManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import androidx.core.os.LocaleListCompat
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.IconLoader
import com.github.kr328.clash.design.compose.theme.Appearance
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.subscription.SUBSCRIPTION_ALERT_CHANNEL
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.logsDir
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.zashboard.ZashboardSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug → Partial Reset, as on iOS: what can go back to a fresh install, one
 * piece at a time. Everything else stays as it is. Android has no VPN
 * configuration of its own to remove, no widgets and keeps no age key.
 */
object PartialReset {
    enum class Item(val title: Int, val detail: Int, val icon: Int) {
        NotificationPrompt(R.string.reset_notification_prompt, R.string.reset_notification_prompt_detail, R.drawable.ic_baseline_notifications),
        ReminderHistory(R.string.reset_reminder_history, R.string.reset_reminder_history_detail, R.drawable.ic_baseline_restore),
        NotificationSettings(R.string.reset_notification_settings, R.string.reset_notification_settings_detail, R.drawable.ic_baseline_notifications),
        Appearance(R.string.reset_appearance, R.string.reset_appearance_detail, R.drawable.ic_baseline_palette),
        Sorting(R.string.reset_sorting, R.string.reset_sorting_detail, R.drawable.ic_baseline_sort),
        Network(R.string.reset_network, R.string.reset_network_detail, R.drawable.ic_baseline_dns),
        Core(R.string.reset_core, R.string.reset_core_detail, R.drawable.ic_baseline_meta),
        Hwid(R.string.reset_hwid, R.string.reset_hwid_detail, R.drawable.ic_baseline_fingerprint),
        LogoCache(R.string.reset_logo_cache, R.string.reset_logo_cache_detail, R.drawable.ic_baseline_image),
        SessionLogs(R.string.session_logs, R.string.reset_session_logs_detail, R.drawable.ic_baseline_assignment),
        CoreCache(R.string.reset_core_cache, R.string.reset_core_cache_detail, R.drawable.ic_mdi_database),
        Downloads(R.string.reset_downloads, R.string.reset_downloads_detail, R.drawable.ic_baseline_cloud_download),
        Zashboard(R.string.reset_zashboard, R.string.reset_zashboard_detail, R.drawable.ic_zashboard),
        Profiles(R.string.reset_profiles, R.string.reset_profiles_detail, R.drawable.ic_baseline_view_list);

        /** The running core holds cache.db, the providers and the databases. */
        fun isAvailable(running: Boolean): Boolean = !(running && (this == CoreCache || this == Downloads))
    }

    enum class Group(val title: Int, val items: List<Item>) {
        Hints(R.string.reset_group_hints, listOf(Item.NotificationPrompt, Item.ReminderHistory, Item.NotificationSettings)),
        Settings(R.string.settings, listOf(Item.Appearance, Item.Sorting, Item.Network, Item.Core, Item.Hwid)),
        Data(R.string.reset_group_data, listOf(Item.LogoCache, Item.SessionLogs, Item.CoreCache, Item.Downloads, Item.Zashboard)),
        Profiles(R.string.reset_group_profiles, listOf(Item.Profiles)),
    }

    /** Files bundled with the app that the core keeps beside cache.db; put back from the APK after a reset. */
    private val bundledGeoFiles = listOf("geoip.metadb", "geosite.dat", "ASN.mmdb", "BundleMRS.7z")

    private const val CORE_CACHE = "cache.db"
    private const val LOGO_CACHE = "remote_icons"

    /** Space an item takes on disk; null for settings. */
    fun size(context: Context, item: Item): Long? = when (item) {
        Item.LogoCache -> logoFiles(context).sumOf(::sizeOf)
        Item.SessionLogs -> sizeOf(context.logsDir)
        Item.CoreCache -> sizeOf(context.clashDir.resolve(CORE_CACHE))
        Item.Downloads -> downloadedFiles(context).sumOf(::sizeOf)
        Item.Zashboard -> zashboardFolders(context).sumOf(::sizeOf)
        else -> null
    }

    /**
     * Resets [items]. A running VPN starts again after its network or core
     * settings went back, as on iOS; one whose profiles went stops.
     */
    suspend fun reset(context: Context, items: Set<Item>) {
        val ui = UiStore(context)
        val service = ServiceStore(context)
        val running = Remote.broadcasts.clashRunning

        if (Item.Profiles in items) {
            if (running) stopAndWait(context)
            withProfile { queryAll().forEach { delete(it.uuid) } }
            ui.resetProxySorting()
        }

        if (Item.NotificationPrompt in items) {
            ui.notificationsAsked = false
        }
        if (Item.ReminderHistory in items) {
            withContext(Dispatchers.IO) {
                context.importedDir.listFiles().orEmpty().forEach { it.resolve("alerts.json").delete() }
            }
            context.getSystemService<NotificationManager>()?.let { manager ->
                manager.activeNotifications
                    .filter { it.notification.channelId == SUBSCRIPTION_ALERT_CHANNEL }
                    .forEach { manager.cancel(it.tag, it.id) }
            }
        }
        if (Item.NotificationSettings in items) {
            service.dynamicNotification = true
            service.notifySubscriptionProgress = true
            service.notifySubscriptionErrors = true
            service.notifySubscriptionAlerts = true
        }

        if (Item.Appearance in items) {
            ui.resetAppearance()
        }
        if (Item.Sorting in items) {
            ui.resetProxySorting()
            ui.resetAccessControlSorting()
        }
        if (Item.Network in items) {
            service.resetConnectionsOnNetworkChange = true
            service.bypassPrivateNetwork = true
            service.dnsHijacking = true
            service.systemProxy = true
            service.allowBypass = false
            service.allowIpv6 = false
            service.accessControlMode = AccessControlMode.AcceptAll
            service.accessControlPackages = emptySet()
            ui.enableVpn = true
        }
        if (Item.Core in items) {
            service.tunStackMode = "mips"
            service.tunCongestionController = ""
            service.disconnectOnNodeSwitch = true
            // Debug → Zashboard and its secret: a new one next time.
            service.zashboardEnabled = false
            service.zashboardSecret = ""
            ZashboardSession.close()
            // Override and Meta Features: the core's own settings on Android.
            withClash { clearOverride(Clash.OverrideSlot.Persist) }
        }
        if (Item.Hwid in items) {
            ui.sendHwid = true
        }

        withContext(Dispatchers.IO) {
            if (Item.LogoCache in items) {
                logoFiles(context).forEach { it.deleteRecursively() }
            }
            if (Item.SessionLogs in items) {
                // The log being written now stays while Logcat records.
                val live = if (LogcatService.running) {
                    context.logsDir.listFiles().orEmpty().maxByOrNull { it.lastModified() }
                } else {
                    null
                }
                context.logsDir.listFiles().orEmpty().filter { it != live }.forEach { it.deleteRecursively() }
            }
            if (Item.CoreCache in items && !running) {
                context.clashDir.resolve(CORE_CACHE).delete()
            }
            if (Item.Downloads in items && !running) {
                downloadedFiles(context).forEach { it.deleteRecursively() }
                restoreBundledGeoFiles(context)
            }
        }
        if (Item.LogoCache in items) IconLoader.clear()
        if (Item.Zashboard in items) ZashboardSession.clearData(context)

        // The tunnel reads its network and stack at start.
        if (running && Item.Profiles !in items && (Item.Network in items || Item.Core in items)) {
            stopAndWait(context)
            context.startClashService()
        }

        // Last: the screens are made again (the language, the theme), which
        // ends the one this reset runs on.
        if (Item.Appearance in items) {
            Appearance.refresh(context.applicationContext, runCatching { withProfile { queryActive() } }.getOrNull())
            if (AppCompatDelegate.getApplicationLocales().isEmpty) {
                ApplicationObserver.createdActivities.forEach { it.recreate() }
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        }
    }

    private suspend fun stopAndWait(context: Context) {
        context.stopClashService()
        repeat(50) {
            if (!Remote.broadcasts.clashRunning) return
            delay(100)
        }
    }

    /** Logos cached on disk and every profile's group icons. */
    private fun logoFiles(context: Context): List<File> =
        listOf(context.cacheDir.resolve(LOGO_CACHE)) +
            context.importedDir.listFiles().orEmpty().map { it.resolve("icons") }

    /**
     * What the core downloaded: every profile's rule and proxy providers, and
     * everything in its home but cache.db — the databases, updated or not
     * (the bundled ones are put back).
     */
    private fun downloadedFiles(context: Context): List<File> =
        context.importedDir.listFiles().orEmpty().map { it.resolve("providers") } +
            context.clashDir.listFiles().orEmpty().filter { it.name != CORE_CACHE }

    private fun restoreBundledGeoFiles(context: Context) {
        context.clashDir.mkdirs()
        for (name in bundledGeoFiles) {
            runCatching {
                context.assets.open(name).use { input ->
                    context.clashDir.resolve(name).outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    /** Where the WebView keeps the app's web data — Zashboard's alone, the only page in the app. */
    private fun zashboardFolders(context: Context): List<File> {
        val data = context.dataDir
        return listOf(data.resolve("app_webview"), context.cacheDir.resolve("WebView"))
    }

    private fun sizeOf(file: File): Long =
        if (file.isFile) file.length() else file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** "12.4 MB". */
    fun formattedSize(context: Context, bytes: Long): String =
        android.text.format.Formatter.formatShortFileSize(context, bytes)
}
