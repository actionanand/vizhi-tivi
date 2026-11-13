package com.actionanand.vizhitivi.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Private VZTV import only. It never changes or publishes the signed official catalogue.
 * As with M3U, choosing a private playlist opts into trying the supplied HTTPS links;
 * this does not grant rights to redistribute them.
 */
fun parseVztv(raw: String, playlistId: String): List<Channel> {
    require(raw.toByteArray(Charsets.UTF_8).size <= 12_000_000) { "VZTV file too large" }
    val root = JSONObject(raw.removePrefix("\uFEFF"))
    require(root.optString("format") == "app.vizhi-tivi.private-channel-collection")
    require(root.optInt("schemaVersion", -1) == 1) { "Unsupported VZTV version" }
    require(root.optJSONObject("collection")?.optString("visibility") == "private") {
        "VZTV collection must be private"
    }
    val rows = root.getJSONArray("channels")
    require(rows.length() in 1..10_000) { "Invalid VZTV channel count" }
    val seen = mutableSetOf<String>()
    val result = ArrayList<Channel>(rows.length())
    val news = Regex("\\bnews\\b", RegexOption.IGNORE_CASE)
    val channelId = Regex("[a-zA-Z0-9._-]{1,160}")
    for (i in 0 until rows.length()) {
        val row = rows.getJSONObject(i)
        val originalId = row.getString("id")
        val name = row.getString("name").trim()
        require(channelId.matches(originalId) && seen.add(originalId) && name.isNotBlank() && name.length <= 500) {
            "Invalid or duplicate VZTV channel"
        }
        val category = row.optString("category").ifBlank { "Imported TV" }
        if (news.containsMatchIn(category) || news.containsMatchIn(name)) continue
        val rawStreams = row.getJSONArray("streams")
        require(rawStreams.length() <= 8) { "Too many VZTV streams" }
        val sourceLanguages = (row.optJSONArray("languages") ?: JSONArray()).let { values ->
            (0 until values.length()).mapNotNull { index ->
                (values.opt(index) as? String)?.takeIf { it.length < 80 }?.let(::languageCode)
            }.distinct().ifEmpty { listOf("und") }
        }
        val urls = mutableSetOf<String>()
        val streams = (0 until rawStreams.length()).mapNotNull { index ->
            val stream = rawStreams.optJSONObject(index) ?: return@mapNotNull null
            val url = stream.optString("url")
            if (!safeUrl(url) || !urls.add(url)) return@mapNotNull null
            val format = stream.optString("format").takeIf { it in listOf("hls", "dash", "progressive") }
                ?: when {
                    url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> "dash"
                    url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> "hls"
                    else -> "progressive"
                }
            Stream(url, format, "approved", true)
        }
        val originalLogo = row.optString("logo")
        result.add(Channel(
            id = "import.${playlistId.take(24)}.${stableId(originalId).take(24)}",
            number = null,
            name = name,
            languages = sourceLanguages,
            country = row.optString("country").ifBlank { "Unknown" },
            category = category,
            provider = row.optString("origin").ifBlank { "Private user import" },
            source = "Private user import",
            logo = originalLogo.takeIf(::safeUrl).orEmpty(),
            streams = streams,
            playlistId = playlistId,
            epgId = originalId,
            sourceLanguages = sourceLanguages
        ))
    }
    require(result.isNotEmpty()) { "No non-news TV channels in VZTV collection" }
    return result
}

fun isVztv(raw: String): Boolean = raw.removePrefix("\uFEFF").trimStart().startsWith("{")
