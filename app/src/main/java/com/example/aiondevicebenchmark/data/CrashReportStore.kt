package com.example.aiondevicebenchmark.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

class CrashReportStore(context: Context) {
    private val appContext = context.applicationContext
    private val crashDirectory: File = File(appContext.getExternalFilesDir(null), "crash-reports")

    fun install() {
        saveHistoricalExitReasons()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (previousHandler is SavingUncaughtExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            SavingUncaughtExceptionHandler(
                store = this,
                previousHandler = previousHandler,
            ),
        )
    }

    fun saveCrash(thread: Thread, throwable: Throwable): File? {
        return runCatching {
            if (!crashDirectory.exists()) {
                crashDirectory.mkdirs()
            }
            val timestamp = Instant.now().toString()
            val file = File(crashDirectory, "crash_${timestamp.safeFilePart()}.json")
            val json = JSONObject()
                .put("timestamp", timestamp)
                .put("thread", thread.name)
                .put("exception", throwable::class.java.name)
                .put("message", throwable.message.orEmpty())
                .put("stackTrace", throwable.stackTraceString())
                .put("causes", throwable.causesJson())
                .put(
                    "device",
                    JSONObject()
                        .put("manufacturer", Build.MANUFACTURER.orEmpty())
                        .put("model", Build.MODEL.orEmpty())
                        .put("androidVersion", Build.VERSION.RELEASE.orEmpty())
                        .put("apiLevel", Build.VERSION.SDK_INT),
                )
            file.writeText(json.toString(2))
            file
        }.getOrNull()
    }

    fun listCrashReports(): List<SavedCrashReport> {
        if (!crashDirectory.exists()) return emptyList()
        return crashDirectory
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                runCatching {
                    val rawJson = file.readText()
                    val json = JSONObject(rawJson)
                    SavedCrashReport(
                        fileName = file.name,
                        absolutePath = file.absolutePath,
                        lastModifiedMs = file.lastModified(),
                        timestamp = json.optString("timestamp"),
                        exception = json.optString("exception"),
                        message = json.optString("message"),
                        thread = json.optString("thread"),
                        rawJson = rawJson,
                    )
                }.getOrNull()
            }
    }

    fun deleteCrashReport(fileName: String): Boolean {
        val file = File(crashDirectory, fileName)
        return file.exists() && file.delete()
    }

    private fun saveHistoricalExitReasons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            if (!crashDirectory.exists()) {
                crashDirectory.mkdirs()
            }
            val activityManager = appContext.getSystemService(ActivityManager::class.java)
            val exits = activityManager.getHistoricalProcessExitReasons(appContext.packageName, 0, 5)
            exits
                .filter { it.reason == ApplicationExitInfo.REASON_CRASH || it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }
                .forEach { exit ->
                    val timestamp = Instant.ofEpochMilli(exit.timestamp).toString()
                    val file = File(crashDirectory, "process_exit_${exit.timestamp}_${exit.reason}.json")
                    if (file.exists()) return@forEach
                    val trace = exit.traceInputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val json = JSONObject()
                        .put("timestamp", timestamp)
                        .put("thread", "")
                        .put("exception", "Process exit: ${exit.reasonLabel()}")
                        .put("message", exit.description.orEmpty())
                        .put("stackTrace", trace)
                        .put("importance", exit.importance)
                        .put("pssKb", exit.pss)
                        .put("rssKb", exit.rss)
                        .put(
                            "device",
                            JSONObject()
                                .put("manufacturer", Build.MANUFACTURER.orEmpty())
                                .put("model", Build.MODEL.orEmpty())
                                .put("androidVersion", Build.VERSION.RELEASE.orEmpty())
                                .put("apiLevel", Build.VERSION.SDK_INT),
                        )
                    file.writeText(json.toString(2))
                }
        }
    }

    private class SavingUncaughtExceptionHandler(
        private val store: CrashReportStore,
        private val previousHandler: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            store.saveCrash(thread, throwable)
            previousHandler?.uncaughtException(thread, throwable)
                ?: kotlin.system.exitProcess(2)
        }
    }

    private fun Throwable.stackTraceString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun Throwable.causesJson(): JSONArray {
        val array = JSONArray()
        var current = cause
        while (current != null) {
            array.put(
                JSONObject()
                    .put("exception", current::class.java.name)
                    .put("message", current.message.orEmpty())
                    .put("stackTrace", current.stackTraceString()),
            )
            current = current.cause
        }
        return array
    }

    private fun ApplicationExitInfo.reasonLabel(): String {
        return when (reason) {
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
            else -> "REASON_$reason"
        }
    }

    private fun String.safeFilePart(): String {
        return replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
    }
}

data class SavedCrashReport(
    val fileName: String,
    val absolutePath: String,
    val lastModifiedMs: Long,
    val timestamp: String,
    val exception: String,
    val message: String,
    val thread: String,
    val rawJson: String,
)
