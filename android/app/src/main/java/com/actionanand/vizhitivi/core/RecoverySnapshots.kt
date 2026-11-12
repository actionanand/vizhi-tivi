package com.actionanand.vizhitivi.core

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** App-private, immutable snapshots. Capacity is deliberately fail-closed: no automatic deletion. */
data class RecoverySnapshot(
    val id: String, val createdAt: Long, val size: Long, val formatVersion: Int?,
    val integrity: String, val recoverableCount: Int, val complete: Boolean, val status: String
)

class RecoverySnapshots(
    private val directory: File, private val legacy: File, private val limit: Int = 5,
    private val syncDirectory: (File) -> Unit = ::syncPrivateDirectory,
    private val boundary: (String) -> Unit = {},
) {
    private val name = Regex("snapshot-([0-9a-f-]{36})\\.json")
    private val pendingName = Regex("snapshot-([0-9a-f-]{36})\\.pending")
    private val maxBytes = 40_000_000L
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun restoredMarker(id: String): Boolean {
        val marker = File(directory, "$id.restored")
        return runCatching { marker.isFile && marker.length() == 1L && marker.inputStream().use { it.read() == 1 } }.getOrDefault(false)
    }
    private fun file(id: String): File {
        require(id.matches(Regex("[0-9a-f-]{36}")))
        return File(directory, "snapshot-$id.json").also { require(it.canonicalFile.parentFile == directory.canonicalFile) }
    }
    private fun bounded(f: File): ByteArray {
        require(f.isFile && f.length() <= maxBytes) { "Snapshot unavailable or too large" }
        return f.inputStream().use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size().toLong() + n <= maxBytes); out.write(buffer, 0, n) }
            out.toByteArray()
        }
    }
    private fun inspect(id: String, f: File): Pair<RecoverySnapshot, String?> {
        return try {
            val bytes = bounded(f)
            val root = if (id == "legacy") null else JSONObject(String(bytes, Charsets.UTF_8))
            if (root != null) require(root.getInt("formatVersion") == 1 && root.getString("id") == id)
            val raw = root?.getString("payload") ?: String(bytes, Charsets.UTF_8)
            val payload = raw.toByteArray(Charsets.UTF_8)
            val integrity = root == null || (root.getString("sha256") == sha(payload) && root.getLong("size") == payload.size.toLong())
            require(integrity)
            val load = try { PrivatePlaylistCodec.decode(raw) } catch (_: Exception) { null }
            val created = root?.getLong("createdAt") ?: f.lastModified()
            require(created > 0)
            RecoverySnapshot(id, created, payload.size.toLong(), if (root == null) 0 else 1, if (root == null) "unverified" else "verified",
                load?.playlists?.size ?: 0, load?.complete ?: false,
                if (restoredMarker(id)) "restored" else "captured") to raw
        } catch (_: Exception) {
            RecoverySnapshot(id, f.lastModified(), f.length(), null, "invalid", 0, false, "invalid") to null
        }
    }
    fun list(): List<RecoverySnapshot> {
        val files = if (directory.exists()) directory.listFiles() ?: error("Snapshot inventory unavailable") else emptyArray()
        val entries = files.mapNotNull { f ->
            name.matchEntire(f.name)?.let { inspect(it.groupValues[1], f).first }
                ?: pendingName.matchEntire(f.name)?.let {
                    RecoverySnapshot("pending:${it.groupValues[1]}", f.lastModified(), f.length(), null, "invalid", 0, false, "invalid")
                }
        }.toMutableList()
        if (legacy.isFile) entries += inspect("legacy", legacy).first
        return entries.sortedByDescending { it.createdAt }
    }
    fun capture(raw: ByteArray): RecoverySnapshot {
        require(raw.size <= 20_000_000) { "Snapshot too large; export original first" }
        if (!directory.exists()) {
            require(directory.mkdirs()) { "Cannot create snapshot storage" }
            syncDirectory(directory.parentFile ?: error("Snapshot parent unavailable"))
        }
        require(list().size < limit) { "Snapshot storage full" }
        val payload = String(raw, Charsets.UTF_8)
        require(payload.toByteArray(Charsets.UTF_8).contentEquals(raw)) { "Snapshot is not UTF-8; export original first" }
        val id = UUID.randomUUID().toString(); val target = file(id); val pending = File(directory, "snapshot-$id.pending")
        val data = JSONObject().put("formatVersion", 1).put("id", id).put("createdAt", System.currentTimeMillis())
            .put("size", raw.size).put("sha256", sha(raw)).put("status", "captured").put("payload", payload).toString().toByteArray(Charsets.UTF_8)
        require(data.size.toLong() <= maxBytes) { "Encoded snapshot too large" }
        require(!target.exists() && pending.createNewFile())
        syncDirectory(directory)
        boundary("created")
        FileOutputStream(pending).use { out -> out.write(data); out.fd.sync() }
        boundary("written")
        require(!target.exists() && pending.renameTo(target)) { "Cannot publish snapshot" }
        syncDirectory(directory)
        boundary("published")
        val entry = inspect(id, target).first
        require(entry.integrity == "verified") { "Snapshot verification failed" }
        return entry
    }
    fun raw(id: String): String {
        val f = if (id == "legacy") legacy else file(id)
        return inspect(id, f).second ?: error("Snapshot integrity failed")
    }
    fun exportBytes(id: String): ByteArray {
        if (id.startsWith("pending:")) {
            val rawId = id.removePrefix("pending:")
            require(rawId.matches(Regex("[0-9a-f-]{36}")))
            return bounded(File(directory, "snapshot-$rawId.pending"))
        }
        val f = if (id == "legacy") legacy else file(id)
        return inspect(id, f).second?.toByteArray(Charsets.UTF_8) ?: bounded(f)
    }
    fun markRestored(id: String) {
        require(id == "legacy" || id.matches(Regex("[0-9a-f-]{36}")))
        require(directory.exists() || directory.mkdirs())
        val marker = File(directory, "$id.restored")
        if (!restoredMarker(id)) {
            FileOutputStream(marker).use { it.write(1); it.fd.sync() }
            syncDirectory(directory)
        }
    }
    fun delete(id: String) {
        val entries = list(); val target = entries.firstOrNull { it.id == id } ?: error("Snapshot unavailable")
        require(entries.any { it.id != target.id && it.integrity != "invalid" && it.recoverableCount > 0 }) { "Keep at least one recoverable snapshot" }
        val f = when {
            id == "legacy" -> legacy
            id.startsWith("pending:") -> File(directory, "snapshot-${id.removePrefix("pending:")}.pending")
            else -> file(id)
        }
        require(f.delete()) { "Could not delete snapshot" }
        boundary("deleted")
        syncDirectory(f.parentFile ?: directory)
        File(directory, "$id.restored").delete()
    }
}
