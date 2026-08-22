package com.example.aiondevicebenchmark.benchmark

import com.example.aiondevicebenchmark.data.BatteryJson
import com.example.aiondevicebenchmark.data.BenchmarkRecord
import com.example.aiondevicebenchmark.data.BenchmarkRunJson
import com.example.aiondevicebenchmark.data.ConditionJson
import com.example.aiondevicebenchmark.data.GenerationConfigJson
import com.example.aiondevicebenchmark.data.HardwareJson
import com.example.aiondevicebenchmark.data.InferenceJson
import com.example.aiondevicebenchmark.data.InferenceConditionJson
import com.example.aiondevicebenchmark.data.InferenceRunJson
import com.example.aiondevicebenchmark.data.MemoryJson
import com.example.aiondevicebenchmark.data.DecodeJson
import com.example.aiondevicebenchmark.data.ModelJson
import com.example.aiondevicebenchmark.data.ModelLoadingJson
import com.example.aiondevicebenchmark.data.ModelUnloadingJson
import com.example.aiondevicebenchmark.data.ObservationJson
import com.example.aiondevicebenchmark.data.PrefillJson
import com.example.aiondevicebenchmark.data.PromptJson
import com.example.aiondevicebenchmark.data.ResultJson
import com.example.aiondevicebenchmark.data.RunBatteryJson
import com.example.aiondevicebenchmark.data.RunJson
import com.example.aiondevicebenchmark.data.RunRamJson
import com.example.aiondevicebenchmark.data.RunSummaryJson
import com.example.aiondevicebenchmark.data.RuntimeJson
import com.example.aiondevicebenchmark.data.TimestampJson
import com.example.aiondevicebenchmark.data.TimestampedBatterySnapshotJson
import com.example.aiondevicebenchmark.data.TotalInferenceJson
import com.example.aiondevicebenchmark.domain.repository.BenchmarkResultRepository
import com.example.aiondevicebenchmark.llm.EngineFactory
import com.example.aiondevicebenchmark.llm.EngineInfo
import com.example.aiondevicebenchmark.llm.GenerationListener
import com.example.aiondevicebenchmark.llm.GenerationResult
import com.example.aiondevicebenchmark.llm.LlmEngine
import com.example.aiondevicebenchmark.telemetry.TelemetryCollector
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class BenchmarkRunner(
    private val engineFactory: EngineFactory,
    private val telemetryCollector: TelemetryCollector,
    private val resultRepository: BenchmarkResultRepository,
) {
    suspend fun run(
        config: BenchmarkConfig,
        onState: (BenchmarkState.Running, BenchmarkRecord?) -> Unit,
    ): String {
        val runGroupId = "G-${UUID.randomUUID()}"
        val totalGenerations = max(1, config.consecutiveGenerations)
        val ramSampleIntervalMs = config.ramSamplingIntervalSeconds.coerceAtLeast(1) * 1000L
        val records = mutableListOf<BenchmarkRecord>()
        val inferenceRuns = mutableListOf<InferenceRunJson>()
        val ramSamples = mutableListOf<com.example.aiondevicebenchmark.data.MemorySampleJson>()
        val runStart = Instant.now().toString()
        val device = telemetryCollector.collectDeviceInfo()
        val batteryBefore = telemetryCollector.collectBattery()
        val ramBeforeLoad = telemetryCollector.memoryMonitor.sampleAppPssMb()
        ramSamples += telemetryCollector.memoryMonitor.sample("BEFORE_MODEL_LOAD")
        var ramAfterLoad: Int? = null
        var ramAfterUnload: Int? = null
        var loadStart = ""
        var loadEnd = ""
        var loadTimeMs: Long? = null
        var unloadStart = ""
        var unloadEnd = ""
        var unloadTimeMs: Long? = null
        var runtime = RuntimeJson(
            engine = config.engineType.displayName,
            version = "",
            backend = "",
            threads = null,
            gpuLayers = null,
            measurementStatus = "",
        )
        var engine: LlmEngine? = null
        var promptInputTokenCount: Int? = null
        val model = ModelJson.from(config.model, config.generation.maxOutputTokens)
        val generationConfig = GenerationConfigJson.from(config.generation)

        try {
            onState(
                BenchmarkState.Running(
                    runGroupId = runGroupId,
                    currentGeneration = 1,
                    totalGenerations = totalGenerations,
                    status = "Preparing telemetry",
                ),
                null,
            )

            engine = engineFactory.create(config.engineType)
            val activeEngine = engine ?: throw IllegalStateException("Engine factory returned no engine")
            onState(
                BenchmarkState.Running(runGroupId, 1, totalGenerations, "Engine created: ${config.engineType.displayName}"),
                null,
            )

            onState(
                BenchmarkState.Running(runGroupId, 1, totalGenerations, "Loading model from local storage"),
                null,
            )
            loadStart = Instant.now().toString()
            val loadResult = sampleRamWhile("MODEL_LOAD", ramSampleIntervalMs, ramSamples) {
                activeEngine.loadModel(config.model).valueOrThrow("Load model")
            }
            loadEnd = Instant.now().toString()
            loadTimeMs = loadResult.loadTimeMs
            ramAfterLoad = telemetryCollector.memoryMonitor.sampleAppPssMb()
            ramSamples += telemetryCollector.memoryMonitor.sample("AFTER_MODEL_LOAD")
            val engineInfo = engine.getEngineInfo()
            runtime = engineInfo.toRuntimeJson()

            onState(
                BenchmarkState.Running(runGroupId, 1, totalGenerations, "Tokenizing prompt for input count"),
                null,
            )
            promptInputTokenCount = activeEngine.tokenize(config.prompt).valueOrThrow("Tokenize").tokenCount

            repeat(totalGenerations) { index ->
                val generationNumber = index + 1
                val runId = "R-${UUID.randomUUID()}"
                try {
                    onState(
                        BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Generating response on background engine thread"),
                        null,
                    )
                    val firstTokenTime = arrayOf<String?>(null)
                    val generationStart = Instant.now().toString()
                    val generationResult: GenerationResult = sampleRamWhile("INFERENCE_RUN_$generationNumber", ramSampleIntervalMs, ramSamples) {
                        activeEngine.generate(
                            prompt = config.prompt,
                            config = config.generation,
                            listener = object : GenerationListener {
                                override fun onFirstToken() {
                                    firstTokenTime[0] = Instant.now().toString()
                                    onState(
                                        BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "First token received"),
                                        null,
                                    )
                                }

                                override fun onToken(token: String, tokenIndex: Int) {
                                    if (tokenIndex == 1 || tokenIndex % 10 == 0) {
                                        onState(
                                            BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Generated $tokenIndex token(s)"),
                                            null,
                                        )
                                    }
                                }
                            },
                        ).valueOrThrow("Generate")
                    }
                    val generationEnd = Instant.now().toString()
                    ramSamples += telemetryCollector.memoryMonitor.sample("AFTER_INFERENCE_RUN_$generationNumber")
                    val condition = ConditionJson(
                        type = config.condition.name,
                        batterySaver = batteryBefore.batterySaver,
                        charging = batteryBefore.charging,
                        screenOn = telemetryCollector.isScreenOn(),
                        appState = telemetryCollector.appState(),
                        memoryPressure = "",
                        consecutiveGenerationNumber = generationNumber,
                        totalConsecutiveGenerations = totalGenerations,
                    )
                    val inference = InferenceJson.from(
                        generationStart = generationStart,
                        generationEnd = generationEnd,
                        firstTokenTime = firstTokenTime[0],
                        generationResult = generationResult,
                    )
                    val record = buildRecord(
                        config = config,
                        runId = runId,
                        runGroupId = runGroupId,
                        timestamp = TimestampJson(start = generationStart, end = generationEnd),
                        condition = condition,
                        device = device,
                        runtime = runtime,
                        model = model,
                        generationConfig = generationConfig,
                        promptInputTokenCount = promptInputTokenCount,
                        modelLoading = ModelLoadingJson(loadStart = loadStart, loadEnd = loadEnd, loadTimeMs = loadTimeMs),
                        inference = inference,
                        memory = MemoryJson(
                            beforeGenerationMb = ramAfterLoad,
                            samples = ramSamples.toList(),
                            peakAppPssMb = ramSamples.mapNotNull { it.appPssMb }.maxOrNull(),
                            afterGenerationMb = telemetryCollector.memoryMonitor.sampleAppPssMb(),
                            afterModelUnloadMb = ramAfterUnload,
                        ),
                        battery = BatteryJson(
                            beforePercentage = batteryBefore.percentage,
                            afterPercentage = null,
                            drainPercentage = null,
                            temperatureBeforeC = batteryBefore.temperatureC,
                            temperatureAfterC = null,
                            thermalStatus = telemetryCollector.thermalStatus(),
                        ),
                        hardware = HardwareJson.fromBackend(runtime.backend, runtime.measurementStatus),
                        modelUnloading = ModelUnloadingJson(unloadStart = unloadStart, unloadEnd = unloadEnd, unloadTimeMs = unloadTimeMs),
                        result = ResultJson(status = "SUCCESS", error = null),
                        issues = measurementIssues(engineInfo.measurementStatus, runtime.backend),
                    )
                    records += record
                    inferenceRuns += InferenceRunJson(
                        runId = runId,
                        index = generationNumber,
                        timestamp = TimestampJson(start = generationStart, end = generationEnd),
                        condition = InferenceConditionJson.from(condition),
                        inference = inference,
                        result = ResultJson(status = "SUCCESS", error = null),
                    )
                    onState(
                        BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Result captured"),
                        record,
                    )
                } catch (error: Exception) {
                    runtime = activeEngine.getEngineInfo().toRuntimeJson()
                    val now = Instant.now().toString()
                    val message = error.message ?: error::class.java.simpleName
                    inferenceRuns += InferenceRunJson(
                        runId = runId,
                        index = generationNumber,
                        timestamp = TimestampJson(start = now, end = now),
                        condition = InferenceConditionJson(
                            type = config.condition.name,
                            screenOn = telemetryCollector.isScreenOn(),
                            appState = telemetryCollector.appState(),
                            memoryPressure = "",
                            consecutiveGenerationNumber = generationNumber,
                            totalConsecutiveGenerations = totalGenerations,
                        ),
                        inference = emptyInference(),
                        result = ResultJson(status = "FAILED", error = message),
                    )
                    val failureRecord = failureRecord(config, runGroupId, generationNumber, totalGenerations, message)
                    records += failureRecord
                    onState(
                        BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Failure captured"),
                        failureRecord,
                    )
                }
            }

            onState(
                BenchmarkState.Running(runGroupId, totalGenerations, totalGenerations, "Unloading model"),
                records.lastOrNull(),
            )
            unloadStart = Instant.now().toString()
            val unloadResult = sampleRamWhile("MODEL_UNLOAD", ramSampleIntervalMs, ramSamples) {
                activeEngine.unloadModel().valueOrThrow("Unload model")
            }
            unloadEnd = Instant.now().toString()
            unloadTimeMs = unloadResult.unloadTimeMs
            ramAfterUnload = telemetryCollector.memoryMonitor.sampleAppPssMb()
            ramSamples += telemetryCollector.memoryMonitor.sample("AFTER_MODEL_UNLOAD")
        } catch (error: Exception) {
            engine?.let { activeEngine ->
                if (unloadStart.isBlank()) {
                    runCatching {
                        unloadStart = Instant.now().toString()
                        val unloadResult = sampleRamWhile("MODEL_UNLOAD", ramSampleIntervalMs, ramSamples) {
                            activeEngine.unloadModel().valueOrThrow("Unload model")
                        }
                        unloadEnd = Instant.now().toString()
                        unloadTimeMs = unloadResult.unloadTimeMs
                        ramAfterUnload = telemetryCollector.memoryMonitor.sampleAppPssMb()
                        ramSamples += telemetryCollector.memoryMonitor.sample("AFTER_MODEL_UNLOAD")
                    }
                }
            }
            val failureRecord = failureRecord(
                config = config,
                runGroupId = runGroupId,
                generationNumber = records.size + 1,
                totalGenerations = totalGenerations,
                error = error.message ?: error::class.java.simpleName,
            )
            records += failureRecord
            onState(
                BenchmarkState.Running(runGroupId, records.size, totalGenerations, "Failure captured"),
                failureRecord,
            )
        }

        val runEnd = Instant.now().toString()
        val batteryAfter = telemetryCollector.collectBattery()
        val batteryDrain = batteryBefore.percentage?.let { before ->
            batteryAfter.percentage?.let { after -> before - after }
        }
        val peakRam = ramSamples.mapNotNull { it.appPssMb }.maxOrNull()
        val successfulRuns = inferenceRuns.count { it.result.status == "SUCCESS" }
        val failedRuns = inferenceRuns.count { it.result.status == "FAILED" }
        onState(
            BenchmarkState.Running(runGroupId, totalGenerations, totalGenerations, "Saving grouped JSON"),
            records.lastOrNull(),
        )
        resultRepository.saveRun(
            BenchmarkRunJson(
                runGroupId = runGroupId,
                startedAt = runStart,
                endedAt = runEnd,
                device = device,
                runtime = runtime,
                model = model,
                generationConfig = generationConfig,
                prompt = PromptJson(
                    promptId = config.promptId,
                    inputTokenCount = promptInputTokenCount,
                    outputTokenTarget = config.generation.maxOutputTokens,
                ),
                battery = RunBatteryJson(
                    beforeStart = TimestampedBatterySnapshotJson.from(runStart, batteryBefore),
                    afterEnd = TimestampedBatterySnapshotJson.from(runEnd, batteryAfter),
                    drainPercentage = batteryDrain,
                    thermalStatus = telemetryCollector.thermalStatus(),
                ),
                ram = RunRamJson(
                    samplingIntervalMs = ramSampleIntervalMs,
                    beforeModelLoadMb = ramBeforeLoad,
                    afterModelLoadMb = ramAfterLoad,
                    afterModelUnloadMb = ramAfterUnload,
                    peakAppPssMb = peakRam,
                    samples = ramSamples,
                ),
                modelLoading = ModelLoadingJson(loadStart = loadStart, loadEnd = loadEnd, loadTimeMs = loadTimeMs),
                inferenceRuns = inferenceRuns,
                hardware = HardwareJson.fromBackend(runtime.backend, runtime.measurementStatus),
                modelUnloading = ModelUnloadingJson(unloadStart = unloadStart, unloadEnd = unloadEnd, unloadTimeMs = unloadTimeMs),
                summary = RunSummaryJson(
                    totalInferenceRuns = inferenceRuns.size,
                    successfulRuns = successfulRuns,
                    failedRuns = failedRuns,
                    averageTtftMs = inferenceRuns.mapNotNull { it.inference.ttftMs?.toDouble() }.averageOrNull(),
                    averagePrefillTokensPerSecond = inferenceRuns.mapNotNull { it.inference.prefill.tokensPerSecond }.averageOrNull(),
                    averageDecodeTokensPerSecond = inferenceRuns.mapNotNull { it.inference.decode.tokensPerSecond }.averageOrNull(),
                    peakAppPssMb = peakRam,
                    batteryDrainPercentage = batteryDrain,
                ),
                result = ResultJson(
                    status = if (failedRuns == 0 && inferenceRuns.isNotEmpty()) "SUCCESS" else "FAILED",
                    error = inferenceRuns.firstOrNull { it.result.status == "FAILED" }?.result?.error,
                ),
                observation = ObservationJson(
                    summary = "Completed $successfulRuns of ${inferenceRuns.size} inference run(s) using ${runtime.backend.ifBlank { runtime.engine }}.",
                    issues = inferenceRuns.mapNotNull { it.result.error } + measurementIssues(runtime.measurementStatus, runtime.backend),
                    notes = listOf(
                        "RAM was sampled every $ramSampleIntervalMs ms across model load, inference, and unload.",
                        "Hardware accelerator usage is inferred from engine backend strings unless profiling evidence says otherwise.",
                    ),
                ),
            ),
        )
        return resultRepository.outputDirectory.absolutePath
    }

    private fun failureRecord(
        config: BenchmarkConfig,
        runGroupId: String,
        generationNumber: Int,
        totalGenerations: Int,
        error: String,
    ): BenchmarkRecord {
        val now = Instant.now().toString()
        val battery = telemetryCollector.collectBattery()
        return BenchmarkRecord(
            run = RunJson(
                runId = "R-${UUID.randomUUID()}",
                runGroupId = runGroupId,
                timestamp = TimestampJson(start = now, end = now),
                condition = ConditionJson(
                    type = config.condition.name,
                    batterySaver = battery.batterySaver,
                    charging = battery.charging,
                    screenOn = telemetryCollector.isScreenOn(),
                    appState = telemetryCollector.appState(),
                    memoryPressure = "",
                    consecutiveGenerationNumber = generationNumber,
                    totalConsecutiveGenerations = totalGenerations,
                ),
            ),
            device = telemetryCollector.collectDeviceInfo(),
            runtime = RuntimeJson(
                engine = config.engineType.displayName,
                version = "",
                backend = "",
                threads = null,
                gpuLayers = null,
                measurementStatus = "",
            ),
            model = ModelJson.from(config.model, config.generation.maxOutputTokens),
            generationConfig = GenerationConfigJson.from(config.generation),
            prompt = PromptJson(
                promptId = config.promptId,
                inputTokenCount = null,
                outputTokenTarget = config.generation.maxOutputTokens,
            ),
            modelLoading = ModelLoadingJson(
                loadStart = "",
                loadEnd = "",
                loadTimeMs = null,
            ),
            inference = emptyInference(),
            memory = MemoryJson(
                beforeGenerationMb = null,
                samples = emptyList(),
                peakAppPssMb = null,
                afterGenerationMb = null,
                afterModelUnloadMb = null,
            ),
            battery = BatteryJson(
                beforePercentage = battery.percentage,
                afterPercentage = battery.percentage,
                drainPercentage = null,
                temperatureBeforeC = battery.temperatureC,
                temperatureAfterC = battery.temperatureC,
                thermalStatus = telemetryCollector.thermalStatus(),
            ),
            hardware = HardwareJson.fromBackend(""),
            modelUnloading = ModelUnloadingJson(
                unloadStart = "",
                unloadEnd = "",
                unloadTimeMs = null,
            ),
            result = ResultJson(status = "FAILED", error = error),
            observation = ObservationJson(summary = "", issues = listOf(error), notes = emptyList()),
        )
    }

    private fun buildRecord(
        config: BenchmarkConfig,
        runId: String,
        runGroupId: String,
        timestamp: TimestampJson,
        condition: ConditionJson,
        device: com.example.aiondevicebenchmark.data.DeviceJson,
        runtime: RuntimeJson,
        model: ModelJson,
        generationConfig: GenerationConfigJson,
        promptInputTokenCount: Int?,
        modelLoading: ModelLoadingJson,
        inference: InferenceJson,
        memory: MemoryJson,
        battery: BatteryJson,
        hardware: HardwareJson,
        modelUnloading: ModelUnloadingJson,
        result: ResultJson,
        issues: List<String>,
    ): BenchmarkRecord {
        return BenchmarkRecord(
            run = RunJson(
                runId = runId,
                runGroupId = runGroupId,
                timestamp = timestamp,
                condition = condition,
            ),
            device = device,
            runtime = runtime,
            model = model,
            generationConfig = generationConfig,
            prompt = PromptJson(
                promptId = config.promptId,
                inputTokenCount = promptInputTokenCount,
                outputTokenTarget = config.generation.maxOutputTokens,
            ),
            modelLoading = modelLoading,
            inference = inference,
            memory = memory,
            battery = battery,
            hardware = hardware,
            modelUnloading = modelUnloading,
            result = result,
            observation = ObservationJson(summary = "", issues = issues, notes = emptyList()),
        )
    }

    private fun emptyInference(): InferenceJson {
        return InferenceJson(
            generationStart = "",
            firstTokenTime = null,
            generatedText = "",
            ttftMs = null,
            prefill = PrefillJson(durationMs = null, tokens = null, tokensPerSecond = null),
            decode = DecodeJson(durationMs = null, tokens = null, tokensPerSecond = null),
            total = TotalInferenceJson(durationMs = null, outputTokens = null, generationEnd = ""),
        )
    }

    private fun measurementIssues(measurementStatus: String, backend: String): List<String> {
        return when (measurementStatus) {
            "SIMULATED" -> listOf("llama.cpp native bindings are not connected; timing is produced by the placeholder adapter.")
            "EXPERIMENTAL_TOKEN_ID_DECODE" -> buildList {
                add("ONNX Runtime path is experimental: prompt tokenization uses pseudo token IDs and generated output is token IDs, not decoded model text.")
                if (backend.contains("NNAPI", ignoreCase = true)) {
                    add("ONNX Runtime NNAPI was requested, but this benchmark does not verify per-op NPU/DSP/GPU placement.")
                }
            }
            "EXPERIMENTAL_TOKENIZER_JSON" -> buildList {
                add("ONNX Runtime tokenizer path is experimental: tokenizer.json is used for prompt IDs and text decode, but sampling and model-specific chat templates are still minimal.")
                if (backend.contains("NNAPI", ignoreCase = true)) {
                    add("ONNX Runtime NNAPI was requested, but this benchmark does not verify per-op NPU/DSP/GPU placement.")
                }
            }
            else -> emptyList()
        }
    }

    private fun EngineInfo.toRuntimeJson(): RuntimeJson {
        return RuntimeJson(
            engine = name,
            version = version,
            backend = backend,
            threads = threads,
            gpuLayers = gpuLayers,
            measurementStatus = measurementStatus,
        )
    }

    private suspend fun <T> sampleRamWhile(
        phase: String,
        intervalMs: Long,
        samples: MutableList<com.example.aiondevicebenchmark.data.MemorySampleJson>,
        block: suspend () -> T,
    ): T = coroutineScope {
        val sampler = launch {
            while (isActive) {
                samples += telemetryCollector.memoryMonitor.sample(phase)
                delay(intervalMs)
            }
        }
        try {
            block()
        } finally {
            sampler.cancelAndJoin()
        }
    }

    private fun List<Double>.averageOrNull(): Double? {
        return if (isEmpty()) null else average()
    }

    private fun <T> Triple<Boolean, String, T?>.valueOrThrow(operation: String): T {
        if (!first) {
            throw IllegalStateException("$operation failed: $second")
        }
        return third ?: throw IllegalStateException("$operation failed: missing engine result")
    }

}
