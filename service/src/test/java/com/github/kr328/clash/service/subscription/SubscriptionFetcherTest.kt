package com.github.kr328.clash.service.subscription

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SubscriptionFetcherTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val now = AtomicLong(1_000_000L)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fetcher(maxBodyBytes: Long = 1 shl 20, ttlMillis: Long = 15_000L) = SubscriptionFetcher(
        cacheDir = folder.newFolder(),
        maxBodyBytes = maxBodyBytes,
        ttlMillis = ttlMillis,
        clock = { now.get() },
    )

    private fun request() = Request.Builder().url(server.url("/sub")).build()

    @Test
    fun concurrentCallsShareOneRequest() {
        server.enqueue(MockResponse().setBody("config").setBodyDelay(300, TimeUnit.MILLISECONDS))
        val fetcher = fetcher()

        val pool = Executors.newFixedThreadPool(5)
        val start = CountDownLatch(1)
        val futures = (1..5).map {
            pool.submit<String> {
                start.await()
                fetcher.fetch(request(), fresh = true).readText()
            }
        }
        start.countDown()

        futures.forEach { assertEquals("config", it.get(10, TimeUnit.SECONDS)) }
        pool.shutdown()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun finishedAnswerIsReusedWithinTtl() {
        server.enqueue(MockResponse().setBody("one"))
        server.enqueue(MockResponse().setBody("two"))
        val fetcher = fetcher()

        assertEquals("one", fetcher.fetch(request(), fresh = true).readText())
        now.addAndGet(5_000)
        assertEquals("one", fetcher.fetch(request(), fresh = false).readText())

        assertEquals(1, server.requestCount)
    }

    @Test
    fun freshStartsANewOperation() {
        server.enqueue(MockResponse().setBody("one"))
        server.enqueue(MockResponse().setBody("two"))
        val fetcher = fetcher()

        assertEquals("one", fetcher.fetch(request(), fresh = true).readText())
        assertEquals("two", fetcher.fetch(request(), fresh = true).readText())

        assertEquals(2, server.requestCount)
    }

    @Test
    fun answerExpiresAfterTtl() {
        server.enqueue(MockResponse().setBody("one"))
        server.enqueue(MockResponse().setBody("two"))
        val fetcher = fetcher(ttlMillis = 15_000L)

        assertEquals("one", fetcher.fetch(request(), fresh = true).readText())
        now.addAndGet(15_001)
        assertEquals("two", fetcher.fetch(request(), fresh = false).readText())

        assertEquals(2, server.requestCount)
    }

    @Test
    fun freshStillJoinsARequestThatIsRunning() {
        server.enqueue(MockResponse().setBody("config").setBodyDelay(500, TimeUnit.MILLISECONDS))
        val fetcher = fetcher()

        val pool = Executors.newSingleThreadExecutor()
        val first = pool.submit<String> { fetcher.fetch(request(), fresh = true).readText() }
        Thread.sleep(150)
        val second = fetcher.fetch(request(), fresh = true).readText()

        assertEquals("config", second)
        assertEquals("config", first.get(10, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun anyHttpStatusIsReturnedWithItsHeadersAndNoBody() {
        server.enqueue(MockResponse().setResponseCode(403).addHeader("x-hwid-not-supported", "true").setBody("no"))
        val fetcher = fetcher()

        val result = fetcher.fetch(request(), fresh = true)

        assertEquals(403, result.code)
        assertFalse(result.isSuccessful)
        assertEquals("true", result.headers["x-hwid-not-supported"])
        assertNull(result.body)

        // and the next step of the same operation gets the same answer without asking again
        val again = fetcher.fetch(request(), fresh = false)
        assertSame(result, again)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun successfulBodyIsKeptInAFile() {
        server.enqueue(MockResponse().setBody("proxies: []").addHeader("subscription-userinfo", "upload=1; download=2; total=3"))
        val fetcher = fetcher()

        val result = fetcher.fetch(request(), fresh = true)

        assertTrue(result.isSuccessful)
        assertNotNull(result.body)
        assertTrue(result.body!!.isFile)
        assertEquals("proxies: []", result.readText())
        assertEquals("upload=1; download=2; total=3", result.headers["subscription-userinfo"])

        val copy = folder.newFile()
        result.copyBodyTo(copy)
        assertEquals("proxies: []", copy.readText())
    }

    @Test
    fun transportFailureThrowsAndANewOperationTriesAgain() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("config"))
        val fetcher = fetcher()

        try {
            fetcher.fetch(request(), fresh = true)
            fail("expected a transport failure")
        } catch (_: IOException) {
        }

        assertEquals("config", fetcher.fetch(request(), fresh = true).readText())
    }

    @Test
    fun aFailureIsSharedWithTheNextStepOfTheSameOperation() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("config"))
        val fetcher = fetcher()

        for (fresh in listOf(true, false)) {
            try {
                fetcher.fetch(request(), fresh = fresh)
                fail("expected a transport failure (fresh = $fresh)")
            } catch (_: IOException) {
            }
        }

        // The second step reused the failure: the response queued for a retry is still waiting.
        assertEquals("config", fetcher.fetch(request(), fresh = true).readText())
    }

    @Test
    fun oversizedBodyIsRejectedAndLeavesNoFile() {
        server.enqueue(MockResponse().setBody("x".repeat(100)))
        val cache = folder.newFolder()
        val fetcher = SubscriptionFetcher(cacheDir = cache, maxBodyBytes = 10, clock = { now.get() })

        try {
            fetcher.fetch(request(), fresh = true)
            fail("expected ResponseTooLargeException")
        } catch (_: SubscriptionFetcher.ResponseTooLargeException) {
        }

        assertEquals(0, cache.listFiles()?.size ?: 0)
    }

    @Test
    fun callerThatStopsWaitingDoesNotCostASecondRequest() {
        server.enqueue(MockResponse().setBody("config").setBodyDelay(600, TimeUnit.MILLISECONDS))
        val fetcher = fetcher()

        assertNull(fetcher.fetchWithin(request(), fresh = true, timeoutMillis = 100))
        assertEquals("config", fetcher.fetch(request(), fresh = false).readText())

        assertEquals(1, server.requestCount)
    }

    @Test
    fun releaseDeletesTheFinishedBody() {
        server.enqueue(MockResponse().setBody("config"))
        val fetcher = fetcher()

        val result = fetcher.fetch(request(), fresh = true)
        val file = result.body!!
        assertTrue(file.exists())

        fetcher.release(request().url.toString())

        assertFalse(file.exists())
    }

    @Test
    fun staleAnswersAreSweptWithTheirFiles() {
        server.enqueue(MockResponse().setBody("one"))
        server.enqueue(MockResponse().setBody("two"))
        val fetcher = fetcher(ttlMillis = 1_000L)

        val first = fetcher.fetch(request(), fresh = true).body!!
        now.addAndGet(2_000)
        fetcher.fetch(request(), fresh = false)

        assertFalse(first.exists())
    }
}
