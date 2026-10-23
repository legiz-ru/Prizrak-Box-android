package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import java.util.*

class ServiceStore(context: Context) {
    private val store = Store(
        PreferenceProvider
            .createSharedPreferencesFromContext(context)
            .asStoreProvider()
    )

    var activeProfile: UUID? by store.typedString(
        key = "active_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    var bypassPrivateNetwork: Boolean by store.boolean(
        key = "bypass_private_network",
        defaultValue = true
    )

    var accessControlMode: AccessControlMode by store.enum(
        key = "access_control_mode",
        defaultValue = AccessControlMode.AcceptAll,
        values = AccessControlMode.values()
    )

    var accessControlPackages by store.stringSet(
        key = "access_control_packages",
        defaultValue = emptySet()
    )

    var dnsHijacking by store.boolean(
        key = "dns_hijacking",
        defaultValue = true
    )

    var systemProxy by store.boolean(
        key = "system_proxy",
        defaultValue = true
    )

    var allowBypass by store.boolean(
        key = "allow_bypass",
        defaultValue = false
    )

    var allowIpv6 by store.boolean(
        key = "allow_ipv6",
        defaultValue = false
    )

    /**
     * Whether live connections should be dropped on a network change
     * (Wi-Fi <-> LTE and back).
     *
     * A connection opened over a vanished interface is dead either way: the only
     * difference is whether the app learns about it right away or after the OS
     * timeout — a minute and more. All that time the user sees "the internet is
     * there but nothing loads".
     *
     * On by default. Turning it off makes sense for someone downloading large
     * files in an app without resume support: there a break is more noticeable
     * than the wait. The interface cache and the DNS connections are reset in
     * any case — that tears nothing down for the user, and without it names stop
     * resolving.
     */
    var resetConnectionsOnNetworkChange by store.boolean(
        key = "reset_connections_on_network_change",
        defaultValue = true
    )

    /**
     * Debug → Auto Disconnect (Zashboard's "auto disconnect on node switch"):
     * choosing another node in a group closes the connections going through
     * that group, so apps reconnect through the new node at once.
     */
    var disconnectOnNodeSwitch by store.boolean(
        key = "disconnect_on_node_switch",
        defaultValue = true
    )

    /** Debug → Stack Mode: mipstack by default, as on iOS; system, gvisor or mixed otherwise. */
    var tunStackMode by store.string(
        key = "tun_stack_mode",
        defaultValue = "mips"
    )

    /**
     * mipstack's TCP congestion controller (Debug → Congestion Controller):
     * cubic, reno, bbr or bbr3; "" for the core's default (CUBIC). The other
     * stacks ignore it.
     */
    var tunCongestionController by store.string(
        key = "tun_congestion_controller",
        defaultValue = ""
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    /**
     * The transient "Updating <profile>" notification shown while a
     * subscription update is in flight (service/clash/module unrelated —
     * posted directly by ProfileWorker, not tied to any foreground-service
     * contract, so unlike the tunnel status notification this one really can
     * be turned off). Off by default: a subscription refreshes in the
     * background every few hours, and a notification for each pass is more
     * noise than signal for most people.
     */
    var notifySubscriptionProgress by store.boolean(
        key = "notify_subscription_progress",
        defaultValue = false
    )

    /**
     * The "Update failed" notification, posted when a scheduled or manual
     * subscription update errors out. Off by default: a single failed refresh
     * is usually transient (the panel was briefly unreachable, the network
     * changed mid-request) and resolves itself on the next scheduled attempt,
     * so alerting on every one of them is more noise than signal. Someone who
     * wants to know can turn it on here.
     */
    var notifySubscriptionErrors by store.boolean(
        key = "notify_subscription_errors",
        defaultValue = false
    )

    /**
     * Expiry and traffic reminders — "expires in N days", "expired",
     * "N% of traffic used" — thresholds set by the panel via the
     * `notify-expire-days`/`notify-traffic-percent` headers (see
     * SubscriptionAlerts). One switch, not three: all three are the same
     * underlying thing — the subscription's state needs attention — and the
     * panel already controls which of them fire at all by what thresholds it
     * sends (or "off" for none). Splitting the local switch further on top of
     * that would be control over a distinction nobody asked for.
     */
    var notifySubscriptionAlerts by store.boolean(
        key = "notify_subscription_alerts",
        defaultValue = true
    )

    /**
     * Debug → Zashboard: the core also opens its controller on
     * [ZASHBOARD_HOST]:[ZASHBOARD_PORT] for the dashboard in Settings →
     * Active Tunnel, the only controller while it is on.
     */
    var zashboardEnabled by store.boolean(
        key = "zashboard_enabled",
        defaultValue = false
    )

    /** Zashboard's secret: made the first time it is turned on, then kept. */
    var zashboardSecret by store.string(
        key = "zashboard_secret",
        defaultValue = ""
    )

    /** [zashboardSecret], made now if there is none yet. */
    fun makeZashboardSecretIfNeeded(): String {
        zashboardSecret.takeIf { it.isNotEmpty() }?.let { return it }
        val bytes = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
        val secret = bytes.joinToString("") { "%02x".format(it) }
        zashboardSecret = secret
        return secret
    }

    companion object {
        const val ZASHBOARD_HOST = "127.0.0.1"
        const val ZASHBOARD_PORT = 9090
    }
}
