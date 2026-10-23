package com.github.kr328.clash

import com.github.kr328.clash.BaseActivity.Event
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.runtime.Composable
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.ProviderItem
import com.github.kr328.clash.design.compose.screen.ProvidersScreen
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class ProvidersActivity : SectionActivity() {
    override fun createSection() = ProvidersSection(this, ::finish)
}

class ProvidersSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {
    private val providersFlow = MutableStateFlow<List<ProviderItem>>(emptyList())
    private val loadingFlow = MutableStateFlow(true)
    private val tickFlow = MutableStateFlow(0)

    override suspend fun run(events: SharedFlow<Event>) = coroutineScope {
        load()
        // "Updated N ago" counts on, once a minute while the screen is seen.
        launch {
            while (true) {
                delay(TimeUnit.MINUTES.toMillis(1))
                if (activity.activityStarted) tickFlow.value++
            }
        }
        events.collect {
            if (it == Event.ProfileLoaded) load()
        }
    }

    private suspend fun load() {
        val providers = runCatching { withClash { queryProviders().sorted() } }.getOrDefault(emptyList())
        providersFlow.value = providers.map { ProviderItem(it, it.updatedAt, false) }
        loadingFlow.value = false
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val items by providersFlow.collectAsStateWithLifecycle()
        val loading by loadingFlow.collectAsStateWithLifecycle()
        val tick by tickFlow.collectAsStateWithLifecycle()
        ProvidersScreen(
            providers = items,
            loading = loading,
            tick = tick,
            onBack = onBack,
            onOpen = ::openDetail,
            onUpdateAll = ::updateAll,
        )
    }

    private fun openDetail(provider: Provider) {
        activity.startActivity(
            ProviderDetailActivity::class.intent.putExtra(
                ProviderDetailActivity.EXTRA_PROVIDER, provider
            )
        )
    }

    private fun updateAll() {
        providersFlow.value.forEachIndexed { index, item ->
            if (item.provider.vehicleType != Provider.VehicleType.Inline && !item.updating) {
                updateOne(index, item.provider)
            }
        }
    }

    private fun updateOne(index: Int, provider: Provider) {
        setItem(index) { it.copy(updating = true) }
        activity.launch {
            try {
                withClash { updateProvider(provider.type, provider.name) }
                setItem(index) { it.copy(updating = false, updatedAt = System.currentTimeMillis()) }
            } catch (e: Exception) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.format_update_provider_failure, provider.name, e.message),
                    Toast.LENGTH_LONG,
                ).show()
                setItem(index) { it.copy(updating = false) }
            }
        }
    }

    private fun setItem(index: Int, transform: (ProviderItem) -> ProviderItem) {
        providersFlow.value = providersFlow.value.toMutableList().also { list ->
            if (index in list.indices) list[index] = transform(list[index])
        }
    }
}
