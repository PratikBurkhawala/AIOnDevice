package com.example.aiondevicebenchmark.benchmark

import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.GenerationConfig
import com.example.aiondevicebenchmark.llm.ModelConfig

enum class BenchmarkCondition(val label: String) {
    NORMAL_COLD("Normal / Cold"),
    SUSTAINED_LOAD("Sustained Load"),
    BATTERY_SAVER("Battery Saver"),
    MEMORY_PRESSURE("Memory Pressure"),
    HOT_PHONE("Hot Phone"),
    BACKGROUND("Background"),
}

data class BenchmarkConfig(
    val engineType: EngineType = EngineType.LLAMA_CPP,
    val model: ModelConfig = ModelConfig(
        name = "Qwen2.5-0.5B-Instruct-Q4_K_M",
        parameters = "0.5B",
        fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        downloadSizeLabel = "0.40 GB",
        description = "Smallest option. Good quality for first device tests and lower RAM phones.",
    ),
    val condition: BenchmarkCondition = BenchmarkCondition.NORMAL_COLD,
    val promptId: String = "P001",
    val prompt: String = DefaultPrompt.text,
    val consecutiveGenerations: Int = 1,
    val generation: GenerationConfig = GenerationConfig(),
)

object DefaultPrompt {
    val text: String = """
        Explain how an Android application can benchmark an on-device language model. Include the role of prefill throughput, decode throughput, time to first token, model load time, app memory, battery drain, and thermal status. Keep the answer practical and focused on reproducible measurements.
    """.trimIndent()
}
