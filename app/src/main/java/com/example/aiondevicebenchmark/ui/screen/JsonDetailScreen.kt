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
    return when {
        path.endsWith("fileSizeBytes") -> path.removeSuffix("Bytes") + "GB"
        path.endsWith("peakAppPssMb") -> path.removeSuffix("peakAppPssMb") + "Peak RAM"
        isMillisecondPath(path) -> path.removeSuffix("Ms") + "Seconds"
        else -> path
    }
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
