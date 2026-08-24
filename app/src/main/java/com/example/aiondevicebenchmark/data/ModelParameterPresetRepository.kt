package com.example.aiondevicebenchmark.data

import android.content.Context
import com.example.aiondevicebenchmark.benchmark.DefaultPrompt
import com.example.aiondevicebenchmark.llm.ModelConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ModelParameterPresetRepository(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val presets: List<ModelParameterPreset> by lazy {
        runCatching {
            context.assets.open(PRESET_FILE).bufferedReader().use { reader ->
                val file = json.decodeFromString<ModelParameterPresetFile>(reader.readText())
                file.presets.map { preset -> preset.resolve(file.defaults) }
            }
        }.getOrDefault(emptyList())
    }

    fun presetFor(model: ModelConfig): ModelParameterPreset? {
        return presets.firstOrNull { preset ->
            preset.engineType == model.engineType.name && preset.modelName == model.name
        }
    }

    private companion object {
        const val PRESET_FILE = "model_parameter_presets.json"
    }
}

@Serializable
private data class ModelParameterPresetFile(
    val version: Int = 1,
    val defaults: ModelParameterPresetDefaults = ModelParameterPresetDefaults(),
    val presets: List<ModelParameterPresetEntry> = emptyList(),
)

@Serializable
private data class ModelParameterPresetDefaults(
    val condition: String = "NORMAL_COLD",
    val promptId: String = "P001",
    val prompt: String = DefaultPrompt.text,
    val consecutiveGenerations: Int = 1,
    val ramSamplingIntervalSeconds: Int = 5,
    val temperature: Double = 0.7,
    @SerialName("topK") val topK: Int = 40,
    @SerialName("topP") val topP: Double = 0.9,
    val seed: Int = 42,
)

@Serializable
private data class ModelParameterPresetEntry(
    val engineType: String,
    val modelName: String,
    val contextSize: Int,
    val maxOutputTokens: Int,
    val gpuLayers: Int,
    val condition: String? = null,
    val promptId: String? = null,
    val prompt: String? = null,
    val consecutiveGenerations: Int? = null,
    val ramSamplingIntervalSeconds: Int? = null,
    val temperature: Double? = null,
    @SerialName("topK") val topK: Int? = null,
    @SerialName("topP") val topP: Double? = null,
    val seed: Int? = null,
) {
    fun resolve(defaults: ModelParameterPresetDefaults): ModelParameterPreset {
        return ModelParameterPreset(
            engineType = engineType,
            modelName = modelName,
            contextSize = contextSize,
            maxOutputTokens = maxOutputTokens,
            gpuLayers = gpuLayers,
            condition = condition ?: defaults.condition,
            promptId = promptId ?: defaults.promptId,
            prompt = prompt ?: defaults.prompt,
            consecutiveGenerations = consecutiveGenerations ?: defaults.consecutiveGenerations,
            ramSamplingIntervalSeconds = ramSamplingIntervalSeconds ?: defaults.ramSamplingIntervalSeconds,
            temperature = temperature ?: defaults.temperature,
            topK = topK ?: defaults.topK,
            topP = topP ?: defaults.topP,
            seed = seed ?: defaults.seed,
        )
    }
}

data class ModelParameterPreset(
    val engineType: String,
    val modelName: String,
    val contextSize: Int,
    val maxOutputTokens: Int,
    val gpuLayers: Int,
    val condition: String,
    val promptId: String,
    val prompt: String,
    val consecutiveGenerations: Int,
    val ramSamplingIntervalSeconds: Int,
    val temperature: Double,
    val topK: Int,
    val topP: Double,
    val seed: Int,
)
