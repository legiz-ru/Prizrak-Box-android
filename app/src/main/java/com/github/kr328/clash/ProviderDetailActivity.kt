package com.github.kr328.clash

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.compose.screen.ProviderDetailScreen
import android.widget.Toast
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProviderDetailActivity : BaseActivity() {
    companion object {
        const val EXTRA_PROVIDER = "provider"
    }

    private val rulesFlow = MutableStateFlow<List<String>>(emptyList())
    private val loadingFlow = MutableStateFlow(true)
    private val updatingFlow = MutableStateFlow(false)

    override suspend fun main() {
        @Suppress("DEPRECATION")
        val provider = intent.getParcelableExtra<Provider>(EXTRA_PROVIDER)!!

        setContent {
            AppTheme {
                val rules by rulesFlow.collectAsStateWithLifecycle()
                val loading by loadingFlow.collectAsStateWithLifecycle()
                val updating by updatingFlow.collectAsStateWithLifecycle()
                ProviderDetailScreen(
                    title = provider.name,
                    rules = rules,
                    loading = loading,
                    updating = updating,
                    searchEnabled = provider.type == Provider.Type.Rule,
                    updateEnabled = provider.vehicleType != Provider.VehicleType.Inline,
                    onBack = { finish() },
                    onUpdate = { update(provider) },
                )
            }
        }

        launch {
            rulesFlow.value = loadProviderRules(provider, cacheDir)
            loadingFlow.value = false
        }

        while (isActive) {
            when (events.receive()) {
                Event.ClashStop -> finish()
                else -> Unit
            }
        }
    }

    private fun update(provider: Provider) {
        launch {
            updatingFlow.value = true
            try {
                withClash { updateProvider(provider.type, provider.name) }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ProviderDetailActivity,
                    e.message ?: e.javaClass.simpleName,
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                try {
                    rulesFlow.value = loadProviderRules(provider, cacheDir)
                } finally {
                    updatingFlow.value = false
                }
            }
        }
    }
}
