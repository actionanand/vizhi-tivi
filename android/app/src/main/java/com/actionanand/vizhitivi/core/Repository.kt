package com.actionanand.vizhitivi.core

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.system.Os
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import com.actionanand.vizhitivi.BuildConfig

private val Context.preferences by preferencesDataStore("preferences-v1")
private const val MAX_PRIVATE_BYTES = 20_000_000
data class Preferences(val locale: String = "en", val theme: String = "system", val favourites: Set<String> = emptySet(), val recent: List<String> = emptyList(), val endpoint: String = BuildConfig.CATALOGUE_MANIFEST_URL, val localTamilNumbers: Boolean = false)

class Repository(private val context: Context) {
    private val lock = Mutex()
    private val publicationLock = Mutex()
    private val cache = AtomicFile(File(context.filesDir, "catalogue-v1.json"))
    private val publication = AtomicFile(File(context.filesDir, "publication-v1.json"))
    private val previousPublication = File(context.filesDir, "publication-v1.previous.json")
    private val imports = AtomicFile(File(context.filesDir, "playlists-v1.json"))
    private val recoveryBackup = AtomicFile(File(context.filesDir, "playlists-recovery-v1.json"))
    private val snapshots = RecoverySnapshots(File(context.filesDir, "recovery-snapshots"), recoveryBackup.baseFile)
    private val network = NetworkFetcher()
    fun hasRecoveryBackup(): Boolean = snapshots.list().isNotEmpty()
    suspend fun recoverySnapshots(): List<RecoverySnapshot> = withContext(Dispatchers.IO) { snapshots.list() }
    val preferences: Flow<Preferences> = context.preferences.data.map { p ->
        fun str(k: String, fallback: String = "") = p[stringPreferencesKey(k)] ?: fallback
        Preferences(str("locale", "en").takeIf { it in listOf("en", "ta", "sa") } ?: "en", str("theme", "system"),
            str("favourites").split('|').filter { it.isNotEmpty() }.toSet(), str("recent").split('|').filter { it.isNotEmpty() }, str("endpoint").ifBlank { BuildConfig.CATALOGUE_MANIFEST_URL }, str("localTamil") == "true")
    }
    suspend fun preference(key: String, value: String) { context.preferences.edit { it[stringPreferencesKey(key)] = value } }
    suspend fun favourite(id: String) { context.preferences.edit { p ->
        val key = stringPreferencesKey("favourites")
        val ids = p[key].orEmpty().split('|').filter { it.isNotEmpty() }.toMutableSet()
        if (!ids.remove(id)) ids.add(id)
        p[key] = ids.sorted().joinToString("|")
    } }
    suspend fun watched(id: String) { context.preferences.edit { p ->
        val key = stringPreferencesKey("recent")
        p[key] = (listOf(id) + p[key].orEmpty().split('|').filter { it.isNotEmpty() && it != id }).take(30).joinToString("|")
    } }
    suspend fun initial(): Catalogue = withContext(Dispatchers.IO) {
        if (publication.baseFile.exists() || File(publication.baseFile.path + ".bak").exists()) {
            decodePublicationCache(publication.openRead().use { readBounded(it, 60_000_000) })
        }
        // An older retained copy can contain streams revoked by a newer
        // publication. Never automatically play it if the active cache is lost.
        else if (previousPublication.isFile) Catalogue(null, emptyList())
        else if (cache.baseFile.exists()) CatalogueCodec.decode(String(cache.readFully(), Charsets.UTF_8)).takeUnless { it.developmentFixture } ?: Catalogue(null, emptyList())
        else Catalogue(null, emptyList())
    }
    private fun decodePublicationCache(bytes: ByteArray): Catalogue = PublishedCatalogue.decode(PublishedCatalogue.cachedFiles(String(bytes, Charsets.UTF_8)))
    internal suspend fun fetchPublication(endpoint: String, previous: Catalogue): PublishedCatalogue.Publication? = withContext(Dispatchers.IO) {
        require(safeUrl(endpoint)) { "Catalogue endpoint must be HTTPS" }
        PublishedCatalogue.load(endpoint.ifBlank { BuildConfig.CATALOGUE_MANIFEST_URL }, previous, ::fetch)
    }
    internal suspend fun preparePublication(next: PublishedCatalogue.Publication): PublicationWrite = publicationLock.withLock {
        val text = PublishedCatalogue.cache(next.files)
        val base = publication.baseFile
        // Resolve a legacy AtomicFile backup before replacing the cache.
        if (File(base.path + ".bak").exists()) publication.openRead().use { }
        if (base.isFile) {
            val prior = base.inputStream().use { readBounded(it, 60_000_000) }
            val valid = try { decodePublicationCache(prior); true } catch (_: Exception) { false }
            // Never replace an existing valid fallback with an invalid cache.
            if (valid) writePublicationCandidate(publicationWriter(previousPublication), prior) { }
        }
        publicationWriter(base).also { stagePublicationCandidate(it, text.toByteArray(Charsets.UTF_8)) }
    }
    private fun publicationWriter(target: File): PublicationWrite {
        val stagedFile = File(target.path + ".candidate")
        return object : PublicationWrite {
            override fun start(): OutputStream = FileOutputStream(stagedFile)
            override fun sync(stream: OutputStream) { (stream as FileOutputStream).fd.sync() }
            // UTF-8 and JSON string escaping can exceed the character count.
            override fun staged(): ByteArray = stagedFile.inputStream().use { readBounded(it, 60_000_000) }
            override fun rename() { Os.rename(stagedFile.path, target.path) }
            override fun syncDirectory() { syncPrivateDirectory(target.parentFile ?: error("Publication parent unavailable")) }
            override fun abort() { if (stagedFile.exists()) check(stagedFile.delete()) }
        }
    }
    suspend fun readFile(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = readBounded(input, 12_000_000)
            require(bytes.size <= 12_000_000) { "Playlist too large" }
            String(bytes, Charsets.UTF_8)
        } ?: error("Unable to read document")
    }
    suspend fun fetch(raw: String): String = network.fetch(raw)
    suspend fun playlists(): PlaylistLoad = withContext(Dispatchers.IO) {
        val base = imports.baseFile
        val backup = File(base.path + ".bak")
        val pending = File(base.path + ".new")
        if (!base.exists() && !backup.exists()) {
            require(!pending.exists()) { "An interrupted private store needs recovery" }
            return@withContext PlaylistLoad(emptyList(), true)
        }
        // AtomicFile.openRead() can delete a .new file. Read without modifying any
        // interrupted artifacts until the user has explicitly recovered them.
        val loaded = PrivatePlaylistCodec.decode(openOriginal().use { String(readBounded(it, MAX_PRIVATE_BYTES), Charsets.UTF_8) })
        if (backup.exists() || pending.exists()) loaded.copy(complete = false) else loaded
    }
    private fun serializePlaylists(playlists: List<Playlist>): String {
        val root = JSONObject().put("schemaVersion", 1).put("playlists", JSONArray(playlists.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("channels", JSONArray(p.channels.map { c ->
                CatalogueCodec.channelJson(c.copy(country = c.country.ifBlank { "Unknown" }, provider = "Private user import"))
            }))
        }))
        val text = root.toString()
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_PRIVATE_BYTES) { "Private store too large" }
        require(PrivatePlaylistCodec.decode(text).complete) { "Invalid private playlists" }
        return text
    }
    suspend fun savePlaylists(playlists: List<Playlist>) = lock.withLock { withContext(Dispatchers.IO) {
        require(interruptedFiles().none { it.exists() }) { "An interrupted private store needs recovery" }
        atomicWrite(imports, serializePlaylists(playlists))
    } }
    suspend fun recoverPlaylists(playlists: List<Playlist>) = lock.withLock { withContext(Dispatchers.IO) {
        val text = serializePlaylists(playlists)
        capturePrivateFiles()
        replaceAndVerify(text)
    } }
    suspend fun restoreSnapshot(id: String, officialNumbers: Set<Int>, current: List<Playlist>): List<Playlist> = lock.withLock { withContext(Dispatchers.IO) {
        val loaded = PrivatePlaylistCodec.decode(snapshots.raw(id))
        require(loaded.complete || loaded.playlists.isNotEmpty()) { "No recoverable playlists" }
        val recovered = current.toMutableList()
        for (playlist in loaded.playlists) {
            val index = recovered.indexOfFirst { it.id == playlist.id }
            if (index < 0) recovered.add(playlist)
            else {
                val existing = recovered[index]
                val known = existing.channels.map { it.id }.toSet()
                recovered[index] = existing.copy(channels = existing.channels + playlist.channels.filterNot { it.id in known })
            }
        }
        val numbers = officialNumbers.toMutableSet()
        val ids = mutableSetOf<String>()
        require(recovered.flatMap { it.channels }.all { c -> ids.add(c.id) && (c.number == null || numbers.add(c.number)) }) { "Channel conflict" }
        val text = serializePlaylists(recovered)
        if (imports.baseFile.exists() || interruptedFiles().any { it.exists() }) capturePrivateFiles()
        replaceAndVerify(text)
        try { snapshots.markRestored(id) } catch (_: Exception) { /* Data is already safely committed. */ }
        recovered
    } }
    suspend fun deleteSnapshot(id: String) = lock.withLock { withContext(Dispatchers.IO) { snapshots.delete(id) } }
    suspend fun exportSnapshot(uri: Uri, id: String) = lock.withLock { withContext(Dispatchers.IO) {
        val data = snapshots.exportBytes(id)
        context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to write export")
    } }
    suspend fun exportPlaylists(uri: Uri, playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        val records = JSONArray(playlists.map { p -> JSONObject().put("id", p.id).put("name", p.name).put("channels", JSONArray(p.channels.map { CatalogueCodec.channelJson(it) })) })
        val data = JSONObject().put("schemaVersion", 1).put("playlists", records).toString().toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to write export")
    }
    suspend fun exportOriginal(uri: Uri, useBackup: Boolean) = withContext(Dispatchers.IO) {
        val data = if (useBackup) snapshots.list().firstOrNull()?.let { snapshots.exportBytes(it.id) } ?: error("No snapshot") else openOriginal().use { it.readBytes() }
        context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to write export")
    }
    private fun openOriginal(): InputStream {
        val base = imports.baseFile
        val backup = File(base.path + ".bak")
        val pending = File(base.path + ".new")
        return when {
            backup.isFile -> backup.inputStream()
            base.isFile -> base.inputStream()
            pending.isFile -> pending.inputStream()
            else -> error("Private store unavailable")
        }
    }
    private fun interruptedFiles(): List<File> = listOf(File(imports.baseFile.path + ".bak"), File(imports.baseFile.path + ".new"))
    private fun capturePrivateFiles() {
        // Save every distinct candidate before AtomicFile is allowed to replace,
        // restore or remove any of them. Capacity or IO failure leaves the active
        // files untouched, even if some snapshots were already created.
        val previous = mutableListOf<ByteArray>()
        val candidates = (listOf(imports.baseFile) + interruptedFiles()).filter { it.isFile }
        require(candidates.isNotEmpty()) { "Private store unavailable" }
        candidates.forEach { file ->
            val bytes = file.inputStream().use { readBounded(it, MAX_PRIVATE_BYTES) }
            if (previous.none { it.contentEquals(bytes) }) { snapshots.capture(bytes); previous.add(bytes) }
        }
    }
    private fun atomicWrite(file: AtomicFile, text: String) {
        val stream = file.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun replaceAndVerify(text: String) {
        val stagedFile = File(imports.baseFile.path + ".new")
        commitPrivateRecovery(object : PrivateRecoveryWrite {
            override fun start(): OutputStream = imports.startWrite()
            override fun sync(stream: OutputStream) { (stream as FileOutputStream).fd.sync() }
            override fun staged(): ByteArray = stagedFile.inputStream().use { readBounded(it, MAX_PRIVATE_BYTES) }
            override fun finish(stream: OutputStream) {
                imports.finishWrite(stream as FileOutputStream)
                syncPrivateDirectory(imports.baseFile.parentFile ?: error("Private store parent unavailable"))
            }
            override fun abort(stream: OutputStream) { imports.failWrite(stream as FileOutputStream) }
            override fun active(): ByteArray = openOriginal().use { readBounded(it, MAX_PRIVATE_BYTES) }
        }, text.toByteArray(Charsets.UTF_8))
    }
    private fun readBounded(input: InputStream, limit: Int = 5_000_000): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size() + count <= limit) { "Response too large" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
