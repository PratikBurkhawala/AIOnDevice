package com.example.aiondevicebenchmark.llama

import com.example.aiondevicebenchmark.llm.EngineFactory
import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.LlmEngine

class LlamaEngineFactory : EngineFactory {
    override fun create(type: EngineType): LlmEngine {
        return when (type) {
            EngineType.LLAMA_CPP -> LlamaCppEngine()
        }
    }
}
