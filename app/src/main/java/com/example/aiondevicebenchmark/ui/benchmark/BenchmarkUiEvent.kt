package com.example.aiondevicebenchmark.ui.benchmark

import com.example.aiondevicebenchmark.benchmark.BenchmarkCondition
import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.ModelConfig

sealed interface BenchmarkUiEvent {
    data class ShowScreen(val screen: AppScreen) : BenchmarkUiEvent
    data class SelectJson(val fileName: String) : BenchmarkUiEvent
    data object RefreshSavedFiles : BenchmarkUiEvent
    data class DeleteJson(val fileName: String) : BenchmarkUiEvent
    data object ShareReportCsv : BenchmarkUiEvent
    data object StartBenchmark : BenchmarkUiEvent
    data class DownloadModel(val model: ModelConfig) : BenchmarkUiEvent
    data class DeleteModel(val model: ModelConfig) : BenchmarkUiEvent
    data class UseModel(val model: ModelConfig) : BenchmarkUiEvent
    data object ClearMessage : BenchmarkUiEvent
    data class UpdateEngine(val value: EngineType) : BenchmarkUiEvent
    data class UpdateModel(val value: String) : BenchmarkUiEvent
    data class UpdateCondition(val value: BenchmarkCondition) : BenchmarkUiEvent
    data class UpdatePrompt(val value: String) : BenchmarkUiEvent
    data class UpdateMaxOutputTokens(val value: String) : BenchmarkUiEvent
    data class UpdateConsecutiveGenerations(val value: String) : BenchmarkUiEvent
    data class UpdateTemperature(val value: String) : BenchmarkUiEvent
    data class UpdateTopK(val value: String) : BenchmarkUiEvent
    data class UpdateTopP(val value: String) : BenchmarkUiEvent
    data class UpdateSeed(val value: String) : BenchmarkUiEvent
}
