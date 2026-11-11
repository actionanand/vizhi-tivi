package com.actionanand.vizhitivi.core

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class DomainTest {
    private fun c(id: String, tamil: Boolean = true, number: Int? = null) = Channel(id, number, id, listOf(if (tamil) "ta" else "en"))
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    @Test fun stableAssignmentsSurviveOrderingAndRename() {
        val initial = allocate(listOf(c("b"), c("a"))).associate { it.id to it.number!! }
        val next = allocate(listOf(c("b").copy(name = "Renamed"),c("new"),c("a")), initial)
        assertEquals(initial["b"],next.first().number); assertEquals(3,next[1].number)
    }
    @Test fun genuinelyVacatedSlotsReused() { assertEquals(1,allocate(listOf(c("new"),c("b")),mapOf("removed" to 1,"b" to 2)).first().number) }
    @Test fun nonTamilStartsAt251() { assertEquals(251,allocate(listOf(c("a",false))).single().number) }
    @Test fun collisionRejected() { assertThrows(IllegalArgumentException::class.java) { allocate(listOf(c("a",number=1),c("b",number=1))) } }
    @Test fun duplicateIdRejected() { assertThrows(IllegalArgumentException::class.java) { allocate(listOf(c("a"),c("a"))) } }
    @Test fun rangesEnforced() { assertThrows(IllegalArgumentException::class.java) { allocate(listOf(c("a",false,1))) } }
    @Test fun tamilExhaustionIsPending() { assertNull(allocate((1..251).map { c("id"+it.toString().padStart(3,'0')) }).last().number) }
    @Test fun reservedOfficialSlotsNotReused() { assertEquals(3,allocate(listOf(c("a")),reserved=setOf(1,2)).single().number) }
    @Test fun parserParityFixture() {
        val records = parseM3u(fixture("playlist.m3u"),"test")
        assertEquals(2,records.size); assertEquals("Sample, Tamil",records[0].name); assertEquals(listOf("ta"),records[0].languages)
        assertEquals("hls",records[0].playable!!.format);assertEquals("dash",records[1].playable!!.format)
        val expected = JSONObject(fixture("playlist-vectors.json")).getJSONArray("expected")
        records.forEachIndexed { i, c -> assertEquals(expected.getJSONObject(i).getString("id"), c.id) }
    }
    @Test fun reimportKeepsIdentityAcrossNameAndUrlChanges() {
        val text=fixture("playlist.m3u")
        assertEquals(parseM3u(text,"test").first().id,parseM3u(text.replace("Sample, Tamil","Renamed").replace("/live.m3u8","/other.m3u8"),"test").first().id)
    }
    @Test fun languageDoesNotUseSpellingHeuristics() { assertEquals("und",languageCode("")); assertEquals("ta",languageCode("Tamil"));assertEquals("ta",languageCode("tam")) }
    @Test fun unsafeSchemesAndCredentialsRejected() { listOf("http://example.invalid/a","file:///a","javascript:alert(1)","https://user:secret@example.invalid/").forEach { assertFalse(safeUrl(it)) } }
    @Test fun invalidPlaylistRejected() { assertThrows(IllegalArgumentException::class.java) { parseM3u("not a playlist","p") } }
    @Test fun catalogueMetadataOnlyFixture() { val decoded=CatalogueCodec.decode(fixture("catalogue-v1.json"));assertTrue(decoded.developmentFixture);assertEquals(2,decoded.channels.size);assertNull(decoded.channels.first().playable) }
    @Test fun futureSchemaRejected() { assertThrows(IllegalArgumentException::class.java) { CatalogueCodec.decode(fixture("catalogue-v1.json").replace("\"schemaVersion\": 1","\"schemaVersion\": 2")) } }
    @Test fun changedPublishedNumberRejected() {
        val raw=fixture("catalogue-v1.json");val previous=CatalogueCodec.decode(raw)
        assertThrows(IllegalArgumentException::class.java) { CatalogueCodec.decode(raw.replace("\"number\": 1,","\"number\": 2,"), previous) }
    }
    @Test fun incompleteMetadataRejected() { val row=JSONObject(fixture("catalogue-v1.json"));row.getJSONArray("channels").getJSONObject(0).put("name","");assertThrows(IllegalArgumentException::class.java) {CatalogueCodec.decode(row.toString())} }
    @Test fun unreviewedStreamHasNoPlayAction() { assertNull(c("a").copy(streams=listOf(Stream("https://example.invalid/a","hls","unreviewed",true))).playable) }
    @Test fun multipleLanguageMetadataAndExtensionlessMime() {
        val rows = parseM3u("#EXTM3U\n#EXTINF:-1 tvg-language=\"Tamil;English\" type=\"application/vnd.apple.mpegurl\",Test\nhttps://example.invalid/watch", "test")
        assertEquals(listOf("ta", "en"), rows.single().languages)
        assertEquals("hls", rows.single().streams.single().format)
    }
}
