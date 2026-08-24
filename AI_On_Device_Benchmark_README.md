# AI on Device Benchmark — On-Device LLM Benchmark

A small Android benchmark harness for running quantized LLMs locally, collecting reproducible performance/telemetry data, and comparing models, quantizations, runtime engines, and device conditions.

## 1. Goal

The application is intentionally **not UI-heavy**. Its purpose is to:

- Run small quantized LLMs inside a real Android app.
- Start with **llama.cpp** as the first inference engine.
- Keep the engine layer open for additional engines later.
- Benchmark prefill throughput, decode throughput, TTFT, peak RAM, model load time, battery drain, and thermal behavior.
- Run controlled stress conditions.
- Store every benchmark generation as structured JSON.
- Provide enough information to produce the benchmark report.

---

# 2. High-Level Architecture

```text
                         Android Benchmark App
                                  │
                                  ▼
                        ┌─────────────────────┐
                        │         UI          │
                        │                     │
                        │ Engine              │
                        │ Model / Quant      │
                        │ Condition           │
                        │ Prompt              │
                        │ Generation Config  │
                        └──────────┬──────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │ BenchmarkController │
                        └──────────┬──────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
                    ▼              ▼              ▼
             EngineFactory   BenchmarkRunner  TelemetryCollector
                    │              │              │
                    ▼              │              │
              ┌──────────┐         │              │
              │ LlmEngine│◄────────┘              │
              └────┬─────┘                        │
                   │                              │
          ┌────────┴────────┐                     │
          ▼                 ▼                     ▼
 ┌────────────────┐  ┌───────────────┐   ┌─────────────────┐
 │LlamaCppEngine  │  │ Future Engine │   │ Android APIs /  │
 │                │  │               │   │ Profiling tools │
 └───────┬────────┘  └───────────────┘   └─────────────────┘
         │
         ▼
    ┌───────────┐
    │ llama.cpp │
    └─────┬─────┘
          │
          ▼
     GGUF Model
          │
          ▼
       Inference
          │
          └───────────────┐
                          ▼
                   ┌──────────────┐
                   │ JSON Logger  │
                   └──────┬───────┘
                          │
                          ▼
                   benchmark-results/
```

---

# 3. Android Architecture

The Android application should follow a simple layered architecture.

```text
┌─────────────────────────────────────────────────────────────┐
│                         Presentation                         │
│                                                             │
│  BenchmarkConfigScreen                                      │
│  BenchmarkProgressScreen                                    │
│  BenchmarkResultScreen                                      │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                       Benchmark Layer                        │
│                                                             │
│  BenchmarkController                                        │
│  BenchmarkRunner                                             │
│  BenchmarkConfig                                             │
│  BenchmarkState                                              │
└───────────────┬─────────────────────────────┬───────────────┘
                │                             │
                ▼                             ▼
┌───────────────────────────┐     ┌───────────────────────────┐
│       LLM Abstraction     │     │      Telemetry Layer      │
│                           │     │                           │
│       LlmEngine           │     │ TelemetryCollector        │
│       EngineFactory       │     │ MemoryMonitor             │
│       ModelManager        │     │ BatteryMonitor            │
└─────────────┬─────────────┘     │ ThermalMonitor             │
              │                   │ DeviceInfoCollector        │
              ▼                   └─────────────┬─────────────┘
┌───────────────────────────┐                   │
│     Runtime Adapters      │                   ▼
│                           │          ┌───────────────────────┐
│     LlamaCppEngine        │          │ Android APIs / Tools  │
│     FutureEngine2         │          │                       │
│     FutureEngine3         │          │ Build / BatteryManager│
└─────────────┬─────────────┘          │ ActivityManager       │
              │                        │ Debug.MemoryInfo       │
              ▼                        │ PowerManager           │
       ┌──────────────┐                │ ADB / Perfetto         │
       │   llama.cpp  │                └───────────────────────┘
       └──────┬───────┘
              │
              ▼
        ┌────────────┐
        │ GGUF Model │
        └────────────┘

                         Persistence
                              │
                              ▼
                     ┌─────────────────┐
                     │  JsonRepository │
                     └────────┬────────┘
                              │
                              ▼
                    benchmark-results/*.json
```

## Architectural principle

`BenchmarkRunner` must **not depend directly on llama.cpp**.

It depends on the generic:

```kotlin
interface LlmEngine
```

This means another runtime can be added later without rewriting the benchmark system.

---

# 4. LLM Engine Abstraction

Initial implementation:

```text
LlmEngine
    │
    └── LlamaCppEngine
            │
            └── llama.cpp
```

Future:

```text
LlmEngine
    ├── LlamaCppEngine
    ├── Engine2
    └── Engine3
```

Suggested interface:

```kotlin
interface LlmEngine {

    suspend fun loadModel(model: ModelConfig): Triple<Boolean, String, LoadResult?>

    suspend fun unloadModel(): Triple<Boolean, String, UnloadResult?>

    fun tokenize(prompt: String): Triple<Boolean, String, TokenizationResult?>

    fun generate(
        prompt: String,
        config: GenerationConfig,
        listener: GenerationListener
    ): Triple<Boolean, String, GenerationResult?>

    fun getEngineInfo(): EngineInfo
}
```

The boolean is `true` on success. The string carries an engine/native error message on failure. The result is non-null only on success.

---

# 4.1 Model Downloads

The APK does not bundle GGUF files. Download models on-device from the **Models** tab before benchmarking.

The current built-in download choices are:

| Model | Quant | Approx size | Source repo | Notes |
| --- | --- | ---: | --- | --- |
| Qwen2.5-0.5B-Instruct | Q4_K_M | 0.40 GB | `bartowski/Qwen2.5-0.5B-Instruct-GGUF` | Default first test model. Smallest option and best for verifying the pipeline. |
| Qwen2.5-0.5B-Instruct | Q8_0 | 0.53 GB | `bartowski/Qwen2.5-0.5B-Instruct-GGUF` | Higher quality than Q4, still small enough for most modern devices. |
| SmolLM2-1.7B-Instruct | Q4_K_M | 1.06 GB | `bartowski/SmolLM2-1.7B-Instruct-GGUF` | Larger instruct model with balanced size/quality. |
| SmolLM2-1.7B-Instruct | Q8_0 | 1.82 GB | `bartowski/SmolLM2-1.7B-Instruct-GGUF` | Largest built-in option; use on devices with enough RAM and storage. |

Downloaded files are saved under an engine-specific app external files directory:

```text
<app external files>/models/llama/<model>.gguf
```

Future engines should use the same pattern, for example:

```text
<app external files>/models/other_engine/<required files>
```

The benchmark config is updated to the local GGUF path only after the file exists. If a selected model is still downloading, the app shows a message and does not start the benchmark for that model.

To run successfully on device:

- Install the debug or signed release APK on an **arm64-v8a** Android device.
- Open **Models**.
- Download one model, preferably Qwen2.5-0.5B Q4_K_M first.
- Tap **Use** for the downloaded model.
- Return to **Benchmark** and start.
- Keep the app open during model download; there is no background `WorkManager` resume flow yet.

---

# 5. Engine Factory

The UI selects an engine, but the benchmark layer should not instantiate engine implementations directly.

```kotlin
enum class EngineType {
    LLAMA_CPP
    // FUTURE_ENGINE
}
```

Example:

```kotlin
class EngineFactory {

    fun create(type: EngineType): LlmEngine {
        return when (type) {
            EngineType.LLAMA_CPP ->
                LlamaCppEngine()
        }
    }
}
```

Later:

```kotlin
when (type) {
    EngineType.LLAMA_CPP -> LlamaCppEngine()
    EngineType.OTHER -> OtherEngine()
}
```

---

# 6. Benchmark Flow

```text
User selects configuration
          │
          ▼
Validate configuration
          │
          ▼
Capture PRE snapshot
          │
          ▼
Create selected LlmEngine
          │
          ▼
Load model
          │
          ├── load start timestamp
          ├── RAM before
          ├── RAM after
          └── load duration
          │
          ▼
Start generation
          │
          ├── generation start
          ├── first token
          ├── TTFT
          ├── prefill
          └── decode
          │
          ▼
Continuous telemetry
          │
          ├── RAM samples
          ├── temperature
          └── battery
          │
          ▼
Generation completed
          │
          ▼
Capture POST snapshot
          │
          ▼
Unload model
          │
          ├── unload duration
          └── RAM after unload
          │
          ▼
Create JSON record
          │
          ▼
Save benchmark result
```

For sustained load:

```text
Generation 1
     ↓
Generation 2
     ↓
...
     ↓
Generation 20
     ↓
POST snapshot
```

Prefer storing **one JSON record per generation**, with a common `runGroupId`.

---

# 7. Benchmark Configuration

The configuration screen can contain:

```text
Engine
  [ llama.cpp ▼ ]

Model
  [ Qwen 2.5 1.5B ▼ ]

Quantization
  [ Q4_K_M ▼ ]

Condition
  [ Normal / Cold ▼ ]

Prompt
  [ Benchmark 600 tokens ▼ ]

Max output tokens
  [ 100 ]

Context size
  [ 1024 ]

GPU layers
  [ -1 ]

Consecutive generations
  [ 20 ]

Temperature
  [ 0.7 ]

Top-K
  [ 40 ]

Top-P
  [ 0.9 ]

Seed
  [ 42 ]

        [ START BENCHMARK ]
```

For the primary comparison, keep generation settings fixed.

The app pre-fills these editable fields from `app/src/main/assets/model_parameter_presets.json`. The JSON contains shared defaults plus per-model overrides, and the UI applies them on initial load, engine change, and model change. Users can still edit the values after they are loaded.

Config-backed fields:

```text
condition
promptId
prompt
contextSize
maxOutputTokens
gpuLayers
consecutiveGenerations
ramSamplingIntervalSeconds
temperature
topK
topP
seed
```

Current model-specific preset values:

| Engine | Model | Context | Max output | GPU layers |
|---|---|---:|---:|---:|
| `LLAMA_CPP` | `Qwen2.5-0.5B-Instruct-Q4_K_M` | 1024 | 128 | -1 |
| `LLAMA_CPP` | `Qwen2.5-0.5B-Instruct-Q8_0` | 1024 | 128 | -1 |
| `LLAMA_CPP` | `SmolLM2-1.7B-Instruct-Q4_K_M` | 512 | 100 | 16 |
| `LLAMA_CPP` | `SmolLM2-1.7B-Instruct-Q8_0` | 512 | 64 | 8 |
| `ONNX_RUNTIME` | `Qwen2.5-0.5B-Instruct-ONNX-Q4` | 256 | 64 | 0 |
| `ONNX_RUNTIME` | `Qwen2.5-0.5B-Instruct-ONNX-Q8` | 256 | 64 | 0 |
| `ONNX_RUNTIME` | `SmolLM2-1.7B-Instruct-ONNX-Q4` | 256 | 32 | 0 |
| `ONNX_RUNTIME` | `SmolLM2-1.7B-Instruct-ONNX-Q8` | 256 | 32 | 0 |

Recommended initial benchmark configuration:

```text
Model:               Qwen2.5-0.5B-Instruct-Q4_K_M
Context size:        1024
Max output tokens:   128
GPU layers:          -1
Generations:         1
RAM sample sec:      5
Temperature:         0.7
Top-K:               40
Top-P:               0.9
Seed:                42
```

These values are **benchmark controls**, not the main variables being compared.

---

# 8. Conditions

Suggested benchmark conditions:

| Condition | Procedure |
|---|---|
| Normal / Cold | Phone rested and at normal temperature |
| Sustained Load | 20 consecutive generations |
| Battery Saver | Android Battery Saver enabled |
| Memory Pressure | Several heavy apps open |
| Hot Phone | Device warmed before benchmark |
| Background | App backgrounded during generation |

Primary matrix:

```text
2 Models
×
2 Quantizations
×
6 Conditions
=
24 Primary Cases
```

Sustained-load testing can additionally contain 20 generation records per case.

---

# 9. What Is Captured

## Device

```text
Manufacturer
Model
Android version
API level
SoC
CPU architecture
CPU core count
GPU
NPU availability/name
Total RAM
```

## Runtime

```text
Engine
Engine version
Backend
Threads
GPU layers
Runtime configuration
```

## Model

```text
Model name
Parameter count
Format
GGUF filename
Quantization
File size
Context size
Maximum output tokens
```

## Generation

```text
Prompt ID
Input token count
Output token target
Actual output tokens
Temperature
Top-K
Top-P
Seed
Generation start
First token
TTFT
Prefill duration
Prefill tok/s
Decode duration
Decode tok/s
Total duration
```

## Memory

```text
RAM before model load
RAM after model load
RAM before generation
RAM samples during generation
Peak app RAM/PSS
RAM after generation
RAM after model unload
```

## Battery / Thermal

```text
Battery before
Battery after
Battery drain
Temperature before
Temperature after
Temperature samples
Temperature change
Thermal status
```

## Hardware

```text
Backend
CPU used
GPU used
NPU used
CPU utilization if measurable
GPU utilization if measurable
NPU utilization if measurable
Profiling tool/evidence
```

---

# 10. JSON Record

One generation should produce one structured record.

```json
{
  "run": {
    "runId": "R001",
    "runGroupId": "G001",
    "timestamp": {
      "start": "...",
      "end": "..."
    },
    "condition": {
      "type": "NORMAL_COLD",
      "batterySaver": false,
      "charging": false,
      "screenOn": true,
      "appState": "FOREGROUND",
      "memoryPressure": "NONE",
      "consecutiveGenerationNumber": 1,
      "totalConsecutiveGenerations": 1
    }
  },

  "device": {
    "manufacturer": "Samsung",
    "model": "SM-XXXX",
    "androidVersion": "15",
    "apiLevel": 35,
    "soc": {
      "manufacturer": "Qualcomm",
      "model": "Snapdragon XXXX"
    },
    "cpu": {
      "architecture": "arm64-v8a",
      "cores": 8
    },
    "gpu": {
      "name": "Adreno XXXX"
    },
    "npu": {
      "name": "Hexagon",
      "available": true
    },
    "ram": {
      "totalMb": 8192
    }
  },

  "runtime": {
    "engine": "llama.cpp",
    "version": "VERSION",
    "backend": "CPU",
    "threads": 8,
    "gpuLayers": 0
  },

  "model": {
    "name": "Qwen2.5-1.5B-Instruct",
    "parameters": "1.5B",
    "format": "GGUF",
    "fileName": "model-q4_k_m.gguf",
    "quantization": "Q4_K_M",
    "fileSizeBytes": 1048576000,
    "contextSize": 2048,
    "maxOutputTokens": 100
  },

  "generationConfig": {
    "temperature": 0.7,
    "topK": 40,
    "topP": 0.9,
    "seed": 42
  },

  "prompt": {
    "promptId": "P001",
    "inputTokenCount": 600,
    "outputTokenTarget": 100
  },

  "modelLoading": {
    "loadStart": "...",
    "loadEnd": "...",
    "loadTimeMs": 2400,
    "ramBeforeLoadMb": 280,
    "ramAfterLoadMb": 1180
  },

  "inference": {
    "generationStart": "...",
    "firstTokenTime": "...",
    "ttftMs": 850,

    "prefill": {
      "durationMs": 700,
      "tokens": 600,
      "tokensPerSecond": 857.14
    },

    "decode": {
      "durationMs": 8200,
      "tokens": 100,
      "tokensPerSecond": 12.20
    },

    "total": {
      "durationMs": 8900,
      "outputTokens": 100
    }
  },

  "memory": {
    "beforeGenerationMb": 1180,

    "samples": [
      {
        "timestamp": "...",
        "appPssMb": 1220
      },
      {
        "timestamp": "...",
        "appPssMb": 1290
      }
    ],

    "peakAppPssMb": 1350,
    "afterGenerationMb": 1320,
    "afterModelUnloadMb": 350
  },

  "battery": {
    "beforePercentage": 82,
    "afterPercentage": 81,
    "drainPercentage": 1,
    "temperatureBeforeC": 29,
    "temperatureAfterC": 35
  },

  "hardware": {
    "backend": "CPU",

    "cpu": {
      "used": true,
      "utilizationPercent": null
    },

    "gpu": {
      "used": false,
      "utilizationPercent": null
    },

    "npu": {
      "used": false,
      "utilizationPercent": null
    },

    "profiling": {
      "tool": null,
      "evidence": null
    }
  },

  "modelUnloading": {
    "unloadStart": "...",
    "unloadEnd": "...",
    "unloadTimeMs": 500
  },

  "result": {
    "status": "SUCCESS",
    "error": null
  },

  "observation": {
    "summary": "",
    "issues": [],
    "notes": []
  }
}
```

---

# 11. Measurement Source

| Information | Source |
|---|---|
| Run/timestamps | App benchmark controller |
| Prompt/token counts | Tokenizer / LLM runtime |
| TTFT | App + first-token callback |
| Prefill/decode tok/s | Prefer runtime timing |
| Load/unload time | App timestamps |
| Model file size | Android `File.length()` |
| App RAM | `Debug.MemoryInfo` / `ActivityManager` |
| Battery | `BatteryManager` |
| Battery Saver | `PowerManager` |
| Device details | Android `Build` APIs |
| Thermal status | Android thermal APIs where available |
| CPU utilization | ADB / Profiler / Perfetto |
| GPU utilization | Profiler/vendor tools where available |
| NPU utilization | Vendor/runtime profiling where available |
| Backend | LLM runtime configuration/logs |
| System memory pressure | ADB / Perfetto |

Do not invent measurements. If GPU/NPU utilization is not exposed, store:

```json
{
  "utilizationPercent": null,
  "measurementStatus": "NOT_AVAILABLE"
}
```

---

# 12. Model Storage

The assignment requires the model to run locally inside an Android app. It does **not** require implementing model downloading.

For development, the model can be made available locally on the device and loaded from app-managed storage.

For a production application:

```text
App installed
      ↓
Check model availability
      ↓
Check device/storage capability
      ↓
Download model from server/CDN
      ↓
Resume if interrupted
      ↓
Verify checksum
      ↓
Store locally
      ↓
Load into runtime
      ↓
On-device inference
```

Download time and model load time should be treated as different measurements.

---

# 13. Suggested Project Structure

```text
app/
└── src/main/
    ├── java/com/example/aiondevicebenchmark/
    │
    ├── benchmark/
    │   ├── BenchmarkController.kt
    │   ├── BenchmarkConfig.kt
    │   ├── BenchmarkRunner.kt
    │   ├── BenchmarkState.kt
    │   └── BenchmarkResult.kt
    │
    ├── llm/
    │   ├── LlmEngine.kt
    │   ├── EngineFactory.kt
    │   ├── ModelConfig.kt
    │   ├── GenerationConfig.kt
    │   └── LlamaCppEngine.kt
    │
    ├── telemetry/
    │   ├── TelemetryCollector.kt
    │   ├── MemoryMonitor.kt
    │   ├── BatteryMonitor.kt
    │   ├── ThermalMonitor.kt
    │   └── DeviceInfoCollector.kt
    │
    ├── data/
    │   ├── JsonRepository.kt
    │   └── BenchmarkJsonModels.kt
    │
    └── ui/
        ├── BenchmarkConfigScreen.kt
        ├── BenchmarkProgressScreen.kt
        └── BenchmarkResultScreen.kt
```

---

# 14. Implementation Priority

Do **not** start by implementing multiple engines.

### Phase 1 — Core

```text
Android project
    ↓
Simple UI
    ↓
LlmEngine interface
    ↓
LlamaCppEngine
    ↓
One GGUF model
    ↓
Generate text
```

### Phase 2 — Benchmarking

```text
Model loading timing
TTFT
Prefill
Decode
RAM
Battery
Temperature
JSON logging
```

### Phase 3 — Experiments

```text
Q4 vs Q8
Model 1 vs Model 2
Normal
Sustained
Battery Saver
Memory Pressure
Hot
Background
```

### Phase 4 — Part 2

```text
AccessibilityService
    ↓
Request permission
    ↓
Open Settings
    ↓
Read nodes
    ↓
Find Bluetooth
    ↓
Click Bluetooth
```

### Phase 5 — Optional

If time remains:

```text
LlmEngine
    ├── LlamaCppEngine       ← Required focus
    ├── OtherEngine           ← Optional
    └── OtherEngine           ← Optional
```

The benchmark infrastructure should already work regardless of which engine is selected.

---

# 15. Final Benchmark Report

The final submission should focus on findings, not the amount of code.

Recommended structure:

1. **Setup**
   - Phone/device
   - Android version
   - Models
   - Quantizations
   - Runtime
   - Reproduction steps

2. **Results**
   - Model
   - Quant
   - Condition
   - Prefill tok/s
   - Decode tok/s
   - TTFT
   - Peak RAM
   - Load time
   - Battery drain
   - Backend

3. **What surprised you**
   - Put this early; the benchmark brief highlights this section.

4. **Why the numbers landed where they did**
   - Prefill vs decode
   - Q4 vs Q8
   - Memory bandwidth
   - Compute
   - Runtime/backend
   - Thermal behavior

5. **Stress results**
   - Sustained load
   - Battery Saver
   - Memory pressure
   - Backgrounding
   - Hot phone

6. **Shipping strategy**
   - Model delivery
   - Storage
   - Loading/unloading
   - Memory pressure
   - User experience

7. **What didn't work**
   - Be technically honest.

8. **What you would try with another week**
   - Better profiling
   - Accelerator backend
   - More devices
   - More models
   - Power measurement
   - Additional runtime

---

# 16. Core Engineering Principle

The most important architectural decision is:

```text
                  BenchmarkRunner
                        │
                        │ depends on
                        ▼
                    LlmEngine
                        ▲
                        │
             ┌──────────┴──────────┐
             │                     │
       LlamaCppEngine          FutureEngine
             │
          llama.cpp
```

The benchmark system should **not know that llama.cpp exists**.

This allows the project to start with one proven engine while leaving the architecture open for additional engines if time permits.

The goal is not to build a multi-engine framework for its own sake. The goal is to demonstrate that the runtime boundary is replaceable while spending the majority of the assignment time on **correct mobile inference, measurement, analysis, and engineering judgment**.
