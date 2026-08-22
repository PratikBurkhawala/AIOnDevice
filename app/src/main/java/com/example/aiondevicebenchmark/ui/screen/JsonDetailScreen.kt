package com.example.aiondevicebenchmark.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiondevicebenchmark.data.SavedJsonFile
import com.example.aiondevicebenchmark.ui.composable.KeyValueTable
import com.example.aiondevicebenchmark.ui.composable.SectionTitle
import com.example.aiondevicebenchmark.ui.composable.keyValueRows

@Composable
fun JsonDetailScreen(file: SavedJsonFile?) {
    if (file == null) {
        Text("Select a JSON file from Saved JSON.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("JSON Detail")
        Text(file.fileName, fontWeight = FontWeight.SemiBold)
        KeyValueTable(rows = keyValueRows(file.record))
    }
}
