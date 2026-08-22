package com.example.aiondevicebenchmark.benchmark

import com.example.aiondevicebenchmark.data.BenchmarkRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BenchmarkController(
    private val runner: BenchmarkRunner,
) {
    private val _state = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val state: StateFlow<BenchmarkState> = _state.asStateFlow()

    suspend fun start(config: BenchmarkConfig) {
        val records = mutableListOf<BenchmarkRecord>()
        try {
            val outputDirectory = runner.run(config) { state, record ->
                _state.value = state
                if (record != null) {
                    records += record
                }
            }
            _state.value = BenchmarkState.Completed(records = records, outputDirectory = outputDirectory)
        } catch (error: Exception) {
            _state.value = BenchmarkState.Failed(error.message ?: "Benchmark failed")
        }
    }
}
