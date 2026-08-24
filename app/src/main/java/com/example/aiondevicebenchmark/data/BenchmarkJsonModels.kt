package com.example.aiondevicebenchmark.data

import com.example.aiondevicebenchmark.llm.GenerationConfig
import com.example.aiondevicebenchmark.llm.GenerationResult
import com.example.aiondevicebenchmark.llm.ModelConfig
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class BenchmarkRecord(
    val run: RunJson,
    val device: DeviceJson,
    val runtime: RuntimeJson,
    val model: ModelJson,
    val generationConfig: GenerationConfigJson,
    val prompt: PromptJson,
    val modelLoading: ModelLoadingJson,
    val inference: InferenceJson,
    val memory: MemoryJson,
    val battery: BatteryJson,
    val hardware: HardwareJson,
    val modelUnloading: ModelUnloadingJson,
    val result: ResultJson,
    val observation: ObservationJson,
)

@Serializable
data class BenchmarkRunJson(
    val runGroupId: String,
    val startedAt: String,
    val endedAt: String,
    val device: DeviceJson,
    val runtime: RuntimeJson,
    val model: ModelJson,
    val generationConfig: GenerationConfigJson,
    val prompt: PromptJson,
    val battery: RunBatteryJson,
    val ram: RunRamJson,
    val modelLoading: ModelLoadingJson,
    val inferenceRuns: List<InferenceRunJson>,
    val hardware: HardwareJson,
    val modelUnloading: ModelUnloadingJson,
    val summary: RunSummaryJson,
    val result: ResultJson,
    val observation: ObservationJson,
)

@Serializable
data class LegacyBenchmarkRunJson(
    val runGroupId: String,
    val startedAt: String,
    val endedAt: String,
    val results: List<BenchmarkRecord>,
)

@Serializable
data class RunJson(
    val runId: String,
    val runGroupId: String,
    val timestamp: TimestampJson,
    val condition: ConditionJson,
)

@Serializable
data class TimestampJson(val start: String, val end: String)

@Serializable
data class ConditionJson(
    val type: String,
    val batterySaver: Boolean,
    val charging: Boolean,
    val screenOn: Boolean,
    val appState: String,
    val memoryPressure: String,
    val consecutiveGenerationNumber: Int,
    val totalConsecutiveGenerations: Int,
)

@Serializable
data class DeviceJson(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val soc: SocJson,
    val cpu: CpuJson,
    val gpu: GpuJson,
    val npu: NpuJson,
    val ram: RamJson,
)

@Serializable
data class SocJson(val manufacturer: String?, val model: String?)

@Serializable
data class CpuJson(val architecture: String?, val cores: Int)

@Serializable
data class GpuJson(val name: String?)

@Serializable
data class NpuJson(val name: String?, val available: Boolean?)

@Serializable
data class RamJson(val totalMb: Long?)

@Serializable
data class RuntimeJson(
    val engine: String,
    val version: String,
    val backend: String,
    val threads: Int?,
    val gpuLayers: Int?,
    val measurementStatus: String = "",
)

@Serializable
data class ModelJson(
    val name: String,
    val parameters: String,
    val format: String,
    val fileName: String,
    val filePath: String = "",
    val quantization: String,
    val fileSizeBytes: Long?,
    val contextSize: Int,
    val gpuLayers: Int,
    val cpuThreads: Int = 0,
    val maxOutputTokens: Int,
) {
    companion object {
        fun from(model: ModelConfig, maxOutputTokens: Int): ModelJson {
            val fileSize = model.fileSizeBytes
                ?: model.filePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }?.length()
            return ModelJson(
                name = model.name,
                parameters = model.parameters,
                format = model.format,
                fileName = model.fileName,
                filePath = model.filePath,
                quantization = model.quantization,
                fileSizeBytes = fileSize,
                contextSize = model.contextSize,
                gpuLayers = model.gpuLayers,
                cpuThreads = model.cpuThreads,
                maxOutputTokens = maxOutputTokens,
            )
        }
    }
}

@Serializable
data class GenerationConfigJson(
    val temperature: Double,
    val topK: Int,
    val topP: Double,
    val seed: Int,
) {
    companion object {
        fun from(config: GenerationConfig): GenerationConfigJson {
            return GenerationConfigJson(
                temperature = config.temperature,
                topK = config.topK,
                topP = config.topP,
                seed = config.seed,
            )
        }
    }
}

@Serializable
data class PromptJson(
    val promptId: String,
    val tokenTarget: Int?,
    val inputTokenCount: Int?,
    val outputTokenTarget: Int,
    val text: String = "",
    val characterCount: Int = text.length,
    val engineInputText: String = text,
    val engineInputCharacterCount: Int = engineInputText.length,
)

@Serializable
data class ModelLoadingJson(
    val loadStart: String,
    val loadEnd: String,
    val loadTimeMs: Long?,
)

@Serializable
data class InferenceJson(
    val generationStart: String,
    val firstTokenTime: String?,
    val generatedText: String = "",
    val ttftMs: Long?,
    val prefill: PrefillJson,
    val decode: DecodeJson,
    val total: TotalInferenceJson,
) {
    companion object {
        fun from(
            generationStart: String,
            generationEnd: String,
            firstTokenTime: String?,
            generationResult: GenerationResult,
        ): InferenceJson {
            return InferenceJson(
                generationStart = generationStart,
                firstTokenTime = firstTokenTime,
                generatedText = generationResult.outputText,
                ttftMs = generationResult.ttftMs,
                prefill = PrefillJson(
                    durationMs = generationResult.prefillDurationMs,
                    tokens = generationResult.prefillTokens,
                    tokensPerSecond = generationResult.prefillTokensPerSecond,
                ),
                decode = DecodeJson(
                    durationMs = generationResult.decodeDurationMs,
                    tokens = generationResult.outputTokens,
                    tokensPerSecond = generationResult.decodeTokensPerSecond,
                ),
                total = TotalInferenceJson(
                    durationMs = generationResult.totalDurationMs,
                    outputTokens = generationResult.outputTokens,
                    generationEnd = generationEnd,
                ),
            )
        }
    }
}

@Serializable
data class PrefillJson(val durationMs: Long?, val tokens: Int?, val tokensPerSecond: Double?)

@Serializable
data class DecodeJson(val durationMs: Long?, val tokens: Int?, val tokensPerSecond: Double?)

@Serializable
data class TotalInferenceJson(
    val durationMs: Long?,
    val outputTokens: Int?,
    val generationEnd: String,
)

@Serializable
data class MemoryJson(
    val beforeGenerationMb: Int?,
    val samples: List<MemorySampleJson>,
    val peakAppPssMb: Int?,
    val afterGenerationMb: Int?,
    val afterModelUnloadMb: Int?,
)

@Serializable
data class MemorySampleJson(
    val timestamp: String,
    val phase: String = "",
    val appPssMb: Int?,
)

@Serializable
data class BatterySnapshotJson(
    val percentage: Int?,
    val temperatureC: Double?,
    val charging: Boolean,
    val batterySaver: Boolean,
)

@Serializable
data class TimestampedBatterySnapshotJson(
    val timestamp: String,
    val percentage: Int?,
    val temperatureC: Double?,
    val charging: Boolean,
    val batterySaver: Boolean,
) {
    companion object {
        fun from(timestamp: String, snapshot: BatterySnapshotJson): TimestampedBatterySnapshotJson {
            return TimestampedBatterySnapshotJson(
                timestamp = timestamp,
                percentage = snapshot.percentage,
                temperatureC = snapshot.temperatureC,
                charging = snapshot.charging,
                batterySaver = snapshot.batterySaver,
            )
        }
    }
}

@Serializable
data class RunBatteryJson(
    val beforeStart: TimestampedBatterySnapshotJson,
    val afterEnd: TimestampedBatterySnapshotJson,
    val drainPercentage: Int?,
    val thermalStatus: String,
)

@Serializable
data class BatteryJson(
    val beforePercentage: Int?,
    val afterPercentage: Int?,
    val drainPercentage: Int?,
    val temperatureBeforeC: Double?,
    val temperatureAfterC: Double?,
    val thermalStatus: String,
)

@Serializable
data class RunRamJson(
    val samplingIntervalMs: Long,
    val beforeModelLoadMb: Int?,
    val afterModelLoadMb: Int?,
    val afterModelUnloadMb: Int?,
    val peakAppPssMb: Int?,
    val samples: List<MemorySampleJson>,
)

@Serializable
data class InferenceRunJson(
    val runId: String,
    val index: Int,
    val timestamp: TimestampJson,
    val condition: InferenceConditionJson,
    val inference: InferenceJson,
    val result: ResultJson,
)

@Serializable
data class InferenceConditionJson(
    val type: String,
    val screenOn: Boolean,
    val appState: String,
    val memoryPressure: String,
    val consecutiveGenerationNumber: Int,
    val totalConsecutiveGenerations: Int,
) {
    companion object {
        fun from(condition: ConditionJson): InferenceConditionJson {
            return InferenceConditionJson(
                type = condition.type,
                screenOn = condition.screenOn,
                appState = condition.appState,
                memoryPressure = condition.memoryPressure,
                consecutiveGenerationNumber = condition.consecutiveGenerationNumber,
                totalConsecutiveGenerations = condition.totalConsecutiveGenerations,
            )
        }
    }
}

@Serializable
data class RunSummaryJson(
    val totalInferenceRuns: Int,
    val successfulRuns: Int,
    val failedRuns: Int,
    val averageTtftMs: Double?,
    val averagePrefillTokensPerSecond: Double?,
    val averageDecodeTokensPerSecond: Double?,
    val peakAppPssMb: Int?,
    val batteryDrainPercentage: Int?,
)

@Serializable
data class HardwareJson(
    val backend: String,
    val cpu: HardwareUnitJson,
    val gpu: HardwareUnitJson,
    val npu: HardwareUnitJson,
    val profiling: ProfilingJson,
) {
    companion object {
        fun fromBackend(backend: String, measurementStatus: String = ""): HardwareJson {
            val usesGpu = backend.contains("GPU", ignoreCase = true) ||
                backend.contains("Vulkan", ignoreCase = true)
            val usesNnapi = backend.contains("NNAPI", ignoreCase = true)
            val backendEvidence = backend.ifBlank { "No backend reported by engine." }
            val acceleratorStatus = when {
                usesNnapi -> "NNAPI execution provider requested; per-op NPU/DSP/GPU placement is not independently verified."
                usesGpu -> "Accelerator reported by backend string; utilization is not sampled."
                else -> "No accelerator backend reported."
            }
            return HardwareJson(
                backend = backend,
                cpu = HardwareUnitJson(
                    used = true,
                    utilizationPercent = null,
                    measurementStatus = measurementStatus.ifBlank { "Process-level CPU fallback remains possible." },
                ),
                gpu = HardwareUnitJson(
                    used = usesGpu,
                    utilizationPercent = null,
                    measurementStatus = if (usesGpu) acceleratorStatus else "Not reported by backend.",
                ),
                npu = HardwareUnitJson(
                    used = usesNnapi,
                    utilizationPercent = null,
                    measurementStatus = if (usesNnapi) acceleratorStatus else "Not reported by backend.",
                ),
                profiling = ProfilingJson(
                    tool = "engine backend string",
                    evidence = "$backendEvidence Measurement status: ${measurementStatus.ifBlank { "UNSPECIFIED" }}",
                ),
            )
        }
    }
}

@Serializable
data class HardwareUnitJson(
    val used: Boolean,
    val utilizationPercent: Double?,
    val measurementStatus: String,
)

@Serializable
data class ProfilingJson(val tool: String?, val evidence: String?)

@Serializable
data class ModelUnloadingJson(
    val unloadStart: String,
    val unloadEnd: String,
    val unloadTimeMs: Long?,
)

@Serializable
data class ResultJson(val status: String, val error: String?)

@Serializable
data class ObservationJson(
    val summary: String,
    val issues: List<String>,
    val notes: List<String>,
)
