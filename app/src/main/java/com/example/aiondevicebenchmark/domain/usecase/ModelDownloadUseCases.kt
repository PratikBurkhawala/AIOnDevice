package com.example.aiondevicebenchmark.domain.usecase

import com.example.aiondevicebenchmark.domain.model.ModelDownloadState
import com.example.aiondevicebenchmark.domain.repository.ModelDownloadRepository
import com.example.aiondevicebenchmark.llm.ModelConfig
import kotlinx.coroutines.flow.StateFlow

class ObserveModelDownloadsUseCase(
    private val repository: ModelDownloadRepository,
) {
    operator fun invoke(): StateFlow<Map<String, ModelDownloadState>> = repository.states
}

class RefreshModelDownloadsUseCase(
    private val repository: ModelDownloadRepository,
) {
    operator fun invoke(models: List<ModelConfig>) = repository.refresh(models)
}

class StartModelDownloadUseCase(
    private val repository: ModelDownloadRepository,
) {
    operator fun invoke(model: ModelConfig) = repository.startDownload(model)
}

class LocalizeModelUseCase(
    private val repository: ModelDownloadRepository,
) {
    operator fun invoke(model: ModelConfig): ModelConfig = repository.localModel(model)
}
