package com.actionanand.vizhitivi.core

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PublishedCatalogueTest {
    private val vectors get() = JSONObject(javaClass.classLoader!!.getResource("official-v1/vectors.json")!!.readText())
    private fun files(kind: String = "synthetic"): MutableMap<String, String> {
        val root = vectors.getJSONObject(kind).getJSONObject("files")
        return root.keys().asSequence().associateWith { root.getString(it) }.toMutableMap()
    }
    private fun changed(path: String, edit: (JSONObject) -> Unit): Map<String, String> {
        val files = files(); val root = JSONObject(files.getValue(path)); edit(root); files[path] = root.toString()
        val m = JSONObject(files.getValue("manifest.json")); m.put("contentSha256", PublishedCatalogue.digest(files)); files["manifest.json"] = m.toString()
        return files
    }
    private val channels get() = PublishedCatalogue.paths[1]
    private val streams get() = PublishedCatalogue.paths[2]
    private fun rejected(files: Map<String, String>) { assertThrows(Exception::class.java) { PublishedCatalogue.decode(files) } }

    @Test fun exactPublishedEmptySnapshotAndDigest() {
        val c = PublishedCatalogue.decode(files("empty")); assertTrue(c.channels.isEmpty()); assertNull(c.revision)
        assertEquals("64fcd16817abc546d7d244c47cbe1b3afb82d9105b47f6ab117370b500feedf4", c.contentSha256)
    }
    @Test fun mobileAndTvMatchCommonWebProjection() {
        val c = PublishedCatalogue.decode(files()); val expected = vectors.getJSONObject("synthetic").getJSONArray("expected")
        assertEquals(expected.length(), c.channels.size)
        c.channels.forEachIndexed { i, channel ->
            val row = expected.getJSONObject(i)
            fun list(key: String) = row.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals(row.getString("id"), channel.id); assertEquals(row.getInt("number"), channel.number)
            assertEquals(list("languages"), channel.languages); assertEquals(list("streamIds"), channel.streams.map { it.id })
            assertEquals(list("statuses"), channel.streams.map { it.status }); assertEquals(row.getString("playable"), channel.playable?.id)
            assertEquals(row.getBoolean("trial"), channel.trial)
        }
        assertEquals("Synthetic fixture provider", c.channels.first().provider)
        assertTrue(c.channels.first().streams.first().rightsEvidenceUrl!!.startsWith("https://example.org/rights/"))
    }
    @Test fun completeCacheRoundTrip() { val f = files(); assertEquals(PublishedCatalogue.decode(f), PublishedCatalogue.decode(PublishedCatalogue.cachedFiles(PublishedCatalogue.cache(f)))) }
    @Test fun localeAndSchemaFilesParticipateInIntegrity() {
        for (path in listOf(channels, streams, PublishedCatalogue.paths[4], PublishedCatalogue.paths[7])) {
            val f = files(); f[path] = JSONObject(f.getValue(path)).put("tampered", true).toString(); rejected(f)
        }
    }
    @Test fun unsupportedVersionsRejected() {
        for (path in listOf("manifest.json", channels, streams)) { val f = files(); f[path] = JSONObject(f.getValue(path)).put("schemaVersion", 2).toString(); rejected(f) }
        rejected(changed(PublishedCatalogue.paths[6]) { it.getJSONObject("properties").getJSONObject("schemaVersion").put("const", 2) })
    }
    @Test fun duplicateChannelIdsAndNumbersRejected() {
        for (field in listOf("id", "number")) rejected(changed(channels) { val a = it.getJSONArray("channels"); a.getJSONObject(1).put(field, a.getJSONObject(0).get(field)) })
    }
    @Test fun duplicateStreamIdsRejected() { rejected(changed(streams) { val a = it.getJSONArray("streams"); a.getJSONObject(1).put("id", a.getJSONObject(0).getString("id")) }) }
    @Test fun missingDuplicateAndCrossChannelReferencesRejected() {
        for (refs in listOf(listOf("unknown-stream"), listOf("tam-a-trial", "tam-a-trial"), listOf("eng-trial"))) {
            rejected(changed(channels) { it.getJSONArray("channels").getJSONObject(0).put("streamIds", org.json.JSONArray(refs)) })
        }
    }
    @Test fun orphanOwnerRejected() { rejected(changed(streams) { it.getJSONArray("streams").getJSONObject(0).put("channelId", "unknown-channel") }) }
    @Test fun invalidPlaybackDateFormatAndSchemeRejected() {
        for ((key, value) in listOf("playbackStatus" to "approved", "format" to "drm", "lastVerifiedAt" to "2026-02-30", "url" to "http://example.com/live")) {
            rejected(changed(streams) { it.getJSONArray("streams").getJSONObject(0).put(key, value) })
        }
    }
    @Test fun languageAndNumberingPolicyRejectedIfInconsistent() {
        for (lang in listOf("ta", "eng")) rejected(changed(channels) { it.getJSONArray("channels").getJSONObject(0).put("languages", org.json.JSONArray(listOf(lang))) })
    }
    @Test fun survivingNumbersPreserved() {
        val old = PublishedCatalogue.decode(files()); val next = changed(channels) { it.getJSONArray("channels").getJSONObject(0).put("number", 8) }
        assertThrows(Exception::class.java) { PublishedCatalogue.decode(next, old) }
    }
    @Test fun digestIsNotAnIntegerRevision() {
        val old = PublishedCatalogue.decode(files()); val next = PublishedCatalogue.decode(changed(channels) { it.getJSONArray("channels").getJSONObject(0).put("name", "Renamed channel") }, old)
        assertNull(next.revision); assertNotEquals(old.contentSha256, next.contentSha256); assertEquals(old.channels.first().id, next.channels.first().id)
    }
    @Test fun manifestCountMismatchRejected() { val f = files(); f["manifest.json"] = JSONObject(f.getValue("manifest.json")).put("channelCount", 999).toString(); rejected(f) }
    @Test fun allFilesFetchedUnderOverrideAndManifestBracketed() = runBlocking {
        val f = files(); val urls = mutableListOf<String>(); val endpoint = "https://mirror.example/data/manifest.json"
        val result = PublishedCatalogue.load(endpoint, Catalogue(null, emptyList())) { url -> urls += url; f.getValue(url.removePrefix("https://mirror.example/data/")) }
        assertEquals(4, result!!.catalogue.channels.size); assertEquals(9, urls.size); assertEquals(endpoint, urls.first()); assertEquals(endpoint, urls.last())
        assertEquals(PublishedCatalogue.paths.toSet(), urls.map { it.removePrefix("https://mirror.example/data/") }.toSet())
    }
    @Test fun unchangedDigestFetchesOnlyOneManifest() = runBlocking {
        val f = files(); val old = PublishedCatalogue.decode(f); var calls = 0
        assertNull(PublishedCatalogue.load("https://mirror.example/manifest.json", old) { calls++; f.getValue("manifest.json") }); assertEquals(1, calls)
    }
    @Test fun mixedDeploymentRejected() = runBlocking {
        val f = files(); var reads = 0
        try {
            PublishedCatalogue.load("https://mirror.example/manifest.json", Catalogue(null, emptyList())) { url ->
                val path = java.net.URI(url).path.removePrefix("/")
                if (path == "manifest.json" && ++reads == 2) JSONObject(f.getValue(path)).put("contentSha256", "0".repeat(64)).toString() else f.getValue(path)
            }
            fail("Mixed deployment must be rejected")
        } catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("changed during refresh")) }
    }
    @Test fun maliciousManifestPathRejectedBeforePayloadFetch() = runBlocking {
        val raw = JSONObject(files().getValue("manifest.json")); raw.getJSONObject("paths").put("streams", "../private.json"); var calls = 0
        try { PublishedCatalogue.load("https://mirror.example/manifest.json", Catalogue(null, emptyList())) { calls++; raw.toString() }; fail("Invalid path accepted") }
        catch (_: IllegalArgumentException) { assertEquals(1, calls) }
    }
}
