package com.example.aiondevicebenchmark.llm

interface EngineCatalog {
    val engines: List<EngineType>
    fun modelsFor(engineType: EngineType): List<ModelConfig>
    fun defaultModelFor(engineType: EngineType): ModelConfig
    fun withDetectedQuantization(model: ModelConfig): ModelConfig
}

class DefaultEngineCatalog : EngineCatalog {
    private val engineModels = mapOf(
        EngineType.LLAMA_CPP to listOf(
            ModelConfig(
                name = "Qwen2.5-0.5B-Instruct-Q4_K_M",
                parameters = "0.5B",
                fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
                quantization = "Q4_K_M",
                fileSizeBytes = 400_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
                downloadSizeLabel = "0.40 GB",
                description = "Smallest option. Good quality for first device tests and lower RAM phones.",
            ),
            ModelConfig(
                name = "Qwen2.5-0.5B-Instruct-Q8_0",
                parameters = "0.5B",
                fileName = "Qwen2.5-0.5B-Instruct-Q8_0.gguf",
                quantization = "Q8_0",
                fileSizeBytes = 530_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q8_0.gguf",
                downloadSizeLabel = "0.53 GB",
                description = "Higher quality Qwen baseline with more memory and storage use than Q4.",
            ),
            ModelConfig(
                name = "SmolLM2-1.7B-Instruct-Q4_K_M",
                parameters = "1.7B",
                fileName = "SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
                quantization = "Q4_K_M",
                fileSizeBytes = 1_060_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
                downloadSizeLabel = "1.06 GB",
                description = "Larger instruct model. Balanced quality and size for stronger benchmark runs.",
            ),
            ModelConfig(
                name = "SmolLM2-1.7B-Instruct-Q8_0",
                parameters = "1.7B",
                fileName = "SmolLM2-1.7B-Instruct-Q8_0.gguf",
                quantization = "Q8_0",
                fileSizeBytes = 1_820_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q8_0.gguf",
                downloadSizeLabel = "1.82 GB",
                description = "Largest included option. Use for quality-focused runs on devices with enough RAM.",
            ),
        ),
    )

    override val engines: List<EngineType> = engineModels.keys.toList()

    override fun modelsFor(engineType: EngineType): List<ModelConfig> = engineModels.getValue(engineType)

    override fun defaultModelFor(engineType: EngineType): ModelConfig = modelsFor(engineType).first()

    override fun withDetectedQuantization(model: ModelConfig): ModelConfig {
        val detected = detectQuantization(model.name) ?: detectQuantization(model.fileName)
        return if (detected == null) model else model.copy(quantization = detected)
    }

    private fun detectQuantization(value: String): String? {
        val known = listOf("Q2_K", "Q3_K_M", "Q4_K_M", "Q5_K_M", "Q6_K", "Q8_0", "F16")
        val normalized = value.uppercase()
        return known.firstOrNull { normalized.contains(it) }
    }
}
