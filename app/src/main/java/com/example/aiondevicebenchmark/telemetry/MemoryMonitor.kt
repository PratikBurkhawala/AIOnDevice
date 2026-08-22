package com.example.aiondevicebenchmark.telemetry

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import com.example.aiondevicebenchmark.data.MemorySampleJson
import java.time.Instant

class MemoryMonitor(context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    fun sample(phase: String = ""): MemorySampleJson {
        return MemorySampleJson(
            timestamp = Instant.now().toString(),
            phase = phase,
            appPssMb = sampleAppPssMb(),
        )
    }

    fun sampleAppPssMb(): Int? {
        val memoryInfo: Debug.MemoryInfo = activityManager
            ?.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            ?.firstOrNull()
            ?: return null
        return memoryInfo.totalPss / 1024
    }

    fun totalRamMb(): Long? {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info) ?: return null
        return info.totalMem / (1024L * 1024L)
    }
}
