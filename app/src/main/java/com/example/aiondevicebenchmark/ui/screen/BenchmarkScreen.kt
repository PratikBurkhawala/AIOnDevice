package com.example.aiondevicebenchmark.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.aiondevicebenchmark.benchmark.BenchmarkCondition
import com.example.aiondevicebenchmark.benchmark.BenchmarkConfig
import com.example.aiondevicebenchmark.benchmark.BenchmarkState
import com.example.aiondevicebenchmark.data.BenchmarkRecord
import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.ModelConfig
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkUiEvent
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkUiState
import com.example.aiondevicebenchmark.ui.composable.EngineDropdown
import com.example.aiondevicebenchmark.ui.composable.KeyValueRow
import com.example.aiondevicebenchmark.ui.composable.MainMetrics
import com.example.aiondevicebenchmark.ui.composable.ModelDropdown
import com.example.aiondevicebenchmark.ui.composable.NumericField
import com.example.aiondevicebenchmark.ui.composable.SectionTitle

@Composable
fun BenchmarkScreen(
    state: BenchmarkUiState,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    BenchmarkConfigScreen(
        state = state,
        supportedEngines = state.supportedEngines,
        supportedModels = state.supportedModels,
        enabled = state.benchmarkState !is BenchmarkState.Running,
        onEvent = onEvent,
    )

    when (val currentState = state.benchmarkState) {
        BenchmarkState.Idle -> Unit
        is BenchmarkState.Running -> BenchmarkProgressScreen(currentState)
        is BenchmarkState.Completed -> BenchmarkResultScreen(
            currentState.records,
            currentState.outputDirectory,
        )
        is BenchmarkState.Failed -> FailureScreen(currentState.message)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BenchmarkConfigScreen(
    state: BenchmarkUiState,
    supportedEngines: List<EngineType>,
    supportedModels: List<ModelConfig>,
    enabled: Boolean,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    val config = state.config
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Configuration")
        EngineDropdown(config.engineType, supportedEngines, enabled) {
            onEvent(BenchmarkUiEvent.UpdateEngine(it))
        }
        ModelDropdown(config.model, supportedModels, enabled) {
            onEvent(BenchmarkUiEvent.UpdateModel(it))
        }

        OutlinedTextField(
            value = config.model.quantization,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = true,
            singleLine = true,
            label = { Text("Quantization") },
        )
        OutlinedTextField(
            value = config.model.filePath,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = true,
            label = { Text("Local model path") },
        )
        KeyValueRow("Acceleration", "llama.cpp uses Vulkan GPU when available. ONNX Runtime tries NNAPI and otherwise uses CPU.")

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Condition", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BenchmarkCondition.entries.forEach { condition ->
                    FilterChip(
                        selected = config.condition == condition,
                        enabled = enabled,
                        onClick = { onEvent(BenchmarkUiEvent.UpdateCondition(condition)) },
                        label = { Text(condition.label) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = config.prompt,
            onValueChange = { onEvent(BenchmarkUiEvent.UpdatePrompt(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            enabled = enabled,
            label = { Text("Prompt") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumericField("Max output", state.maxOutputTokensInput, enabled, { onEvent(BenchmarkUiEvent.UpdateMaxOutputTokens(it)) }, Modifier.weight(1f))
            NumericField("Generations", state.generationsInput, enabled, { onEvent(BenchmarkUiEvent.UpdateConsecutiveGenerations(it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumericField("RAM sample sec", state.ramSamplingIntervalInput, enabled, { onEvent(BenchmarkUiEvent.UpdateRamSamplingInterval(it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumericField("Temperature", state.temperatureInput, enabled, { onEvent(BenchmarkUiEvent.UpdateTemperature(it)) }, Modifier.weight(1f), KeyboardType.Decimal)
            NumericField("Top-K", state.topKInput, enabled, { onEvent(BenchmarkUiEvent.UpdateTopK(it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumericField("Top-P", state.topPInput, enabled, { onEvent(BenchmarkUiEvent.UpdateTopP(it)) }, Modifier.weight(1f), KeyboardType.Decimal)
            NumericField("Seed", state.seedInput, enabled, { onEvent(BenchmarkUiEvent.UpdateSeed(it)) }, Modifier.weight(1f))
        }

        Button(
            onClick = { onEvent(BenchmarkUiEvent.StartBenchmark) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        ) {
            Text("Start Benchmark")
        }
    }
}

@Composable
private fun BenchmarkProgressScreen(state: BenchmarkState.Running) {
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Result")
        Text("Generation ${state.currentGeneration} of ${state.totalGenerations}")
        LinearProgressIndicator(
            progress = { state.currentGeneration / state.totalGenerations.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(state.status, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BenchmarkResultScreen(records: List<BenchmarkRecord>, outputDirectory: String) {
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Result")
        Text("Saved ${records.size} JSON record(s).")
        Text(outputDirectory, style = MaterialTheme.typography.bodySmall)
        records.lastOrNull()?.let { last ->
            MainMetrics(record = last)
            KeyValueRow("Generated response", last.inference.generatedText)
        }
    }
}

@Composable
private fun FailureScreen(message: String) {
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Failure")
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}
