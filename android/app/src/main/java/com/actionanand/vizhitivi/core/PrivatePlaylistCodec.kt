package com.actionanand.vizhitivi.core

import org.json.JSONObject

data class PlaylistLoad(val playlists: List<Playlist>, val complete: Boolean)

object PrivatePlaylistCodec {
    fun decode(raw: String): PlaylistLoad {
        val root = JSONObject(raw)
        require(root.get("schemaVersion") is Int && root.getInt("schemaVersion") == 1) { "Unsupported private store version" }
        val rows = root.getJSONArray("playlists")
        var complete = true
        val ids = mutableSetOf<String>()
        val channelIds = mutableSetOf<String>()
        val numbers = mutableSetOf<Int>()
        val playlists = (0 until rows.length()).mapNotNull { i ->
            try {
                val p = rows.getJSONObject(i)
                require(p.get("id") is String && p.get("name") is String)
                val channels = CatalogueCodec.decode(JSONObject().put("schemaVersion", 1).put("revision", 1).put("channels", p.getJSONArray("channels")).toString()).channels
                val id = p.getString("id").also { require(it.isNotBlank() && it !in ids) }
                require(channels.none { it.id in channelIds || (it.number != null && it.number in numbers) }) { "Private record collision" }
                ids.add(id); channelIds.addAll(channels.map { it.id }); numbers.addAll(channels.mapNotNull { it.number })
                Playlist(id, p.getString("name"), channels.map { it.copy(playlistId = id) })
            } catch (_: Exception) { complete = false; null }
        }
        return PlaylistLoad(playlists, complete)
    }
}
