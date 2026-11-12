package com.actionanand.vizhitivi.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecoverySnapshotsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun manager(root: File, limit: Int = 5, boundary: (String) -> Unit = {}) =
        RecoverySnapshots(root, root.resolve("legacy.json"), limit, syncDirectory = {}, boundary = boundary)

    private fun payload(id: String): ByteArray {
        val channel = Channel(id, 1, id, listOf("ta"), country = "Unknown", provider = "Private user import", source = "private")
        val playlist = JSONObject().put("id", id).put("name", id)
            .put("channels", JSONArray().put(CatalogueCodec.channelJson(channel)))
        return JSONObject().put("schemaVersion", 1).put("playlists", JSONArray().put(playlist))
            .toString().toByteArray(Charsets.UTF_8)
    }

    @Test fun multipleSnapshotsRemainIndependentAcrossRestart() {
        val root = temporary.newFolder()
        val saved = (1..3).map { manager(root).capture(payload("channel$it")) }
        assertEquals(3, manager(root).list().size)
        saved.forEachIndexed { i, record ->
            assertEquals("verified", record.integrity)
            assertEquals(1, record.recoverableCount)
            assertTrue(record.complete)
            assertArrayEquals(payload("channel${i + 1}"), manager(root).exportBytes(record.id))
        }
    }

    @Test fun capacityAndLastRecoverableSnapshotFailClosed() {
        val root = temporary.newFolder()
        val manager = manager(root, 2)
        val first = manager.capture(payload("first"))
        manager.capture("broken".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { manager.capture(payload("third")) }
        assertThrows(IllegalArgumentException::class.java) { manager.delete(first.id) }
        assertArrayEquals(payload("first"), manager.exportBytes(first.id))
    }

    @Test fun corruptMetadataIsVisibleAndExportable() {
        val root = temporary.newFolder()
        val manager = manager(root)
        val saved = manager.capture(payload("first"))
        val file = root.resolve("snapshot-${saved.id}.json")
        file.writeText("{damaged")
        assertEquals("invalid", manager.list().single().integrity)
        assertEquals("{damaged", String(manager.exportBytes(saved.id)))
        assertThrows(IllegalStateException::class.java) { manager.raw(saved.id) }
    }

    @Test fun interruptionsLeavePendingFilesVisibleButNeverRestorable() {
        for (phase in listOf("created", "written", "published")) {
            val root = temporary.newFolder()
            val original = manager(root).capture(payload("original"))
            val interrupted = manager(root, boundary = { if (it == phase) error("simulated interruption") })
            assertThrows(IllegalStateException::class.java) { interrupted.capture(payload("second")) }
            val restarted = manager(root)
            assertArrayEquals(payload("original"), restarted.exportBytes(original.id))
            val entries = restarted.list()
            assertEquals(2, entries.size)
            val second = entries.single { it.id != original.id }
            if (phase == "published") {
                assertEquals("verified", second.integrity)
                assertArrayEquals(payload("second"), restarted.exportBytes(second.id))
            } else {
                assertEquals("invalid", second.integrity)
                assertTrue(second.id.startsWith("pending:"))
                assertThrows(IllegalArgumentException::class.java) { restarted.raw(second.id) }
                assertTrue(restarted.exportBytes(second.id).isNotEmpty() || phase == "created")
                restarted.delete(second.id)
                assertEquals(1, restarted.list().size)
            }
            restarted.capture(payload("third"))
            assertArrayEquals(payload("original"), restarted.exportBytes(original.id))
        }
    }

    @Test fun invalidRestoredMarkerDoesNotClaimACompletedRecovery() {
        val root = temporary.newFolder()
        val manager = manager(root)
        val saved = manager.capture(payload("first"))
        root.resolve("${saved.id}.restored").writeBytes(byteArrayOf())
        assertEquals("captured", manager.list().single().status)
        manager.markRestored(saved.id)
        assertEquals("restored", manager.list().single().status)
    }

    @Test fun interruptedOrphanCountsTowardBoundedCapacity() {
        val root = temporary.newFolder()
        val first = manager(root, 2).capture(payload("first"))
        assertThrows(IllegalStateException::class.java) {
            manager(root, 2) { if (it == "written") error("interrupted") }.capture(payload("second"))
        }
        val restarted = manager(root, 2)
        assertThrows(IllegalArgumentException::class.java) { restarted.capture(payload("third")) }
        assertArrayEquals(payload("first"), restarted.exportBytes(first.id))
    }

    @Test fun directorySyncFailureNeverOverwritesExistingSnapshots() {
        for (failedSync in listOf(1, 2)) {
            val root = temporary.newFolder()
            val saved = manager(root).capture(payload("first"))
            var count = 0
            val failing = RecoverySnapshots(root, root.resolve("legacy.json"), syncDirectory = {
                if (++count == failedSync) error("sync failed")
            })
            assertThrows(IllegalStateException::class.java) { failing.capture(payload("second")) }
            val restarted = manager(root)
            assertArrayEquals(payload("first"), restarted.exportBytes(saved.id))
            val second = restarted.list().single { it.id != saved.id }
            assertEquals(if (failedSync == 1) "invalid" else "verified", second.integrity)
        }
    }

    @Test fun snapshotRetainsChannelIdentityNumberAndLanguageMetadata() {
        val root = temporary.newFolder()
        val channel = Channel("stable", 7, "Channel", listOf("ta"), country = "Unknown", provider = "Private user import", source = "private", languageOverride = "ta", sourceLanguages = listOf("en"))
        val playlist = JSONObject().put("id", "playlist").put("name", "Playlist")
            .put("channels", JSONArray().put(CatalogueCodec.channelJson(channel)))
        val data = JSONObject().put("schemaVersion", 1).put("playlists", JSONArray().put(playlist)).toString()
        val manager = manager(root)
        val id = manager.capture(data.toByteArray(Charsets.UTF_8)).id
        val decoded = PrivatePlaylistCodec.decode(manager(root).raw(id)).playlists.single().channels.single()
        assertEquals(channel.id, decoded.id)
        assertEquals(channel.number, decoded.number)
        assertEquals("ta", decoded.languageOverride)
        assertEquals(listOf("en"), decoded.sourceLanguages)
    }

    @Test fun interruptedDeleteRetainsAnotherRecoverableSnapshot() {
        val root = temporary.newFolder()
        val first = manager(root).capture(payload("first"))
        val second = manager(root).capture(payload("second"))
        val interrupted = manager(root, boundary = { if (it == "deleted") error("interrupted") })
        assertThrows(IllegalStateException::class.java) { interrupted.delete(first.id) }
        val restarted = manager(root)
        assertNull(restarted.list().find { it.id == first.id })
        assertArrayEquals(payload("second"), restarted.exportBytes(second.id))
        assertEquals("verified", restarted.list().single().integrity)
    }
}
