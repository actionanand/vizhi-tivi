package com.actionanand.vizhitivi

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.util.Log

/** Minimal platform runner; keeps the instrumentation APK independent of test libraries. */
class PlatformInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val tests = PlatformChecks(this)
        val cases = listOf(
            "atomicFileAndSnapshots" to tests::atomicFileAndSnapshots,
            "repositoryConcurrency" to tests::repositoryConcurrency,
            "loopbackDeadlineAndCancellation" to tests::loopbackDeadlineAndCancellation,
            "startupRecoveryAndLocales" to tests::startupRecoveryAndLocales,
        )
        var failures = 0
        cases.forEachIndexed { index, (name, run) ->
            val status = Bundle().apply {
                putString("class", PlatformChecks::class.java.name)
                putString("test", name)
                putInt("current", index + 1)
                putInt("numtests", cases.size)
            }
            sendStatus(1, status)
            try {
                run()
                sendStatus(0, status)
            } catch (failure: Throwable) {
                failures++
                // Never emit exception messages: a provider may include a private URL.
                val location = failure.stackTrace.filter { it.className.startsWith("com.actionanand.vizhitivi.PlatformChecks") }
                    .joinToString(" <- ") { "${it.methodName}:${it.lineNumber}" }
                status.putString("stack", "Platform check failed: ${failure.javaClass.simpleName} at $location")
                Log.e("VizhiPlatformTest", "Platform check failed: $name (${failure.javaClass.simpleName})")
                sendStatus(-2, status)
            }
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", "\n${cases.size} platform checks, $failures failures\n")
        })
    }
}
