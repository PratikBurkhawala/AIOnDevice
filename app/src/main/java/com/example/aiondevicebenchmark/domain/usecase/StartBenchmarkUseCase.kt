package com.example.aiondevicebenchmark.domain.usecase

import com.example.aiondevicebenchmark.benchmark.BenchmarkConfig
import com.example.aiondevicebenchmark.benchmark.BenchmarkController
import com.example.aiondevicebenchmark.benchmark.BenchmarkState
import kotlinx.coroutines.flow.StateFlow

class StartBenchmarkUseCase(
    private val controller: BenchmarkController,
) {
    val state: StateFlow<BenchmarkState> = controller.state

    suspend operator fun invoke(config: BenchmarkConfig) {
        controller.start(config)
    }
}
