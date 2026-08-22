package com.example.aiondevicebenchmark.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiondevicebenchmark.domain.model.ModelDownloadState
import com.example.aiondevicebenchmark.domain.model.ModelDownloadStatus
import com.example.aiondevicebenchmark.llm.ModelConfig
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkUiEvent
import com.example.aiondevicebenchmark.ui.composable.KeyValueRow
import com.example.aiondevicebenchmark.ui.composable.SectionTitle
import com.example.aiondevicebenchmark.ui.composable.formatFileSizeGb

@Composable
fun ModelDownloadScreen(
    models: List<ModelConfig>,
    downloads: Map<String, ModelDownloadState>,
    storageDirectory: String,
    selectedFileName: String?,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Models")
        Text(
            text = "Download a GGUF model before benchmarking. Qwen is lighter; SmolLM is larger and needs more memory.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (storageDirectory.isNotBlank()) {
            Text("Storage: $storageDirectory", style = MaterialTheme.typography.bodySmall)
        }

        models.forEach { model ->
            val state = downloads["${model.engineType.storageName}/${model.fileName}"]
            ModelDownloadItem(
                model = model,
                state = state,
                selected = selectedFileName == model.fileName,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun ModelDownloadItem(
    model: ModelConfig,
    state: ModelDownloadState?,
    selected: Boolean,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    val status = state?.status ?: ModelDownloadStatus.NotDownloaded
    val isDownloading = state?.isDownloading == true
    val isDownloaded = state?.isReady == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (selected) "${model.name} (selected)" else model.name,
            fontWeight = FontWeight.SemiBold,
        )
        Text(model.description, style = MaterialTheme.typography.bodySmall)
        KeyValueRow("Quantization", model.quantization)
        KeyValueRow("Size", model.downloadSizeLabel)
        KeyValueRow("Status", state?.message ?: "Not downloaded")

        if (isDownloading) {
            LinearProgressIndicator(
                progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = downloadProgressText(state),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEvent(BenchmarkUiEvent.DownloadModel(model)) },
                enabled = !isDownloading && !isDownloaded,
            ) {
                Text(if (status == ModelDownloadStatus.Failed) "Retry" else "Download")
            }
            OutlinedButton(
                onClick = { onEvent(BenchmarkUiEvent.UseModel(model)) },
                enabled = !isDownloading,
            ) {
                Text("Use")
            }
            OutlinedButton(
                onClick = { onEvent(BenchmarkUiEvent.DeleteModel(model)) },
                enabled = !isDownloading && isDownloaded,
            ) {
                Text("Delete")
            }
        }
    }
}

private fun downloadProgressText(state: ModelDownloadState): String {
    val downloaded = formatBytes(state.bytesDownloaded)
    val total = state.totalBytes?.let { formatBytes(it) }
    return if (total == null) downloaded else "$downloaded / $total"
}

private fun formatBytes(bytes: Long): String {
    return formatFileSizeGb(bytes)
}
