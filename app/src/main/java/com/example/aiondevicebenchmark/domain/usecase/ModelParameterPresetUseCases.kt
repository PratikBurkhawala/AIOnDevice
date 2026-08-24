package com.example.aiondevicebenchmark.domain.usecase

import com.example.aiondevicebenchmark.data.ModelParameterPreset
import com.example.aiondevicebenchmark.data.ModelParameterPresetRepository
import com.example.aiondevicebenchmark.llm.ModelConfig

class GetModelParameterPresetUseCase(
    private val repository: ModelParameterPresetRepository,
) {
    operator fun invoke(model: ModelConfig): ModelParameterPreset? = repository.presetFor(model)
}
