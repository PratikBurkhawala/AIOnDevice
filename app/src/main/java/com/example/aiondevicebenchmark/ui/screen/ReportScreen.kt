package com.example.aiondevicebenchmark.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiondevicebenchmark.data.SavedJsonFile
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkUiEvent
import com.example.aiondevicebenchmark.ui.composable.SectionTitle
import com.example.aiondevicebenchmark.ui.composable.formatDouble
import com.example.aiondevicebenchmark.ui.composable.formatMemoryMb
import com.example.aiondevicebenchmark.ui.composable.formatSeconds

@Composable
fun ReportScreen(
    files: List<SavedJsonFile>,
    loading: Boolean,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Report")
            OutlinedButton(
                onClick = { onEvent(BenchmarkUiEvent.ShareReportCsv) },
                enabled = files.isNotEmpty(),
            ) {
                Text("Share CSV")
            }
        }
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Loading report...")
        } else if (files.isEmpty()) {
            Text("No saved JSON files yet.")
        } else {
            ReportTable(files = files)
        }
    }
}

@Composable
private fun ReportTable(files: List<SavedJsonFile>) {
    val horizontalScroll = rememberScrollState()
    Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
        ReportRow(
            values = listOf(
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
            ),
            header = true,
        )
        files.forEach { file ->
            val record = file.record
            ReportRow(
                values = listOf(
                    record.model.name,
                    record.model.quantization,
                    record.runtime.engine,
                    formatDouble(record.inference.prefill.tokensPerSecond),
                    formatDouble(record.inference.decode.tokensPerSecond),
                    formatSeconds(record.inference.ttftMs),
                    formatMemoryMb(record.memory.peakAppPssMb),
                    formatSeconds(record.modelLoading.loadTimeMs),
                    record.device.model,
                    record.run.condition.type,
                ),
                header = false,
            )
        }
    }
}

@Composable
private fun ReportRow(values: List<String>, header: Boolean) {
    Row {
        values.forEachIndexed { index, value ->
            val minWidth = when (index) {
                0 -> 240.dp
                8 -> 180.dp
                else -> 120.dp
            }
            Text(
                text = value,
                modifier = Modifier
                    .defaultMinSize(minWidth = minWidth)
                    .background(if (header) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider()
}
