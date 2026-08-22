package com.example.aiondevicebenchmark.benchmark

import com.example.aiondevicebenchmark.data.BatteryJson
import com.example.aiondevicebenchmark.data.BenchmarkRecord
import com.example.aiondevicebenchmark.data.ConditionJson
import com.example.aiondevicebenchmark.data.GenerationConfigJson
import com.example.aiondevicebenchmark.data.HardwareJson
import com.example.aiondevicebenchmark.data.InferenceJson
import com.example.aiondevicebenchmark.data.MemoryJson
import com.example.aiondevicebenchmark.data.DecodeJson
import com.example.aiondevicebenchmark.data.ModelJson
import com.example.aiondevicebenchmark.data.ModelLoadingJson
import com.example.aiondevicebenchmark.data.ModelUnloadingJson
import com.example.aiondevicebenchmark.data.ObservationJson
import com.example.aiondevicebenchmark.data.PrefillJson
import com.example.aiondevicebenchmark.data.PromptJson
import com.example.aiondevicebenchmark.data.ResultJson
import com.example.aiondevicebenchmark.data.RunJson
import com.example.aiondevicebenchmark.data.RuntimeJson
import com.example.aiondevicebenchmark.data.TimestampJson
import com.example.aiondevicebenchmark.data.TotalInferenceJson
import com.example.aiondevicebenchmark.domain.repository.BenchmarkResultRepository
import com.example.aiondevicebenchmark.llm.EngineFactory
import com.example.aiondevicebenchmark.llm.GenerationListener
import com.example.aiondevicebenchmark.llm.GenerationResult
import com.example.aiondevicebenchmark.telemetry.TelemetryCollector
import java.time.Instant
import java.util.UUID
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
        val records = mutableListOf<BenchmarkRecord>()

        repeat(totalGenerations) { index ->
            val generationNumber = index + 1
            try {
                onState(
                    BenchmarkState.Running(
                        runGroupId = runGroupId,
                        currentGeneration = generationNumber,
                        totalGenerations = totalGenerations,
                        status = "Preparing telemetry",
                    ),
                    null,
                )

            val runId = "R-${UUID.randomUUID()}"
            val runStart = Instant.now().toString()
            val device = telemetryCollector.collectDeviceInfo()
            val batteryBefore = telemetryCollector.collectBattery()
            val ramBeforeLoad = telemetryCollector.memoryMonitor.sampleAppPssMb()
            val engine = engineFactory.create(config.engineType)
            onState(
                BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Engine created: ${config.engineType.displayName}"),
                null,
            )

            onState(
                BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Loading model from local storage"),
                null,
            )
            val loadStart = Instant.now().toString()
            val loadResult = engine.loadModel(config.model).valueOrThrow("Load model")
            val loadEnd = Instant.now().toString()
            val ramAfterLoad = telemetryCollector.memoryMonitor.sampleAppPssMb()
            val engineInfo = engine.getEngineInfo()

            onState(
                BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Generating response on background engine thread"),
                null,
            )
            val memorySamples = mutableListOf<com.example.aiondevicebenchmark.data.MemorySampleJson>()
            val firstTokenTime = arrayOf<String?>(null)
            val generationStart = Instant.now().toString()
            val generationResult: GenerationResult = engine.generate(
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
                            memorySamples += telemetryCollector.memoryMonitor.sample()
                            onState(
                                BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Generated $tokenIndex token(s)"),
                                null,
                            )
                        }
                    }
                },
            ).valueOrThrow("Generate")
            val generationEnd = Instant.now().toString()
            val ramAfterGeneration = telemetryCollector.memoryMonitor.sampleAppPssMb()
            onState(
                BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Tokenizing prompt for input count"),
                null,
            )
            val promptInputTokenCount = engine.tokenize(config.prompt).valueOrThrow("Tokenize").tokenCount

            onState(
                BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Unloading model"),
                null,
            )
            val unloadStart = Instant.now().toString()
            val unloadResult = engine.unloadModel().valueOrThrow("Unload model")
            val unloadEnd = Instant.now().toString()
            val ramAfterUnload = telemetryCollector.memoryMonitor.sampleAppPssMb()
            val batteryAfter = telemetryCollector.collectBattery()

            val record = BenchmarkRecord(
                run = RunJson(
                    runId = runId,
                    runGroupId = runGroupId,
                    timestamp = TimestampJson(start = runStart, end = Instant.now().toString()),
                    condition = ConditionJson(
                        type = config.condition.name,
                        batterySaver = batteryBefore.batterySaver,
                        charging = batteryBefore.charging,
                        screenOn = telemetryCollector.isScreenOn(),
                        appState = "FOREGROUND",
                        memoryPressure = "",
                        consecutiveGenerationNumber = generationNumber,
                        totalConsecutiveGenerations = totalGenerations,
                    ),
                ),
                device = device,
                runtime = RuntimeJson(
                    engine = engineInfo.name,
                    version = engineInfo.version,
                    backend = engineInfo.backend,
                    threads = engineInfo.threads,
                    gpuLayers = engineInfo.gpuLayers,
                ),
                model = ModelJson.from(config.model, config.generation.maxOutputTokens),
                generationConfig = GenerationConfigJson.from(config.generation),
                prompt = PromptJson(
                    promptId = config.promptId,
                    inputTokenCount = promptInputTokenCount,
                    outputTokenTarget = config.generation.maxOutputTokens,
                ),
                modelLoading = ModelLoadingJson(
                    loadStart = loadStart,
                    loadEnd = loadEnd,
                    loadTimeMs = loadResult.loadTimeMs,
                    ramBeforeLoadMb = ramBeforeLoad,
                    ramAfterLoadMb = ramAfterLoad,
                ),
                inference = InferenceJson.from(
                    generationStart = generationStart,
                    generationEnd = generationEnd,
                    firstTokenTime = firstTokenTime[0],
                    generationResult = generationResult,
                ),
                memory = MemoryJson(
                    beforeGenerationMb = ramAfterLoad,
                    samples = memorySamples,
                    peakAppPssMb = (memorySamples.map { it.appPssMb } + ramAfterGeneration).filterNotNull().maxOrNull(),
                    afterGenerationMb = ramAfterGeneration,
                    afterModelUnloadMb = ramAfterUnload,
                ),
                battery = BatteryJson(
                    beforePercentage = batteryBefore.percentage,
                    afterPercentage = batteryAfter.percentage,
                    drainPercentage = batteryBefore.percentage?.let { before ->
                        batteryAfter.percentage?.let { after -> before - after }
                    },
                    temperatureBeforeC = batteryBefore.temperatureC,
                    temperatureAfterC = batteryAfter.temperatureC,
                    thermalStatus = telemetryCollector.thermalStatus(),
                ),
                hardware = HardwareJson.cpuOnly(engineInfo.backend),
                modelUnloading = ModelUnloadingJson(
                    unloadStart = unloadStart,
                    unloadEnd = unloadEnd,
                    unloadTimeMs = unloadResult.unloadTimeMs,
                ),
                result = ResultJson(status = "SUCCESS", error = null),
                observation = ObservationJson(
                    summary = "",
                    issues = if (engineInfo.measurementStatus == "SIMULATED") {
                        listOf("llama.cpp native bindings are not connected; timing is produced by the placeholder adapter.")
                    } else {
                        emptyList()
                    },
                    notes = emptyList(),
                ),
            )

            records += record
            onState(
                BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Result captured"),
                record,
            )
            } catch (error: Exception) {
                val failureRecord = failureRecord(
                    config = config,
                    runGroupId = runGroupId,
                    generationNumber = generationNumber,
                    totalGenerations = totalGenerations,
                    error = error.message ?: error::class.java.simpleName,
                )
                records += failureRecord
                onState(
                    BenchmarkState.Running(runGroupId, generationNumber, totalGenerations, "Failure captured"),
                    failureRecord,
                )
            }
        }

        if (records.isNotEmpty()) {
            onState(
                BenchmarkState.Running(runGroupId, totalGenerations, totalGenerations, "Saving grouped JSON"),
                records.last(),
            )
            resultRepository.saveRun(runGroupId, records)
        }
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
                    appState = "",
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
                ramBeforeLoadMb = null,
                ramAfterLoadMb = null,
            ),
            inference = InferenceJson(
                generationStart = "",
                firstTokenTime = null,
                generatedText = "",
                ttftMs = null,
                prefill = PrefillJson(durationMs = null, tokens = null, tokensPerSecond = null),
                decode = DecodeJson(durationMs = null, tokens = null, tokensPerSecond = null),
                total = TotalInferenceJson(durationMs = null, outputTokens = null, generationEnd = ""),
            ),
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
            hardware = HardwareJson.cpuOnly(""),
            modelUnloading = ModelUnloadingJson(
                unloadStart = "",
                unloadEnd = "",
                unloadTimeMs = null,
            ),
            result = ResultJson(status = "FAILED", error = error),
            observation = ObservationJson(summary = "", issues = listOf(error), notes = emptyList()),
        )
    }

    private fun <T> Triple<Boolean, String, T?>.valueOrThrow(operation: String): T {
        if (!first) {
            throw IllegalStateException("$operation failed: $second")
        }
        return third ?: throw IllegalStateException("$operation failed: missing engine result")
    }
}
