package com.example.aiondevicebenchmark.background

import android.content.Context
import java.io.Closeable

class BackgroundWorkTracker(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var activeCount = 0

    fun begin(message: String): Closeable {
        synchronized(lock) {
            activeCount += 1
            BackgroundWorkService.start(appContext, message)
        }
        return Closeable { end() }
    }

    private fun end() {
        synchronized(lock) {
            activeCount = (activeCount - 1).coerceAtLeast(0)
            if (activeCount == 0) {
                BackgroundWorkService.stop(appContext)
            }
        }
    }
}
