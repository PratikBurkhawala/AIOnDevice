package com.example.aiondevicebenchmark.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiondevicebenchmark.data.BenchmarkRecord

@Composable
fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun MainMetrics(record: BenchmarkRecord) {
    Column {
        KeyValueRow("Model", record.model.name)
        KeyValueRow("Quant", record.model.quantization)
        KeyValueRow("Runtime", "${record.runtime.engine} ${record.runtime.version}")
        KeyValueRow("Prefill tok/s", formatDouble(record.inference.prefill.tokensPerSecond))
        KeyValueRow("Decode tok/s", formatDouble(record.inference.decode.tokensPerSecond))
        KeyValueRow("TTFT", "${record.inference.ttftMs} ms")
        KeyValueRow("Peak RAM", "${record.memory.peakAppPssMb ?: "not available"} MB")
        KeyValueRow("Load time", "${record.modelLoading.loadTimeMs} ms")
    }
}

@Composable
fun KeyValueTable(rows: List<Pair<String, String>>) {
    Column {
        rows.forEach { (key, value) ->
            KeyValueRow(key, value)
            HorizontalDivider()
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = key,
            modifier = Modifier
                .weight(0.38f)
                .padding(vertical = 8.dp, horizontal = 6.dp),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value,
            modifier = Modifier
                .weight(0.62f)
                .padding(vertical = 8.dp, horizontal = 6.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

fun keyValueRows(record: BenchmarkRecord): List<Pair<String, String>> {
    return listOf(
        "Run ID" to record.run.runId,
        "Run group" to record.run.runGroupId,
        "Start" to record.run.timestamp.start,
        "End" to record.run.timestamp.end,
        "Condition" to record.run.condition.type,
        "Model" to record.model.name,
        "Quantization" to record.model.quantization,
        "Runtime" to record.runtime.engine,
        "Runtime version" to record.runtime.version,
        "Backend" to record.runtime.backend,
        "Threads" to record.runtime.threads.toString(),
        "GGUF file" to record.model.fileName,
        "Local GGUF path" to record.model.filePath,
        "File size bytes" to record.model.fileSizeBytes?.toString().orEmpty(),
        "Context size" to record.model.contextSize.toString(),
        "Temperature" to record.generationConfig.temperature.toString(),
        "Top-K" to record.generationConfig.topK.toString(),
        "Top-P" to record.generationConfig.topP.toString(),
        "Seed" to record.generationConfig.seed.toString(),
        "Prompt ID" to record.prompt.promptId,
        "Input tokens" to record.prompt.inputTokenCount.toString(),
        "Output target" to record.prompt.outputTokenTarget.toString(),
        "Generated response" to record.inference.generatedText,
        "TTFT ms" to record.inference.ttftMs.toString(),
        "Prefill tokens/sec" to formatDouble(record.inference.prefill.tokensPerSecond),
        "Decode tokens/sec" to formatDouble(record.inference.decode.tokensPerSecond),
        "Total duration ms" to record.inference.total.durationMs.toString(),
        "Peak app PSS MB" to record.memory.peakAppPssMb?.toString().orEmpty(),
        "Battery before" to record.battery.beforePercentage?.toString().orEmpty(),
        "Battery after" to record.battery.afterPercentage?.toString().orEmpty(),
        "Battery drain" to record.battery.drainPercentage?.toString().orEmpty(),
        "Thermal status" to record.battery.thermalStatus,
        "Load time ms" to record.modelLoading.loadTimeMs.toString(),
        "Unload time ms" to record.modelUnloading.unloadTimeMs.toString(),
        "Status" to record.result.status,
        "Error" to record.result.error.orEmpty(),
        "Observation" to record.observation.summary,
    )
}
