package com.actionanand.vizhitivi.core

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class VztvCodecTest {
    private fun channel(
        id: String = "dd-tamil",
        name: String = "DD Tamil",
        category: String = "General",
        urls: List<String> = listOf("https://example.invalid/live.m3u8")
    ): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("category", category).put("country", "IN")
        .put("languages", JSONArray().put("tam").put("ta"))
        .put("streams", JSONArray().apply {
            urls.forEach { url -> put(JSONObject().put("url", url).put("format", "hls")) }
        })

    private fun pack(vararg channels: JSONObject): String = JSONObject()
        .put("format", "app.vizhi-tivi.private-channel-collection")
        .put("schemaVersion", 1)
        .put("collection", JSONObject().put("visibility", "private"))
        .put("channels", JSONArray().apply { channels.forEach(::put) }).toString()

    @Test fun supportsPrivateVztvMetadataAndPlayableStreams() {
        val rows = parseVztv(pack(channel()), "playlist")
        assertEquals(1, rows.size)
        assertEquals("DD Tamil", rows[0].name)
        assertEquals(listOf("ta"), rows[0].sourceLanguages)
        assertEquals("IN", rows[0].country)
        assertEquals("hls", rows[0].playable?.format)
        assertNull(rows[0].number)
    }

    @Test fun preservesMetadataOnlyAndSkipsNews() {
        val rows = parseVztv(pack(
            channel(urls = emptyList()),
            channel("news", "Tamil News", "News")
        ), "playlist")
        assertEquals(1, rows.size)
        assertNull(rows[0].playable)
    }

    @Test fun rejectsDuplicateIdsAndWrongSchemaOrVisibility() {
        assertThrows(Exception::class.java) { parseVztv(pack(channel(), channel()), "p") }
        assertThrows(Exception::class.java) {
            parseVztv(JSONObject(pack(channel())).put("schemaVersion", 2).toString(), "p")
        }
        assertThrows(Exception::class.java) {
            parseVztv(JSONObject(pack(channel()))
                .put("collection", JSONObject().put("visibility", "public")).toString(), "p")
        }
    }

    @Test fun rejectsUnsafeStreamsAndPreservesStableChannelIdentity() {
        val a = parseVztv(pack(channel(urls = listOf(
            "http://example.invalid/x", "https://example.invalid/live.m3u8",
            "https://example.invalid/live.m3u8"
        ))), "p").single()
        assertEquals(1, a.streams.size)
        val b = parseVztv(pack(channel(name = "DD Pothigai", urls = listOf("https://example.invalid/new.m3u8"))), "p").single()
        assertEquals(a.id, b.id)
        assertTrue(isVztv(pack(channel())))
        assertFalse(isVztv("#EXTM3U"))
    }
}
