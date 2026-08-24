package com.example.aiondevicebenchmark.ui.benchmark

import com.example.aiondevicebenchmark.benchmark.BenchmarkConfig
import com.example.aiondevicebenchmark.benchmark.BenchmarkState
import com.example.aiondevicebenchmark.data.SavedCrashReport
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
    val crashReports: List<SavedCrashReport> = emptyList(),
    val reportLoading: Boolean = false,
    val selectedFileName: String? = null,
    val selectedFile: SavedJsonFile? = null,
    val focusResultRequest: Int = 0,
    val supportedEngines: List<EngineType> = emptyList(),
    val supportedModels: List<ModelConfig> = emptyList(),
    val modelDownloads: Map<String, ModelDownloadState> = emptyMap(),
    val modelStorageDirectory: String = "",
    val message: String? = null,
    val maxOutputTokensInput: String = config.generation.maxOutputTokens.toString(),
    val contextSizeInput: String = config.model.contextSize.toString(),
    val generationsInput: String = config.consecutiveGenerations.toString(),
    val ramSamplingIntervalInput: String = config.ramSamplingIntervalSeconds.toString(),
    val llamaGpuLayersInput: String = config.model.gpuLayers.toString(),
    val temperatureInput: String = config.generation.temperature.toString(),
    val topKInput: String = config.generation.topK.toString(),
    val topPInput: String = config.generation.topP.toString(),
    val seedInput: String = config.generation.seed.toString(),
)
