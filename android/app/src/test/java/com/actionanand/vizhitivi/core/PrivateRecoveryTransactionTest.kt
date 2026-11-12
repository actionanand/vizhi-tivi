package com.actionanand.vizhitivi.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

class PrivateRecoveryTransactionTest {
    @get:Rule val temporary = TemporaryFolder()

    private class DiskWrite(private val directory: File, private val failure: String? = null) : PrivateRecoveryWrite {
        val activeFile = File(directory, "active.json")
        val pendingFile = File(directory, "active.json.new")
        var aborted = false
        override fun start(): OutputStream {
            if (failure == "start") error("start")
            val file = pendingFile.outputStream()
            if (failure != "partial") return file
            return object : OutputStream() {
                override fun write(value: Int) { file.write(value); error("partial write") }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    file.write(bytes, offset, minOf(length, 8)); error("partial write")
                }
                override fun close() = file.close()
            }
        }
        override fun sync(stream: OutputStream) {
            if (failure == "sync") error("sync")
            (stream as FileOutputStream).fd.sync()
        }
        override fun staged(): ByteArray = if (failure == "staged") byteArrayOf(0) else pendingFile.readBytes()
        override fun finish(stream: OutputStream) {
            if (failure == "before-rename") error("before rename")
            stream.close()
            Files.move(pendingFile.toPath(), activeFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            if (failure == "after-rename") error("after rename")
        }
        override fun abort(stream: OutputStream) { aborted = true; stream.close(); pendingFile.delete() }
        override fun active(): ByteArray {
            if (failure == "active-verification") error("verification unavailable")
            return activeFile.readBytes()
        }
    }

    @Test fun precommitFailuresLeaveActiveBytesUntouched() {
        for (failure in listOf("start", "partial", "sync", "staged", "before-rename")) {
            val disk = DiskWrite(temporary.newFolder(), failure)
            disk.activeFile.writeText("previous")
            assertThrows(Exception::class.java) { commitPrivateRecovery(disk, "candidate".toByteArray()) }
            assertEquals("previous", disk.activeFile.readText())
            assertEquals(failure in listOf("partial", "sync", "staged"), disk.aborted)
            if (failure == "before-rename") assertEquals("candidate", disk.pendingFile.readText())
        }
    }

    @Test fun successfulCommitIsVerifiedFromDisk() {
        val disk = DiskWrite(temporary.newFolder())
        disk.activeFile.writeText("previous")
        commitPrivateRecovery(disk, "candidate".toByteArray())
        assertEquals("candidate", disk.activeFile.readText())
        assertFalse(disk.pendingFile.exists())
    }

    @Test fun afterCommitOrVerificationFailureDoesNotPerformAnUnsafeRollback() {
        for (failure in listOf("after-rename", "active-verification")) {
        val directory = temporary.newFolder()
        val disk = DiskWrite(directory, failure)
        disk.activeFile.writeText("previous")
        val snapshots = RecoverySnapshots(directory.resolve("snapshots"), directory.resolve("legacy"), syncDirectory = {})
        val prior = snapshots.capture(disk.activeFile.readBytes())
        assertThrows(IllegalStateException::class.java) { commitPrivateRecovery(disk, "candidate".toByteArray()) }
        assertEquals("candidate", disk.activeFile.readText())
        assertEquals("previous", String(snapshots.exportBytes(prior.id)))
        assertFalse(disk.aborted)
        }
    }

    @Test fun snapshotCapacityFailureHappensBeforeActiveReplacement() {
        val directory = temporary.newFolder()
        val disk = DiskWrite(directory)
        disk.activeFile.writeText("previous")
        val snapshots = RecoverySnapshots(directory.resolve("snapshots"), directory.resolve("legacy"), 1, syncDirectory = {})
        snapshots.capture("older".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            snapshots.capture(disk.activeFile.readBytes())
            commitPrivateRecovery(disk, "candidate".toByteArray())
        }
        assertEquals("previous", disk.activeFile.readText())
        assertFalse(disk.pendingFile.exists())
    }

    @Test fun restartAndRepeatedRecoveryPreserveValidPlaylistMetadata() {
        val directory = temporary.newFolder()
        fun data(id: String, number: Int): ByteArray {
            val channel = Channel(id, number, id, listOf("ta"), country = "Unknown", provider = "Private user import", source = "private", languageOverride = "ta", sourceLanguages = listOf("en"))
            val playlist = JSONObject().put("id", "playlist").put("name", "Playlist")
                .put("channels", JSONArray().put(CatalogueCodec.channelJson(channel)))
            return JSONObject().put("schemaVersion", 1).put("playlists", JSONArray().put(playlist)).toString().toByteArray()
        }
        val old = data("old", 7)
        val candidate = data("new", 8)
        val partial = DiskWrite(directory, "partial")
        partial.activeFile.writeBytes(old)
        assertThrows(IllegalStateException::class.java) { commitPrivateRecovery(partial, candidate) }
        val restarted = DiskWrite(directory, "sync")
        assertEquals("old", PrivatePlaylistCodec.decode(restarted.activeFile.readText()).playlists.single().channels.single().id)
        assertThrows(IllegalStateException::class.java) { commitPrivateRecovery(restarted, candidate) }
        commitPrivateRecovery(DiskWrite(directory), candidate)
        val restored = PrivatePlaylistCodec.decode(DiskWrite(directory).activeFile.readText()).playlists.single().channels.single()
        assertEquals("new", restored.id)
        assertEquals(8, restored.number)
        assertEquals("ta", restored.languageOverride)
        assertEquals(listOf("en"), restored.sourceLanguages)
    }
}
