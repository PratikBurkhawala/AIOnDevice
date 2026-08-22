package com.example.aiondevicebenchmark.ui.benchmark

import com.example.aiondevicebenchmark.benchmark.BenchmarkConfig
import com.example.aiondevicebenchmark.benchmark.BenchmarkState
import com.example.aiondevicebenchmark.data.SavedJsonFile
import com.example.aiondevicebenchmark.domain.model.ModelDownloadState
import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.ModelConfig

data class BenchmarkUiState(
    val config: BenchmarkConfig = BenchmarkConfig(),
    val benchmarkState: BenchmarkState = BenchmarkState.Idle,
    val screen: AppScreen = AppScreen.Benchmark,
    val savedFiles: List<SavedJsonFile> = emptyList(),
    val reportFiles: List<SavedJsonFile> = emptyList(),
    val reportLoading: Boolean = false,
    val selectedFileName: String? = null,
    val selectedFile: SavedJsonFile? = null,
    val focusResultRequest: Int = 0,
    val supportedEngines: List<EngineType> = emptyList(),
    val supportedModels: List<ModelConfig> = emptyList(),
    val modelDownloads: Map<String, ModelDownloadState> = emptyMap(),
    val modelStorageDirectory: String = "",
    val message: String? = null,
)
