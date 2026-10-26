package com.github.kr328.clash

import com.github.kr328.clash.BaseActivity.Event
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.runtime.Composable
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.core.model.ConnectionSnapshot
import com.github.kr328.clash.design.compose.screen.ConnectionsScreen
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val ConnectionsJson = Json { coerceInputValues = true; ignoreUnknownKeys = true }

class ConnectionsActivity : SectionActivity() {
    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }

    override fun createSection() = ConnectionsSection(this, ::finish)
}

class ConnectionsSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {

    private val activeGroupsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<ConnectionGroup>>(emptyList())
    private val closedGroupsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<ConnectionGroup>>(emptyList())
    private val speedFlow = kotlinx.coroutines.flow.MutableStateFlow("")
    private val pausedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val cachingFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    private var lastSnapshot = ConnectionSnapshot()
    // The previous poll's byte counts, overall and per connection, and when it came:
    // the speeds are the difference over the time between two polls.
    private var lastPollAt = 0L
    private var lastUploadTotal = -1L
    private var lastDownloadTotal = -1L
    private var lastBytes: Map<String, Pair<Long, Long>> = emptyMap()
    private var connectionRates: Map<String, Pair<Long, Long>> = emptyMap()
    private var closedConnections: List<Connection> = emptyList()
    private val closedIds = mutableSetOf<String>()
    private var currentFilter = ""

    override suspend fun run(events: SharedFlow<Event>) = coroutineScope {
        launch {
            while (true) {
                try {
                    val json = withClash { queryConnections() }
                    val snapshot = ConnectionsJson.decodeFromString(
                        ConnectionSnapshot.serializer(), json
                    )
                    updateSnapshot(snapshot)
                } catch (_: Exception) {}
                delay(1000)
            }
        }
        events.collect {
            // No tunnel, no connections: the section closes.
            if (it == Event.ClashStop) close()
        }
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val active by activeGroupsFlow.collectAsStateWithLifecycle()
        val closed by closedGroupsFlow.collectAsStateWithLifecycle()
        val speed by speedFlow.collectAsStateWithLifecycle()
        val paused by pausedFlow.collectAsStateWithLifecycle()
        val caching by cachingFlow.collectAsStateWithLifecycle()
        ConnectionsScreen(
            activeGroups = active,
            closedGroups = closed,
            speed = speed,
            paused = paused,
            caching = caching,
            onBack = onBack,
            onTogglePause = { pausedFlow.value = !pausedFlow.value },
            onToggleCache = ::toggleCache,
            onKillAll = ::killAll,
            onFilterChange = ::filterChange,
            onOpenGroup = ::openAppConnections,
        )
    }

    private suspend fun updateSnapshot(snapshot: ConnectionSnapshot) {
        // Speeds from what moved since the last poll (also while paused, so resuming
        // does not show one long interval as a burst). The core reports byte totals.
        val now = android.os.SystemClock.elapsedRealtime()
        val seconds = (now - lastPollAt) / 1000.0
        val first = lastUploadTotal < 0 || seconds <= 0.0
        fun rate(current: Long, previous: Long): Long =
            if (first) 0 else ((current - previous).coerceAtLeast(0) / seconds).toLong()
        val totalUp = rate(snapshot.uploadTotal, lastUploadTotal)
        val totalDown = rate(snapshot.downloadTotal, lastDownloadTotal)
        // A connection opened since the last poll moved all of its bytes in that time.
        connectionRates = snapshot.connections.associate { c ->
            val (up, down) = lastBytes[c.id] ?: (0L to 0L)
            c.id to (rate(c.upload, up) to rate(c.download, down))
        }
        lastBytes = snapshot.connections.associate { it.id to (it.upload to it.download) }
        lastUploadTotal = snapshot.uploadTotal
        lastDownloadTotal = snapshot.downloadTotal
        lastPollAt = now

        if (pausedFlow.value) return

        val activeIds = snapshot.connections.map { it.id }.toSet()

        if (cachingFlow.value) {
            val newlyClosed = lastSnapshot.connections
                .filter { it.id !in activeIds && it.id !in closedIds }
            if (newlyClosed.isNotEmpty()) {
                closedIds.addAll(newlyClosed.map { it.id })
                closedConnections = (closedConnections + newlyClosed).takeLast(300)
                closedGroupsFlow.value = filterGroups(buildGroups(closedConnections))
            }
        }

        lastSnapshot = snapshot

        // The speed now; it was the session's byte totals with "/s" after them.
        speedFlow.value = "↑ ${totalUp.toBytesString()}/s  ↓ ${totalDown.toBytesString()}/s"

        activeGroupsFlow.value = filterGroups(buildGroups(snapshot.connections, live = true))
    }

    private fun toggleCache() {
        val enabled = !cachingFlow.value
        cachingFlow.value = enabled
        if (!enabled) {
            closedConnections = emptyList()
            closedIds.clear()
            closedGroupsFlow.value = emptyList()
        }
        Toast.makeText(
            activity,
            if (enabled) com.github.kr328.clash.design.R.string.connections_cache_enabled
            else com.github.kr328.clash.design.R.string.connections_cache_disabled,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun killAll() {
        activity.launch {
            try { withClash { closeAllConnections() } } catch (_: Exception) {}
        }
    }

    private fun filterChange(filter: String) {
        currentFilter = filter
        activity.launch {
            activeGroupsFlow.value = filterGroups(buildGroups(lastSnapshot.connections, live = true))
            closedGroupsFlow.value = filterGroups(buildGroups(closedConnections))
        }
    }

    private fun filterGroups(groups: List<ConnectionGroup>): List<ConnectionGroup> {
        if (currentFilter.isEmpty()) return groups
        return groups.filter {
            it.appName.contains(currentFilter, ignoreCase = true) ||
                it.packageName.contains(currentFilter, ignoreCase = true) ||
                it.connections.any { c -> matchesFilter(c, currentFilter) }
        }
    }

    private fun matchesFilter(c: Connection, q: String): Boolean {
        val m = c.metadata
        return listOf(
            m.host, m.destinationIP, m.sourceIP, m.process,
            m.sniffHost, m.remoteDestination, c.rule, c.rulePayload,
            c.chains.firstOrNull() ?: ""
        ).any { it.contains(q, ignoreCase = true) }
    }

    private suspend fun buildGroups(connections: List<Connection>, live: Boolean = false): List<ConnectionGroup> {
        val rates = connectionRates
        val pm = activity.packageManager
        val appPackage = activity.packageName
        return withContext(Dispatchers.Default) {
            connections
                .groupBy { c -> if (c.metadata.process.isEmpty() || c.metadata.process == appPackage) "" else c.metadata.process }
                .map { (pkg, conns) ->
                    val isPrizrak = pkg.isEmpty()
                    val icon = if (isPrizrak) null else runCatching {
                        pm.getApplicationInfo(pkg, 0).loadIcon(pm)
                    }.getOrNull()
                    val name = if (isPrizrak) activity.getString(com.github.kr328.clash.design.R.string.prizrak_core_name)
                    else runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrElse { pkg }

                    val upSpeed = if (live) conns.sumOf { rates[it.id]?.first ?: 0L } else 0L
                    val downSpeed = if (live) conns.sumOf { rates[it.id]?.second ?: 0L } else 0L

                    ConnectionGroup(
                        pkg, name, icon, conns, upSpeed, downSpeed,
                        uploadTotal = conns.sumOf { it.upload },
                        downloadTotal = conns.sumOf { it.download },
                        live = live,
                    )
                }
                // The busiest now first, then by what they moved.
                .sortedWith(
                    compareByDescending<ConnectionGroup> { it.packageName.isEmpty() }
                        .thenByDescending { it.uploadSpeed + it.downloadSpeed }
                        .thenByDescending { it.uploadTotal + it.downloadTotal }
                )
        }
    }

    private fun openAppConnections(group: ConnectionGroup, showClosed: Boolean) {
        val intent = AppConnectionsActivity::class.intent
        intent.putExtra(ConnectionsActivity.EXTRA_PACKAGE, group.packageName)
        intent.putExtra(AppConnectionsActivity.EXTRA_APP_NAME, group.appName)
        if (showClosed) {
            val closedJson = ConnectionsJson.encodeToString(
                ListSerializer(Connection.serializer()),
                group.connections
            )
            intent.putExtra(AppConnectionsActivity.EXTRA_CLOSED_JSON, closedJson)
        }
        activity.startActivity(intent)
    }
}
