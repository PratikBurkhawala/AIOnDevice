package com.example.aiondevicebenchmark.benchmark

import com.example.aiondevicebenchmark.data.BenchmarkRecord

sealed interface BenchmarkState {
    data object Idle : BenchmarkState
    data class Running(
        val runGroupId: String,
        val currentGeneration: Int,
        val totalGenerations: Int,
        val status: String,
    ) : BenchmarkState

    data class Completed(
        val records: List<BenchmarkRecord>,
        val outputDirectory: String,
    ) : BenchmarkState

    data class Failed(val message: String) : BenchmarkState
}
