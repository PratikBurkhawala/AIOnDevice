package com.example.aiondevicebenchmark.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiondevicebenchmark.data.SavedJsonFile
import com.example.aiondevicebenchmark.ui.composable.KeyValueTable
import com.example.aiondevicebenchmark.ui.composable.SectionTitle
import com.example.aiondevicebenchmark.ui.composable.formatFileSizeGb
import com.example.aiondevicebenchmark.ui.composable.formatLocalTimestamp
import com.example.aiondevicebenchmark.ui.composable.formatMemoryMb
import com.example.aiondevicebenchmark.ui.composable.formatSeconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Composable
fun JsonDetailScreen(file: SavedJsonFile?) {
    if (file == null) {
        Text("Select a JSON file from Saved JSON.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("JSON Detail")
        Text(file.fileName, fontWeight = FontWeight.SemiBold)
        KeyValueTable(rows = remember(file.rawJson) { flattenedJsonRows(file.rawJson) })
    }
}

private fun flattenedJsonRows(rawJson: String): List<Pair<String, String>> {
    return runCatching {
        val root = Json.parseToJsonElement(rawJson)
        buildList { flattenJson(path = "", element = root) }
    }.getOrElse {
        listOf("raw" to rawJson)
    }
}

private fun MutableList<Pair<String, String>>.flattenJson(path: String, element: JsonElement) {
    when (element) {
        is JsonObject -> {
            if (element.isEmpty() && path.isNotBlank()) add(displayLabel(path) to "")
            element.forEach { (key, value) ->
                flattenJson(path.child(key), value)
            }
        }
        is JsonArray -> {
            if (element.isEmpty()) add(displayLabel(path) to "")
            element.forEachIndexed { index, value ->
                flattenJson("$path[$index]", value)
            }
        }
        JsonNull -> add(displayLabel(path) to "")
        is JsonPrimitive -> add(displayLabel(path) to displayPrimitive(path, element))
    }
}

private fun String.child(key: String): String = if (isBlank()) key else "$this.$key"

private fun displayLabel(path: String): String {
    return labelOverrides[path] ?: readablePath(path)
}

private fun displayPrimitive(path: String, primitive: JsonPrimitive): String {
    if (primitive.booleanOrNull != null) return primitive.booleanOrNull.toString()
    primitive.longOrNull?.let { value ->
        return when {
            isMillisecondPath(path) -> formatSeconds(value)
            path.endsWith("fileSizeBytes") -> formatFileSizeGb(value)
            path.endsWith("peakAppPssMb") ||
                path.endsWith("ramBeforeLoadMb") ||
                path.endsWith("ramAfterLoadMb") ||
                path.endsWith("beforeGenerationMb") ||
                path.endsWith("afterGenerationMb") ||
                path.endsWith("afterModelUnloadMb") ||
                path.endsWith("appPssMb") -> formatMemoryMb(value.toInt())
            else -> value.toString()
        }
    }
    primitive.doubleOrNull?.let { return it.toString() }
    val text = primitive.jsonPrimitive.contentOrNull.orEmpty()
    if (text.isPlaceholder()) return ""
    val localTimestamp = formatLocalTimestamp(text)
    return localTimestamp.ifBlank { text }
}

private fun isMillisecondPath(path: String): Boolean {
    return path.endsWith("Ms") ||
        path.endsWith("durationMs") ||
        path.endsWith("loadTimeMs") ||
        path.endsWith("unloadTimeMs") ||
        path.endsWith("ttftMs")
}

private fun String.isPlaceholder(): Boolean {
    return equals("NA", ignoreCase = true) ||
        equals("N/A", ignoreCase = true) ||
        equals("UNKNOWN", ignoreCase = true) ||
        equals("NOT_AVAILABLE", ignoreCase = true) ||
        equals("NOT_MEASURED", ignoreCase = true) ||
        equals("not available", ignoreCase = true)
}

private val labelOverrides = mapOf(
    "run.runId" to "Run ID",
    "run.runGroupId" to "Run Group",
    "run.timestamp.start" to "Start Time",
    "run.timestamp.end" to "End Time",
    "run.condition.type" to "Condition",
    "run.condition.batterySaver" to "Battery Saver",
    "run.condition.charging" to "Charging",
    "run.condition.screenOn" to "Screen On",
    "run.condition.appState" to "App State",
    "run.condition.memoryPressure" to "Memory Pressure",
    "run.condition.consecutiveGenerationNumber" to "Generation Number",
    "run.condition.totalConsecutiveGenerations" to "Total Generations",
    "device.manufacturer" to "Device Manufacturer",
    "device.model" to "Device Model",
    "device.androidVersion" to "Android Version",
    "device.apiLevel" to "Android API Level",
    "device.soc.manufacturer" to "SoC Manufacturer",
    "device.soc.model" to "SoC Model",
    "device.cpu.architecture" to "CPU Architecture",
    "device.cpu.cores" to "CPU Cores",
    "device.gpu.name" to "GPU Name",
    "device.npu.name" to "NPU Name",
    "device.npu.available" to "NPU Available",
    "device.ram.totalMb" to "Total RAM",
    "runtime.engine" to "Engine",
    "runtime.version" to "Engine Version",
    "runtime.backend" to "Backend",
    "runtime.threads" to "Threads",
    "runtime.gpuLayers" to "GPU Layers",
    "model.name" to "Model Name",
    "model.parameters" to "Model Parameters",
    "model.format" to "Model Format",
    "model.fileName" to "Model File",
    "model.filePath" to "Model Path",
    "model.quantization" to "Quantization",
    "model.fileSizeBytes" to "Model File Size",
    "model.contextSize" to "Context Size",
    "model.maxOutputTokens" to "Max Output Tokens",
    "generationConfig.temperature" to "Temperature",
    "generationConfig.topK" to "Top K",
    "generationConfig.topP" to "Top P",
    "generationConfig.seed" to "Seed",
    "prompt.promptId" to "Prompt ID",
    "prompt.inputTokenCount" to "Input Tokens",
    "prompt.outputTokenTarget" to "Output Token Target",
    "modelLoading.loadStart" to "Model Load Start",
    "modelLoading.loadEnd" to "Model Load End",
    "modelLoading.loadTimeMs" to "Model Load Time",
    "modelLoading.ramBeforeLoadMb" to "RAM Before Load",
    "modelLoading.ramAfterLoadMb" to "RAM After Load",
    "inference.generationStart" to "Generation Start",
    "inference.firstTokenTime" to "First Token Time",
    "inference.generatedText" to "Generated Response",
    "inference.ttftMs" to "Time To First Token",
    "inference.prefill.durationMs" to "Prefill Time",
    "inference.prefill.tokens" to "Prefill Tokens",
    "inference.prefill.tokensPerSecond" to "Prefill Tokens Per Second",
    "inference.decode.durationMs" to "Decode Time",
    "inference.decode.tokens" to "Decode Tokens",
    "inference.decode.tokensPerSecond" to "Decode Tokens Per Second",
    "inference.total.durationMs" to "Total Inference Time",
    "inference.total.outputTokens" to "Output Tokens",
    "inference.total.generationEnd" to "Generation End",
    "memory.beforeGenerationMb" to "RAM Before Generation",
    "memory.peakAppPssMb" to "Peak RAM",
    "memory.afterGenerationMb" to "RAM After Generation",
    "memory.afterModelUnloadMb" to "RAM After Model Unload",
    "battery.beforePercentage" to "Battery Before",
    "battery.afterPercentage" to "Battery After",
    "battery.drainPercentage" to "Battery Drain",
    "battery.temperatureBeforeC" to "Battery Temperature Before",
    "battery.temperatureAfterC" to "Battery Temperature After",
    "battery.thermalStatus" to "Thermal Status",
    "hardware.backend" to "Hardware Backend",
    "hardware.cpu.used" to "CPU Used",
    "hardware.cpu.utilizationPercent" to "CPU Utilization",
    "hardware.cpu.measurementStatus" to "CPU Measurement Status",
    "hardware.gpu.used" to "GPU Used",
    "hardware.gpu.utilizationPercent" to "GPU Utilization",
    "hardware.gpu.measurementStatus" to "GPU Measurement Status",
    "hardware.npu.used" to "NPU Used",
    "hardware.npu.utilizationPercent" to "NPU Utilization",
    "hardware.npu.measurementStatus" to "NPU Measurement Status",
    "hardware.profiling.tool" to "Profiling Tool",
    "hardware.profiling.evidence" to "Profiling Evidence",
    "modelUnloading.unloadStart" to "Model Unload Start",
    "modelUnloading.unloadEnd" to "Model Unload End",
    "modelUnloading.unloadTimeMs" to "Model Unload Time",
    "result.status" to "Status",
    "result.error" to "Error",
    "observation.summary" to "Observation Summary",
)

private fun readablePath(path: String): String {
    val memorySample = Regex("""memory\.samples\[(\d+)]\.(timestamp|appPssMb)""").matchEntire(path)
    if (memorySample != null) {
        val index = memorySample.groupValues[1].toInt() + 1
        return when (memorySample.groupValues[2]) {
            "timestamp" -> "Memory Sample $index Time"
            else -> "Memory Sample $index RAM"
        }
    }

    val listItem = Regex("""(.+)\[(\d+)]""").matchEntire(path)
    if (listItem != null) {
        val index = listItem.groupValues[2].toInt() + 1
        return "${listItem.groupValues[1].substringAfterLast('.').toDisplayWords()} $index"
    }

    return path
        .split('.')
        .joinToString(" - ") { it.toDisplayWords() }
}

private fun String.toDisplayWords(): String {
    return replace(Regex("""\[(\d+)]""")) { match ->
        " ${match.groupValues[1].toInt() + 1}"
    }
        .replace(Regex("""([a-z])([A-Z])"""), "$1 $2")
        .replace("Pss", "PSS")
        .replace("Mb", "MB")
        .replace("Ms", "Time")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
