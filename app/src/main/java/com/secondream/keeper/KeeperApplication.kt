package com.secondream.keeper

import android.app.Application
import android.util.Log

/**
 * Application subclass installing a top-level uncaught-exception handler.
 * On crash, the full stacktrace is written to filesDir/last_crash.txt so the
 * next launch can read it (and the user can send it for debugging). Also
 * ensures the app actually terminates rather than entering a restart loop.
 */
class KeeperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = java.io.File(filesDir, "last_crash.txt")
                file.writeText(
                    buildString {
                        append("Time: ${java.util.Date()}\n")
                        append("Thread: ${thread.name}\n")
                        append("Stacktrace:\n")
                        append(throwable.stackTraceToString())
                    }
                )
                Log.e("KeeperCrash", "Uncaught", throwable)
            } catch (_: Throwable) {}
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
