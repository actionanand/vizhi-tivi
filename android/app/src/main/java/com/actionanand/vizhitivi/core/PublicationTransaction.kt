package com.actionanand.vizhitivi.core

import java.io.OutputStream

/** The rename is the commit boundary; an older accepted cache remains intact until then. */
internal interface PublicationWrite {
    fun start(): OutputStream
    fun sync(stream: OutputStream)
    fun staged(): ByteArray
    fun rename()
    fun syncDirectory()
    fun abort()
}

internal fun stagePublicationCandidate(write: PublicationWrite, candidate: ByteArray) {
    val stream = write.start()
    try {
        stream.write(candidate)
        stream.flush()
        write.sync(stream)
        require(write.staged().contentEquals(candidate)) { "Publication staging verification failed" }
        stream.close()
    } catch (failure: Exception) {
        try { stream.close() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        try { write.abort() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        throw failure
    }
}

internal fun finishPublicationCandidate(write: PublicationWrite, committed: () -> Unit) {
    var renamed = false
    try {
        write.rename()
        renamed = true
        committed()
        write.syncDirectory()
    } catch (failure: Exception) {
        if (!renamed) try { write.abort() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        throw failure
    }
}

internal fun writePublicationCandidate(write: PublicationWrite, candidate: ByteArray, committed: () -> Unit) {
    stagePublicationCandidate(write, candidate)
    finishPublicationCandidate(write, committed)
}
