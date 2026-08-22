package com.example.aiondevicebenchmark.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiondevicebenchmark.benchmark.BenchmarkState
import com.example.aiondevicebenchmark.ui.benchmark.AppScreen
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkUiEvent
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkUiState
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun BenchmarkRoute(viewModel: BenchmarkViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BenchmarkApp(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BenchmarkApp(
    state: BenchmarkUiState,
    onEvent: (BenchmarkUiEvent) -> Unit,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.focusResultRequest) {
        if (state.focusResultRequest > 0) {
            coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
        }
    }
    LaunchedEffect(state.benchmarkState) {
        if (state.benchmarkState !is BenchmarkState.Idle) {
            coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI on Device Benchmark") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppScreen.entries
                        .filter { it != AppScreen.JsonDetail || state.selectedFile != null }
                        .forEach { item ->
                            FilterChip(
                                selected = state.screen == item,
                                onClick = { onEvent(BenchmarkUiEvent.ShowScreen(item)) },
                                label = { Text(item.label) },
                            )
                        }
                }

                state.message?.let { message ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick = { onEvent(BenchmarkUiEvent.ClearMessage) }) {
                            Text("Dismiss")
                        }
                    }
                }

                when (state.screen) {
                    AppScreen.Benchmark -> BenchmarkScreen(state = state, onEvent = onEvent)
                    AppScreen.Models -> ModelDownloadScreen(
                        models = state.supportedModels,
                        downloads = state.modelDownloads,
                        storageDirectory = state.modelStorageDirectory,
                        selectedFileName = state.config.model.fileName,
                        onEvent = onEvent,
                    )
                    AppScreen.JsonList -> SavedJsonListScreen(
                        files = state.savedFiles,
                        onEvent = onEvent,
                    )
                    AppScreen.JsonDetail -> JsonDetailScreen(file = state.selectedFile, onEvent = onEvent)
                    AppScreen.Report -> ReportScreen(
                        files = state.reportFiles,
                        crashReports = state.crashReports,
                        loading = state.reportLoading,
                        onEvent = onEvent,
                    )
                    AppScreen.Crashes -> CrashReportScreen(
                        crashReports = state.crashReports,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}
