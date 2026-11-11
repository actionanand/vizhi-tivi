package com.actionanand.vizhitivi.core

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.io.InputStream
import java.io.ByteArrayOutputStream
import com.actionanand.vizhitivi.BuildConfig

private val Context.preferences by preferencesDataStore("preferences-v1")
data class Preferences(val locale: String = "en", val theme: String = "system", val favourites: Set<String> = emptySet(), val recent: List<String> = emptyList(), val endpoint: String = BuildConfig.CATALOGUE_MANIFEST_URL, val localTamilNumbers: Boolean = false)

class Repository(private val context: Context) {
    private val lock = Mutex()
    private val cache = AtomicFile(File(context.filesDir, "catalogue-v1.json"))
    private val publication = AtomicFile(File(context.filesDir, "publication-v1.json"))
    private val imports = AtomicFile(File(context.filesDir, "playlists-v1.json"))
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
        if (publication.baseFile.exists()) PublishedCatalogue.decode(PublishedCatalogue.cachedFiles(String(publication.readFully(), Charsets.UTF_8)))
        else if (cache.baseFile.exists()) CatalogueCodec.decode(String(cache.readFully(), Charsets.UTF_8)).takeUnless { it.developmentFixture } ?: Catalogue(null, emptyList())
        else Catalogue(null, emptyList())
    }
    suspend fun refresh(endpoint: String, previous: Catalogue): Catalogue = lock.withLock { withContext(Dispatchers.IO) {
        require(safeUrl(endpoint)) { "Catalogue endpoint must be HTTPS" }
        val next = PublishedCatalogue.load(endpoint.ifBlank { BuildConfig.CATALOGUE_MANIFEST_URL }, previous, ::fetch)
        if (next == null) previous else {
            atomicWrite(publication, PublishedCatalogue.cache(next.files))
            next.catalogue
        }
    } }
    suspend fun readFile(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = readBounded(input)
            require(bytes.size <= 5_000_000) { "Playlist too large" }
            String(bytes, Charsets.UTF_8)
        } ?: error("Unable to read document")
    }
    suspend fun fetch(raw: String): String = withContext(Dispatchers.IO) {
        require(safeUrl(raw)) { "Only valid HTTPS URLs are supported" }
        var failure: Exception? = null
        repeat(2) { attempt ->
            try {
                var url = raw
                repeat(4) {
                    require(safeUrl(url))
                    val connection = URI(url).toURL().openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
                    try {
                        if (connection.responseCode in 300..399) {
                            url = URI(url).resolve(connection.getHeaderField("Location") ?: error("Invalid redirect")).toString()
                        } else {
                            require(connection.responseCode in 200..299) { "Server unavailable" }
                            val bytes = connection.inputStream.use { readBounded(it) }
                            require(bytes.size <= 5_000_000) { "Response too large" }
                            return@withContext String(bytes, Charsets.UTF_8)
                        }
                    } finally { connection.disconnect() }
                }
                error("Too many redirects")
            } catch (e: Exception) { failure = e; if (attempt == 0) delay(500) }
        }
        throw failure ?: error("Network unavailable")
    }
    suspend fun playlists(): List<Playlist> = withContext(Dispatchers.IO) {
        if (!imports.baseFile.exists()) return@withContext emptyList()
        val root = JSONObject(String(imports.readFully(), Charsets.UTF_8))
        require(root.getInt("schemaVersion") == 1) { "Unsupported private store version" }
        val rows = root.getJSONArray("playlists")
        (0 until rows.length()).map { i ->
            val p = rows.getJSONObject(i)
            val channels = CatalogueCodec.decode(JSONObject().put("schemaVersion", 1).put("revision", 1).put("channels", p.getJSONArray("channels")).toString()).channels
            Playlist(p.getString("id"), p.getString("name"), channels.map { it.copy(playlistId = p.getString("id")) })
        }
    }
    suspend fun savePlaylists(playlists: List<Playlist>) = lock.withLock { withContext(Dispatchers.IO) {
        val root = JSONObject().put("schemaVersion", 1).put("playlists", JSONArray(playlists.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("channels", JSONArray(p.channels.map { c ->
                CatalogueCodec.channelJson(c.copy(country = c.country.ifBlank { "Unknown" }, provider = "Private user import"))
            }))
        }))
        atomicWrite(imports, root.toString())
    } }
    private fun atomicWrite(file: AtomicFile, text: String) {
        val stream = file.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size() + count <= 5_000_000) { "Response too large" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
