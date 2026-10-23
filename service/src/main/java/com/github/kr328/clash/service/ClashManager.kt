package com.github.kr328.clash.service

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.service.data.Selection
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.ILogObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendOverrideChanged
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel

class ClashManager(private val context: Context) : IClashManager,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val store = ServiceStore(context)
    private var logReceiver: ReceiveChannel<LogMessage>? = null

    override fun queryTunnelState(): TunnelState {
        return Clash.queryTunnelState()
    }

    override fun queryTrafficTotal(): Long {
        return Clash.queryTrafficTotal()
    }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        return Clash.queryGroupNames(excludeNotSelectable)
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        return Clash.queryGroup(name, proxySort)
    }

    override fun queryConfiguration(): UiConfiguration {
        return Clash.queryConfiguration()
    }

    override fun queryRules(): String {
        return Clash.queryRulesJson()
    }

    override fun queryProviders(): ProviderList {
        return ProviderList(Clash.queryProviders())
    }

    override fun dumpRuleProviderToText(name: String, outputPath: String): String {
        return Clash.dumpRuleProviderToText(name, outputPath)
    }

    override fun queryRuleProviderFilePath(name: String): String {
        return Clash.queryRuleProviderFilePath(name) ?: ""
    }

    override fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride {
        return Clash.queryOverride(slot)
    }

    override fun patchSelector(group: String, name: String): Boolean {
        // What the group was before: only a real switch closes anything.
        val before = if (store.disconnectOnNodeSwitch) {
            runCatching { Clash.queryGroup(group, ProxySort.Default) }.getOrNull()
        } else {
            null
        }

        return Clash.patchSelector(group, name).also {
            val current = store.activeProfile ?: return@also

            if (it) {
                SelectionDao().setSelected(Selection(current, group, name))
                if (before != null) closeConnectionsThrough(group, before, name)
            } else {
                SelectionDao().removeSelected(current, group)
            }
        }
    }

    /**
     * Debug → Auto Disconnect, as Zashboard does it: the connections going
     * through [group] closed, so apps reconnect through the new node. Not for
     * the node already chosen, and never for LoadBalance or Smart groups —
     * they spread connections themselves, no single node was "switched".
     */
    private fun closeConnectionsThrough(group: String, before: ProxyGroup, chosen: String) {
        if (before.now == chosen) return
        if (before.type.equals("LoadBalance", ignoreCase = true) || before.type.equals("Smart", ignoreCase = true)) {
            Log.i("Auto disconnect: $group is ${before.type}, nothing closed")
            return
        }
        runCatching {
            val through = Clash.queryConnections().connections.filter { group in it.chains }
            through.forEach { Clash.closeConnection(it.id) }
            Log.i("Auto disconnect: $group ${before.now} → $chosen, closed ${through.size} connections")
        }.onFailure { Log.w("Auto disconnect: $group failed", it) }
    }

    override fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride) {
        Clash.patchOverride(slot, configuration)

        context.sendOverrideChanged()
    }

    override fun clearOverride(slot: Clash.OverrideSlot) {
        Clash.clearOverride(slot)
    }

    override suspend fun healthCheck(group: String) {
        return Clash.healthCheck(group).await()
    }

    override suspend fun healthCheckProxy(group: String, name: String) {
        return Clash.healthCheckProxy(group, name).await()
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) {
        return Clash.updateProvider(type, name).await()
    }

    override fun queryConnections(): String {
        return com.github.kr328.clash.core.bridge.Bridge.nativeQueryConnections()
    }

    override fun closeConnection(id: String): Boolean {
        return Clash.closeConnection(id)
    }

    override fun closeAllConnections() {
        com.github.kr328.clash.core.bridge.Bridge.nativeCloseAllConnections()
    }

    override fun setLogObserver(observer: ILogObserver?) {
        synchronized(this) {
            logReceiver?.apply {
                cancel()

                Clash.forceGc()
            }

            if (observer != null) {
                logReceiver = Clash.subscribeLogcat().also { c ->
                    launch {
                        try {
                            while (isActive) {
                                observer.newItem(c.receive())
                            }
                        } catch (e: CancellationException) {
                            // intended behavior
                            // ignore
                        } catch (e: Exception) {
                            Log.w("UI crashed", e)
                        } finally {
                            withContext(NonCancellable) {
                                c.cancel()

                                Clash.forceGc()
                            }
                        }
                    }
                }
            }
        }
    }
}
