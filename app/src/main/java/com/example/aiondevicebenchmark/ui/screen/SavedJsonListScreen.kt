package com.example.aiondevicebenchmark.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.example.aiondevicebenchmark.ui.composable.formatDate

@Composable
fun SavedJsonListScreen(
    files: List<SavedJsonFile>,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Saved JSON")
            OutlinedButton(onClick = { onEvent(BenchmarkUiEvent.RefreshSavedFiles) }) {
                Text("Refresh")
            }
        }
        if (files.isEmpty()) {
            Text("No saved JSON files yet.")
        } else {
            files.forEach { file ->
                SavedJsonListItem(file = file, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun SavedJsonListItem(
    file: SavedJsonFile,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(file.fileName, fontWeight = FontWeight.SemiBold)
        Text("Saved: ${formatDate(file.lastModifiedMs)}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEvent(BenchmarkUiEvent.SelectJson(file.fileName)) }) {
                Text("Open")
            }
            OutlinedButton(onClick = { onEvent(BenchmarkUiEvent.DeleteJson(file.fileName)) }) {
                Text("Delete")
            }
        }
    }
}
