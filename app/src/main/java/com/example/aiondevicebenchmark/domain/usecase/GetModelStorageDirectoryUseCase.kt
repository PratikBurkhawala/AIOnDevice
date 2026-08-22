package com.example.aiondevicebenchmark.domain.usecase

import com.example.aiondevicebenchmark.domain.repository.ModelDownloadRepository
import com.example.aiondevicebenchmark.llm.EngineType

class GetModelStorageDirectoryUseCase(
    private val repository: ModelDownloadRepository,
) {
    operator fun invoke(engineType: EngineType): String = repository.storageDirectory(engineType).absolutePath
}
