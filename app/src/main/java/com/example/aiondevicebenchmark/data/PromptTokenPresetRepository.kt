package com.example.aiondevicebenchmark.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PromptTokenPresetRepository(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val presets: List<PromptTokenPreset> by lazy {
        runCatching {
            context.assets.open(PRESET_FILE).bufferedReader().use { reader ->
                json.decodeFromString<PromptTokenPresetFile>(reader.readText()).presets
                    .sortedBy { it.tokenTarget }
            }
        }.getOrDefault(emptyList())
    }

    fun listPresets(): List<PromptTokenPreset> = presets

    fun presetFor(tokenTarget: Int): PromptTokenPreset? {
        return presets.firstOrNull { it.tokenTarget == tokenTarget }
    }

    private companion object {
        const val PRESET_FILE = "prompt_token_presets.json"
    }
}

@Serializable
private data class PromptTokenPresetFile(
    val version: Int = 1,
    val presets: List<PromptTokenPreset> = emptyList(),
)

@Serializable
data class PromptTokenPreset(
    val tokenTarget: Int,
    val promptId: String,
    val prompt: String,
)
