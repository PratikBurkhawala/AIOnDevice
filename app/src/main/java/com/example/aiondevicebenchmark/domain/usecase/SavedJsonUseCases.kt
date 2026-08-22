package com.example.aiondevicebenchmark.domain.usecase

import com.example.aiondevicebenchmark.data.SavedJsonFile
import com.example.aiondevicebenchmark.domain.repository.BenchmarkResultRepository

class ListSavedJsonFilesUseCase(
    private val repository: BenchmarkResultRepository,
) {
    operator fun invoke(): List<SavedJsonFile> = repository.listSavedFiles()
}

class FindSavedJsonFileUseCase(
    private val repository: BenchmarkResultRepository,
) {
    operator fun invoke(fileName: String): SavedJsonFile? = repository.findSavedFile(fileName)
}

class DeleteSavedJsonFileUseCase(
    private val repository: BenchmarkResultRepository,
) {
    operator fun invoke(fileName: String): Boolean = repository.delete(fileName)
}
