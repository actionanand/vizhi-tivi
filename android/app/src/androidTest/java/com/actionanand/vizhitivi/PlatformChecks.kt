package com.actionanand.vizhitivi

import android.app.Instrumentation
import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.actionanand.vizhitivi.core.*
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Synthetic-only checks. Startup test refuses to overwrite existing app data. */
class PlatformChecks(private val instrumentation: Instrumentation) {
    private val target get() = instrumentation.targetContext
    private val privateUrl = "https://example.invalid/private-${UUID.randomUUID()}.m3u8"
    private fun channel(id: String, number: Int = 255) = Channel(
        id, number, id, listOf("en"), country = "Unknown", provider = "Private user import",
        source = "private", streams = listOf(Stream(privateUrl, "hls", "approved", true)),
        languageOverride = "en", sourceLanguages = listOf("ta")
    )
    private fun payload(id: String): ByteArray = JSONObject().put("schemaVersion", 1)
        .put("playlists", JSONArray().put(JSONObject().put("id", "playlist").put("name", "Synthetic")
            .put("channels", JSONArray().put(CatalogueCodec.channelJson(channel(id)))))).toString().toByteArray()
    private fun temporary(): File = File.createTempFile("vizhi-platform-", ".test", target.cacheDir).let {
        check(it.delete() && it.mkdir()); check(it.canonicalFile.parentFile == target.cacheDir.canonicalFile); it
    }
    private fun waitUntil(condition: () -> Boolean) {
        repeat(300) { if (condition()) return; Thread.sleep(100) }
        error("Timed out waiting for platform state")
    }

    fun atomicFileAndSnapshots() {
        val dir = temporary()
        try {
            val base = File(dir, "playlists.json")
            val atomic = AtomicFile(base)
            val original = payload("old")
            atomic.startWrite().also { it.write(original); atomic.finishWrite(it) }
            check(atomic.openRead().use { it.readBytes() }.contentEquals(original))
            val interrupted = atomic.startWrite()
            interrupted.write("partial".toByteArray()); interrupted.fd.sync(); interrupted.close()
            check(File(base.path + ".new").exists())
            check(AtomicFile(base).openRead().use { it.readBytes() }.contentEquals(original))
            val snapshots = RecoverySnapshots(File(dir, "snapshots"), File(dir, "legacy"))
            val first = snapshots.capture(original)
            val second = snapshots.capture(payload("second"))
            val interruptedSnapshots = RecoverySnapshots(File(dir, "snapshots"), File(dir, "legacy"), boundary = {
                if (it == "written") error("injected interruption")
            })
            check(runCatching { interruptedSnapshots.capture(payload("third")) }.isFailure)
            val inventory = RecoverySnapshots(File(dir, "snapshots"), File(dir, "legacy")).list()
            check(inventory.size == 3 && inventory.any { it.id.startsWith("pending:") && it.integrity == "invalid" })
            check(snapshots.exportBytes(first.id).contentEquals(original))
            check(snapshots.exportBytes(second.id).contentEquals(payload("second")))
            val candidate = payload("new")
            var aborted = false
            val beforeRename = object : PrivateRecoveryWrite {
                override fun start(): OutputStream = atomic.startWrite()
                override fun sync(stream: OutputStream) { (stream as FileOutputStream).fd.sync() }
                override fun staged(): ByteArray = File(base.path + ".new").readBytes()
                override fun finish(stream: OutputStream) { error("injected before commit") }
                override fun abort(stream: OutputStream) { aborted = true; atomic.failWrite(stream as FileOutputStream) }
                override fun active(): ByteArray = base.readBytes()
            }
            check(runCatching { commitPrivateRecovery(beforeRename, candidate) }.isFailure)
            check(!aborted && AtomicFile(base).openRead().use { it.readBytes() }.contentEquals(original))
            val writer = object : PrivateRecoveryWrite {
                override fun start(): OutputStream = atomic.startWrite()
                override fun sync(stream: OutputStream) { (stream as FileOutputStream).fd.sync() }
                override fun staged(): ByteArray = File(base.path + ".new").readBytes()
                override fun finish(stream: OutputStream) { atomic.finishWrite(stream as FileOutputStream); error("injected after commit") }
                override fun abort(stream: OutputStream) { aborted = true; atomic.failWrite(stream as FileOutputStream) }
                override fun active(): ByteArray = base.readBytes()
            }
            check(runCatching { commitPrivateRecovery(writer, candidate) }.isFailure)
            check(!aborted && AtomicFile(base).openRead().use { it.readBytes() }.contentEquals(candidate))
            check(snapshots.exportBytes(first.id).contentEquals(original))
        } finally { check(dir.canonicalFile.parentFile == target.cacheDir.canonicalFile); dir.deleteRecursively() }
    }

    fun repositoryConcurrency() {
        val dir = temporary()
        try {
            val isolated = object : ContextWrapper(target) { override fun getFilesDir(): File = dir }
            val repository = Repository(isolated)
            runBlocking {
                val first = async(Dispatchers.IO) { repository.savePlaylists(listOf(Playlist("first", "First", listOf(channel("first"))))) }
                val second = async(Dispatchers.IO) { repository.savePlaylists(listOf(Playlist("second", "Second", listOf(channel("second"))))) }
                first.await(); second.await()
                val result = repository.playlists()
                check(result.complete && result.playlists.single().id in setOf("first", "second"))
                val saved = result.playlists.single().channels.single()
                check(saved.number == 255 && saved.languageOverride == "en" && saved.sourceLanguages == listOf("ta"))
            }
        } finally { check(dir.canonicalFile.parentFile == target.cacheDir.canonicalFile); dir.deleteRecursively() }
    }

    fun loopbackDeadlineAndCancellation() {
        fun exercise(cancel: Boolean) {
            val loopback = InetAddress.getByName("127.0.0.1")
            val server = ServerSocket(0, 1, loopback)
            val accepted = CountDownLatch(1)
            val serverThread = Thread {
                try {
                    server.accept().use { peer ->
                        accepted.countDown()
                        while (!Thread.currentThread().isInterrupted) {
                            if (!cancel) { peer.getOutputStream().write(65); peer.getOutputStream().flush() }
                            Thread.sleep(25)
                        }
                    }
                } catch (_: Exception) { }
            }.apply { isDaemon = true; start() }
            val disconnected = CountDownLatch(1)
            val connection = object : HttpURLConnection(URL("https://127.0.0.1/fixture")) {
                private val socket = Socket()
                override fun connect() { if (!socket.isConnected) socket.connect(InetSocketAddress(loopback, server.localPort)) }
                override fun usingProxy() = false
                override fun disconnect() { try { socket.close() } finally { disconnected.countDown() } }
                override fun getResponseCode(): Int { connect(); return 200 }
                override fun getInputStream(): InputStream = socket.getInputStream()
            }
            try {
                runBlocking {
                    val fetcher = NetworkFetcher(deadlineMs = if (cancel) 5_000 else 300, open = { connection })
                    if (cancel) {
                        val job = launch(Dispatchers.IO) { fetcher.fetch("https://127.0.0.1/fixture") }
                        check(accepted.await(3, TimeUnit.SECONDS)); job.cancelAndJoin()
                    } else {
                        try { fetcher.fetch("https://127.0.0.1/fixture"); error("Expected deadline") }
                        catch (_: SocketTimeoutException) { }
                    }
                }
                check(disconnected.await(3, TimeUnit.SECONDS))
            } finally { server.close(); serverThread.interrupt(); serverThread.join(2_000) }
        }
        exercise(false); exercise(true)
    }

    fun startupRecoveryAndLocales() {
        val files = target.filesDir
        val reserved = listOf("publication-v1.json", "publication-v1.json.bak", "publication-v1.previous.json", "catalogue-v1.json", "playlists-v1.json", "playlists-v1.json.bak", "playlists-v1.json.new", "recovery-snapshots", "datastore/preferences-v1.preferences_pb")
        check(reserved.none { File(files, it).exists() }) { "Requires a fresh, disposable app installation" }
        val repo = Repository(target)
        val fixture = PublishedCatalogue.paths.associateWith { path -> instrumentation.context.assets.open(path).bufferedReader().use { it.readText() } }
        val cache = AtomicFile(File(files, "publication-v1.json"))
        cache.startWrite().also { it.write(PublishedCatalogue.cache(fixture).toByteArray()); cache.finishWrite(it) }
        runBlocking {
            repo.preference("endpoint", "https://127.0.0.1:1/manifest.json")
            repo.savePlaylists(listOf(Playlist("playlist", "Synthetic", listOf(channel("private")))))
            repo.favourite("private")
        }
        val privateFile = File(files, "playlists-v1.json")
        val damaged = JSONObject(privateFile.readText())
        damaged.getJSONArray("playlists").put(JSONObject().put("id", "bad").put("name", "Bad").put("channels", JSONArray().put(JSONObject())))
        privateFile.writeText(damaged.toString())
        fun launch(): MainActivity = instrumentation.startActivitySync(Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var activity = launch()
        try {
            var vm = ViewModelProvider(activity)[LiveViewModel::class.java]
            waitUntil { vm.state.value.recoveryRequired && vm.state.value.catalogue.channels.size == 4 && vm.state.value.playlists.size == 1 }
            val recoveredChannel = vm.state.value.playlists.single().channels.single()
            check(recoveredChannel.id == "private" && recoveredChannel.number == 255 && recoveredChannel.languageOverride == "en" && recoveredChannel.sourceLanguages == listOf("ta"))
            check(runBlocking { repo.preferences.first().favourites }.contains("private"))
            val export = File.createTempFile("vizhi-platform-export-", ".json", target.cacheDir)
            runBlocking { repo.exportPlaylists(Uri.fromFile(export), vm.state.value.playlists) }
            check(PrivatePlaylistCodec.decode(export.readText()).playlists.size == 1)
            check(export.delete())
            vm.cancelNetwork(); vm.recoverPlaylists()
            waitUntil { !vm.state.value.recoveryRequired && vm.state.value.recoverySnapshots.isNotEmpty() }
            check(vm.state.value.catalogue.channels.size == 4)
            val firstSnapshot = vm.state.value.recoverySnapshots.first { it.recoverableCount > 0 }
            val countBeforeRestore = vm.state.value.recoverySnapshots.size
            vm.restoreSnapshot(firstSnapshot.id)
            waitUntil { !vm.state.value.localBusy && vm.state.value.recoverySnapshots.size > countBeforeRestore }
            val restored = vm.state.value.playlists.single().channels.single()
            check(restored.id == "private" && restored.number == 255 && restored.languageOverride == "en" && restored.sourceLanguages == listOf("ta"))
            check(runBlocking { repo.preferences.first().favourites }.contains("private"))
            waitUntil { !vm.state.value.networkBusy }
            stalledRefreshRetainsCatalogue(repo, vm)
            playbackReplacementAndRemoval(activity, vm, fixture)
            val originalActivity = activity
            val recreated = AtomicReference<MainActivity?>()
            val created = CountDownLatch(1)
            val app = target.applicationContext as Application
            val callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, state: Bundle?) { if (a is MainActivity && a !== originalActivity) { recreated.set(a); created.countDown() } }
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityResumed(a: Activity) = Unit
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, state: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) = Unit
            }
            app.registerActivityLifecycleCallbacks(callbacks)
            try {
                instrumentation.runOnMainSync { originalActivity.recreate() }
                check(created.await(10, TimeUnit.SECONDS))
                activity = recreated.get() ?: error("Recreated activity unavailable")
                vm = ViewModelProvider(activity)[LiveViewModel::class.java]
                waitUntil { vm.state.value.catalogue.channels.size == 4 }
            } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
            for (locale in listOf("en", "ta", "sa")) {
                runBlocking { repo.preference("locale", locale) }
                waitUntil { vm.state.value.preferences.locale == locale }
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
                activity = launch(); vm = ViewModelProvider(activity)[LiveViewModel::class.java]
                waitUntil { vm.state.value.preferences.locale == locale && vm.state.value.catalogue.channels.size == 4 }
                vm.cancelNetwork()
            }
            val log = instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${android.os.Process.myPid()} -v brief")
            log.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).bufferedReader().useLines { lines ->
                    check(lines.take(50_000).none { it.contains(privateUrl) })
                }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync() }
    }

    private fun stalledRefreshRetainsCatalogue(repo: Repository, vm: LiveViewModel) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val accepted = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val serverThread = Thread {
            try {
                server.accept().use { peer ->
                    accepted.countDown(); peer.soTimeout = 8_000
                    while (peer.getInputStream().read() >= 0) { }
                    closed.countDown()
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        try {
            val previous = vm.state.value.catalogue
            val endpoint = "https://127.0.0.1:${server.localPort}/manifest.json"
            runBlocking { repo.preference("endpoint", endpoint) }
            waitUntil { vm.state.value.preferences.endpoint == endpoint }
            val job = vm.refresh()
            check(accepted.await(5, TimeUnit.SECONDS))
            vm.cancelNetwork()
            waitUntil { job.isCompleted }
            check(closed.await(8, TimeUnit.SECONDS))
            check(vm.state.value.catalogue == previous)
        } finally { server.close(); serverThread.interrupt(); serverThread.join(2_000) }
    }

    private fun playbackReplacementAndRemoval(activity: MainActivity, vm: LiveViewModel, fixture: Map<String, String>) {
        fun playNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == "Play" || node.contentDescription?.toString() == "Play") return node
            for (index in 0 until node.childCount) playNode(node.getChild(index))?.let { return it }
            return null
        }
        fun clickPlay() {
            waitUntil { playNode(instrumentation.uiAutomation.rootInActiveWindow) != null }
            var node = playNode(instrumentation.uiAutomation.rootInActiveWindow)
            while (node != null && !node.isClickable) node = node.parent
            check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        }
        fun view(root: View): PlayerView? {
            if (root is PlayerView) return root
            if (root is ViewGroup) for (index in 0 until root.childCount) view(root.getChildAt(index))?.let { return it }
            return null
        }
        fun playerView(): PlayerView? {
            val found = AtomicReference<PlayerView?>()
            instrumentation.runOnMainSync { found.set(view(activity.window.decorView)) }
            return found.get()
        }
        fun player(): Player? {
            val found = AtomicReference<Player?>()
            instrumentation.runOnMainSync { found.set(view(activity.window.decorView)?.player) }
            return found.get()
        }
        fun playerHasMedia(): Boolean {
            val ready = AtomicReference(false)
            instrumentation.runOnMainSync { ready.set(view(activity.window.decorView)?.player?.currentMediaItem != null) }
            return ready.get()
        }
        clickPlay()
        waitUntil { playerHasMedia() }
        val old = player() ?: error("Player unavailable")
        val oldView = playerView() ?: error("Player view unavailable")
        val oldUrl = AtomicReference<String>()
        instrumentation.runOnMainSync { oldUrl.set(old.currentMediaItem?.localConfiguration?.uri.toString()) }
        val previous = vm.state.value.catalogue
        val selected = previous.channels.first { it.playable?.url == oldUrl.get() }
        val modified = fixture.toMutableMap()
        val streams = JSONObject(modified.getValue("catalog/v1/streams.json"))
        val rows = streams.getJSONArray("streams")
        val streamId = selected.playable?.id ?: error("Selected stream unavailable")
        for (index in 0 until rows.length()) if (rows.getJSONObject(index).getString("id") == streamId) {
            rows.getJSONObject(index).put("url", "https://example.invalid/replacement.m3u8")
        }
        modified["catalog/v1/streams.json"] = streams.toString()
        val manifest = JSONObject(modified.getValue("manifest.json"))
        manifest.put("contentSha256", PublishedCatalogue.digest(modified))
        modified["manifest.json"] = manifest.toString()
        val replacement = PublishedCatalogue.decode(modified, previous)
        @Suppress("UNCHECKED_CAST")
        val flow = LiveViewModel::class.java.getDeclaredField("mutable").let { it.isAccessible = true; it.get(vm) as MutableStateFlow<LiveState> }
        flow.update { it.copy(catalogue = replacement) }
        waitUntil { player()?.let { it !== old } == true && playerHasMedia() }
        val current = player() ?: error("Replacement player unavailable")
        val currentUrl = AtomicReference<String>()
        instrumentation.runOnMainSync { currentUrl.set(current.currentMediaItem?.localConfiguration?.uri.toString()) }
        check(currentUrl.get() == "https://example.invalid/replacement.m3u8")
        flow.update { it.copy(catalogue = it.catalogue.copy(channels = it.catalogue.channels.filterNot { c -> c.id == selected.id })) }
        waitUntil { playerView() == null }
        val detached = AtomicReference<Boolean>()
        instrumentation.runOnMainSync { detached.set(oldView.player == null) }
        check(detached.get())
        // Exercise a separately validated empty publication while selected.
        flow.update { it.copy(catalogue = previous) }
        clickPlay()
        waitUntil { playerHasMedia() }
        val emptyFiles = fixture.toMutableMap()
        emptyFiles["catalog/v1/catalogue.json"] = JSONObject().put("schemaVersion", 1).put("channels", JSONArray()).toString()
        emptyFiles["catalog/v1/streams.json"] = JSONObject().put("schemaVersion", 1).put("streams", JSONArray()).toString()
        val emptyManifest = JSONObject(emptyFiles.getValue("manifest.json")).put("channelCount", 0).put("streamCount", 0)
        emptyManifest.put("contentSha256", PublishedCatalogue.digest(emptyFiles))
        emptyFiles["manifest.json"] = emptyManifest.toString()
        val empty = PublishedCatalogue.decode(emptyFiles, previous)
        flow.update { it.copy(catalogue = empty) }
        waitUntil { playerView() == null }
        flow.update { it.copy(catalogue = previous) }
        clickPlay()
        waitUntil { playerHasMedia() }
        val backView = playerView() ?: error("Player view unavailable")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil { playerView() == null }
        instrumentation.runOnMainSync { check(backView.player == null) }
    }
}
