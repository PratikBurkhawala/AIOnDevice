package com.example.aiondevicebenchmark.domain.usecase

import com.example.aiondevicebenchmark.data.PromptTokenPreset
import com.example.aiondevicebenchmark.data.PromptTokenPresetRepository

class ListPromptTokenPresetsUseCase(
    private val repository: PromptTokenPresetRepository,
) {
    operator fun invoke(): List<PromptTokenPreset> = repository.listPresets()
}

class GetPromptTokenPresetUseCase(
    private val repository: PromptTokenPresetRepository,
) {
    operator fun invoke(tokenTarget: Int): PromptTokenPreset? = repository.presetFor(tokenTarget)
}
