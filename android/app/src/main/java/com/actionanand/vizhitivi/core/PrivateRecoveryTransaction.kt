package com.actionanand.vizhitivi.core

import java.io.OutputStream
import java.io.File
import android.system.Os
import android.system.OsConstants

internal fun syncPrivateDirectory(directory: File) {
    require(directory.isDirectory)
    val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
    try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
}

/** The active store changes only after the complete staged candidate is synced and verified. */
internal interface PrivateRecoveryWrite {
    fun start(): OutputStream
    fun sync(stream: OutputStream)
    fun staged(): ByteArray
    fun finish(stream: OutputStream)
    fun abort(stream: OutputStream)
    fun active(): ByteArray
}

internal fun commitPrivateRecovery(write: PrivateRecoveryWrite, candidate: ByteArray) {
    val stream = write.start()
    var finishing = false
    try {
        stream.write(candidate)
        stream.flush()
        write.sync(stream)
        require(write.staged().contentEquals(candidate)) { "Recovery staging verification failed" }
        // finish may have committed even if it throws. Do not attempt a second
        // write or remove its staged data after this point; the original has
        // already been captured independently before the operation starts.
        finishing = true
        write.finish(stream)
    } catch (failure: Exception) {
        if (!finishing) try { write.abort(stream) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        else try { stream.close() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        throw failure
    }
    require(write.active().contentEquals(candidate)) { "Recovery commit could not be verified" }
}
