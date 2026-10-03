package com.github.kr328.clash.service.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Loads a profile logo (`profile-logo` header) or a proxy-group icon from a
 * `file://` path or an http(s) URL.
 *
 * Remote images are cached on disk under `cacheDir/remote_icons/<url.hashCode()>`,
 * the same place the UI uses, so a logo the main screen already showed is read
 * from disk and a notification does not need the network. A download is capped
 * at [MAX_BYTES] and a few seconds, so a slow or huge logo can never hold a
 * notification back for long. Returns null whenever the image is missing,
 * cannot be decoded (BitmapFactory has no SVG support) or the load fails.
 */
object ProfileLogoLoader {
    /** Largest logo accepted, as promised by the `Profile-Logo` header docs. */
    const val MAX_BYTES = 2L * 1024 * 1024

    private const val CACHE_DIR = "remote_icons"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @param maxSizePx when positive, the image is decoded downscaled so that
     * both sides stay at or above this size (a cheap power-of-two subsample);
     * used for notifications, where a full-size bitmap is wasteful and a very
     * large one can overflow the binder transaction limit. 0 keeps full size.
     */
    suspend fun load(context: Context, url: String, maxSizePx: Int = 0): Bitmap? {
        if (url.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            try {
                if (url.startsWith("file://")) {
                    val file = File(url.removePrefix("file://"))

                    if (file.isFile) decode(file.readBytes(), maxSizePx) else null
                } else if (isRemoteUrl(url)) {
                    loadRemote(context, url, maxSizePx)
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Load logo $url: $e")

                null
            }
        }
    }

    private fun loadRemote(context: Context, url: String, maxSizePx: Int): Bitmap? {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val cacheFile = File(dir, url.hashCode().toString())

        if (cacheFile.isFile) {
            decode(cacheFile.readBytes(), maxSizePx)?.let { return it }
        }

        val request = Request.Builder().url(url).build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null

            val body = response.body ?: return@use null

            if (body.contentLength() > MAX_BYTES) return@use null

            val bytes = readCapped(body.byteStream(), MAX_BYTES) ?: return@use null
            val bitmap = decode(bytes, maxSizePx) ?: return@use null

            runCatching { cacheFile.writeBytes(bytes) }

            bitmap
        }
    }

    private fun decode(bytes: ByteArray, maxSizePx: Int): Bitmap? {
        val options = BitmapFactory.Options()

        if (maxSizePx > 0) {
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = sampleSizeFor(options.outWidth, options.outHeight, maxSizePx)
            options.inJustDecodeBounds = false
        }

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** Only http and https logos are fetched, as the header docs promise. */
    internal fun isRemoteUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    /**
     * The largest power-of-two subsample that keeps both sides at or above
     * [maxSizePx]; 1 when the image is already small or [maxSizePx] is not positive.
     */
    internal fun sampleSizeFor(width: Int, height: Int, maxSizePx: Int): Int {
        if (maxSizePx <= 0) return 1

        var sample = 1

        while (width / (sample * 2) >= maxSizePx && height / (sample * 2) >= maxSizePx) {
            sample *= 2
        }

        return sample
    }

    /** Reads the whole stream, or returns null as soon as it exceeds [limit] bytes. */
    internal fun readCapped(stream: InputStream, limit: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0L

        while (true) {
            val read = stream.read(buffer)

            if (read < 0) break

            total += read

            if (total > limit) return null

            out.write(buffer, 0, read)
        }

        return out.toByteArray()
    }
}
