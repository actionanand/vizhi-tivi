package com.actionanand.vizhitivi.core

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublicationTransactionTest {
    @get:Rule val temporary = TemporaryFolder()

    private class DiskWrite(directory: File, private val failure: String?, name: String = "publication.json") : PublicationWrite {
        val active = File(directory, name)
        val stage = File(directory, "$name.candidate")
        override fun start(): OutputStream {
            if (failure == "start") error("start")
            return stage.outputStream()
        }
        override fun sync(stream: OutputStream) {
            if (failure == "sync") error("sync")
            (stream as FileOutputStream).fd.sync()
        }
        override fun staged(): ByteArray = if (failure == "corrupt") byteArrayOf(0) else stage.readBytes()
        override fun rename() {
            if (failure == "rename") error("rename")
            Files.move(stage.toPath(), active.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        override fun syncDirectory() { if (failure == "directory") error("directory") }
        override fun abort() { stage.delete() }
    }

    @Test fun failuresBeforeRenamePreserveTheOldCache() {
        for (failure in listOf("start", "sync", "corrupt", "rename")) {
            val disk = DiskWrite(temporary.newFolder(), failure)
            disk.active.writeText("old")
            var committed = false
            assertThrows(Exception::class.java) { writePublicationCandidate(disk, "new".toByteArray()) { committed = true } }
            assertEquals("old", disk.active.readText())
            assertFalse(committed)
        }
    }

    @Test fun successfulRenameSurvivesDirectorySyncFailureWithoutRollback() {
        val disk = DiskWrite(temporary.newFolder(), "directory")
        disk.active.writeText("old")
        var committed = false
        assertThrows(IllegalStateException::class.java) { writePublicationCandidate(disk, "new".toByteArray()) { committed = true } }
        assertTrue(committed)
        assertEquals("new", disk.active.readText())
    }

    @Test fun successfulCommitPublishesCompleteCandidate() {
        val disk = DiskWrite(temporary.newFolder(), null)
        disk.active.writeText("old")
        var committed = false
        writePublicationCandidate(disk, "new".toByteArray()) { committed = true }
        assertTrue(committed)
        assertEquals("new", disk.active.readText())
        assertFalse(disk.stage.exists())
    }

    @Test fun stagedButUncommittedCandidateLeavesOldCacheUsable() {
        val disk = DiskWrite(temporary.newFolder(), null)
        disk.active.writeText("old")
        stagePublicationCandidate(disk, "new".toByteArray())
        assertEquals("old", disk.active.readText())
        assertEquals("new", disk.stage.readText())
        var committed = false
        finishPublicationCandidate(disk) { committed = true }
        assertTrue(committed)
        assertEquals("new", disk.active.readText())
    }

    @Test fun previousCacheRemainsAvailableIfFinalSyncFails() {
        val directory = temporary.newFolder()
        val active = DiskWrite(directory, "directory")
        val previous = DiskWrite(directory, null, "publication.previous.json")
        active.active.writeText("last-known-good")
        writePublicationCandidate(previous, active.active.readBytes()) { }
        var committed = false
        assertThrows(IllegalStateException::class.java) { writePublicationCandidate(active, "new-valid".toByteArray()) { committed = true } }
        assertTrue(committed)
        assertEquals("new-valid", active.active.readText())
        assertEquals("last-known-good", previous.active.readText())
    }
}
