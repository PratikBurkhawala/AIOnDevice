package com.example.aiondevicebenchmark.onnx

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

internal class OnnxRuntimeEngine : LlmEngine {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokenizer: ByteLevelBpeTokenizer? = null
    private var loadedModelName: String = ""
    private var loadedContextSize: Int = DEFAULT_CONTEXT_TOKENS
    private var engineInfo = EngineInfo(
        name = "ONNX Runtime",
        version = "native",
        backend = "ONNX Runtime CPU",
        threads = Runtime.getRuntime().availableProcessors(),
        gpuLayers = 0,
        measurementStatus = "EXPERIMENTAL_ONNX_DECODE_UNAVAILABLE",
    )

    override suspend fun loadModel(model: ModelConfig): Triple<Boolean, String, LoadResult?> {
        if (model.filePath.isBlank()) {
            return failure("Model file path is required.")
        }
        if (!File(model.filePath).exists()) {
            return failure("Model file does not exist: ${model.filePath}")
        }

        unloadModel()
        return try {
            val start = Instant.now()
            val localEnv = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            val backend = if (options.tryEnableNnapi()) "ONNX Runtime NNAPI" else "ONNX Runtime CPU"
            val localSession = localEnv.createSession(model.filePath, options)
            val localTokenizer = model.tokenizerFilePath
                .takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?.takeIf { it.exists() && it.length() > 0L }
                ?.let { ByteLevelBpeTokenizer.fromFile(it) }
            env = localEnv
            session = localSession
            tokenizer = localTokenizer
            loadedModelName = model.name
            loadedContextSize = model.contextSize.coerceAtLeast(1)
            engineInfo = engineInfo.copy(
                backend = backend,
                threads = Runtime.getRuntime().availableProcessors(),
                measurementStatus = if (localTokenizer == null) {
                    "EXPERIMENTAL_ONNX_DECODE_UNAVAILABLE"
                } else {
                    "EXPERIMENTAL_TOKENIZER_JSON"
                },
            )
            success(LoadResult(loadTimeMs = elapsedMs(start)))
        } catch (error: Throwable) {
            failure("ONNX model load failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    override suspend fun unloadModel(): Triple<Boolean, String, UnloadResult?> {
        return try {
            val start = Instant.now()
            session?.close()
            session = null
            tokenizer = null
            loadedModelName = ""
            loadedContextSize = DEFAULT_CONTEXT_TOKENS
            success(UnloadResult(unloadTimeMs = elapsedMs(start)))
        } catch (error: Throwable) {
            failure("ONNX model unload failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    override fun effectivePrompt(prompt: String): String = formatPrompt(prompt)

    override fun tokenize(prompt: String): Triple<Boolean, String, TokenizationResult?> {
        return success(TokenizationResult(tokenCount = formatPrompt(prompt).toTokenIds().size))
    }

    override suspend fun generate(
        prompt: String,
        config: GenerationConfig,
        listener: GenerationListener,
    ): Triple<Boolean, String, GenerationResult?> {
        val activeSession = session ?: return failure("Model must be loaded before generation.")
        val activeEnv = env ?: return failure("ONNX Runtime environment is not initialized.")

        return try {
            val totalStart = Instant.now()
            val activeTokenizer = tokenizer
                ?: return failure("ONNX generation requires tokenizer.json for text decode. Re-download the model so the tokenizer file is present.")
            val promptTokens = formatPrompt(prompt).toTokenIds().toMutableList()
            if (promptTokens.isEmpty()) {
                promptTokens += DEFAULT_BOS_TOKEN
            }
            val outputTarget = config.maxOutputTokens.coerceAtLeast(1)
            if (promptTokens.size + outputTarget > loadedContextSize) {
                return failure(
                    "Prompt and output target exceed ONNX context size. " +
                        "Prompt tokens=${promptTokens.size}, output target=$outputTarget, context size=$loadedContextSize.",
                )
            }

            val prefillStart = Instant.now()
            var stepResult = activeSession.runStep(
                env = activeEnv,
                tokenIds = promptTokens,
                past = OnnxKvCache.empty(),
            )
            var nextToken = stepResult.nextToken
            var cache = stepResult.cache
            val prefillMs = elapsedMs(prefillStart)

            val outputTokens = mutableListOf<Long>()
            var firstTokenMs: Long? = null
            val decodeStart = Instant.now()
            repeat(outputTarget) { index ->
                if (firstTokenMs == null) {
                    firstTokenMs = elapsedMs(totalStart)
                    listener.onFirstToken()
                }
                outputTokens += nextToken
                val piece = "<$nextToken>"
                listener.onToken(piece, index + 1)
                val previousCache = cache
                stepResult = activeSession.runStep(
                    env = activeEnv,
                    tokenIds = listOf(nextToken),
                    past = cache,
                )
                cache = stepResult.cache
                previousCache.close()
                nextToken = stepResult.nextToken
            }
            cache.close()
            val decodeMs = elapsedMs(decodeStart)
            val totalMs = elapsedMs(totalStart)
            val output = activeTokenizer.decode(outputTokens)
            if (output.isBlank()) {
                return failure("ONNX generation produced no decoded text. The model likely emitted only special/end tokens; check the tokenizer, chat template, and model compatibility.")
            }

            success(
                GenerationResult(
                    outputText = output,
                    ttftMs = firstTokenMs ?: totalMs,
                    prefillDurationMs = prefillMs,
                    prefillTokens = promptTokens.size,
                    prefillTokensPerSecond = promptTokens.size.perSecond(prefillMs),
                    decodeDurationMs = decodeMs,
                    outputTokens = outputTokens.size,
                    decodeTokensPerSecond = outputTokens.size.perSecond(decodeMs),
                    totalDurationMs = totalMs,
                ),
            )
        } catch (error: Throwable) {
            failure("ONNX generation failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    override fun getEngineInfo(): EngineInfo = engineInfo

    private fun OrtSession.runStep(
        env: OrtEnvironment,
        tokenIds: List<Long>,
        past: OnnxKvCache,
    ): OnnxStepResult {
        val inputs = buildInputs(env, inputInfo, tokenIds, past)
        inputs.useAll {
            run(inputs).use { result ->
                val logits = result.asSequence()
                    .mapNotNull { it.value as? OnnxTensor }
                    .firstOrNull { tensor ->
                        val info = tensor.info
                        info.type == OnnxJavaType.FLOAT && info.shape.size >= 2
                    }
                    ?: error("ONNX model did not return a FLOAT logits tensor")
                val nextToken = logits.argmaxLastToken()
                val cache = OnnxKvCache.fromOutputs(result.asSequence().mapNotNull { (name, value) ->
                    (value as? OnnxTensor)?.let { name to it.copyTensor(env) }
                }.toMap())
                return OnnxStepResult(nextToken = nextToken, cache = cache)
            }
        }
    }

    private fun buildInputs(
        env: OrtEnvironment,
        inputInfo: Map<String, NodeInfo>,
        tokenIds: List<Long>,
        past: OnnxKvCache,
    ): Map<String, OnnxTensor> {
        val pastLength = past.sequenceLength
        val totalLength = pastLength + tokenIds.size
        return inputInfo.mapValues { (name, nodeInfo) ->
            val tensorInfo = nodeInfo.info as? TensorInfo
                ?: error("Unsupported ONNX input type for $name")
            val lowerName = name.lowercase()
            when {
                lowerName == "input_ids" -> longTensor(env, tokenIds, longArrayOf(1L, tokenIds.size.toLong()))
                lowerName == "attention_mask" -> {
                    longTensor(env, List(totalLength.toInt()) { 1L }, longArrayOf(1L, totalLength))
                }
                lowerName == "position_ids" -> {
                    val positions = List(tokenIds.size) { index -> pastLength + index.toLong() }
                    longTensor(env, positions, longArrayOf(1L, tokenIds.size.toLong()))
                }
                lowerName.startsWith("past_key_values.") -> {
                    past.tensors[lowerName]?.copyTensor(env)
                        ?: emptyPastTensor(env, tensorInfo, lowerName)
                }
                tensorInfo.type == OnnxJavaType.INT64 -> {
                    val shape = tensorInfo.shape.toConcreteShape(tokenIds.size, pastLength)
                    longTensor(env, List(shape.elementCount()) { 0L }, shape)
                }
                tensorInfo.type == OnnxJavaType.FLOAT -> {
                    val shape = tensorInfo.shape.toConcreteShape(tokenIds.size, pastLength)
                    floatTensor(env, FloatArray(shape.elementCount()), shape)
                }
                else -> error("Unsupported ONNX input $name with type ${tensorInfo.type}")
            }
        }
    }

    private fun emptyPastTensor(env: OrtEnvironment, tensorInfo: TensorInfo, name: String): OnnxTensor {
        val shape = tensorInfo.shape.toPastCacheShape(pastLength = 0)
        if (shape.any { it < 0 }) {
            error("Cannot infer empty KV-cache shape for $name: ${tensorInfo.shape.joinToString(prefix = "[", postfix = "]")}")
        }
        return OnnxTensor.createTensor(env, directFloatBuffer(shape.elementCount()), shape)
    }

    private fun longTensor(env: OrtEnvironment, values: List<Long>, shape: LongArray): OnnxTensor {
        val expected = shape.elementCount()
        val data = directLongBuffer(expected)
        repeat(expected) { index ->
            data.put(index, values.getOrElse(index % values.size.coerceAtLeast(1)) { 0L })
        }
        return OnnxTensor.createTensor(env, data, shape)
    }

    private fun floatTensor(env: OrtEnvironment, values: FloatArray, shape: LongArray): OnnxTensor {
        val data = directFloatBuffer(values.size)
        values.forEachIndexed { index, value -> data.put(index, value) }
        return OnnxTensor.createTensor(env, data, shape)
    }

    private fun OnnxTensor.copyTensor(env: OrtEnvironment): OnnxTensor {
        val info = this.info
        val shape = info.shape
        return when (info.type) {
            OnnxJavaType.FLOAT -> {
                val source = floatBuffer
                val copy = directFloatBuffer(source.limit())
                for (index in 0 until source.limit()) {
                    copy.put(index, source.get(index))
                }
                OnnxTensor.createTensor(env, copy, shape)
            }
            OnnxJavaType.INT64 -> {
                val source = longBuffer
                val copy = directLongBuffer(source.limit())
                for (index in 0 until source.limit()) {
                    copy.put(index, source.get(index))
                }
                OnnxTensor.createTensor(env, copy, shape)
            }
            else -> error("Unsupported tensor copy type ${info.type}")
        }
    }

    private fun directFloatBuffer(elementCount: Int): FloatBuffer {
        return ByteBuffer
            .allocateDirect(elementCount * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    private fun directLongBuffer(elementCount: Int): LongBuffer {
        return ByteBuffer
            .allocateDirect(elementCount * Long.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asLongBuffer()
    }

    private fun OnnxTensor.argmaxLastToken(): Long {
        val info = this.info
        val shape = info.shape
        val vocabSize = shape.lastOrNull()?.takeIf { it > 0 }?.toInt()
            ?: error("Logits tensor has no vocab dimension")
        val buffer = floatBuffer
        val offset = buffer.limit() - vocabSize
        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in 0 until vocabSize) {
            val value = buffer.get(offset + i)
            if (value > bestValue) {
                bestValue = value
                bestIndex = i
            }
        }
        return bestIndex.toLong()
    }

    private fun LongArray.toConcreteShape(tokenCount: Int, pastLength: Long): LongArray {
        if (isEmpty()) return longArrayOf(1L, tokenCount.toLong())
        return mapIndexed { index, dim ->
            when {
                dim > 0 -> dim
                size >= 2 && index == 0 -> 1L
                size >= 2 && index == 1 -> max(1L, pastLength + tokenCount)
                else -> 1L
            }
        }.toLongArray()
    }

    private fun LongArray.toPastCacheShape(pastLength: Long): LongArray {
        if (size != 4) return toConcreteShape(tokenCount = 1, pastLength = pastLength)
        return longArrayOf(
            if (this[0] > 0) this[0] else 1L,
            if (this[1] > 0) this[1] else 1L,
            pastLength,
            if (this[3] > 0) this[3] else 1L,
        )
    }

    private fun LongArray.elementCount(): Int {
        if (any { it == 0L }) return 0
        return fold(1L) { acc, dim -> acc * dim.coerceAtLeast(1L) }
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private inline fun <T> Map<String, OnnxTensor>.useAll(block: () -> T): T {
        return try {
            block()
        } finally {
            values.forEach { it.close() }
        }
    }

    private fun OrtSession.SessionOptions.tryEnableNnapi(): Boolean {
        return runCatching {
            val method = javaClass.methods.firstOrNull { it.name == "addNnapi" && it.parameterTypes.isEmpty() }
                ?: return false
            method.invoke(this)
            true
        }.getOrDefault(false)
    }

    private fun formatPrompt(prompt: String): String {
        val trimmed = prompt.trim()
        if (trimmed.contains("<|im_start|>")) return prompt
        val modelName = loadedModelName.lowercase()
        return if (modelName.contains("qwen") || modelName.contains("smollm")) {
            "<|im_start|>user\n$trimmed<|im_end|>\n<|im_start|>assistant\n"
        } else {
            prompt
        }
    }

    private fun String.toTokenIds(): List<Long> {
        tokenizer?.let { activeTokenizer ->
            val encoded = activeTokenizer.encode(this)
            if (encoded.isNotEmpty()) return encoded
        }
        return trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { token -> 100L + abs(token.hashCode() % PSEUDO_VOCAB_BUCKETS) }
            .ifEmpty { listOf(DEFAULT_BOS_TOKEN) }
    }

    private fun Int.perSecond(durationMs: Long): Double {
        return if (durationMs <= 0L) 0.0 else this / (durationMs / 1000.0)
    }

    private fun elapsedMs(start: Instant): Long {
        return Duration.between(start, Instant.now()).toMillis()
    }

    private fun <T> success(value: T): Triple<Boolean, String, T?> = Triple(true, "", value)

    private fun <T> failure(message: String): Triple<Boolean, String, T?> = Triple(false, message, null)

    private companion object {
        const val DEFAULT_BOS_TOKEN = 1L
        const val DEFAULT_CONTEXT_TOKENS = 512
        const val PSEUDO_VOCAB_BUCKETS = 32000
        val PAST_KEY_VALUE_PATTERN = Regex("""past_key_values\.(\d+)\.(key|value)""")
    }
}

private data class OnnxStepResult(
    val nextToken: Long,
    val cache: OnnxKvCache,
)

private data class OnnxKvCache(
    val tensors: Map<String, OnnxTensor>,
) {
    val sequenceLength: Long
        get() = tensors.values.firstOrNull()?.info?.shape?.getOrNull(2)?.coerceAtLeast(0L) ?: 0L

    companion object {
        fun empty(): OnnxKvCache = OnnxKvCache(emptyMap())

        fun fromOutputs(outputs: Map<String, OnnxTensor>): OnnxKvCache {
            val cache = outputs.mapNotNull { (name, tensor) ->
                val lowerName = name.lowercase()
                if (!lowerName.startsWith("present.")) return@mapNotNull null
                val inputName = lowerName.replaceFirst("present.", "past_key_values.")
                inputName to tensor
            }.toMap()
            return OnnxKvCache(cache)
        }
    }

    fun close() {
        tensors.values.forEach { it.close() }
    }
}
