package com.example.aiondevicebenchmark.llama

import com.example.aiondevicebenchmark.llm.EngineInfo
import com.example.aiondevicebenchmark.llm.GenerationConfig
import com.example.aiondevicebenchmark.llm.GenerationListener
import com.example.aiondevicebenchmark.llm.GenerationResult
import com.example.aiondevicebenchmark.llm.LlmEngine
import com.example.aiondevicebenchmark.llm.LoadResult
import com.example.aiondevicebenchmark.llm.ModelConfig
import com.example.aiondevicebenchmark.llm.TokenizationResult
import com.example.aiondevicebenchmark.llm.UnloadResult

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LlamaCppEngine : LlmEngine {
    private var handle: Long = 0L
    private var engineInfo = EngineInfo(
        name = "llama.cpp",
        version = "native",
        backend = "CPU",
        threads = Runtime.getRuntime().availableProcessors(),
        gpuLayers = 0,
        measurementStatus = "NATIVE",
    )

    override suspend fun loadModel(model: ModelConfig): Triple<Boolean, String, LoadResult?> {
        if (model.filePath.isBlank()) {
            return failure("Model file path is required.")
        }
        if (!File(model.filePath).exists()) {
            return failure("Model file does not exist: ${model.filePath}")
        }

        if (handle != 0L) {
            val unloadResult = unloadModel()
            if (!unloadResult.first) {
                return failure("Unable to unload previous model: ${unloadResult.second}")
            }
        }

        val nativeResult = safeNative("Load model") {
            withContext(Dispatchers.IO) {
                runWithTimeout(LOAD_TIMEOUT_MS, "Load model") {
                    NativeLlamaBridge.loadModel(
                        modelPath = model.filePath,
                        contextSize = model.contextSize,
                        maxOutputTokens = 512,
                    )
                }
            }
        }
        if (!nativeResult.first) {
            return failure(nativeResult.second)
        }
        val load = nativeResult.third ?: return failure("Native load completed without a result.")

        handle = load.handle
        engineInfo = EngineInfo(
            name = "llama.cpp",
            version = load.version,
            backend = load.backend,
            threads = load.threads,
            gpuLayers = load.gpuLayers,
            measurementStatus = "NATIVE",
        )
        return success(LoadResult(loadTimeMs = load.loadTimeMs))
    }

    override suspend fun unloadModel(): Triple<Boolean, String, UnloadResult?> {
        if (handle == 0L) {
            return success(UnloadResult(unloadTimeMs = 0L))
        }

        val activeHandle = handle
        handle = 0L
        val nativeResult = safeNative("Unload model") {
            withContext(Dispatchers.IO) {
                runWithTimeout(UNLOAD_TIMEOUT_MS, "Unload model") {
                    NativeLlamaBridge.unloadModel(activeHandle)
                }
            }
        }
        if (!nativeResult.first) {
            return failure(nativeResult.second)
        }
        return success(UnloadResult(unloadTimeMs = nativeResult.third ?: 0L))
    }

    override fun tokenize(prompt: String): Triple<Boolean, String, TokenizationResult?> {
        if (handle == 0L) {
            return failure("Model must be loaded before tokenization.")
        }
        return try {
            val nativeResult = NativeLlamaBridge.tokenize(handle, prompt)
            if (!nativeResult.first) {
                failure(nativeResult.second)
            } else {
                success(TokenizationResult(tokenCount = nativeResult.third ?: 0))
            }
        } catch (error: Throwable) {
            failure("Tokenize failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    override suspend fun generate(
        prompt: String,
        config: GenerationConfig,
        listener: GenerationListener,
    ): Triple<Boolean, String, GenerationResult?> {
        if (handle == 0L) {
            return failure("Model must be loaded before generation.")
        }

        val activeHandle = handle
        val nativeResult = safeNative("Generate") {
            withContext(Dispatchers.IO) {
                val timeoutMs = generationTimeoutMs(config.maxOutputTokens)
                runWithTimeout(
                    timeoutMs = timeoutMs,
                    operation = "Generate",
                    onTimeout = { NativeLlamaBridge.requestAbort(activeHandle) },
                    timeoutMessage = "Generate timed out after ${timeoutMs / 1000}s and did not stop within ${ABORT_GRACE_TIMEOUT_MS / 1000}s.",
                    graceTimeoutMs = ABORT_GRACE_TIMEOUT_MS,
                ) {
                    var firstTokenSent = false
                    NativeLlamaBridge.generate(
                        handle = activeHandle,
                        prompt = prompt,
                        maxOutputTokens = config.maxOutputTokens,
                        temperature = config.temperature.toFloat(),
                        topK = config.topK,
                        topP = config.topP.toFloat(),
                        seed = config.seed,
                        callback = NativeTokenCallback { token, tokenIndex ->
                            if (!firstTokenSent) {
                                firstTokenSent = true
                                listener.onFirstToken()
                            }
                            listener.onToken(token = token, tokenIndex = tokenIndex)
                        },
                    )
                }
            }
        }
        if (!nativeResult.first) {
            return failure(nativeResult.second)
        }
        val generation = nativeResult.third ?: return failure("Native generation completed without a result.")

        return success(
            GenerationResult(
                outputText = generation.outputText,
                ttftMs = generation.ttftMs,
                prefillDurationMs = generation.prefillDurationMs,
                prefillTokens = generation.prefillTokens,
                prefillTokensPerSecond = generation.prefillTokens.perSecond(generation.prefillDurationMs),
                decodeDurationMs = generation.decodeDurationMs,
                outputTokens = generation.outputTokens,
                decodeTokensPerSecond = generation.outputTokens.perSecond(generation.decodeDurationMs),
                totalDurationMs = generation.totalDurationMs,
            ),
        )
    }

    override fun getEngineInfo(): EngineInfo = engineInfo

    private fun Int.perSecond(durationMs: Long): Double {
        return if (durationMs <= 0L) 0.0 else this / (durationMs / 1000.0)
    }

    private suspend fun <T> safeNative(
        operation: String,
        block: suspend () -> Triple<Boolean, String, T?>,
    ): Triple<Boolean, String, T?> {
        return try {
            block()
        } catch (error: Throwable) {
            failure("$operation failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    private fun <T> runWithTimeout(
        timeoutMs: Long,
        operation: String,
        onTimeout: () -> Unit = {},
        timeoutMessage: String = "$operation timed out after ${timeoutMs / 1000}s.",
        graceTimeoutMs: Long = 0L,
        block: () -> Triple<Boolean, String, T?>,
    ): Triple<Boolean, String, T?> {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit(Callable { block() })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            onTimeout()
            if (graceTimeoutMs > 0L) {
                try {
                    future.get(graceTimeoutMs, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    future.cancel(true)
                    failure(timeoutMessage)
                }
            } else {
                future.cancel(true)
                failure(timeoutMessage)
            }
        } catch (error: Throwable) {
            failure("$operation failed: ${error.message ?: error::class.java.simpleName}")
        } finally {
            executor.shutdownNow()
        }
    }

    private fun generationTimeoutMs(maxOutputTokens: Int): Long {
        return (60_000L + maxOutputTokens.coerceAtLeast(1) * 10_000L).coerceAtMost(GENERATE_TIMEOUT_MAX_MS)
    }

    private fun <T> success(value: T): Triple<Boolean, String, T?> = Triple(true, "", value)

    private fun <T> failure(message: String): Triple<Boolean, String, T?> = Triple(false, message, null)

    private companion object {
        const val LOAD_TIMEOUT_MS = 5 * 60_000L
        const val UNLOAD_TIMEOUT_MS = 30_000L
        const val ABORT_GRACE_TIMEOUT_MS = 15_000L
        const val GENERATE_TIMEOUT_MAX_MS = 15 * 60_000L
    }
}
