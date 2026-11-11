package com.actionanand.vizhitivi.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate

/** Adapter for vizhi-tivi-data's published API, separate from private/legacy JSON. */
object PublishedCatalogue {
    val paths = listOf("manifest.json", "catalog/v1/catalogue.json", "catalog/v1/streams.json",
        "catalog/v1/locales/en.json", "catalog/v1/locales/ta.json", "catalog/v1/locales/sa.json",
        "schema/catalogue-v1.schema.json", "schema/streams-v1.schema.json")
    data class Publication(val files: Map<String, String>, val catalogue: Catalogue)
    private fun obj(value: JsonElement?) = value as? JsonObject ?: error("Expected object")
    private fun keys(value: JsonObject, expected: Collection<String>) { require(value.keys == expected.toSet()) { "Incompatible fields" } }
    private fun str(value: JsonElement?, empty: Boolean = false): String {
        val p = value as? JsonPrimitive ?: error("Expected string")
        require(p.isString && (empty || p.content.isNotBlank())) { "Expected string" }
        return p.content
    }
    private fun integer(value: JsonElement?, min: Int = 0): Int {
        val p = value as? JsonPrimitive ?: error("Expected integer")
        require(!p.isString)
        val n = p.doubleOrNull ?: error("Expected integer")
        require(n.isFinite() && n in min.toDouble()..Int.MAX_VALUE.toDouble() && n.toInt().toDouble() == n)
        return n.toInt()
    }
    private fun array(value: JsonElement?): JsonArray = (value as? JsonArray ?: error("Expected array")).also { require(it.size <= 10000) }
    private fun strings(value: JsonElement?) = array(value).map { str(it) }
    private fun unique(values: List<String>) { require(values.distinct().size == values.size) { "Duplicate reference/identity" } }
    private fun id(value: JsonElement?) = str(value).also { require(it.matches(Regex("[a-z0-9][a-z0-9-]{2,99}"))) { "Invalid stable ID" } }
    private fun https(value: JsonElement?) = str(value).also { require(safeUrl(it) && URI(it).rawFragment == null) { "Expected HTTPS URL" } }
    private fun nullable(value: JsonElement?) = if (value == JsonNull) "" else str(value, true)
    private fun document(raw: String, field: String): JsonArray {
        val root = obj(Json.parseToJsonElement(raw)); keys(root, listOf("schemaVersion", field))
        require(integer(root["schemaVersion"]) == 1) { "Unsupported API schema" }
        return array(root[field])
    }
    fun manifest(raw: String): JsonObject {
        val m = obj(Json.parseToJsonElement(raw))
        require(integer(m["schemaVersion"]) == 1 && str(m["contentSha256"]).matches(Regex("[a-f0-9]{64}"))) { "Unsupported manifest" }
        integer(m["channelCount"]); integer(m["streamCount"])
        require(strings(m["uiLocales"]) == listOf("en", "ta", "sa")) { "Unsupported UI locales" }
        val numbering = obj(m["numbering"]); keys(numbering, listOf("tamil", "other"))
        val tamil = obj(numbering["tamil"]); val other = obj(numbering["other"])
        keys(tamil, listOf("start", "end")); keys(other, listOf("start"))
        require(integer(tamil["start"]) == 1 && integer(tamil["end"]) == 250 && integer(other["start"]) == 251)
        val p = obj(m["paths"]); keys(p, listOf("catalogue", "streams", "schemas", "locales"))
        require(str(p["catalogue"]) == paths[1] && str(p["streams"]) == paths[2]) { "Unsupported publication path" }
        val locales = obj(p["locales"]); keys(locales, listOf("en", "ta", "sa"))
        listOf("en", "ta", "sa").forEachIndexed { i, l -> require(str(locales[l]) == paths[i + 3]) }
        val schemas = obj(p["schemas"]); keys(schemas, listOf("catalogue", "streams"))
        require(str(schemas["catalogue"]) == paths[6] && str(schemas["streams"]) == paths[7])
        return m
    }

    // JSON.stringify retains non-index property insertion order. Kotlin JsonObject preserves
    // parsed order; numeric keys use JavaScript's integer-index ordering. Normalize number
    // spellings (e.g. 1.0 -> 1) and never sort ordinary schema/locale keys alphabetically.
    private fun canonical(value: JsonElement): String = when (value) {
        is JsonObject -> {
            fun index(key: String) = key.toLongOrNull()?.takeIf { it in 0..4294967294L && it.toString() == key }
            val entries = value.entries.sortedWith { a, b ->
                val x = index(a.key); val y = index(b.key)
                when { x != null && y != null -> x.compareTo(y); x != null -> -1; y != null -> 1; else -> 0 }
            }
            entries.joinToString(",", "{", "}") { JsonPrimitive(it.key).toString() + ":" + canonical(it.value) }
        }
        is JsonArray -> value.joinToString(",", "[", "]") { canonical(it) }
        is JsonPrimitive -> when {
            value == JsonNull -> "null"
            value.isString || value.booleanOrNull != null -> value.toString()
            else -> {
                val n = value.doubleOrNull ?: error("Invalid number")
                require(n.isFinite())
                if (n == 0.0) "0" else {
                    val decimal = BigDecimal.valueOf(n).stripTrailingZeros()
                    if (kotlin.math.abs(n) >= 1e-6 && kotlin.math.abs(n) < 1e21) decimal.toPlainString()
                    else decimal.toString().lowercase()
                }
            }
        }
    }
    fun digest(files: Map<String, String>): String {
        fun read(path: String) = Json.parseToJsonElement(files.getValue(path))
        val payload = buildJsonObject {
            put("catalogue", read(paths[1])); put("streamDoc", read(paths[2]))
            put("localeDoc", buildJsonObject { listOf("en", "ta", "sa").forEachIndexed { i, l -> put(l, read(paths[i + 3])) } })
            put("schemaDoc", buildJsonObject { put("catalogue", read(paths[6])); put("streams", read(paths[7])) })
        }
        return stableId(canonical(payload))
    }
    fun decode(files: Map<String, String>, previous: Catalogue? = null): Catalogue {
        require(files.keys == paths.toSet() && files.values.all { it.length <= 5_000_000 } && files.values.sumOf { it.length.toLong() } <= 10_000_000) { "Invalid publication files/size" }
        val m = manifest(files.getValue(paths[0]))
        val rows = document(files.getValue(paths[1]), "channels")
        val streamRows = document(files.getValue(paths[2]), "streams")
        require(rows.size == integer(m["channelCount"]) && streamRows.size == integer(m["streamCount"])) { "Publication count mismatch" }
        paths.takeLast(2).forEach { p ->
            val schema = obj(Json.parseToJsonElement(files.getValue(p)))
            require(integer(obj(obj(schema["properties"])["schemaVersion"])["const"]) == 1) { "Incompatible published schema" }
        }
        var localeKeys: Set<String>? = null
        paths.subList(3, 6).forEach { p ->
            val locale = obj(Json.parseToJsonElement(files.getValue(p)))
            require(locale.isNotEmpty() && (localeKeys == null || locale.keys == localeKeys)) { "Incompatible locale document" }
            locale.values.forEach { str(it) }; localeKeys = locale.keys
        }
        val hash = str(m["contentSha256"])
        require(digest(files) == hash) { "Publication integrity mismatch" }
        val streams = linkedMapOf<String, Pair<String, Stream>>()
        streamRows.forEach { value ->
            val s = obj(value); keys(s, listOf("id", "channelId", "url", "format", "playbackStatus", "lastVerifiedAt", "rightsEvidenceUrl"))
            val streamId = id(s["id"]); require(streamId !in streams) { "Duplicate stream ID" }
            val format = str(s["format"]).also { require(it in listOf("hls", "dash", "progressive")) }
            val status = str(s["playbackStatus"]).also { require(it in listOf("verified", "experimental")) }
            val date = str(s["lastVerifiedAt"]).also { require(it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))); LocalDate.parse(it) }
            streams[streamId] = id(s["channelId"]) to Stream(https(s["url"]), format, status, true, streamId, date, https(s["rightsEvidenceUrl"]))
        }
        val ids = mutableSetOf<String>(); val numbers = mutableSetOf<Int>(); val used = mutableSetOf<String>()
        val old = previous?.takeIf { it.contentSha256 != null }?.channels?.associateBy { it.id }.orEmpty()
        val channels = rows.map { value ->
            val c = obj(value); keys(c, listOf("id", "number", "name", "languages", "country", "region", "category", "broadcaster", "website", "logoUrl", "sourceUrls", "streamIds"))
            val channelId = id(c["id"]); val number = integer(c["number"], 1)
            require(ids.add(channelId) && numbers.add(number)) { "Duplicate channel ID/number" }
            val languages = strings(c["languages"]); unique(languages)
            require(languages.isNotEmpty() && languages.all { it.matches(Regex("[a-z]{3}")) })
            require(if ("tam" in languages) number <= 250 else number >= 251) { "Invalid number range" }
            val refs = strings(c["streamIds"]); unique(refs)
            val joined = refs.map { ref ->
                val stream = streams[ref] ?: error("Unknown stream reference")
                require(stream.first == channelId && used.add(ref)) { "Invalid stream owner/reference" }; stream.second
            }
            val sources = array(c["sourceUrls"]).map { https(it) }; require(sources.isNotEmpty()) { "Missing provenance" }; https(c["website"])
            val logo = nullable(c["logoUrl"]); if (logo.isNotEmpty()) https(JsonPrimitive(logo))
            val country = str(c["country"]).also { require(it.matches(Regex("[A-Z]{2}"))) }
            val category = str(c["category"]).also { require(it in listOf("general", "news", "sports", "music", "kids", "entertainment", "education", "culture", "religious", "other")) }
            old[channelId]?.let { require(it.number == number) { "Surviving published assignment changed" } }
            Channel(channelId, number, str(c["name"]), languages.map(::languageCode), country,
                nullable(c["region"]), category, nullable(c["broadcaster"]), sources.joinToString("\n"), logo, joined)
        }
        require(used.size == streams.size) { "Orphan stream record" }
        return Catalogue(null, channels, contentSha256 = hash)
    }
    fun cache(files: Map<String, String>): String = buildJsonObject {
        put("cacheVersion", 1); put("files", buildJsonObject { files.forEach { (path, raw) -> put(path, raw) } })
    }.toString()
    fun cachedFiles(raw: String): Map<String, String> {
        val cache = obj(Json.parseToJsonElement(raw)); keys(cache, listOf("cacheVersion", "files"))
        require(integer(cache["cacheVersion"]) == 1)
        val files = obj(cache["files"]); keys(files, paths)
        return files.mapValues { str(it.value) }
    }
    suspend fun load(endpoint: String, previous: Catalogue, fetch: suspend (String) -> String): Publication? {
        https(JsonPrimitive(endpoint))
        val first = fetch(endpoint); val m = manifest(first)
        if (str(m["contentSha256"]) == previous.contentSha256) {
            require(integer(m["channelCount"]) == previous.channels.size && integer(m["streamCount"]) == previous.channels.sumOf { it.streams.size })
            return null
        }
        val base = URI(endpoint).resolve(".")
        val fetched = coroutineScope { paths.drop(1).map { path -> async { path to fetch(base.resolve(path).toString()) } }.awaitAll().toMap() }
        require(manifest(fetch(endpoint)) == m) { "Publication changed during refresh" }
        val files = mapOf(paths[0] to first) + fetched
        return Publication(files, decode(files, previous))
    }
}
