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
        KeyValueRow("TTFT", formatSeconds(record.inference.ttftMs))
        KeyValueRow("Peak RAM", formatMemoryMb(record.memory.peakAppPssMb))
        KeyValueRow("Load time", formatSeconds(record.modelLoading.loadTimeMs))
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
        "Start" to formatLocalTimestamp(record.run.timestamp.start),
        "End" to formatLocalTimestamp(record.run.timestamp.end),
        "Condition" to record.run.condition.type,
        "Model" to record.model.name,
        "Quantization" to record.model.quantization,
        "Runtime" to record.runtime.engine,
        "Runtime version" to record.runtime.version,
        "Backend" to record.runtime.backend,
        "Measurement status" to record.runtime.measurementStatus,
        "Threads" to record.runtime.threads.toString(),
        "Model file" to record.model.fileName,
        "Local model path" to record.model.filePath,
        "File size" to formatFileSizeGb(record.model.fileSizeBytes),
        "Context size" to record.model.contextSize.toString(),
        "Temperature" to record.generationConfig.temperature.toString(),
        "Top-K" to record.generationConfig.topK.toString(),
        "Top-P" to record.generationConfig.topP.toString(),
        "Seed" to record.generationConfig.seed.toString(),
        "Prompt ID" to record.prompt.promptId,
        "Prompt characters" to record.prompt.characterCount.toString(),
        "Prompt text" to record.prompt.text,
        "Engine prompt characters" to record.prompt.engineInputCharacterCount.toString(),
        "Engine prompt text" to record.prompt.engineInputText,
        "Input tokens" to record.prompt.inputTokenCount?.toString().orEmpty(),
        "Output target" to record.prompt.outputTokenTarget.toString(),
        "Generated response" to record.inference.generatedText,
        "TTFT" to formatSeconds(record.inference.ttftMs),
        "Prefill tokens/sec" to formatDouble(record.inference.prefill.tokensPerSecond),
        "Decode tokens/sec" to formatDouble(record.inference.decode.tokensPerSecond),
        "Total duration" to formatSeconds(record.inference.total.durationMs),
        "Peak RAM" to formatMemoryMb(record.memory.peakAppPssMb),
        "Battery before" to record.battery.beforePercentage?.toString().orEmpty(),
        "Battery after" to record.battery.afterPercentage?.toString().orEmpty(),
        "Battery drain" to record.battery.drainPercentage?.toString().orEmpty(),
        "Thermal status" to record.battery.thermalStatus,
        "Hardware evidence" to record.hardware.profiling.evidence.orEmpty(),
        "Load time" to formatSeconds(record.modelLoading.loadTimeMs),
        "Unload time" to formatSeconds(record.modelUnloading.unloadTimeMs),
        "Status" to record.result.status,
        "Error" to record.result.error.orEmpty(),
        "Observation" to record.observation.summary,
    )
}
