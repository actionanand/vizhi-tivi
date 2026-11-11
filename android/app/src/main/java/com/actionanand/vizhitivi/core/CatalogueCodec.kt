package com.actionanand.vizhitivi.core

import org.json.JSONArray
import org.json.JSONObject

object CatalogueCodec {
    private fun integer(value: Any): Int {
        require(value is Number && value.toDouble().isFinite() && value.toDouble() in 1.0..2147483647.0 && value.toDouble() == value.toInt().toDouble()) { "Invalid integer" }
        return value.toInt()
    }
    fun decode(text: String, previous: Catalogue? = null): Catalogue {
        require(text.length <= 5_000_000)
        val root = JSONObject(text)
        require(integer(root.get("schemaVersion")) == 1) { "Unsupported catalogue version" }
        require(!root.has("developmentFixture") || root.get("developmentFixture") is Boolean)
        val rev = integer(root.get("revision"))
        require(rev >= (previous?.revision ?: 1)) { "Invalid or older revision" }
        val rows = root.getJSONArray("channels")
        require(rows.length() <= 10000)
        val channels = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            fun required(key: String) = row.getString(key).also { require(it.isNotBlank()) { "Missing $key" } }
            val id = required("id").also { require(it.matches(Regex("[a-zA-Z0-9._-]{1,160}"))) }
            val langs = row.getJSONArray("languages").let { a -> (0 until a.length()).map { a.getString(it).also { s -> require(s.length >= 2) } } }
            require(langs.isNotEmpty())
            require(row.has("number")) { "Missing number" }
            val number = if (row.isNull("number")) null else integer(row.get("number"))
            listOf("logo", "epgId", "description").forEach { key -> require(!row.has(key) || row.get(key) is String) }
            val logo = row.optString("logo").also { require(it.isEmpty() || safeUrl(it)) }
            val streams = row.getJSONArray("streams").let { a -> (0 until a.length()).map {
                val s = a.getJSONObject(it)
                val url = s.getString("url").also { value -> require(safeUrl(value)) }
                val format = s.getString("format").also { value -> require(value in listOf("hls", "dash", "progressive")) }
                val status = s.getString("status").also { value -> require(value in listOf("approved", "unavailable", "unreviewed")) }
                require(s.get("permitted") is Boolean)
                Stream(url, format, status, s.getBoolean("permitted"))
            } }
            Channel(id, number, required("name"), langs, required("country"), row.getString("region"), required("category"),
                required("provider"), required("source"), logo, streams, epgId = row.optString("epgId"), description = row.optString("description"))
        }
        // Reject changed published assignments for surviving IDs rather than silently rewriting them.
        previous?.channels?.associateBy { it.id }?.let { old -> channels.forEach { c ->
            old[c.id]?.number?.let { n -> require(c.number == n) { "Published number changed for surviving ID" } }
        } }
        allocate(channels) // validates duplicate IDs, collisions and language ranges
        return Catalogue(rev, channels, root.optBoolean("developmentFixture"))
    }

    fun channelJson(c: Channel): JSONObject = JSONObject().apply {
        put("id", c.id); put("number", c.number ?: JSONObject.NULL); put("name", c.name)
        put("languages", JSONArray(c.languages)); put("country", c.country); put("region", c.region)
        put("category", c.category); put("provider", c.provider); put("source", c.source); put("logo", c.logo)
        put("epgId", c.epgId); put("description", c.description)
        put("streams", JSONArray(c.streams.map { s -> JSONObject().apply {
            put("url", s.url); put("format", s.format); put("status", s.status); put("permitted", s.permitted)
        } }))
    }
}
