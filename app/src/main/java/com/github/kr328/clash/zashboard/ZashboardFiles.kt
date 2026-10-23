package com.github.kr328.clash.zashboard

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * https://appassets.androidplatform.net/zashboard/… → the files of the
 * bundled dashboard (assets/zashboard in the APK). Nothing outside it; any
 * other address on that host is not found.
 */
internal class ZashboardFiles(private val context: Context, private val languageCode: String) {
    fun serve(url: Uri): WebResourceResponse? {
        if (url.scheme != "https" || url.host != HOST) return null
        val path = url.path.orEmpty()
        if (!path.startsWith("/$ROOT/")) return notFound()
        val relative = path.removePrefix("/$ROOT/").ifEmpty { "index.html" }
        if (relative.split('/').any { it == ".." || it == "." || it.isEmpty() }) return notFound()

        val bytes = when (relative) {
            "index.html" -> withStartup(read("index.html") ?: return notFound())
            // No service worker: the page is the app's, served fresh by it, as on iOS.
            "registerSW.js" -> ByteArray(0)
            else -> read(relative) ?: return notFound()
        }
        return WebResourceResponse(mimeType(relative), "utf-8", ByteArrayInputStream(bytes)).apply {
            responseHeaders = mapOf("Cache-Control" to "no-cache")
        }
    }

    private fun read(relative: String): ByteArray? = try {
        context.assets.open("$ROOT/$relative").use { it.readBytes() }
    } catch (e: IOException) {
        null
    }

    /**
     * What runs before the dashboard: its height ([APP_HEIGHT]), and its
     * first language, the app's — on its own it reads navigator.language,
     * "ru" here, which it does not know ("ru-RU"), and falls back to English.
     * Its own choice later stands.
     */
    private fun withStartup(html: ByteArray): ByteArray {
        val script = "<script>if (localStorage.getItem('config/language') === null) " +
            "localStorage.setItem('config/language', '$languageCode');" +
            APP_HEIGHT + "</script>"
        return String(html, Charsets.UTF_8).replaceFirst("<head>", "<head>$script").toByteArray()
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun mimeType(relative: String): String = when (val extension = relative.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "webmanifest" -> "application/manifest+json"
        "svg" -> "image/svg+xml"
        "wasm" -> "application/wasm"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    companion object {
        /**
         * The page is as tall as its window, by `var(--app-height, 100dvh)`; in
         * Android's WebView 100dvh can stay at the size the view had when the
         * page started (0 before layout). The window's own height instead,
         * kept as it changes (the keyboard, a rotation, a fold).
         */
        private const val APP_HEIGHT = "(function () {" +
            "function set() { document.documentElement.style.setProperty('--app-height', window.innerHeight + 'px'); }" +
            "set(); window.addEventListener('resize', set);" +
            "})();"

        const val HOST = "appassets.androidplatform.net"
        private const val ROOT = "zashboard"
        const val INDEX_URL = "https://$HOST/$ROOT/index.html"

        /** The bundled release ("3.29.1"), from zashboard/VERSION; "" if missing. */
        fun version(context: Context): String = try {
            context.assets.open("$ROOT/VERSION").use { it.readBytes().toString(Charsets.UTF_8).trim() }
        } catch (e: IOException) {
            ""
        }
    }
}
