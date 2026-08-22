package com.example.aiondevicebenchmark.ui.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiondevicebenchmark.benchmark.BenchmarkConfig
import com.example.aiondevicebenchmark.benchmark.BenchmarkState
import com.example.aiondevicebenchmark.domain.usecase.DeleteSavedJsonFileUseCase
import com.example.aiondevicebenchmark.domain.usecase.DeleteModelUseCase
import com.example.aiondevicebenchmark.domain.usecase.DetectModelQuantizationUseCase
import com.example.aiondevicebenchmark.domain.usecase.FindSavedJsonFileUseCase
import com.example.aiondevicebenchmark.domain.usecase.GetDefaultModelForEngineUseCase
import com.example.aiondevicebenchmark.domain.usecase.GetModelStorageDirectoryUseCase
import com.example.aiondevicebenchmark.domain.usecase.GetModelsForEngineUseCase
import com.example.aiondevicebenchmark.domain.usecase.GetSupportedEnginesUseCase
import com.example.aiondevicebenchmark.domain.usecase.LocalizeModelUseCase
import com.example.aiondevicebenchmark.domain.usecase.ListSavedJsonFilesUseCase
import com.example.aiondevicebenchmark.domain.usecase.ObserveModelDownloadsUseCase
import com.example.aiondevicebenchmark.domain.usecase.RefreshModelDownloadsUseCase
import com.example.aiondevicebenchmark.domain.usecase.ShareReportCsvUseCase
import com.example.aiondevicebenchmark.domain.usecase.StartModelDownloadUseCase
import com.example.aiondevicebenchmark.domain.usecase.StartBenchmarkUseCase
import com.example.aiondevicebenchmark.llm.GenerationConfig
import com.example.aiondevicebenchmark.llm.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BenchmarkViewModel(
    private val startBenchmarkUseCase: StartBenchmarkUseCase,
    private val listSavedJsonFilesUseCase: ListSavedJsonFilesUseCase,
    private val findSavedJsonFileUseCase: FindSavedJsonFileUseCase,
    private val deleteSavedJsonFileUseCase: DeleteSavedJsonFileUseCase,
    private val shareReportCsvUseCase: ShareReportCsvUseCase,
    private val getSupportedEnginesUseCase: GetSupportedEnginesUseCase,
    private val getModelsForEngineUseCase: GetModelsForEngineUseCase,
    private val getDefaultModelForEngineUseCase: GetDefaultModelForEngineUseCase,
    private val detectModelQuantizationUseCase: DetectModelQuantizationUseCase,
    private val observeModelDownloadsUseCase: ObserveModelDownloadsUseCase,
    private val refreshModelDownloadsUseCase: RefreshModelDownloadsUseCase,
    private val startModelDownloadUseCase: StartModelDownloadUseCase,
    private val deleteModelUseCase: DeleteModelUseCase,
    private val localizeModelUseCase: LocalizeModelUseCase,
    private val getModelStorageDirectoryUseCase: GetModelStorageDirectoryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BenchmarkUiState(
            supportedEngines = getSupportedEnginesUseCase(),
            supportedModels = getModelsForEngineUseCase(BenchmarkConfig().engineType),
            modelStorageDirectory = getModelStorageDirectoryUseCase(BenchmarkConfig().engineType),
        ),
    )
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    init {
        refreshModelDownloadsUseCase(_uiState.value.supportedModels)
        refreshSavedFiles()
        viewModelScope.launch {
            startBenchmarkUseCase.state.collect { benchmarkState ->
                _uiState.update { it.copy(benchmarkState = benchmarkState) }
                if (benchmarkState is BenchmarkState.Completed) {
                    refreshSavedFiles()
                }
            }
        }
        viewModelScope.launch {
            observeModelDownloadsUseCase().collect { downloads ->
                _uiState.update { current ->
                    val config = current.config.copy(model = localizeModelUseCase(current.config.model))
                    current.copy(
                        modelDownloads = downloads,
                        config = config,
                        supportedModels = current.supportedModels.map { localizeModelUseCase(it) },
                    )
                }
            }
        }
    }

    fun onEvent(event: BenchmarkUiEvent) {
        when (event) {
            is BenchmarkUiEvent.ShowScreen -> showScreen(event.screen)
            is BenchmarkUiEvent.SelectJson -> selectJson(event.fileName)
            BenchmarkUiEvent.RefreshSavedFiles -> refreshSavedFiles()
            is BenchmarkUiEvent.DeleteJson -> deleteJson(event.fileName)
            BenchmarkUiEvent.ShareReportCsv -> shareReportCsv()
            BenchmarkUiEvent.StartBenchmark -> startBenchmark()
            is BenchmarkUiEvent.DownloadModel -> downloadModel(event.model)
            is BenchmarkUiEvent.DeleteModel -> deleteModel(event.model)
            is BenchmarkUiEvent.UseModel -> useModel(event.model)
            BenchmarkUiEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
            is BenchmarkUiEvent.UpdateEngine -> update {
                val model = getDefaultModelForEngineUseCase(event.value)
                it.copy(engineType = event.value, model = prepareModel(model))
            }
            is BenchmarkUiEvent.UpdateModel -> update { config ->
                val model = getModelsForEngineUseCase(config.engineType)
                    .firstOrNull { model -> model.name == event.value }
                    ?: getDefaultModelForEngineUseCase(config.engineType)
                config.copy(model = prepareModel(model))
            }
            is BenchmarkUiEvent.UpdateCondition -> update { it.copy(condition = event.value) }
            is BenchmarkUiEvent.UpdatePrompt -> update { it.copy(prompt = event.value) }
            is BenchmarkUiEvent.UpdateMaxOutputTokens -> updateGenerationInt(event.value) { current, intValue ->
                current.copy(maxOutputTokens = intValue.coerceIn(1, 512))
            }
            is BenchmarkUiEvent.UpdateConsecutiveGenerations -> update {
                it.copy(consecutiveGenerations = event.value.toIntOrNull()?.coerceIn(1, 20) ?: it.consecutiveGenerations)
            }
            is BenchmarkUiEvent.UpdateTemperature -> updateGenerationDouble(event.value) { current, doubleValue ->
                current.copy(temperature = doubleValue.coerceIn(0.0, 2.0))
            }
            is BenchmarkUiEvent.UpdateTopK -> updateGenerationInt(event.value) { current, intValue ->
                current.copy(topK = intValue.coerceIn(1, 200))
            }
            is BenchmarkUiEvent.UpdateTopP -> updateGenerationDouble(event.value) { current, doubleValue ->
                current.copy(topP = doubleValue.coerceIn(0.0, 1.0))
            }
            is BenchmarkUiEvent.UpdateSeed -> updateGenerationInt(event.value) { current, intValue ->
                current.copy(seed = intValue)
            }
        }
    }

    private fun showScreen(screen: AppScreen) {
        _uiState.update { it.copy(screen = screen) }
        when (screen) {
            AppScreen.JsonList -> refreshSavedFiles()
            AppScreen.Report -> loadReportFiles()
            AppScreen.Models -> refreshModelDownloadsUseCase(_uiState.value.supportedModels)
            else -> Unit
        }
    }

    private fun selectJson(fileName: String) {
        val selectedFile = _uiState.value.savedFiles.firstOrNull { it.fileName == fileName }
            ?: findSavedJsonFileUseCase(fileName)
        refreshSavedFiles()
        _uiState.update {
            it.copy(
                selectedFileName = fileName,
                selectedFile = selectedFile,
                screen = AppScreen.JsonDetail,
            )
        }
    }

    private fun startBenchmark() {
        val selectedModel = _uiState.value.config.model
        val downloadState = _uiState.value.modelDownloads[selectedModel.downloadKey()]
        if (downloadState?.isDownloading == true) {
            _uiState.update { it.copy(message = "${selectedModel.name} is still downloading. Wait for it to finish before benchmarking.") }
            return
        }
        if (selectedModel.filePath.isBlank()) {
            _uiState.update { it.copy(message = "Download ${selectedModel.name} before starting the benchmark.") }
            return
        }

        _uiState.update { it.copy(focusResultRequest = it.focusResultRequest + 1, message = null) }
        startBenchmarkUseCase(_uiState.value.config)
        refreshSavedFiles()
    }

    private fun downloadModel(model: ModelConfig) {
        startModelDownloadUseCase(model)
        _uiState.update { it.copy(message = "Downloading ${model.name}. The download continues while the app is in the background.") }
    }

    private fun deleteModel(model: ModelConfig) {
        val prepared = prepareModel(model)
        val deleted = deleteModelUseCase(prepared)
        refreshModelDownloadsUseCase(_uiState.value.supportedModels)
        _uiState.update { current ->
            val selectedModel = if (current.config.model.downloadKey() == prepared.downloadKey()) {
                current.config.model.copy(filePath = "", fileSizeBytes = null)
            } else {
                current.config.model
            }
            current.copy(
                config = current.config.copy(model = selectedModel),
                message = if (deleted) "${prepared.name} deleted." else "${prepared.name} is not downloaded.",
            )
        }
    }

    private fun useModel(model: ModelConfig) {
        val prepared = prepareModel(model)
        val state = _uiState.value.modelDownloads[prepared.downloadKey()]
        val message = when {
            state?.isDownloading == true -> "${prepared.name} is downloading. It cannot be benchmarked until the download finishes."
            !state?.isReady.orFalse() -> "${prepared.name} selected. Download it before benchmarking."
            else -> "${prepared.name} selected for benchmark."
        }
        update { it.copy(model = prepared) }
        _uiState.update { it.copy(screen = AppScreen.Benchmark, message = message) }
    }

    private fun refreshSavedFiles() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                listSavedJsonFilesUseCase()
            }
            _uiState.update { current ->
                val selectedFile = current.selectedFileName?.let { selected ->
                    files.firstOrNull { it.fileName == selected } ?: current.selectedFile
                }
                current.copy(savedFiles = files, selectedFile = selectedFile)
            }
        }
    }

    private fun loadReportFiles() {
        _uiState.update { it.copy(reportLoading = true) }
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                listSavedJsonFilesUseCase()
            }
            _uiState.update { it.copy(reportFiles = files, reportLoading = false) }
        }
    }

    private fun deleteJson(fileName: String) {
        deleteSavedJsonFileUseCase(fileName)
        _uiState.update {
            if (it.selectedFileName == fileName) {
                it.copy(selectedFileName = null, selectedFile = null, screen = AppScreen.JsonList)
            } else {
                it
            }
        }
        refreshSavedFiles()
    }

    private fun shareReportCsv() {
        val files = _uiState.value.reportFiles.ifEmpty { _uiState.value.savedFiles }
        if (files.isNotEmpty()) {
            shareReportCsvUseCase(files)
        }
    }

    private fun update(block: (BenchmarkConfig) -> BenchmarkConfig) {
        _uiState.update { current ->
            val config = block(current.config)
            refreshModelDownloadsUseCase(getModelsForEngineUseCase(config.engineType))
            current.copy(
                config = config,
                supportedModels = getModelsForEngineUseCase(config.engineType).map { model -> localizeModelUseCase(model) },
                modelStorageDirectory = getModelStorageDirectoryUseCase(config.engineType),
            )
        }
    }

    private fun prepareModel(model: ModelConfig): ModelConfig {
        return localizeModelUseCase(detectModelQuantizationUseCase(model))
    }

    private fun updateGenerationInt(value: String, block: (GenerationConfig, Int) -> GenerationConfig) {
        val parsed = value.toIntOrNull() ?: return
        update { it.copy(generation = block(it.generation, parsed)) }
    }

    private fun updateGenerationDouble(value: String, block: (GenerationConfig, Double) -> GenerationConfig) {
        val parsed = value.toDoubleOrNull() ?: return
        update { it.copy(generation = block(it.generation, parsed)) }
    }

    private fun Boolean?.orFalse(): Boolean = this == true

    private fun ModelConfig.downloadKey(): String = "${engineType.storageName}/$fileName"
}
