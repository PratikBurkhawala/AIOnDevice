package com.example.aiondevicebenchmark.domain.usecase

import com.example.aiondevicebenchmark.llm.EngineCatalog
import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.ModelConfig

class GetSupportedEnginesUseCase(
    private val engineCatalog: EngineCatalog,
) {
    operator fun invoke(): List<EngineType> = engineCatalog.engines
}

class GetModelsForEngineUseCase(
    private val engineCatalog: EngineCatalog,
) {
    operator fun invoke(engineType: EngineType): List<ModelConfig> = engineCatalog.modelsFor(engineType)
}

class GetDefaultModelForEngineUseCase(
    private val engineCatalog: EngineCatalog,
) {
    operator fun invoke(engineType: EngineType): ModelConfig = engineCatalog.defaultModelFor(engineType)
}

class DetectModelQuantizationUseCase(
    private val engineCatalog: EngineCatalog,
) {
    operator fun invoke(model: ModelConfig): ModelConfig = engineCatalog.withDetectedQuantization(model)
}
