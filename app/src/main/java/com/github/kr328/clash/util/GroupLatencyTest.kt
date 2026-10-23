package com.github.kr328.clash.util

import com.github.kr328.clash.core.model.ProxyGroup
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** At most this many nodes of a group measured at once, as on iOS. */
private const val CONCURRENT_TESTS = 6

/** Types with nothing to measure. */
private val UNTESTABLE = setOf("Direct", "Reject", "RejectDrop", "Pass", "Compatible")

/**
 * A group's latency test, as on iOS: every node measured on its own, at most
 * [CONCURRENT_TESTS] at a time, and each row shows its result as soon as it
 * is in — the whole group's check answered only once the slowest node had.
 * [testing] holds the nodes still being measured and, until the last one is,
 * the group's own name (its test button keeps turning). [refresh] reads the
 * results back; called at most every 300 ms while the test runs and once at
 * the end. A Smart group is checked as a whole (its nodes are its providers').
 */
suspend fun testGroupLatency(
    groupName: String,
    group: ProxyGroup?,
    testing: MutableStateFlow<Set<String>>,
    refresh: suspend () -> Unit,
) {
    val members = group?.proxies.orEmpty().filter { it.type !in UNTESTABLE }.map { it.name }.toSet()
    testing.value = testing.value + groupName + members
    try {
        if (group == null || group.type == "Smart" || members.isEmpty()) {
            withClash { healthCheck(groupName) }
            refresh()
            return
        }
        val permits = Semaphore(CONCURRENT_TESTS)
        val refreshLock = Mutex()
        var refreshQueued = false
        coroutineScope {
            for (name in members) {
                launch {
                    permits.withPermit {
                        runCatching { withClash { healthCheckProxy(groupName, name) } }
                    }
                    testing.value = testing.value - name
                    // Results read back together: one query for the nodes that finished close by.
                    val queue = refreshLock.withLock { (!refreshQueued).also { refreshQueued = true } }
                    if (queue) launch {
                        delay(300)
                        refreshLock.withLock { refreshQueued = false }
                        runCatching { refresh() }
                    }
                }
            }
        }
        refresh()
    } finally {
        testing.value = testing.value - members - groupName
    }
}
