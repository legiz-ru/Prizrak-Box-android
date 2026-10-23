package com.github.kr328.clash

import com.github.kr328.clash.BaseActivity.Event
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.core.model.Rule
import com.github.kr328.clash.design.compose.screen.RulesScreen
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val RulesJson = Json { ignoreUnknownKeys = true }

class RulesActivity : SectionActivity() {
    override fun createSection() = RulesSection(this, ::finish)
}

class RulesSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {
    private val rulesFlow = MutableStateFlow<List<Rule>>(emptyList())
    private val loadingFlow = MutableStateFlow(true)
    private val errorFlow = MutableStateFlow<Pair<String, String>?>(null)

    override suspend fun run(events: SharedFlow<Event>) {
        loadRules()
        events.collect {
            if (it == Event.ProfileLoaded) loadRules()
        }
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val rules by rulesFlow.collectAsStateWithLifecycle()
        val loading by loadingFlow.collectAsStateWithLifecycle()
        val error by errorFlow.collectAsStateWithLifecycle()
        RulesScreen(
            rules = rules,
            loading = loading,
            error = error,
            onBack = onBack,
        )
    }

    private suspend fun loadRules() {
        loadingFlow.value = true
        errorFlow.value = null
        try {
            val json = withClash { queryRules() }
            val rules = RulesJson.decodeFromString(ListSerializer(Rule.serializer()), json)
            rulesFlow.value = rules
            errorFlow.value = null
        } catch (e: Exception) {
            errorFlow.value = activity.getString(com.github.kr328.clash.design.R.string.no_rules) to
                (e.message ?: e.javaClass.simpleName)
        } finally {
            loadingFlow.value = false
        }
    }
}
