package com.example.aiondevicebenchmark.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.aiondevicebenchmark.domain.repository.BenchmarkResultRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

class JsonRepository(context: Context) : BenchmarkResultRepository {
    private val appContext = context.applicationContext
    override val outputDirectory: File = File(context.getExternalFilesDir(null), "benchmark-results")
    private val reportDirectory: File = File(context.cacheDir, "reports")

    private val json = Json {
        prettyPrint = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    override fun save(record: BenchmarkRecord): File {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        val file = nextAvailableFile(baseName(record), extension = "json", directory = outputDirectory)
        file.writeText(json.encodeToString(record))
        return file
    }

    override fun listSavedFiles(): List<SavedJsonFile> {
        if (!outputDirectory.exists()) return emptyList()
        return outputDirectory
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                runCatching {
                    val rawJson = file.readText()
                    SavedJsonFile(
                        fileName = file.name,
                        absolutePath = file.absolutePath,
                        lastModifiedMs = file.lastModified(),
                        sizeBytes = file.length(),
                        rawJson = rawJson,
                        record = json.decodeFromString(rawJson),
                    )
                }.getOrNull()
            }
    }

    override fun findSavedFile(fileName: String): SavedJsonFile? {
        return listSavedFiles().firstOrNull { it.fileName == fileName }
    }

    override fun delete(fileName: String): Boolean {
        val file = File(outputDirectory, fileName)
        return file.exists() && file.delete()
    }

    override fun saveReportCsv(records: List<BenchmarkRecord>): Uri? {
        if (!reportDirectory.exists()) {
            reportDirectory.mkdirs()
        }
        val file = File(reportDirectory, "ai_on_device_benchmark_report_${Instant.now().epochSecond}.csv")
        file.writeText(records.toCsv())
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
    }

    private fun baseName(record: BenchmarkRecord): String {
        return listOf(
            record.runtime.engine,
            record.model.name,
            record.model.quantization,
            record.run.condition.type,
        ).joinToString("_") { it.safeFilePart() }
    }

    private fun nextAvailableFile(baseName: String, extension: String, directory: File): File {
        var candidate = File(directory, "$baseName.$extension")
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$index.$extension")
            index += 1
        }
        return candidate
    }

    private fun String.safeFilePart(): String {
        return trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
    }

    private fun List<BenchmarkRecord>.toCsv(): String {
        val header = listOf(
            "Model",
            "Quant",
            "Runtime",
            "Prefill tok/s",
            "Decode tok/s",
            "TTFT",
            "Peak RAM",
            "Load time",
            "Device Name",
            "Condition",
        )
        val rows = map { record ->
            listOf(
                record.model.name,
                record.model.quantization,
                record.runtime.engine,
                record.inference.prefill.tokensPerSecond.toString(),
                record.inference.decode.tokensPerSecond.toString(),
                "${record.inference.ttftMs} ms",
                "${record.memory.peakAppPssMb ?: "NA"} MB",
                "${record.modelLoading.loadTimeMs} ms",
                record.device.model,
                record.run.condition.type,
            )
        }
        return (listOf(header) + rows).joinToString("\n") { row ->
            row.joinToString(",") { it.csvEscape() }
        }
    }

    private fun String.csvEscape(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }
}

data class SavedJsonFile(
    val fileName: String,
    val absolutePath: String,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
    val rawJson: String,
    val record: BenchmarkRecord,
)
