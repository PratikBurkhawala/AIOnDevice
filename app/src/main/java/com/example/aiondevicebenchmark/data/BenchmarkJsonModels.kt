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
    val threads: Int,
    val gpuLayers: Int,
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
    val inputTokenCount: Int,
    val outputTokenTarget: Int,
)

@Serializable
data class ModelLoadingJson(
    val loadStart: String,
    val loadEnd: String,
    val loadTimeMs: Long,
    val ramBeforeLoadMb: Int?,
    val ramAfterLoadMb: Int?,
)

@Serializable
data class InferenceJson(
    val generationStart: String,
    val firstTokenTime: String?,
    val generatedText: String = "",
    val ttftMs: Long,
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
data class PrefillJson(val durationMs: Long, val tokens: Int, val tokensPerSecond: Double)

@Serializable
data class DecodeJson(val durationMs: Long, val tokens: Int, val tokensPerSecond: Double)

@Serializable
data class TotalInferenceJson(
    val durationMs: Long,
    val outputTokens: Int,
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
data class MemorySampleJson(val timestamp: String, val appPssMb: Int?)

@Serializable
data class BatterySnapshotJson(
    val percentage: Int?,
    val temperatureC: Double?,
    val charging: Boolean,
    val batterySaver: Boolean,
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
data class HardwareJson(
    val backend: String,
    val cpu: HardwareUnitJson,
    val gpu: HardwareUnitJson,
    val npu: HardwareUnitJson,
    val profiling: ProfilingJson,
) {
    companion object {
        fun cpuOnly(backend: String): HardwareJson {
            return HardwareJson(
                backend = backend,
                cpu = HardwareUnitJson(used = true, utilizationPercent = null, measurementStatus = "NOT_MEASURED"),
                gpu = HardwareUnitJson(used = false, utilizationPercent = null, measurementStatus = "NOT_AVAILABLE"),
                npu = HardwareUnitJson(used = false, utilizationPercent = null, measurementStatus = "NOT_AVAILABLE"),
                profiling = ProfilingJson(tool = null, evidence = null),
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
    val unloadTimeMs: Long,
)

@Serializable
data class ResultJson(val status: String, val error: String?)

@Serializable
data class ObservationJson(
    val summary: String,
    val issues: List<String>,
    val notes: List<String>,
)
