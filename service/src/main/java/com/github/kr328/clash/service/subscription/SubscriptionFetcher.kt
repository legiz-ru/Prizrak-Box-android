package com.github.kr328.clash.service.subscription

import android.content.Context
import com.github.kr328.clash.common.log.Log
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Downloads a subscription URL — headers and body together — once, and hands
 * the same answer to everyone who asks for it.
 *
 * Updating or importing a profile used to need four or five separate requests
 * to the panel for the same URL: one to read the management headers, one to
 * pick a live endpoint, one to download the config, one more for the traffic
 * numbers. Each of them got the same response. Now every one of those steps
 * asks this class, and the panel sees a single request:
 *
 *  - a request that is already running is joined, not repeated, and it is
 *    not tied to whoever started it (a cancelled caller does not abort it);
 *  - a finished answer is reused for [ttlMillis], so the steps of one
 *    operation that run one after another share it;
 *  - [fetch] with `fresh = true` marks the start of a new operation (the
 *    user taps Update, the scheduler wakes up, an import begins): it ignores a
 *    finished answer, but still joins a request that is running right now.
 *
 * Any HTTP status comes back as a [Result] — the caller decides what a 403 with
 * `x-hwid-not-supported` means. Only transport failures throw. A failure is
 * shared like an answer for the same [ttlMillis]: the steps of one operation
 * that follow a failed first step do not hammer an unreachable panel again,
 * and a new operation (`fresh = true`) always tries again. The body of a 2xx
 * answer is kept in a file, not in memory.
 */
class SubscriptionFetcher(
    private val cacheDir: File,
    private val client: OkHttpClient = defaultClient(),
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
    private val ttlMillis: Long = TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "subscription-fetch").apply { isDaemon = true }
    },
    private val cleaner: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "subscription-fetch-cleaner").apply { isDaemon = true }
    },
) {
    class ResponseTooLargeException(limit: Long) : IOException("Response larger than $limit bytes")

    /** The answer for one URL. [body] is null unless [code] is 2xx. */
    class Result(
        val requestUrl: HttpUrl,
        val code: Int,
        val headers: Headers,
        val body: File?,
    ) {
        val isSuccessful: Boolean get() = code in 200..299

        fun readText(): String = body?.readText(Charsets.UTF_8) ?: ""

        fun copyBodyTo(target: File) {
            target.parentFile?.mkdirs()
            body?.copyTo(target, overwrite = true) ?: target.writeBytes(ByteArray(0))
        }
    }

    private class Entry {
        val done = CountDownLatch(1)

        @Volatile
        var result: Result? = null

        @Volatile
        var error: Throwable? = null

        @Volatile
        var completedAt: Long = 0

        val finished: Boolean get() = done.count == 0L
    }

    private val lock = Any()
    private val entries = ConcurrentHashMap<String, Entry>()

    init {
        sweepOrphans()
    }

    /**
     * Blocks until the answer for [request] is available. Throws what the
     * transport threw (an [IOException] in practice).
     */
    fun fetch(request: Request, fresh: Boolean): Result {
        val entry = obtain(request, fresh)
        entry.done.await()

        return entry.take()
    }

    /**
     * Like [fetch], but gives up waiting after [timeoutMillis] and returns null.
     * The request itself keeps running, and the next [fetch] of the same URL
     * joins it — which is how a short preflight does not cost a second request.
     */
    fun fetchWithin(request: Request, fresh: Boolean, timeoutMillis: Long): Result? {
        val entry = obtain(request, fresh)

        if (!entry.done.await(timeoutMillis, TimeUnit.MILLISECONDS)) return null

        return entry.take()
    }

    /** Forgets a finished answer for [url] and deletes its body file. */
    fun release(url: String) {
        synchronized(lock) {
            val entry = entries[url] ?: return
            if (!entry.finished) return

            entries.remove(url)
            entry.result?.body?.delete()
        }
    }

    private fun Entry.take(): Result {
        error?.let { throw it }

        return result ?: throw IOException("No result")
    }

    private fun obtain(request: Request, fresh: Boolean): Entry {
        val key = request.url.toString()

        synchronized(lock) {
            val now = clock()
            sweep(now)

            val existing = entries[key]
            if (existing != null) {
                // Running right now: whoever asks joins it, fresh or not.
                if (!existing.finished) return existing

                val usable = !fresh && now - existing.completedAt <= ttlMillis
                if (usable) return existing

                entries.remove(key)
                existing.result?.body?.delete()
            }

            val entry = Entry()
            entries[key] = entry
            executor.execute { run(entry, request) }

            return entry
        }
    }

    private fun run(entry: Entry, request: Request) {
        try {
            entry.result = download(request)
        } catch (t: Throwable) {
            entry.error = t
        } finally {
            entry.completedAt = clock()
            entry.done.countDown()

            // The body of an answer nobody asks for again must not stay on disk:
            // it is a subscription, with keys in it.
            try {
                cleaner.schedule(
                    { synchronized(lock) { sweep(clock()) } },
                    ttlMillis + CLEANUP_SLACK_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
            } catch (_: Exception) {
                // the cleaner was shut down; the next fetch sweeps anyway
            }
        }
    }

    private fun download(request: Request): Result {
        client.newCall(request).execute().use { response ->
            val successful = response.isSuccessful
            var file: File? = null

            if (successful) {
                val body = response.body ?: throw IOException("Empty response body")

                cacheDir.mkdirs()
                file = File(cacheDir, "${UUID.randomUUID()}.body")

                try {
                    file.outputStream().use { output ->
                        body.byteStream().use { input -> copyLimited(input, output, maxBodyBytes) }
                    }
                } catch (t: Throwable) {
                    file.delete()

                    throw t
                }
            }

            return Result(request.url, response.code, response.headers, file)
        }
    }

    private fun copyLimited(input: InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(8192)
        var total = 0L

        while (true) {
            val read = input.read(buffer)

            if (read < 0) break

            total += read

            if (total > limit) throw ResponseTooLargeException(limit)

            output.write(buffer, 0, read)
        }
    }

    /** Drops finished answers that outlived the TTL, together with their files. */
    private fun sweep(now: Long) {
        val iterator = entries.entries.iterator()

        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()

            if (entry.finished && now - entry.completedAt > ttlMillis) {
                iterator.remove()
                entry.result?.body?.delete()
            }
        }
    }

    /**
     * Body files left behind by a process that died mid-operation. The folder
     * is shared by every process of the app, so only files old enough to be
     * nobody's are removed.
     */
    private fun sweepOrphans() {
        try {
            val threshold = clock() - ORPHAN_AGE_MILLIS

            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < threshold) file.delete()
            }
        } catch (e: Exception) {
            Log.w("Sweep $cacheDir: $e", e)
        }
    }

    companion object {
        /** Caps a download so a misbehaving server can't exhaust storage or memory. */
        const val MAX_BODY_BYTES = 32L shl 20 // 32 MiB

        /** How long a finished answer stays usable for the next step of the same operation. */
        const val TTL_MILLIS = 15_000L

        private const val CLEANUP_SLACK_MILLIS = 1_000L

        private const val ORPHAN_AGE_MILLIS = 10L * 60 * 1000

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // Fail over to a mirror quickly when the host is simply not there...
            .connectTimeout(9, TimeUnit.SECONDS)
            // ...but let a large config arrive: this is idle time, not total time.
            .readTimeout(30, TimeUnit.SECONDS)
            // A redirect from https to http would send the subscription (and any
            // HWID headers) in the clear; the 3xx is returned as it is and reported
            // by the caller instead of being followed.
            .followSslRedirects(false)
            .build()

        @Volatile
        private var shared: SubscriptionFetcher? = null

        /** The one fetcher of this process. */
        fun shared(context: Context): SubscriptionFetcher =
            shared ?: synchronized(this) {
                shared ?: SubscriptionFetcher(File(context.applicationContext.cacheDir, "subscription_fetch"))
                    .also { shared = it }
            }
    }
}
