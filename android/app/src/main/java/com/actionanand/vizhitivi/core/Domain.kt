package com.actionanand.vizhitivi.core

import java.net.URI
import java.security.MessageDigest

data class Stream(val url: String, val format: String, val status: String = "unreviewed", val permitted: Boolean = false,
    val id: String? = null, val lastVerifiedAt: String? = null, val rightsEvidenceUrl: String? = null)
data class Channel(
    val id: String, val number: Int?, val name: String, val languages: List<String>,
    val country: String = "", val region: String = "", val category: String = "Imported TV",
    val provider: String = "", val source: String = "", val logo: String = "",
    val streams: List<Stream> = emptyList(), val playlistId: String? = null,
    val epgId: String = "", val description: String = "", val languageOverride: String? = null, val sourceLanguages: List<String>? = null
) {
    val tamil get() = "ta" in languages
    val playable get() = streams.firstOrNull { safeUrl(it.url) && it.permitted && it.status in listOf("approved", "verified") }
        ?: streams.firstOrNull { safeUrl(it.url) && it.permitted && it.status == "experimental" }
    val trial get() = playable?.status == "experimental"
}
data class Catalogue(val revision: Int?, val channels: List<Channel>, val developmentFixture: Boolean = false,
    val contentSha256: String? = null)
data class Playlist(val id: String, val name: String, val channels: List<Channel>)

fun safeUrl(raw: String): Boolean = runCatching {
    val uri = URI(raw)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
}.getOrDefault(false)
fun stableId(raw: String): String = MessageDigest.getInstance("SHA-256")
    .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
fun languageCode(raw: String): String = when (raw.trim().lowercase()) {
    "tamil", "tam", "தமிழ்", "ta" -> "ta"
    "english", "eng", "en" -> "en"
    "sanskrit", "san", "sa" -> "sa"
    else -> raw.trim().lowercase().takeIf { it.length >= 2 } ?: "und"
}
fun channelLanguages(raw: String): List<String> = raw.split(',', ';').map(::languageCode).distinct()
fun resetLanguageOverride(c: Channel): Channel {
    val detected = c.sourceLanguages ?: return c
    return c.copy(languages = detected, languageOverride = null, number = if (("ta" in c.languages) == ("ta" in detected)) c.number else null)
}

/** Full snapshots only: removed IDs vacate slots; surviving IDs keep assignments. */
fun allocate(channels: List<Channel>, previous: Map<String, Int> = emptyMap(), reserved: Set<Int> = emptySet()): List<Channel> {
    require(channels.map { it.id }.distinct().size == channels.size) { "Duplicate channel ID" }
    val used = reserved.toMutableSet()
    val assigned = mutableMapOf<String, Int>()
    channels.sortedBy { it.id }.forEach { c ->
        val n = previous[c.id] ?: c.number
        if (n != null) {
            require(n >= 1 && (if (c.tamil) n <= 250 else n >= 251)) { "Invalid number range" }
            require(used.add(n)) { "Channel number collision" }
            assigned[c.id] = n
        }
    }
    channels.sortedBy { it.id }.filter { it.id !in assigned }.forEach { c ->
        val n = if (c.tamil) (1..250).firstOrNull { it !in used }
            else generateSequence(251) { it + 1 }.first { it !in used }
        if (n != null) { assigned[c.id] = n; used += n }
    }
    return channels.map { it.copy(number = assigned[it.id]) }
}

fun parseM3u(text: String, playlistId: String): List<Channel> {
    require(text.length <= 5_000_000) { "Playlist too large" }
    val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.toList()
    require(lines.firstOrNull() == "#EXTM3U") { "Expected #EXTM3U playlist" }
    var info: String? = null
    val records = linkedMapOf<String, Channel>()
    val urls = mutableSetOf<String>()
    for (line in lines.drop(1)) {
        if (line.startsWith("#EXTINF:")) { info = line; continue }
        if (line.isBlank() || line.startsWith("#")) continue
        val meta = info ?: continue
        info = null
        if (!safeUrl(line)) continue
        val attrs = Regex("([\\w-]+)=\"([^\"]*)\"").findAll(meta).associate { it.groupValues[1] to it.groupValues[2] }
        // Split at the first comma outside quoted attributes; display names may contain commas.
        var quoted = false
        val comma = meta.indexOfFirst { ch -> if (ch == '"') quoted = !quoted; ch == ',' && !quoted }
        val name = (if (comma >= 0) meta.substring(comma + 1) else "").ifBlank { attrs["tvg-name"].orEmpty() }
        if (name.isBlank()) continue
        val identity = attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: line
        val id = "import.$playlistId.${stableId(identity)}"
        val mime = (attrs["mime-type"] ?: attrs["type"].orEmpty()).lowercase()
        val format = when {
            mime.contains("dash") || URI(line).path.endsWith(".mpd", true) -> "dash"
            mime.contains("mpegurl") || URI(line).path.endsWith(".m3u8", true) -> "hls"
            else -> "progressive"
        }
        if (id !in records && urls.add(line)) records[id] = Channel(
            id, null, name, channelLanguages(attrs["tvg-language"] ?: attrs["language"].orEmpty()),
            country = "Unknown", provider = "Private user import",
            category = attrs["group-title"].orEmpty().ifBlank { "Imported TV" }, logo = attrs["tvg-logo"].orEmpty().takeIf(::safeUrl).orEmpty(),
            streams = listOf(Stream(line, format, "approved", true)), playlistId = playlistId,
            source = "Private user import", epgId = attrs["tvg-id"].orEmpty(), sourceLanguages = channelLanguages(attrs["tvg-language"] ?: attrs["language"].orEmpty())
        )
    }
    require(records.isNotEmpty()) { "No supported HTTPS channels in playlist" }
    require(records.size <= 10000) { "Too many channels" }
    return records.values.toList()
}

/** No transport implementation or credentials until an explicit future sync integration. */
interface PrivateBackupTransport {
    suspend fun upload(versionedJson: String)
    suspend fun download(): String?
}
