package com.example.aiondevicebenchmark.llm

interface LlmEngine {
    suspend fun loadModel(model: ModelConfig): Triple<Boolean, String, LoadResult?>
    suspend fun unloadModel(): Triple<Boolean, String, UnloadResult?>
    fun tokenize(prompt: String): Triple<Boolean, String, TokenizationResult?>
    suspend fun generate(
        prompt: String,
        config: GenerationConfig,
        listener: GenerationListener,
    ): Triple<Boolean, String, GenerationResult?>

    fun getEngineInfo(): EngineInfo
}

fun interface EngineFactory {
    fun create(type: EngineType): LlmEngine
}

enum class EngineType(val displayName: String) {
    LLAMA_CPP("llama.cpp"),
    ONNX_RUNTIME("ONNX Runtime");

    val storageName: String
        get() = when (this) {
            LLAMA_CPP -> "llama"
            ONNX_RUNTIME -> "onnx"
        }
}

interface GenerationListener {
    fun onFirstToken() = Unit
    fun onToken(token: String, tokenIndex: Int) = Unit
}

data class ModelConfig(
    val engineType: EngineType = EngineType.LLAMA_CPP,
    val name: String = "Qwen2.5-1.5B-Instruct",
    val parameters: String = "1.5B",
    val format: String = "GGUF",
    val fileName: String = "model-q4_k_m.gguf",
    val filePath: String = "",
    val tokenizerFileName: String = "",
    val tokenizerFilePath: String = "",
    val tokenizerDownloadUrl: String = "",
    val quantization: String = "Q4_K_M",
    val fileSizeBytes: Long? = null,
    val contextSize: Int = 2048,
    val downloadUrl: String = "",
    val downloadSizeLabel: String = "",
    val description: String = "",
)

data class GenerationConfig(
    val maxOutputTokens: Int = 100,
    val temperature: Double = 0.7,
    val topK: Int = 40,
    val topP: Double = 0.9,
    val seed: Int = 42,
)

data class LoadResult(val loadTimeMs: Long)
data class UnloadResult(val unloadTimeMs: Long)
data class TokenizationResult(val tokenCount: Int)

data class GenerationResult(
    val outputText: String,
    val ttftMs: Long,
    val prefillDurationMs: Long,
    val prefillTokens: Int,
    val prefillTokensPerSecond: Double,
    val decodeDurationMs: Long,
    val outputTokens: Int,
    val decodeTokensPerSecond: Double,
    val totalDurationMs: Long,
)

data class EngineInfo(
    val name: String,
    val version: String,
    val backend: String,
    val threads: Int,
    val gpuLayers: Int,
    val measurementStatus: String,
)
