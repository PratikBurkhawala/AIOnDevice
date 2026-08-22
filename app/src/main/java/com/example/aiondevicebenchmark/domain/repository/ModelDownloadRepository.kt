package com.example.aiondevicebenchmark.domain.repository

import com.example.aiondevicebenchmark.domain.model.ModelDownloadState
import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.ModelConfig
import java.io.File
import kotlinx.coroutines.flow.StateFlow

interface ModelDownloadRepository {
    val states: StateFlow<Map<String, ModelDownloadState>>
    fun refresh(models: List<ModelConfig>)
    fun startDownload(model: ModelConfig)
    fun deleteModel(model: ModelConfig): Boolean
    fun stateFor(model: ModelConfig): ModelDownloadState
    fun localModel(model: ModelConfig): ModelConfig
    fun storageDirectory(engineType: EngineType): File
}
