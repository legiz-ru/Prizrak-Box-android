package com.github.kr328.clash.zashboard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.doOnLayout
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import com.github.kr328.clash.common.log.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.geometry.Offset
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The built-in Zashboard (Debug → Zashboard, then Settings → Active Tunnel →
 * Zashboard), as on iOS: shown over the app ([ZashboardActivity]), minimized
 * behind the round menu button with the page kept alive, or closed — the web
 * view released, and with it its memory.
 */
object ZashboardSession {
    enum class State { Closed, Shown, Minimized }

    private val stateFlow = MutableStateFlow(State.Closed)
    val state: StateFlow<State> = stateFlow.asStateFlow()

    /**
     * Where the round menu button was dragged to, in the window's pixels; null
     * for its corner. One place on every screen, as it is one button.
     */
    val buttonOffset = MutableStateFlow<Offset?>(null)

    /** The page while it is open, shown or minimized; null once closed. */
    private var webView: WebView? = null

    /** The web view's context: the activity showing it, the application while minimized. */
    private var host: MutableContextWrapper? = null

    /** Debug → Zashboard is on. */
    fun isEnabled(context: Context): Boolean = ServiceStore(context).zashboardEnabled

    /** Over the app, the page made the first time and kept while minimized. */
    fun open(context: Context) {
        if (!isEnabled(context)) return
        val intent = Intent(context, ZashboardActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** [ZashboardActivity] is coming up: shown from now on, the page attached when its view is made. */
    internal fun markShown() {
        stateFlow.value = State.Shown
    }

    /** The page, for [activity]'s window: made now, or the one kept while minimized. */
    internal fun attach(activity: Activity): WebView {
        stateFlow.value = State.Shown
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            host?.baseContext = activity
            return view
        }
        val wrapper = MutableContextWrapper(activity)
        val view = make(wrapper, activity)
        host = wrapper
        webView = view
        return view
    }

    /** The page leaves [activity]'s window (minimized, or the screen made again); it lives on. */
    internal fun detach(activity: Activity) {
        val view = webView ?: return
        if (host?.baseContext !== activity) return
        (view.parent as? ViewGroup)?.removeView(view)
        host?.baseContext = activity.applicationContext
    }

    /** Behind the round button, the page kept: [ZashboardActivity] leaves on this. */
    fun minimize() {
        if (stateFlow.value == State.Shown) stateFlow.value = State.Minimized
    }

    /** Gone, and the page with it. */
    fun close() {
        if (stateFlow.value == State.Closed) return
        stateFlow.value = State.Closed
        webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
        host = null
    }

    /** Runs [script] in the page while it is open; [result] gets its JSON value. */
    internal fun evaluate(script: String, result: (String?) -> Unit) {
        val view = webView ?: return result(null)
        view.evaluateJavascript(script, result)
    }

    /**
     * Debug → Zashboard turned on or off. On, its secret is made the first
     * time, and the controller settings of Override are cleared — while it is
     * on, Zashboard's controller is the only one. Writing Override reloads a
     * running configuration, now with or without that controller. Off, the
     * page goes: it has nothing to talk to any more.
     */
    suspend fun setEnabled(context: Context, on: Boolean) {
        val store = ServiceStore(context)
        if (on) store.makeZashboardSecretIfNeeded()
        store.zashboardEnabled = on
        withClash {
            val configuration = queryOverride(Clash.OverrideSlot.Persist)
            if (on) configuration.clearController()
            patchOverride(Clash.OverrideSlot.Persist, configuration)
        }
        if (!on) close()
    }

    /** Override sets something of the controller: turning Zashboard on clears it. */
    suspend fun overrideHasController(): Boolean =
        withClash { queryOverride(Clash.OverrideSlot.Persist).clearController() }

    /** Its settings, backends and cache — all the app's web data, Zashboard's alone. */
    fun clearData(context: Context) {
        close()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        WebView(context.applicationContext).apply {
            clearCache(true)
            destroy()
        }
    }

    /** The setup page with the core's controller filled in: the dashboard adds it as its backend and opens. */
    private fun setupUrl(context: Context): String {
        val store = ServiceStore(context)
        val query = listOf(
            "disableUpgradeCore" to "1",
            // Android runs the TUN, not the core's switch.
            "disableTunMode" to "1",
            "http" to "true",
            "hostname" to ServiceStore.ZASHBOARD_HOST,
            "port" to ServiceStore.ZASHBOARD_PORT.toString(),
            "secret" to store.makeZashboardSecretIfNeeded(),
        ).joinToString("&") { (key, value) -> "$key=${Uri.encode(value)}" }
        return ZashboardFiles.INDEX_URL + "#/setup?" + query
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun make(context: Context, activity: Activity): WebView {
        val files = ZashboardFiles(activity.applicationContext, languageCode(activity))
        return WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // The page is the app's own https origin; the controller is plain http on loopback.
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // The page's console in the app's log: its errors are found there.
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.i("Zashboard: ${message.messageLevel()} ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    files.serve(request.url)


                // The dashboard stays in the app; a link out of it (its GitHub, a rule's source) opens in the browser.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host == ZashboardFiles.HOST) return false
                    runCatching {
                        view.context.startActivity(
                            Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    return true
                }
            }
            // Once it has a size: the page takes its first measurements then.
            val url = setupUrl(activity)
            doOnLayout { loadUrl(url) }
        }
    }

    /** The app's language in the dashboard's codes. */
    private fun languageCode(context: Context): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val language = if (locales.isEmpty) context.resources.configuration.locales[0].language else locales[0]?.language
        return when (language) {
            "ru" -> "ru-RU"
            "zh" -> "zh-CN"
            else -> "en-US"
        }
    }
}

/**
 * Override's controller settings, which Zashboard takes over while it is on:
 * External Controller, its TLS, CORS and the secret. True when any was set.
 */
fun ConfigurationOverride.clearController(): Boolean {
    val had = externalController != null || externalControllerTLS != null || secret != null ||
        externalControllerCors.allowOrigins != null || externalControllerCors.allowPrivateNetwork != null
    externalController = null
    externalControllerTLS = null
    secret = null
    externalControllerCors.allowOrigins = null
    externalControllerCors.allowPrivateNetwork = null
    return had
}
