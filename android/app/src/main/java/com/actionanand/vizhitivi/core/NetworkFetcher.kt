package com.actionanand.vizhitivi.core

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A cancellation is a normal outcome, distinct from timeout and other failures. */
enum class NetworkOutcome { NONE, TIMED_OUT, CANCELLED, FAILED }

internal fun networkOutcome(error: Throwable): NetworkOutcome = when (error) {
    is CancellationException -> NetworkOutcome.CANCELLED
    is SocketTimeoutException -> NetworkOutcome.TIMED_OUT
    else -> NetworkOutcome.FAILED
}

/** Cancellation wins until a validated result starts its durable commit. */
internal class NetworkCommitGate {
    private val lock = Mutex()
    private var cancelled = false
    private var committed = false
    suspend fun cancel(): Boolean = lock.withLock {
        if (committed || cancelled) false else { cancelled = true; true }
    }
    suspend fun <T> commit(block: suspend () -> T): T = lock.withLock {
        if (cancelled) throw CancellationException("Request cancelled before commit")
        // Once the commit starts, finish both persistence and its in-memory
        // publication even if the owning lifecycle is concurrently cancelled.
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) { block().also { committed = true } }
    }
}

/** Refreshes read their baseline only after all older refreshes finish. */
internal class PublicationCoordinator {
    private val lock = Mutex()
    suspend fun <T> run(block: suspend () -> T): T = lock.withLock { block() }
}

/** Keep memory aligned with the old or new cache at the atomic rename boundary. */
internal suspend fun <T> commitPublication(next: T, previous: T, persist: suspend (() -> Unit) -> Unit, apply: (T) -> Unit) {
    var committed = false
    try { persist { committed = true }; apply(next) } catch (failure: Exception) {
        apply(if (committed) next else previous)
        throw failure
    }
}

/** Finite request budget including retries, redirects, headers, and the complete body. */
internal class NetworkFetcher(
    private val deadlineMs: Long = 15_000,
    private val maxBytes: Int = 5_000_000,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val workers: ThreadPoolExecutor = sharedWorkers,
    private val closers: ThreadPoolExecutor = sharedClosers,
    private val timer: ScheduledThreadPoolExecutor = sharedTimer,
    private val permits: Semaphore = sharedPermits,
) {
    companion object {
        private fun factory(name: String) = ThreadFactory { task -> Thread(task, name).apply { isDaemon = true } }
        // At most eight leases can retain connections across request and close
        // work. A hung or failed disconnect consumes its lease; further work
        // fails closed at capacity. Platform calls may ignore interruption.
        private val sharedWorkers = ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS, ArrayBlockingQueue(32), factory("vizhi-fetch"), ThreadPoolExecutor.AbortPolicy())
        private val sharedClosers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(32), factory("vizhi-close"), ThreadPoolExecutor.AbortPolicy())
        private val sharedTimer = ScheduledThreadPoolExecutor(1, factory("vizhi-deadline")).apply { removeOnCancelPolicy = true }
        private val sharedPermits = Semaphore(8)
    }

    suspend fun fetch(raw: String): String = suspendCancellableCoroutine { continuation ->
        require(deadlineMs > 0 && maxBytes > 0)
        require(safeUrl(raw)) { "Only valid HTTPS URLs are supported" }
        if (!permits.tryAcquire()) {
            continuation.resumeWithException(RejectedExecutionException("Network capacity exhausted"))
            return@suspendCancellableCoroutine
        }
        val done = AtomicBoolean(false)
        // A permit is held until both the worker and every scheduled close have
        // finished. A hung disconnect therefore applies bounded backpressure.
        val leaseLock = Any()
        var workerFinished = false
        var pendingCloses = 0
        var leaseReleased = false
        var cleanupFailed = false
        fun releaseIfFinished() {
            // A failed disconnect may leave a live socket. Keep its admission
            // slot rather than allowing repeated failures to grow resources.
            if (workerFinished && pendingCloses == 0 && !cleanupFailed && !leaseReleased) { leaseReleased = true; permits.release() }
        }
        fun markCleanupFailed() = synchronized(leaseLock) { cleanupFailed = true }
        fun workerComplete() = synchronized(leaseLock) { workerFinished = true; releaseIfFinished() }
        val workerState = AtomicInteger(0) // queued, running, finished/cancelled-before-start
        val connection = AtomicReference<HttpURLConnection?>()
        val task = AtomicReference<Future<*>?>()
        fun closeConnection() {
            val active = synchronized(leaseLock) {
                if (workerFinished) null else connection.get()?.also { pendingCloses++ }
            } ?: return
            // disconnect() may block behind an in-flight read; never run it on the UI or timer thread.
            try { closers.execute {
                try { if (connection.compareAndSet(active, null)) try { active.disconnect() } catch (_: Exception) { markCleanupFailed() } }
                finally { synchronized(leaseLock) { pendingCloses--; releaseIfFinished() } }
            } } catch (_: RejectedExecutionException) {
                // Keep the worker's permit until its own finally disconnects.
                synchronized(leaseLock) { pendingCloses--; releaseIfFinished() }
            }
        }
        fun stopWorker() { closeConnection(); task.get()?.cancel(true); if (workerState.compareAndSet(0, 2)) workerComplete(); workers.purge() }
        val timeout = try { timer.schedule({
            if (done.compareAndSet(false, true)) {
                continuation.resumeWithException(SocketTimeoutException("Request deadline exceeded"))
                stopWorker()
            }
        }, deadlineMs, TimeUnit.MILLISECONDS) } catch (failure: RejectedExecutionException) {
            if (workerState.compareAndSet(0, 2)) workerComplete()
            continuation.resumeWithException(failure)
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            if (done.compareAndSet(false, true)) { timeout.cancel(false); stopWorker() }
        }
        try {
            val submitted = workers.submit {
                if (!workerState.compareAndSet(0, 1)) return@submit
                try {
                    val value = read(raw, connection, done, ::markCleanupFailed)
                    if (done.compareAndSet(false, true)) { timeout.cancel(false); continuation.resume(value) }
                } catch (e: Exception) {
                    if (done.compareAndSet(false, true)) { timeout.cancel(false); continuation.resumeWithException(e) }
                } finally { workerState.set(2); workerComplete() }
            }
            task.set(submitted)
            if (done.get()) submitted.cancel(true)
        } catch (e: RejectedExecutionException) {
            if (workerState.compareAndSet(0, 2)) workerComplete()
            if (done.compareAndSet(false, true)) { timeout.cancel(false); continuation.resumeWithException(e) }
        }
    }

    private fun read(raw: String, active: AtomicReference<HttpURLConnection?>, done: AtomicBoolean, cleanupFailed: () -> Unit): String {
        fun ensureActive() { if (done.get() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Request cancelled") }
        var failure: Exception? = null
        repeat(2) { attempt ->
            try {
                var url = raw
                repeat(4) {
                    ensureActive()
                    require(safeUrl(url)) { "Only valid HTTPS URLs are supported" }
                    val connection = open(URI(url).toURL())
                    active.set(connection)
                    try {
                        ensureActive()
                        connection.connectTimeout = 8_000
                        connection.readTimeout = 8_000
                        connection.instanceFollowRedirects = false
                        connection.useCaches = false
                        val status = connection.responseCode
                        if (status in 300..399) {
                            url = URI(url).resolve(connection.getHeaderField("Location") ?: error("Invalid redirect")).toString()
                        } else {
                            require(status in 200..299) { "Server unavailable" }
                            val bytes = ByteArrayOutputStream()
                            connection.inputStream.use { input ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    require(bytes.size() + count <= maxBytes) { "Response too large" }
                                    bytes.write(buffer, 0, count)
                                }
                            }
                            ensureActive()
                            return String(bytes.toByteArray(), Charsets.UTF_8)
                        }
                    } finally {
                        if (active.compareAndSet(connection, null)) try { connection.disconnect() } catch (failure: Exception) { cleanupFailed(); throw failure }
                    }
                }
                error("Too many redirects")
            } catch (e: Exception) {
                ensureActive()
                failure = e
                if (attempt == 0) Thread.sleep(500)
            }
        }
        throw failure ?: error("Network unavailable")
    }
}
