package com.example.aiondevicebenchmark.onnx

import com.example.aiondevicebenchmark.llm.EngineFactory
import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.LlmEngine

class OnnxEngineFactory : EngineFactory {
    override fun create(type: EngineType): LlmEngine {
        return when (type) {
            EngineType.ONNX_RUNTIME -> OnnxRuntimeEngine()
            else -> error("Unsupported ONNX engine type: $type")
        }
    }
}
