package com.github.kr328.clash

import android.app.ActivityManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.isAllowForceDarkCompat
import com.github.kr328.clash.common.compat.isLightNavigationBarCompat
import com.github.kr328.clash.common.compat.isLightStatusBarsCompat
import com.github.kr328.clash.common.compat.isSystemBarsTranslucentCompat
import com.github.kr328.clash.core.bridge.ClashException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.kr328.clash.zashboard.ZashboardOverlay
import com.github.kr328.clash.zashboard.ZashboardSession
import com.github.kr328.clash.design.compose.theme.AppTheme
import com.github.kr328.clash.design.compose.theme.Appearance
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.DayNight
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.ActivityResultLifecycle
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.github.kr328.clash.design.R

abstract class BaseActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    Broadcasts.Observer {

    internal val uiStore by lazy { UiStore(this) }
    protected val events = Channel<Event>(Channel.UNLIMITED)
    /**
     * The same events for anyone else on this screen: a settings section shown
     * beside the list (a wide window) follows them as its own window would.
     */
    internal val eventFlow = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    /** Each update's result: the profile, and why it failed (null when it did not). */
    protected val profileUpdates = MutableSharedFlow<Pair<UUID?, String?>>(extraBufferCapacity = 32)
    internal var activityStarted: Boolean = false
    protected val clashRunning: Boolean
        get() = Remote.broadcasts.clashRunning

    private var defer: suspend () -> Unit = {}
    private var deferRunning = false
    private val nextRequestKey = AtomicInteger(0)
    private var dayNight: DayNight = DayNight.Day

    private class RetainedBag {
        val values = HashMap<String, Any?>()
    }

    // Plain platform API, no ViewModel dependency needed: an object handed
    // back verbatim across a configuration-change recreation (rotation,
    // locale/font-scale change, …), never across a real finish. Lazy because
    // getLastCustomNonConfigurationInstance() is only meaningful once
    // Activity.attach() has run — a raw property initializer would read it
    // too early, before the previous instance's retained bag is wired up.
    private val retainedBag: RetainedBag by lazy {
        @Suppress("DEPRECATION")
        (lastCustomNonConfigurationInstance as? RetainedBag) ?: RetainedBag()
    }

    @Suppress("DEPRECATION")
    override fun onRetainCustomNonConfigurationInstance(): Any = retainedBag

    /**
     * A field made in a property initializer asks for its value before the
     * activity is attached, when what the previous instance retained is not
     * there yet: it got a fresh one. Here, attached, those fresh values take
     * the retained ones (a state flow's value, an array's contents); keys
     * nobody asked for yet are kept for later.
     */
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun restoreRetained() {
        val previous = lastCustomNonConfigurationInstance as? RetainedBag ?: return
        if (previous === retainedBag) return
        for ((key, old) in previous.values) {
            when (val current = retainedBag.values[key]) {
                null -> retainedBag.values[key] = old
                is MutableStateFlow<*> -> if (old is MutableStateFlow<*>) {
                    (current as MutableStateFlow<Any?>).value = old.value
                }
                is BooleanArray -> if (old is BooleanArray) old.copyInto(current)
                else -> retainedBag.values[key] = old
            }
        }
    }

    /**
     * Ephemeral, unsaved UI state (a draft edit, a navigation stack, a set of
     * picks) that would otherwise reset to [initial] every time this screen's
     * Activity instance is recreated by a configuration change — reuses the
     * same object across the recreation instead. Call once per field, from a
     * property initializer, with a [key] unique within the Activity; the
     * value survives rotation but not a real finish() / process death.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <T> retainedValue(key: String, initial: () -> T): T {
        return retainedBag.values.getOrPut(key, initial) as T
    }

    /** [retainedValue] specialised for the common MutableStateFlow(default) field pattern. */
    internal fun <T> retainedStateFlow(key: String, initial: () -> T): MutableStateFlow<T> {
        return retainedValue(key) { MutableStateFlow(initial()) }
    }

    protected abstract suspend fun main()

    private fun sendEvent(event: Event) {
        events.trySend(event)
        eventFlow.tryEmit(event)
    }

    fun defer(operation: suspend () -> Unit) {
        this.defer = operation
    }

    suspend fun <I, O> startActivityForResult(
        contracts: ActivityResultContract<I, O>,
        input: I,
    ): O = withContext(Dispatchers.Main) {
        val requestKey = nextRequestKey.getAndIncrement().toString()

        ActivityResultLifecycle().use { lifecycle, start ->
            suspendCoroutine { c ->
                activityResultRegistry.register(requestKey, lifecycle, contracts) {
                    c.resume(it)
                }.apply { start() }.launch(input)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreRetained()
        applyDayNight()

        // Apply excludeFromRecents setting to all app tasks.
        checkNotNull(getSystemService<ActivityManager>()).appTasks.forEach { task ->
            task.setExcludeFromRecents(uiStore.hideFromRecents)
        }

        // A profile's theme may turn the app light or dark: every screen
        // made the other way is made again.
        launch {
            Appearance.state.collect {
                if (queryDayNight() != dayNight) recreate()
            }
        }

        launch {
            main()
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        Remote.broadcasts.addObserver(this)
        sendEvent(Event.ActivityStart)
        refreshAppearance()
        // The VPN stopped while the app was away: Zashboard went with it.
        if (!clashRunning) ZashboardSession.close()
    }

    override fun onStop() {
        super.onStop()
        activityStarted = false
        Remote.broadcasts.removeObserver(this)
        sendEvent(Event.ActivityStop)
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    override fun finish() {
        if (deferRunning) return
        deferRunning = true

        launch {
            try {
                defer()
            } finally {
                withContext(NonCancellable) {
                    super.finish()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (queryDayNight(newConfig) != dayNight) {
            ApplicationObserver.createdActivities.forEach {
                it.recreate()
            }
        }
    }

    open fun shouldDisplayHomeAsUpEnabled(): Boolean {
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        this.onBackPressed()
        return true
    }

    override fun onProfileChanged() {
        sendEvent(Event.ProfileChanged)
        refreshAppearance()
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        sendEvent(Event.ProfileUpdateCompleted)
        profileUpdates.tryEmit(uuid to null)
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        sendEvent(Event.ProfileUpdateFailed)
        profileUpdates.tryEmit(uuid to (reason ?: ""))
    }

    override fun onProfileLoaded() {
        sendEvent(Event.ProfileLoaded)
        refreshAppearance()
    }

    override fun onServiceRecreated() {
        sendEvent(Event.ServiceRecreated)
    }

    override fun onStarted() {
        sendEvent(Event.ClashStart)
    }

    override fun onStopped(cause: String?) {
        sendEvent(Event.ClashStop)
        // Zashboard has nothing to talk to any more.
        ZashboardSession.close()

        if (cause != null && activityStarted) {
            android.widget.Toast.makeText(
                this,
                ClashException(cause).message ?: cause,
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Settings → Visual → Theme, or the active profile's `pxa-theme` mode. */
    private fun queryDayNight(config: Configuration = resources.configuration): DayNight {
        val systemDark = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (Appearance.isDark(uiStore, systemDark)) DayNight.Night else DayNight.Day
    }

    /** Whether this activity was made dark (Settings → Visual → Theme, or the profile's). */
    internal val isNightTheme: Boolean
        get() = dayNight == DayNight.Night

    /** Zashboard's round menu over this screen while the dashboard is minimized. */
    protected open val showsZashboardMenu: Boolean
        get() = true

    /**
     * The screen's Compose theme: light or dark as this activity was made, with
     * the app's accent; Zashboard's round menu over it while it is minimized.
     */
    @Composable
    protected fun AppTheme(content: @Composable () -> Unit) {
        AppTheme(darkTheme = dayNight == DayNight.Night) {
            if (showsZashboardMenu) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    ZashboardOverlay(this@BaseActivity)
                }
            } else {
                content()
            }
        }
    }

    /** The accent and the profile's theme worked out again for the active profile. */
    internal fun refreshAppearance() {
        launch {
            val active = try {
                withProfile { queryActive() }
            } catch (e: Exception) {
                return@launch
            }
            Appearance.refresh(applicationContext, active)
        }
    }

    private fun applyDayNight(config: Configuration = resources.configuration) {
        val dayNight = queryDayNight(config)
        when (dayNight) {
            DayNight.Night -> theme.applyStyle(R.style.AppThemeDark, true)
            DayNight.Day -> theme.applyStyle(R.style.AppThemeLight, true)
        }

        window.isAllowForceDarkCompat = false
        window.isSystemBarsTranslucentCompat = true
        
        window.statusBarColor = resolveThemedColor(android.R.attr.statusBarColor)
        window.navigationBarColor = resolveThemedColor(android.R.attr.navigationBarColor)

        if (Build.VERSION.SDK_INT >= 23) {
            window.isLightStatusBarsCompat = resolveThemedBoolean(android.R.attr.windowLightStatusBar)
        }

        if (Build.VERSION.SDK_INT >= 27) {
            window.isLightNavigationBarCompat = resolveThemedBoolean(android.R.attr.windowLightNavigationBar)
        }

        this.dayNight = dayNight
    }

    enum class Event {
        ServiceRecreated,
        ActivityStart,
        ActivityStop,
        ClashStop,
        ClashStart,
        ProfileLoaded,
        ProfileChanged,
        ProfileUpdateCompleted,
        ProfileUpdateFailed,
    }
}
