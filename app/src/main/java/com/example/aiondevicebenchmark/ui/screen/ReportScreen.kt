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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiondevicebenchmark.data.SavedCrashReport
import com.example.aiondevicebenchmark.data.SavedJsonFile
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkUiEvent
import com.example.aiondevicebenchmark.ui.composable.SectionTitle
import com.example.aiondevicebenchmark.ui.composable.formatDouble
import com.example.aiondevicebenchmark.ui.composable.formatMemoryMb
import com.example.aiondevicebenchmark.ui.composable.formatSeconds

@Composable
fun ReportScreen(
    files: List<SavedJsonFile>,
    crashReports: List<SavedCrashReport>,
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
        } else if (files.isEmpty() && crashReports.isEmpty()) {
            Text("No saved JSON files yet.")
        } else {
            if (crashReports.isNotEmpty()) {
                CrashReports(
                    crashReports = crashReports,
                    onEvent = onEvent,
                    includeRawPreview = false,
                )
            }
            if (files.isNotEmpty()) {
                ReportTable(files = files)
            }
        }
    }
}

@Composable
fun CrashReportScreen(
    crashReports: List<SavedCrashReport>,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Crashes")
        if (crashReports.isEmpty()) {
            Text("No crash reports captured yet.")
            Text(
                "After a crash, restart the app and open this tab. Reports are saved under Android/data/<package>/files/crash-reports/.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            CrashReports(
                crashReports = crashReports,
                onEvent = onEvent,
                includeRawPreview = true,
            )
        }
    }
}

@Composable
private fun CrashReports(
    crashReports: List<SavedCrashReport>,
    onEvent: (BenchmarkUiEvent) -> Unit,
    includeRawPreview: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Crash Reports (${crashReports.size})")
        crashReports.forEach { crash ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(crash.timestamp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("${crash.exception}: ${crash.message}", style = MaterialTheme.typography.bodySmall)
                Text("Thread: ${crash.thread}", style = MaterialTheme.typography.bodySmall)
                Text("File: ${crash.absolutePath}", style = MaterialTheme.typography.bodySmall)
                if (includeRawPreview) {
                    SelectionContainer {
                        Text(
                            text = crash.rawJson.take(1400),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onEvent(BenchmarkUiEvent.ShareCrashReport(crash.absolutePath)) }) {
                        Text("Share crash")
                    }
                    OutlinedButton(onClick = { onEvent(BenchmarkUiEvent.DeleteCrashReport(crash.fileName)) }) {
                        Text("Delete crash")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportTable(files: List<SavedJsonFile>) {
    val horizontalScroll = rememberScrollState()
    Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
        ReportRow(
            values = listOf(
                "Run",
                "Model",
                "Quant",
                "Runtime",
                "Backend",
                "Measurement",
                "Evidence",
                "Input tokens",
                "Prefill tok/s",
                "Decode tok/s",
                "TTFT",
                "Peak RAM",
                "Battery drain",
                "Load time",
                "Device Name",
                "Condition",
            ),
            header = true,
        )
        files.flatMap { it.records }.forEach { record ->
            ReportRow(
                values = listOf(
                    record.run.condition.consecutiveGenerationNumber.toString(),
                    record.model.name,
                    record.model.quantization,
                    record.runtime.engine,
                    record.runtime.backend,
                    record.runtime.measurementStatus,
                    record.hardware.profiling.evidence.orEmpty(),
                    record.prompt.inputTokenCount?.toString().orEmpty(),
                    formatDouble(record.inference.prefill.tokensPerSecond),
                    formatDouble(record.inference.decode.tokensPerSecond),
                    formatSeconds(record.inference.ttftMs),
                    formatMemoryMb(record.memory.peakAppPssMb),
                    record.battery.drainPercentage?.toString().orEmpty(),
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
                1 -> 240.dp
                4 -> 180.dp
                5 -> 220.dp
                6 -> 320.dp
                13 -> 180.dp
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
