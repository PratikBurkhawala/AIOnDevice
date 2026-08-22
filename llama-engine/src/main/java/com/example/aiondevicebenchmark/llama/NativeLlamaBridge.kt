package com.example.aiondevicebenchmark.llama

internal object NativeLlamaBridge {
    init {
        System.loadLibrary("ai_on_device_llama_jni")
    }

    external fun loadModel(
        modelPath: String,
        contextSize: Int,
        maxOutputTokens: Int,
    ): Triple<Boolean, String, NativeLoadResult?>

    external fun unloadModel(handle: Long): Triple<Boolean, String, Long?>
    external fun tokenize(handle: Long, prompt: String): Triple<Boolean, String, Int?>
    external fun requestAbort(handle: Long): Triple<Boolean, String, Boolean?>
    external fun generate(
        handle: Long,
        prompt: String,
        maxOutputTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        seed: Int,
        callback: NativeTokenCallback,
    ): Triple<Boolean, String, NativeGenerationResult?>
}

internal fun interface NativeTokenCallback {
    fun onToken(token: String, tokenIndex: Int)
}

internal data class NativeLoadResult(
    val handle: Long,
    val loadTimeMs: Long,
    val backend: String,
    val threads: Int,
    val gpuLayers: Int,
    val version: String,
)

internal data class NativeGenerationResult(
    val outputText: String,
    val ttftMs: Long,
    val prefillDurationMs: Long,
    val prefillTokens: Int,
    val decodeDurationMs: Long,
    val outputTokens: Int,
    val totalDurationMs: Long,
)
