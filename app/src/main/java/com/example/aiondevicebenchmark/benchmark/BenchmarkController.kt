package com.example.aiondevicebenchmark.benchmark

import com.example.aiondevicebenchmark.data.BenchmarkRecord
import com.example.aiondevicebenchmark.background.BackgroundWorkTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BenchmarkController(
    private val runner: BenchmarkRunner,
    private val scope: CoroutineScope,
    private val backgroundWorkTracker: BackgroundWorkTracker,
) {
    private val _state = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val state: StateFlow<BenchmarkState> = _state.asStateFlow()
    private var activeJob: Job? = null

    fun start(config: BenchmarkConfig) {
        if (activeJob?.isActive == true) {
            _state.value = BenchmarkState.Failed("Benchmark is already running.")
            return
        }
        activeJob = scope.launch {
            backgroundWorkTracker.begin("Benchmark is running").use {
                runBenchmark(config)
            }
        }
    }

    private suspend fun runBenchmark(config: BenchmarkConfig) {
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
        } finally {
            activeJob = null
        }
    }
}
