# AI on Device Benchmark

Android benchmark app for running small LLMs on-device, collecting repeatable performance and telemetry data, and comparing engines, models, quantizations, prompts, and device conditions.

## 1. Goal

The app is a practical benchmark harness, not a UI-heavy demo. It is intended to:

- run quantized LLMs inside a real Android app;
- start with native `llama.cpp` and keep the runtime boundary replaceable;
- provide an experimental ONNX Runtime path for comparison work;
- measure model load time, prompt tokenization, TTFT, prefill throughput, decode throughput, peak RAM, battery drain, and thermal status;
- record selected device conditions such as cold run, sustained load, battery saver, memory pressure, hot phone, and background run;
- save structured JSON results and export table-form CSV reports;
- capture crash reports when native or runtime failures happen.

## 2. High-Level Architecture

```text
                         Android Benchmark App
                                  |
                                  v
                        +---------------------+
                        | Compose UI          |
                        |                     |
                        | Benchmark tab       |
                        | Models tab          |
                        | Saved JSON tab      |
                        | Report tab          |
                        | Crashes tab         |
                        +----------+----------+
                                   |
                                   v
                        +---------------------+
                        | BenchmarkViewModel  |
                        +----------+----------+
                                   |
                                   v
                        +---------------------+
                        | BenchmarkController |
                        +----------+----------+
                                   |
                    +--------------+--------------+
                    |              |              |
                    v              v              v
             EngineFactory   BenchmarkRunner  TelemetryCollector
                    |              |              |
                    v              |              v
              +-----------+        |       Android APIs
              | LlmEngine |<-------+       Build / BatteryManager
              +-----+-----+                ActivityManager
                    |                      Debug.MemoryInfo
          +---------+---------+            PowerManager
          |                   |
          v                   v
 +----------------+   +------------------+
 | LlamaCppEngine |   | OnnxRuntimeEngine|
 +-------+--------+   +--------+---------+
         |                     |
         v                     v
    llama.cpp JNI        ONNX Runtime
         |                     |
         v                     v
      GGUF model          ONNX + tokenizer
          \                   /
           \                 /
            v               v
             +-------------+
             | JSON Logger |
             +------+------+
                    |
                    v
          benchmark-results/*.json
```

## 3. Android Architecture

```text
+-------------------------------------------------------------+
| Presentation                                                 |
|                                                             |
| BenchmarkApp / BenchmarkRoute                               |
| BenchmarkScreen                                             |
| ModelDownloadScreen                                         |
| SavedJsonListScreen / JsonDetailScreen                      |
| ReportScreen / CrashReportScreen                            |
+-----------------------------+-------------------------------+
                              |
                              v
+-------------------------------------------------------------+
| UI State and Use Cases                                      |
|                                                             |
| BenchmarkViewModel                                          |
| StartBenchmarkUseCase                                       |
| ModelDownloadUseCases                                       |
| SavedJsonUseCases                                           |
| ShareReportCsvUseCase                                       |
| CrashReportUseCases                                         |
+-----------------------------+-------------------------------+
                              |
                              v
+-------------------------------------------------------------+
| Benchmark Layer                                             |
|                                                             |
| BenchmarkController                                         |
| BenchmarkRunner                                             |
| BenchmarkConfig                                             |
| BenchmarkState                                              |
+--------------+------------------------------+---------------+
               |                              |
               v                              v
+----------------------------+     +--------------------------+
| LLM Abstraction            |     | Telemetry Layer          |
|                            |     |                          |
| LlmEngine                  |     | TelemetryCollector       |
| EngineFactory              |     | MemoryMonitor            |
| EngineCatalog              |     | BatteryMonitor           |
| ModelConfig                |     | ThermalMonitor           |
+-------------+--------------+     | DeviceInfoCollector      |
              |                    +------------+-------------+
              v                                 |
+----------------------------+                  v
| Runtime Adapters           |          +---------------------+
|                            |          | Android APIs        |
| LlamaCppEngine             |          +---------------------+
| OnnxRuntimeEngine          |
+-------------+--------------+
              |
              v
+-------------------------------------------------------------+
| Persistence                                                 |
|                                                             |
| ModelDownloadRepositoryImpl -> models/<engine>/             |
| JsonRepository              -> benchmark-results/*.json     |
| CrashReportStore            -> crash-reports/*.json         |
+-------------------------------------------------------------+
```

The key rule is that `BenchmarkRunner` does not know about `llama.cpp`, ONNX, or any future engine directly. It only calls the `LlmEngine` interface.

## 4. Project Modules

```text
:app
  Android application, Compose UI, Koin wiring, benchmark orchestration,
  telemetry, model downloads, JSON persistence, CSV sharing, crash capture.

:engine
  Runtime-neutral contracts and catalog:
  LlmEngine, EngineFactory, EngineCatalog, EngineType, ModelConfig,
  GenerationConfig, EngineInfo, GenerationResult.

:llama-engine
  Native llama.cpp adapter:
  LlamaCppEngine, NativeLlamaBridge, ai_on_device_llama_jni.cpp,
  CMakeLists.txt, llama.cpp submodule.

:onnx-engine
  Experimental ONNX Runtime adapter:
  OnnxRuntimeEngine, OnnxEngineFactory, ByteLevelBpeTokenizer.
```

## 5. Runtime Boundary

The shared interface is in `engine/src/main/java/com/example/aiondevicebenchmark/llm/LlmEngine.kt`.

```kotlin
interface LlmEngine {
    suspend fun loadModel(model: ModelConfig): Triple<Boolean, String, LoadResult?>
    suspend fun unloadModel(): Triple<Boolean, String, UnloadResult?>
    fun effectivePrompt(prompt: String): String = prompt
    fun tokenize(prompt: String): Triple<Boolean, String, TokenizationResult?>
    suspend fun generate(
        prompt: String,
        config: GenerationConfig,
        listener: GenerationListener,
    ): Triple<Boolean, String, GenerationResult?>
    fun getEngineInfo(): EngineInfo
}
```

Current engines:

| Engine | Module | Status | Backend behavior |
| --- | --- | --- | --- |
| `llama.cpp` | `:llama-engine` | Main native path | Loads GGUF through JNI. The native build targets `arm64-v8a` and enables Vulkan support in CMake. Runtime metadata reports backend, threads, GPU layers, and native version. |
| `ONNX Runtime` | `:onnx-engine` | Experimental | Loads ONNX files and tokenizer JSON. It tries ONNX Runtime NNAPI when available and otherwise uses CPU. Result metadata marks the tokenizer/decode path as experimental. |

Engine creation is wired in `app/src/main/java/com/example/aiondevicebenchmark/di/AppModules.kt`.

## 6. Model Catalog and Downloads

The APK does not bundle model weights. Models are downloaded on-device from the `Models` tab and stored in app external files.

### llama.cpp GGUF Models

| Model | Quant | Approx size | Source repo | Notes |
| --- | --- | ---: | --- | --- |
| Qwen2.5-0.5B-Instruct | Q4_K_M | 0.40 GB | `bartowski/Qwen2.5-0.5B-Instruct-GGUF` | Default first test model. Smallest option and best for verifying the pipeline. |
| Qwen2.5-0.5B-Instruct | Q8_0 | 0.53 GB | `bartowski/Qwen2.5-0.5B-Instruct-GGUF` | Higher quality baseline with modest storage use. |
| SmolLM2-1.7B-Instruct | Q4_K_M | 1.06 GB | `bartowski/SmolLM2-1.7B-Instruct-GGUF` | Larger instruct model with balanced size and quality. |
| SmolLM2-1.7B-Instruct | Q8_0 | 1.82 GB | `bartowski/SmolLM2-1.7B-Instruct-GGUF` | Largest built-in GGUF option. Use on devices with enough RAM and storage. |

### ONNX Runtime Models

| Model | Quant | Approx size | Source repo | Notes |
| --- | --- | ---: | --- | --- |
| Qwen2.5-0.5B-Instruct | Q4 | 786 MB | `onnx-community/Qwen2.5-0.5B-Instruct` | ONNX Q4 model plus tokenizer JSON. |
| Qwen2.5-0.5B-Instruct | Q8 | 512 MB | `onnx-community/Qwen2.5-0.5B-Instruct` | ONNX INT8 model shown as Q8 plus tokenizer JSON. |
| SmolLM2-1.7B-Instruct | Q4 | 1.41 GB | `HuggingFaceTB/SmolLM2-1.7B-Instruct` | Larger ONNX Q4 option. |
| SmolLM2-1.7B-Instruct | Q8 | 1.71 GB | `HuggingFaceTB/SmolLM2-1.7B-Instruct` | Larger ONNX INT8 option. |

Storage layout:

```text
/sdcard/Android/data/com.example.aiondevicebenchmark/files/models/
  llama/
    *.gguf
  onnx/
    *.onnx
    *-tokenizer.json
```

Download behavior:

- Downloads run from the app with a foreground data-sync service.
- Partially downloaded files use `.part` files and are moved into place after completion.
- The benchmark config receives a local file path only after the model is ready.
- ONNX generation requires both the `.onnx` file and the tokenizer JSON.
- The app currently does not implement a persistent background resume queue such as WorkManager.

## 7. Setup

### Host Requirements

- Android Studio or Android SDK command-line tools.
- JDK 17.
- Android Gradle Plugin compatible with version `8.5.2`.
- Android SDK for compile SDK 34.
- Android NDK `27.0.12077973`.
- CMake `3.22.1`.
- USB debugging enabled on an Android device.
- An `arm64-v8a` device for native llama.cpp runs.

### Clone and Prepare

If starting from a fresh clone, initialize native dependencies:

```bash
git submodule update --init --recursive
```

Make sure the Android SDK path is available through `ANDROID_HOME` or `ANDROID_SDK_ROOT`. The `llama-engine` Gradle file falls back to `~/Android/Sdk` if neither variable is set.

### Build

```bash
./gradlew assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### First Launch Checklist

1. Open the app.
2. Go to `Models`.
3. Select the engine you want to test from the `Benchmark` tab if needed, then return to `Models`.
4. Download a small model first. Recommended first test: Qwen2.5-0.5B Q4.
5. Tap `Use` on the downloaded model.
6. Return to `Benchmark`.
7. Keep generation count low for the first smoke test.

## 8. How To Use the App

### Benchmark Tab

Use this tab to configure and run a benchmark.

Controls:

```text
Engine                 llama.cpp or ONNX Runtime
Model                  Model from the current engine catalog
Quantization           Read-only value from selected model metadata
Local model path       Read-only path after a downloaded model is selected
Condition              Label for the test condition
Prompt                 Prompt text used for generation
Max output             Target generated token count
Generations            Number of consecutive generations in the run group
RAM sample sec         RAM sampling interval during load/inference/unload
Temperature            Sampling temperature
Top-K                  Sampling top-k
Top-P                  Sampling top-p
Seed                   Runtime seed
```

Recommended first benchmark:

```text
Engine:                llama.cpp
Model:                 Qwen2.5-0.5B-Instruct-Q4_K_M
Condition:             Normal / Cold
Max output:            100
Generations:           1
RAM sample sec:        5 or 10 for short runs
Temperature:           0.7
Top-K:                 40
Top-P:                 0.9
Seed:                  42
```

For sustained-load testing, increase `Generations` to a larger number such as 20 and keep the prompt and sampling settings fixed.

### Models Tab

Use this tab to:

- see the models available for the selected engine;
- download model files;
- download tokenizer JSON for ONNX models;
- select a downloaded model with `Use`;
- delete downloaded files.

The model must be downloaded and selected before a benchmark can run successfully.

### Saved JSON Tab

Use this tab to:

- list saved benchmark JSON files;
- open a JSON detail view;
- delete old results.

### Report Tab

Use this tab to:

- view aggregate report rows from saved JSON files;
- share benchmark results as CSV;
- share crash reports.

### Crashes Tab

Use this tab to inspect native or JVM crash reports captured by `CrashReportStore`. On Android 11 and newer, the app also reads recent historical process exit reasons for native crashes.

## 9. Benchmark Flow

```text
User selects configuration
          |
          v
Validate local model path
          |
          v
Capture run start device/battery/RAM state
          |
          v
Create selected LlmEngine
          |
          v
Load model
          |
          +-- load start/end timestamp
          +-- load duration
          +-- RAM samples during load
          |
          v
Capture engine info
          |
          v
Tokenize prompt and record effective engine prompt
          |
          v
Run generation 1..N
          |
          +-- generation start/end timestamp
          +-- first token timestamp
          +-- TTFT
          +-- prefill duration/tokens/tok/s
          +-- decode duration/tokens/tok/s
          +-- generated text
          +-- RAM samples during inference
          |
          v
Unload model
          |
          +-- unload start/end timestamp
          +-- unload duration
          +-- RAM after unload
          |
          v
Capture run end battery/thermal state
          |
          v
Write grouped JSON file
```

The current implementation writes one grouped JSON file per benchmark run group. Each file contains a list of per-generation `inferenceRuns` plus run-level summary data. The repository can still read older one-record and legacy grouped JSON formats.

## 10. Conditions

Available labels:

| Condition | How to run it |
| --- | --- |
| Normal / Cold | Let the phone rest until temperature is normal, then run one benchmark. |
| Sustained Load | Use multiple consecutive generations, for example 20. |
| Battery Saver | Enable Android Battery Saver before starting the run. |
| Memory Pressure | Open several heavy apps before starting the run. |
| Hot Phone | Warm the device before running, then record the run under this label. |
| Background | Start the run and background the app during generation. |

The app records the selected label and device state. It does not force Battery Saver, memory pressure, heat, or backgrounding by itself.

## 11. What Is Captured

### Device

```text
Manufacturer
Model
Android version
API level
SoC manufacturer/model when available
CPU architecture
CPU core count
GPU name when available
NPU availability/name when available
Total RAM
```

### Runtime

```text
Engine
Version
Backend
Threads
GPU layers
Measurement status
```

### Model

```text
Model name
Parameter count
Format
File name
Local file path
Quantization
File size
Context size
Max output tokens
```

### Prompt and Generation

```text
Prompt ID
Original prompt text
Effective engine prompt text
Input token count
Output token target
Temperature
Top-K
Top-P
Seed
Generated text
TTFT
Prefill duration
Prefill tokens
Prefill tokens per second
Decode duration
Decode tokens
Decode tokens per second
Total duration
```

### Memory

```text
RAM before model load
RAM after model load
RAM samples during model load, inference, and unload
Peak app PSS
RAM after model unload
```

### Battery and Thermal

```text
Battery snapshot before run
Battery snapshot after run
Battery drain percentage when both readings are available
Battery temperature before/after
Charging state
Battery Saver state
Thermal status
```

### Hardware Evidence

```text
Backend string
CPU used flag
GPU used flag inferred from backend string
NPU used flag inferred from NNAPI backend string
Utilization percentages when available
Profiling evidence text
Measurement status notes
```

The app does not invent CPU/GPU/NPU utilization. If utilization is unavailable, fields remain null and the measurement status explains the limitation.

## 12. Result Files

Benchmark results are saved under:

```text
/sdcard/Android/data/com.example.aiondevicebenchmark/files/benchmark-results/
```

Crash reports are saved under:

```text
/sdcard/Android/data/com.example.aiondevicebenchmark/files/crash-reports/
```

CSV reports are generated into the app cache and shared through Android `FileProvider`.

Pull benchmark results with ADB:

```bash
adb shell ls /sdcard/Android/data/com.example.aiondevicebenchmark/files/benchmark-results
adb pull /sdcard/Android/data/com.example.aiondevicebenchmark/files/benchmark-results ./benchmark-results
```

Pull crash reports:

```bash
adb shell ls /sdcard/Android/data/com.example.aiondevicebenchmark/files/crash-reports
adb pull /sdcard/Android/data/com.example.aiondevicebenchmark/files/crash-reports ./crash-reports
```

## 13. JSON Shape

A current benchmark result is a grouped run JSON. The exact file is pretty-printed and includes nullable fields explicitly.

Top-level shape:

```json
{
  "runGroupId": "G-...",
  "startedAt": "...",
  "endedAt": "...",
  "device": {},
  "runtime": {},
  "model": {},
  "generationConfig": {},
  "prompt": {},
  "battery": {},
  "ram": {},
  "modelLoading": {},
  "inferenceRuns": [],
  "hardware": {},
  "modelUnloading": {},
  "summary": {},
  "result": {},
  "observation": {}
}
```

Important sections:

```json
{
  "runtime": {
    "engine": "llama.cpp",
    "version": "native-version",
    "backend": "CPU or Vulkan/NNAPI backend string",
    "threads": 8,
    "gpuLayers": 0,
    "measurementStatus": "NATIVE"
  },
  "model": {
    "name": "Qwen2.5-0.5B-Instruct-Q4_K_M",
    "parameters": "0.5B",
    "format": "GGUF",
    "fileName": "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
    "filePath": "/storage/emulated/0/Android/data/.../models/llama/...",
    "quantization": "Q4_K_M",
    "fileSizeBytes": 400000000,
    "contextSize": 2048,
    "maxOutputTokens": 100
  },
  "summary": {
    "totalInferenceRuns": 1,
    "successfulRuns": 1,
    "failedRuns": 0,
    "averageTtftMs": 850.0,
    "averagePrefillTokensPerSecond": 857.14,
    "averageDecodeTokensPerSecond": 12.2,
    "peakAppPssMb": 1350,
    "batteryDrainPercentage": 1
  }
}
```

Each `inferenceRuns` item contains:

```json
{
  "runId": "R-...",
  "index": 1,
  "timestamp": {
    "start": "...",
    "end": "..."
  },
  "condition": {
    "type": "NORMAL_COLD",
    "screenOn": true,
    "appState": "FOREGROUND",
    "memoryPressure": "",
    "consecutiveGenerationNumber": 1,
    "totalConsecutiveGenerations": 1
  },
  "inference": {
    "generationStart": "...",
    "firstTokenTime": "...",
    "generatedText": "...",
    "ttftMs": 850,
    "prefill": {
      "durationMs": 700,
      "tokens": 600,
      "tokensPerSecond": 857.14
    },
    "decode": {
      "durationMs": 8200,
      "tokens": 100,
      "tokensPerSecond": 12.2
    },
    "total": {
      "durationMs": 8900,
      "outputTokens": 100,
      "generationEnd": "..."
    }
  },
  "result": {
    "status": "SUCCESS",
    "error": null
  }
}
```

## 14. Producing a Benchmark Report

Use a consistent matrix and keep generation settings fixed when comparing models or engines.

Recommended primary matrix:

```text
Engine:        llama.cpp first, ONNX Runtime separately because it is experimental
Models:        Qwen2.5-0.5B and SmolLM2-1.7B
Quantization:  Q4 and Q8 variants
Conditions:    Normal / Cold, Sustained Load, Battery Saver, Memory Pressure, Hot Phone, Background
Prompt:        Same prompt for every comparable run
Max output:    Same token target for every comparable run
Seed:          Same seed for every comparable run
```

Suggested report columns:

```text
Run
Model
Quant
Runtime
Backend
Measurement status
Hardware evidence
Prefill tok/s
Decode tok/s
TTFT
Peak RAM
Battery drain
Load time
Device name
Condition
```

The app's `Report` screen exports these rows as CSV from saved JSON files.

## 15. Measurement Sources

| Information | Source |
| --- | --- |
| Run IDs and timestamps | `BenchmarkRunner` |
| Prompt token count | Selected `LlmEngine.tokenize()` |
| Effective prompt | Selected engine formatting logic |
| TTFT | First-token callback timing and engine result |
| Prefill/decode metrics | Native/runtime generation result |
| Model load/unload time | Engine result and app timestamps |
| Model file size | Catalog metadata or Android `File.length()` |
| App RAM/PSS | `Debug.MemoryInfo` / `ActivityManager` via `MemoryMonitor` |
| Battery | Android battery APIs via `BatteryMonitor` |
| Battery Saver | `PowerManager` |
| Device details | Android `Build` APIs |
| Thermal status | Android thermal APIs where available |
| Hardware backend | Runtime `EngineInfo.backend` |
| GPU/NPU use | Inferred only from backend strings such as Vulkan or NNAPI |
| Crash data | Uncaught exception handler and historical process exit reasons |

## 16. Development Notes

Useful files:

```text
settings.gradle.kts
build.gradle.kts
app/build.gradle.kts
engine/src/main/java/com/example/aiondevicebenchmark/llm/LlmEngine.kt
engine/src/main/java/com/example/aiondevicebenchmark/llm/SupportedEngines.kt
app/src/main/java/com/example/aiondevicebenchmark/di/AppModules.kt
app/src/main/java/com/example/aiondevicebenchmark/benchmark/BenchmarkRunner.kt
app/src/main/java/com/example/aiondevicebenchmark/data/BenchmarkJsonModels.kt
app/src/main/java/com/example/aiondevicebenchmark/data/JsonRepository.kt
app/src/main/java/com/example/aiondevicebenchmark/data/ModelDownloadRepositoryImpl.kt
llama-engine/src/main/java/com/example/aiondevicebenchmark/llama/LlamaCppEngine.kt
llama-engine/src/main/cpp/ai_on_device_llama_jni.cpp
onnx-engine/src/main/java/com/example/aiondevicebenchmark/onnx/OnnxRuntimeEngine.kt
```

To add another engine:

1. Add a new `EngineType`.
2. Add model catalog entries in `DefaultEngineCatalog`.
3. Implement `LlmEngine` in a new module or package.
4. Add an engine factory.
5. Wire the factory in `AppModules.kt`.
6. Keep `BenchmarkRunner` unchanged unless the shared result schema needs a deliberate extension.

Core engineering principle:

```text
BenchmarkRunner
      |
      v
 LlmEngine interface
      |
      +--> LlamaCppEngine
      |
      +--> OnnxRuntimeEngine
      |
      +--> FutureEngine
```

The benchmark system should not depend on a specific runtime. That keeps the project focused on correct on-device inference, measurement, analysis, and honest reporting.
