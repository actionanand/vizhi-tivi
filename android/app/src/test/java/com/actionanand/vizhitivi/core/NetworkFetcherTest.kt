package com.actionanand.vizhitivi.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

class NetworkFetcherTest {
    @Test fun networkOutcomesDistinguishTimeoutCancellationAndFailure() {
        assertEquals(NetworkOutcome.TIMED_OUT, networkOutcome(SocketTimeoutException()))
        assertEquals(NetworkOutcome.CANCELLED, networkOutcome(CancellationException()))
        assertEquals(NetworkOutcome.FAILED, networkOutcome(IllegalStateException()))
    }
    private class Response(
        private val status: Int = 200,
        private val body: InputStream = ByteArrayInputStream("ok".toByteArray()),
        private val location: String? = null,
        private val onStatus: () -> Unit = {},
    ) : HttpURLConnection(URL("https://example.invalid/test")) {
        val disconnected = CountDownLatch(1)
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected.countDown() }
        override fun getResponseCode(): Int { onStatus(); return status }
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
        override fun getInputStream(): InputStream = body
    }

    @Test fun returnsCompleteResponse() = runBlocking {
        val connection = Response()
        assertEquals("ok", NetworkFetcher(deadlineMs = 2_000, open = { connection }).fetch("https://example.invalid/test"))
        assertTrue(connection.disconnected.await(1, TimeUnit.SECONDS))
    }

    @Test fun deadlineStopsSlowDripEvenWhenEachReadReturnsPromptly() = runBlocking {
        val started = CountDownLatch(1)
        val connection = Response(body = object : InputStream() {
            override fun read(): Int { started.countDown(); Thread.sleep(30); return 65 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { b[off] = read().toByte(); return 1 }
        })
        val start = System.nanoTime()
        try {
            NetworkFetcher(deadlineMs = 250, open = { connection }).fetch("https://example.invalid/test")
            fail("Expected total deadline")
        } catch (_: SocketTimeoutException) { }
        assertTrue(started.await(1, TimeUnit.SECONDS))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3_000)
        assertTrue(connection.disconnected.await(3, TimeUnit.SECONDS))
    }

    @Test fun cancellingAStalledRequestReleasesItsConnection() = runBlocking {
        val started = CountDownLatch(1)
        val connection = Response(onStatus = { started.countDown(); Thread.sleep(30_000) })
        val job = launch(Dispatchers.Default) { NetworkFetcher(deadlineMs = 10_000, open = { connection }).fetch("https://example.invalid/test") }
        assertTrue(started.await(3, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(connection.disconnected.await(3, TimeUnit.SECONDS))
    }

    @Test fun deadlineCancelsAStalledHeaderReadWithoutWaitingForSocketTimeout() = runBlocking {
        val entered = CountDownLatch(1)
        val attempts = AtomicInteger()
        val connection = Response(onStatus = { entered.countDown(); Thread.sleep(30_000) })
        try {
            NetworkFetcher(deadlineMs = 250, open = { attempts.incrementAndGet(); connection }).fetch("https://example.invalid/test")
            fail("Expected total deadline")
        } catch (_: SocketTimeoutException) { }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertTrue(connection.disconnected.await(3, TimeUnit.SECONDS))
        assertEquals(1, attempts.get())
    }

    @Test fun rejectsOversizeAndInsecureRedirectWithoutFetchingIt() = runBlocking {
        try {
            NetworkFetcher(deadlineMs = 2_000, maxBytes = 3, open = { Response(body = ByteArrayInputStream("abcd".toByteArray())) }).fetch("https://example.invalid/test")
            fail("Expected size rejection")
        } catch (_: IllegalArgumentException) { }
        val opened = AtomicInteger()
        try {
            NetworkFetcher(deadlineMs = 2_000, open = { opened.incrementAndGet(); Response(status = 302, location = "http://example.invalid/insecure") }).fetch("https://example.invalid/test")
            fail("Expected redirect rejection")
        } catch (_: IllegalArgumentException) { }
        assertEquals(2, opened.get()) // only the HTTPS entry point is opened, once per attempt
    }

    @Test fun redirectLoopsHaveAFiniteHopAndRetryLimit() = runBlocking {
        val opened = AtomicInteger()
        try {
            NetworkFetcher(deadlineMs = 2_000, open = { opened.incrementAndGet(); Response(status = 302, location = "/loop") }).fetch("https://example.invalid/test")
            fail("Expected redirect limit")
        } catch (_: IllegalStateException) { }
        assertEquals(8, opened.get())
    }
    @Test fun cancellationBeforeCommitPreventsPublication() = runBlocking {
        val gate = NetworkCommitGate()
        assertTrue(gate.cancel())
        var committed = false
        try { gate.commit { committed = true }; fail("Expected cancellation") } catch (_: CancellationException) { }
        assertFalse(committed)
    }
    @Test fun cancellationRacingAnAlreadyStartedCommitIsNotReportedAsAccepted() = runBlocking {
        val gate = NetworkCommitGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val write = async(Dispatchers.Default) { gate.commit { entered.complete(Unit); release.await(); "saved" } }
        entered.await()
        val cancel = async(Dispatchers.Default) { gate.cancel() }
        release.complete(Unit)
        assertEquals("saved", write.await())
        assertFalse(cancel.await())
    }
    @Test fun lifecycleCancellationBeforeCommitPreventsPersistence() = runBlocking {
        val gate = NetworkCommitGate()
        val entered = CompletableDeferred<Unit>()
        val blocker = CompletableDeferred<Unit>()
        val owner = launch(Dispatchers.Default) {
            entered.complete(Unit)
            blocker.await()
            gate.commit { fail("cancelled lifecycle must not commit") }
        }
        entered.await()
        owner.cancelAndJoin()
        blocker.complete(Unit)
        Unit
    }
    @Test fun lifecycleCancellationDuringCommitFinishesPublication() = runBlocking {
        val gate = NetworkCommitGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cacheWritten = false
        var stateUpdated = false
        val owner = launch(Dispatchers.Default) { gate.commit {
            cacheWritten = true
            entered.complete(Unit)
            release.await()
            stateUpdated = true
        } }
        entered.await()
        owner.cancel()
        release.complete(Unit)
        owner.join()
        assertTrue(cacheWritten)
        assertTrue(stateUpdated)
        assertFalse(gate.cancel())
    }
    @Test fun saturatedWorkersFailPromptlyWithoutWaitingForDeadline() = runBlocking {
        val workers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val closers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val timer = ScheduledThreadPoolExecutor(1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            workers.execute { entered.countDown(); release.await() }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            workers.execute { release.await() }
            val start = System.nanoTime()
            try { NetworkFetcher(deadlineMs = 10_000, workers = workers, closers = closers, timer = timer).fetch("https://example.invalid/test"); fail("Expected saturation") }
            catch (_: RejectedExecutionException) { }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
        } finally { release.countDown(); workers.shutdownNow(); closers.shutdownNow(); timer.shutdownNow() }
    }
    @Test fun rejectedCleanupStillCancelsWorkerAndWorkerClosesConnection() = runBlocking {
        val workers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val closers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val timer = ScheduledThreadPoolExecutor(1)
        val started = CountDownLatch(1)
        val connection = Response(onStatus = { started.countDown(); Thread.sleep(30_000) })
        closers.shutdownNow() // deterministically reject the asynchronous disconnect
        try {
            val job = launch(Dispatchers.Default) { NetworkFetcher(deadlineMs = 10_000, open = { connection }, workers = workers, closers = closers, timer = timer).fetch("https://example.invalid/test") }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(connection.disconnected.await(2, TimeUnit.SECONDS))
        } finally { workers.shutdownNow(); timer.shutdownNow() }
    }
    @Test fun overlappingRefreshesReadTheLatestCommittedBaseline() = runBlocking {
        val coordinator = PublicationCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var publication = "old"
        val first = async(Dispatchers.Default) { coordinator.run {
            assertEquals("old", publication)
            firstEntered.complete(Unit)
            release.await()
            publication = "first"
        } }
        firstEntered.await()
        val second = async(Dispatchers.Default) { coordinator.run {
            assertEquals("first", publication)
            publication = "second"
        } }
        release.complete(Unit)
        first.await(); second.await()
        assertEquals("second", publication)
    }
    @Test fun failedDisconnectDoesNotPreventLaterRequests() = runBlocking {
        val workers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val closers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val timer = ScheduledThreadPoolExecutor(1)
        val permits = Semaphore(2)
        val started = CountDownLatch(1)
        val attempted = CountDownLatch(1)
        val broken = object : HttpURLConnection(URL("https://example.invalid/test")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { attempted.countDown(); throw IllegalStateException("disconnect failed") }
            override fun getResponseCode(): Int { started.countDown(); Thread.sleep(30_000); return 200 }
        }
        try {
            val job = launch(Dispatchers.Default) { NetworkFetcher(open = { broken }, workers = workers, closers = closers, timer = timer, permits = permits).fetch("https://example.invalid/test") }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(attempted.await(2, TimeUnit.SECONDS))
            assertEquals("ok", NetworkFetcher(open = { Response() }, workers = workers, closers = closers, timer = timer, permits = permits).fetch("https://example.invalid/test"))
        } finally { workers.shutdownNow(); closers.shutdownNow(); timer.shutdownNow() }
    }
    @Test fun disconnectFailureRetainsAdmissionSlot() = runBlocking {
        val workers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val closers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val timer = ScheduledThreadPoolExecutor(1)
        val permits = Semaphore(1)
        val broken = object : HttpURLConnection(URL("https://example.invalid/test")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { throw IllegalStateException("disconnect failed") }
            override fun getResponseCode() = 200
            override fun getInputStream(): InputStream = ByteArrayInputStream("ok".toByteArray())
        }
        try {
            try { NetworkFetcher(open = { broken }, workers = workers, closers = closers, timer = timer, permits = permits).fetch("https://example.invalid/test"); fail("Expected disconnect failure") }
            catch (_: IllegalStateException) { }
            assertEquals(0, permits.availablePermits())
            try { NetworkFetcher(open = { Response() }, workers = workers, closers = closers, timer = timer, permits = permits).fetch("https://example.invalid/test"); fail("Expected admission refusal") }
            catch (_: RejectedExecutionException) { }
        } finally { workers.shutdownNow(); closers.shutdownNow(); timer.shutdownNow() }
    }
    @Test fun cancellationImmediatelyAfterCommitDoesNotUndoPublication() = runBlocking {
        val gate = NetworkCommitGate()
        var persisted = false
        var visible = false
        val committed = CompletableDeferred<Unit>()
        val owner = launch(Dispatchers.Default) {
            gate.commit { persisted = true; visible = true }
            committed.complete(Unit)
            delay(30_000)
        }
        committed.await()
        owner.cancelAndJoin()
        assertTrue(persisted && visible)
        assertFalse(gate.cancel())
    }
    @Test fun timeoutAndCancellationRaceCompletesWithoutPublishing() = runBlocking {
        repeat(10) {
            val entered = CountDownLatch(1)
            val connection = Response(onStatus = { entered.countDown(); Thread.sleep(30_000) })
            val job = launch(Dispatchers.Default) {
                try { NetworkFetcher(deadlineMs = 40, open = { connection }).fetch("https://example.invalid/test"); fail("stalled request returned") }
                catch (_: SocketTimeoutException) { }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            delay(40)
            job.cancelAndJoin()
            assertTrue(connection.disconnected.await(2, TimeUnit.SECONDS))
        }
    }
    @Test fun hungCleanupRetainsAdmissionPermitUntilItFinishes() = runBlocking {
        val workers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val closers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val timer = ScheduledThreadPoolExecutor(1)
        val permits = Semaphore(1)
        val started = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocked = object : HttpURLConnection(URL("https://example.invalid/test")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { closeStarted.countDown(); release.await() }
            override fun getResponseCode(): Int { started.countDown(); Thread.sleep(30_000); return 200 }
        }
        try {
            val job = launch(Dispatchers.Default) { NetworkFetcher(open = { blocked }, workers = workers, closers = closers, timer = timer, permits = permits).fetch("https://example.invalid/test") }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(closeStarted.await(2, TimeUnit.SECONDS))
            try { NetworkFetcher(open = { Response() }, workers = workers, closers = closers, timer = timer, permits = permits).fetch("https://example.invalid/test"); fail("Expected admission refusal") }
            catch (_: RejectedExecutionException) { }
        } finally { release.countDown(); workers.shutdownNow(); closers.shutdownNow(); timer.shutdownNow() }
    }
    @Test fun failedFinalizationReconcilesTheActualCommittedData() = runBlocking {
        var disk = "old"
        var visible = "old"
        try {
            commitPublication("new", "old", persist = { mark -> disk = "new"; mark(); error("directory sync failed") }, apply = { visible = it })
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals("new", disk)
        assertEquals("new", visible)
    }
    @Test fun unreadableCacheAfterFailedFinalizationRetainsPreviousState() = runBlocking {
        var visible = "old"
        try {
            commitPublication("new", "old", persist = { error("write failed") }, apply = { visible = it })
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals("old", visible)
    }
    @Test fun rejectedDeadlineSchedulerReleasesAdmissionPermit() = runBlocking {
        val workers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val closers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val timer = ScheduledThreadPoolExecutor(1).apply { shutdownNow() }
        val permits = Semaphore(1)
        try {
            try { NetworkFetcher(workers = workers, closers = closers, timer = timer, permits = permits).fetch("https://example.invalid/test"); fail("Expected scheduler rejection") }
            catch (_: RejectedExecutionException) { }
            assertEquals(1, permits.availablePermits())
        } finally { workers.shutdownNow(); closers.shutdownNow() }
    }
}
