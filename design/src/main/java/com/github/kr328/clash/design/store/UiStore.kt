package com.github.kr328.clash.design.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.compose.background.HomeBackgroundStyle
import com.github.kr328.clash.design.compose.theme.AccentChoice
import com.github.kr328.clash.design.model.DarkMode

class UiStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    private val store = Store(preferences.asStoreProvider())

    var enableVpn: Boolean by store.boolean(
        key = "enable_vpn",
        defaultValue = true
    )

    /** Settings → Interface → Theme: dark on Android TV, the system's elsewhere, unless chosen. */
    var darkMode: DarkMode by store.enum(
        key = "dark_mode",
        defaultValue = if (TvUtils.isTv(context)) DarkMode.ForceDark else DarkMode.Auto,
        values = DarkMode.values()
    )

    var hideAppIcon: Boolean by store.boolean(
        key = "hide_app_icon",
        defaultValue = with(Companion) {
            context.packageManager.getComponentEnabledSetting(
                context.mainActivityAlias
            ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    )

    var hideFromRecents: Boolean by store.boolean(
        key = "hide_from_recents",
        defaultValue = false,
    )

    var proxyExcludeNotSelectable by store.boolean(
        key = "proxy_exclude_not_selectable",
        defaultValue = false,
    )

    var proxyLine: Int by store.int(
        key = "proxy_line",
        defaultValue = 2
    )

    var proxySort: ProxySort by store.enum(
        key = "proxy_sort",
        defaultValue = ProxySort.Default,
        values = ProxySort.values()
    )

    /**
     * Per-profile proxy sort. Remembered separately for each profile (keyed by its UUID)
     * so switching/restarting profiles keeps each profile's own sort. Falls back to the
     * legacy global [proxySort] when the profile has no stored value (smooth migration),
     * and to the global value entirely when [profileUuid] is empty (no active profile).
     */
    fun getProxySort(profileUuid: String): ProxySort {
        if (profileUuid.isEmpty()) return proxySort
        val name = store.provider.getString(proxySortKey(profileUuid), proxySort.name)
        return ProxySort.values().find { it.name == name } ?: ProxySort.Default
    }

    fun setProxySort(profileUuid: String, sort: ProxySort) {
        if (profileUuid.isEmpty()) {
            proxySort = sort
            return
        }
        store.provider.setString(proxySortKey(profileUuid), sort.name)
    }

    private fun proxySortKey(profileUuid: String) = "proxy_sort_$profileUuid"

    var proxyLastGroup: String by store.string(
        key = "proxy_last_group",
        defaultValue = ""
    )

    var accessControlSort: AppInfoSort by store.enum(
        key = "access_control_sort",
        defaultValue = AppInfoSort.Label,
        values = AppInfoSort.values(),
    )

    var accessControlReverse: Boolean by store.boolean(
        key = "access_control_reverse",
        defaultValue = false
    )

    var accessControlSystemApp: Boolean by store.boolean(
        key = "access_control_system_app",
        defaultValue = false,
    )

    var sendHwid: Boolean by store.boolean(
        key = "send_hwid",
        defaultValue = true,
    )

    var delayDisplayDots: Boolean by store.boolean(
        key = "delay_display_dots",
        defaultValue = true,
    )

    /** Settings → Visual → Accent Color: an [AccentChoice] name; the profile's colour by default. */
    var accentChoice: AccentChoice by store.enum(
        key = "accent_choice",
        defaultValue = AccentChoice.Profile,
        values = AccentChoice.entries.toTypedArray(),
    )

    /** Personalisation: the colour saved there, "RRGGBB". */
    var accentCustomHex: String by store.string(
        key = "accent_custom_hex",
        defaultValue = AccentChoice.DEFAULT_CUSTOM_HEX,
    )

    /** Personalisation → Palette: grid or spectrum, as last left. */
    var accentPaletteSpectrum: Boolean by store.boolean(
        key = "accent_palette_spectrum",
        defaultValue = false,
    )

    /** Settings → Visual → Home Background: a [HomeBackgroundStyle] id; Topography by default. */
    var homeBackground: String by store.string(
        key = "home_background",
        defaultValue = HomeBackgroundStyle.DEFAULT.id,
    )

    /**
     * Settings → Visual → Profile Theme: whether the active profile's
     * `pxa-theme` applies. One switch for the whole app, on by default.
     */
    var useProfileTheme: Boolean by store.boolean(
        key = "use_profile_theme",
        defaultValue = true,
    )

    /**
     * Whether the app has already asked for POST_NOTIFICATIONS once (Android
     * 13+). Not "granted" — set on an explicit "Allow" AND on an explicit
     * "Not now", cleared never. The point is to ask exactly once, at the first
     * connect attempt, instead of blindly on every cold start.
     */
    /**
     * Settings → Service → Release UI in Background: while the VPN runs, the
     * app's screens close when it goes to the background (the VPN lives in
     * its own process), so the app holds less memory.
     */
    var releaseUiInBackground: Boolean by store.boolean(
        key = "release_ui_in_background",
        defaultValue = false,
    )

    var notificationsAsked: Boolean by store.boolean(
        key = "notifications_asked",
        defaultValue = false,
    )

    /** Debug → Partial Reset → Language, theme…: Settings → Visual as on a fresh install. */
    fun resetAppearance() {
        preferences.edit()
            .remove("dark_mode")
            .remove("delay_display_dots")
            .remove("proxy_line")
            .remove("accent_choice")
            .remove("accent_custom_hex")
            .remove("accent_palette_spectrum")
            .remove("home_background")
            .remove("use_profile_theme")
            .apply()
    }

    /** Debug → Partial Reset → Sorting: node order (every profile's own) and the last opened group. */
    fun resetProxySorting() {
        val editor = preferences.edit().remove("proxy_sort").remove("proxy_last_group")
        preferences.all.keys.filter { it.startsWith("proxy_sort_") }.forEach(editor::remove)
        editor.apply()
    }

    /** Debug → Partial Reset → Sorting: the order of the app list in access control. */
    fun resetAccessControlSorting() {
        preferences.edit()
            .remove("access_control_sort")
            .remove("access_control_reverse")
            .remove("access_control_system_app")
            .apply()
    }

    companion object {
        private const val PREFERENCE_NAME = "ui"

        val Context.mainActivityAlias: ComponentName
            get() = ComponentName(this, "${packageName}.MainActivityAlias")
    }
}
