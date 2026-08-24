# AI on Device Benchmark

Android benchmark app for running small LLMs on-device, collecting repeatable performance and telemetry data, and comparing engines, models, quantizations, prompts, and device conditions.

## Benchmark Submission Report

The results below come from the checked-in `report.csv`. The device used for these runs was Samsung `SM-A176B`. The `llama.cpp` runs reported a Vulkan GPU backend on `Mali-G68`; the ONNX runs reported `ONNX Runtime NNAPI`.

### Setup Summary

- Android app package: `com.example.aiondevicebenchmark`
- Root project: `AiOnDeviceBenchmark`
- Main runtime paths: native `llama.cpp` GGUF and ONNX Runtime Android
- Android SDK: `compileSdk = 34`, `minSdk = 28`, `targetSdk = 34`
- Java/Kotlin target: 17
- Gradle wrapper: Gradle 8.13
- Native ABI: `arm64-v8a`
- Native build: Android NDK `27.0.12077973`, CMake `3.22.1`, C++17
- `llama.cpp` Vulkan support: `GGML_VULKAN=ON`

Build and install from the repo root:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK does not bundle model weights. Models are downloaded from the app's `Models` screen into app external storage, then selected for a benchmark run.

The benchmark runner loads the selected model once, tokenizes the prompt, runs all configured consecutive generations while keeping the model loaded, samples app memory, collects battery and thermal snapshots, unloads the model once after the generation loop completes, and writes structured JSON results. This matters for the 10-generation rows: load time is paid once for the benchmark group, not once per generation.

Default generation settings in code:

| Setting | Value |
| --- | --- |
| Max output tokens | 100 |
| Temperature | 0.7 |
| Top K | 40 |
| Top P | 0.9 |
| Seed | 42 |
| GGUF context size | 2048 |
| GGUF GPU layers | `-1`, offload as much as the backend accepts |
| GGUF CPU threads | `0`, runtime/default thread choice |

### Results

| Generations | Model | Quant | Runtime | Backend | Prefill tok/s | Decode tok/s | TTFT | Peak RAM | Battery drain | Load time | Condition |
| ---: | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | Qwen2.5-0.5B-Instruct | Q4_K_M | llama.cpp | GPU: Mali-G68 | 4.64 | 2.52 | 65.125 s | 798 MB | 4% | 0.696 s | SUSTAINED_LOAD |
| 1 | Qwen2.5-0.5B-Instruct | Q4_K_M | llama.cpp | GPU: Mali-G68 | 4.77 | 2.59 | 63.264 s | 768 MB | 1% | 2.718 s | MEMORY_PRESSURE |
| 1 | Qwen2.5-0.5B-Instruct | Q4_K_M | llama.cpp | GPU: Mali-G68 | 4.55 | 2.19 | 66.398 s | 110 MB | 0% | 0.984 s | BATTERY_SAVER |
| 1 | Qwen2.5-0.5B-Instruct | Q4_K_M | llama.cpp | GPU: Mali-G68 | 4.77 | 2.61 | 63.304 s | 118 MB | 0% | 0.723 s | BACKGROUND |
| 1 | Qwen2.5-0.5B-Instruct | Q4_K_M | llama.cpp | GPU: Mali-G68 | 4.64 | 2.58 | 65.083 s | 118 MB | 0% | 1.622 s | NORMAL_COLD |
| 10 | Qwen2.5-0.5B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 3.48 | 2.51 | 27.862 s | 908 MB | 3% | 0.836 s | SUSTAINED_LOAD |
| 1 | Qwen2.5-0.5B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 4.62 | 2.52 | 65.362 s | 129 MB | 1% | 2.265 s | BACKGROUND |
| 1 | Qwen2.5-0.5B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 4.63 | 2.19 | 65.244 s | 113 MB | 1% | 2.355 s | BATTERY_SAVER |
| 1 | Qwen2.5-0.5B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 4.61 | 2.57 | 65.448 s | 119 MB | 0% | 2.217 s | SUSTAINED_LOAD |
| 1 | Qwen2.5-0.5B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 4.53 | 2.70 | 86.229 s | 116 MB | 1% | 1.020 s | NORMAL_COLD |
| 1 | SmolLM2-1.7B-Instruct | Q4_K_M | llama.cpp | GPU: Mali-G68 | 1.28 | 0.61 | 270.028 s | 1332 MB | 0% | 3.425 s | NORMAL_COLD |
| 1 | SmolLM2-1.7B-Instruct | Q4_K_M | llama.cpp | GPU: Mali-G68 | 1.32 | 0.67 | 262.343 s | 1776 MB | 1% | 3.739 s | BACKGROUND |
| 1 | SmolLM2-1.7B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 1.17 | 0.40 | 278.583 s | 2074 MB | 2% | 5.805 s | MEMORY_PRESSURE |
| 1 | SmolLM2-1.7B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 1.15 | 0.39 | 282.600 s | 2079 MB | 1% | 6.255 s | BATTERY_SAVER |
| 1 | SmolLM2-1.7B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 1.11 | 0.57 | 348.426 s | 2676 MB | 2% | 4.497 s | NORMAL_COLD |
| 10 | SmolLM2-1.7B-Instruct | Q8_0 | llama.cpp | GPU: Mali-G68 | 1.21 | 0.41 | 270.842 s | 2104 MB | 19% | 6.096 s | SUSTAINED_LOAD |
| 1 | Qwen2.5-0.5B-Instruct | Q4 | ONNX Runtime | ONNX Runtime NNAPI | 32.30 | 2.52 | 5.610 s | 142 MB | 0% | 7.674 s | NORMAL_COLD |
| 1 | Qwen2.5-0.5B-Instruct | Q8 | ONNX Runtime | ONNX Runtime NNAPI | 39.28 | 1.60 | 4.630 s | 125 MB | 0% | 4.695 s | NORMAL_COLD |

Summary:

| Model / Runtime | Rows | Avg prefill tok/s | Avg decode tok/s | Avg TTFT | Avg peak RAM | Avg load time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Qwen2.5 0.5B Q4_K_M / llama.cpp | 5 | 4.67 | 2.50 | 64.63 s | 382 MB | 1.35 s |
| Qwen2.5 0.5B Q8_0 / llama.cpp | 5 | 4.38 | 2.50 | 62.03 s | 277 MB | 1.74 s |
| SmolLM2 1.7B Q4_K_M / llama.cpp | 2 | 1.30 | 0.64 | 266.19 s | 1554 MB | 3.58 s |
| SmolLM2 1.7B Q8_0 / llama.cpp | 4 | 1.16 | 0.44 | 295.11 s | 2233 MB | 5.66 s |
| Qwen2.5 0.5B Q4 / ONNX Runtime | 1 | 32.30 | 2.52 | 5.61 s | 142 MB | 7.67 s |
| Qwen2.5 0.5B Q8 / ONNX Runtime | 1 | 39.28 | 1.60 | 4.63 s | 125 MB | 4.70 s |

### What Surprised Me and Explanation

**Surprise: ONNX reached the first token much faster than llama.cpp for Qwen2.5 0.5B.** The ONNX NNAPI Qwen rows reached TTFT in 4.630-5.610 seconds, while the llama.cpp Qwen rows were mostly around 63-66 seconds, with one normal-cold Q8 row at 86.229 seconds and one 10-generation Q8 sustained row at 27.862 seconds. My explanation is that the ONNX path is getting a better first-token path on this device for this model, probably because NNAPI is handling the initial graph execution more efficiently than the current llama.cpp Vulkan setup. Decode was not always better on ONNX: Qwen ONNX Q4 decode was similar to llama.cpp Qwen decode, while ONNX Q8 decode was slower.

**Surprise: the app and even the system felt slow before the first token, then behaved normally after the first token appeared.** My explanation is that prefill is the heavy phase. Before the first token, the runtime is processing the whole prompt and preparing model state. On the GPU/Vulkan path this can compete with Android rendering and compositor work, which makes the UI feel slow. After prefill completes, decode becomes smaller repeated token steps, so the UI becomes responsive again.

**Surprise: GPU acceleration did not automatically mean better user experience.** Running on GPU can improve some compute paths, but it can also compete with the UI because Android itself needs GPU time for rendering. A CPU-only or partial-offload run may feel smoother for the UI if CPU threads are capped and at least one or two cores are left free. It is not guaranteed, because saturating all CPU cores can also make the UI slow. The practical next step is to expose CPU threads and GPU layers in the UI and test the same prompt under different CPU/GPU splits.

**Surprise: model configuration mattered as much as model choice.** Context size, GPU layer count, CPU thread count, output token limit, and runtime backend all affected whether the model ran reliably. Giving larger values to a model can crash or stall the run, especially on a memory-constrained phone. My explanation is that each increase raises memory pressure and runtime scheduling cost. The app needs conservative defaults per model, then UI controls to tune them intentionally.

**Surprise: the answer can stop incomplete when the output token limit is reached.** The benchmark currently has a max output token setting, defaulting to 100. If the model has not finished its response by that cap, generation stops and the answer remains incomplete. The output token count is therefore not automatically determined by answer completeness; it is a benchmark parameter. For reporting, that is useful because runs are comparable, but for user-facing answers it needs a larger cap or stop-condition handling.

**Surprise: Q4 was not always faster in every metric.** I expected Q4 to be consistently faster than Q8 because it moves less data. That held for SmolLM2 decode and memory, but Qwen Q8 had a much lower TTFT in the 10-generation sustained row. My explanation is that the single metric is affected by backend scheduling, cache state, run condition, and prompt processing, not only quantization. More repeated runs are needed before making a strong Q4-vs-Q8 claim.

**Surprise: sustained SmolLM2 Q8 was the most expensive battery run.** The 10-generation SmolLM2 Q8_0 sustained-load row drained 19% battery, while the 10-generation Qwen rows drained 3-4%. My explanation is that the larger 1.7B Q8 model keeps the device under high load for much longer because decode stays around 0.41 tok/s and TTFT is still 270.842 seconds. The model is not just slower; it holds the device in the expensive state for longer.

**Surprise: SmolLM2 1.7B was much heavier than Qwen2.5 0.5B on the same device.** SmolLM2 decode was around 0.39-0.67 tok/s, while Qwen llama.cpp decode was around 2.19-2.70 tok/s. My explanation is straightforward: the larger model needs more memory movement and compute per token. This is the clearest trend in the data.

### What I Couldn't Get Working

The ONNX path is now producing benchmark rows, but I still consider it experimental because the app reports `EXPERIMENTAL_TOKENIZER_JSON`. I would not treat ONNX output quality or tokenizer behavior as fully validated yet.

I also do not yet have a CPU-only baseline for the same prompts and models. The current llama.cpp rows use the reported Mali-G68 GPU backend, so I cannot prove from this report alone whether CPU, GPU, or partial offload gives the best balance of speed and UI responsiveness.

I did not capture enough repeated runs per exact configuration to separate stable behavior from run-order effects, cache state, thermal state, or Android background activity. The 10-generation rows help and now include both Qwen and SmolLM2 sustained-load examples, but a stronger report still needs repeated trials under the same condition.

### What I'd Try Next With Another Week

First, I would add explicit CPU/GPU controls to the UI: CPU thread count, GPU layer count, context size, and output token cap. Then I would run the same prompt across CPU-only, GPU-only or max-offload, and partial-offload configurations.

Second, I would measure UI responsiveness during inference, not only model throughput. The key question is whether CPU-only with capped threads gives a smoother app than GPU offload during the prefill phase.

Third, I would build a safer default-configuration table per model. Smaller Qwen models can start with more aggressive defaults, while larger SmolLM2 models need more conservative context and output settings to avoid crashes or long stalls.

Fourth, I would repeat every row at least 5-10 times and report median, p90, and min/max for TTFT, prefill tok/s, decode tok/s, peak RAM, load time, and battery drain.

Fifth, I would add stronger sustained-load reporting for battery and thermal behavior. The SmolLM2 Q8 10-generation run shows that the energy cost can become the main result, so the next report should include battery temperature, thermal status over time, and whether Android throttled the workload.

Sixth, I would improve completion handling. For benchmark comparability, a fixed output token cap is useful. For answer quality, the app should also record whether generation ended naturally or stopped because the output token limit was reached.

Seventh, I would continue validating ONNX Runtime. The first-token numbers are strong, but I would check generated text quality, tokenizer correctness, NNAPI fallback behavior, and whether ONNX still wins after repeated warm and cold runs.

## 1. Goal

The app is a practical benchmark harness, not a UI-heavy demo. It is intended to:

- run quantized LLMs inside a real Android app;
- start with native `llama.cpp` and keep the runtime boundary replaceable;
- provide an experimental ONNX Runtime path for comparison work;
- measure model load time, prompt tokenization, TTFT, prefill throughput, decode throughput, peak RAM, battery drain, and thermal status;
- record selected device conditions such as cold run, sustained load, battery saver, memory pressure, hot phone, and background run;
- keep long downloads and benchmark runs alive through a foreground background-work service;
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
                                   v
                        +---------------------+
                        | BackgroundWork      |
                        | foreground service  |
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
| BackgroundWorkTracker / BackgroundWorkService               |
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
  foreground background-work service, telemetry, model downloads,
  JSON persistence, CSV sharing, crash capture.

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

Benchmark behavior:

- Benchmark runs are also wrapped by `BackgroundWorkTracker`.
- While a benchmark is active, `BackgroundWorkService` runs as a foreground service with an ongoing notification.
- Native engine load/generation/unload work is dispatched away from the UI thread; the UI observes progress through benchmark state updates.

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
- Foreground-service permission support on the target device. The manifest declares `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`.

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
CPU threads            llama.cpp only: 0 uses the app's auto resolver; positive values request that many CPU worker threads
GPU layers             llama.cpp only: -1 all GPU layers, 0 CPU, positive value partial GPU offload
Condition              Label for the test condition
Prompt                 Prompt text used for generation
Max output             Target generated token count
Context size           Number of tokens the runtime can keep in the prompt + generated response window
Generations            Number of consecutive generations in the run group
RAM sample sec         RAM sampling interval during load/inference/unload
Temperature            Sampling temperature
Top-K                  Sampling top-k
Top-P                  Sampling top-p
Seed                   Runtime seed
```

The visible input fields are pre-filled from `app/src/main/assets/model_parameter_presets.json`. The file has shared `defaults` and per-model `presets`; the app resolves them when the benchmark screen first loads, when the engine changes, and when the selected model changes. Users can still edit the UI fields after they are loaded from the file.

Config-backed fields:

```text
condition
promptId
prompt
contextSize
maxOutputTokens
cpuThreads
gpuLayers
consecutiveGenerations
ramSamplingIntervalSeconds
temperature
topK
topP
seed
```

What the pre-filled input values mean:

| Input | Meaning |
| --- | --- |
| `condition` | Run label stored with the result; it does not force phone state by itself. |
| `promptId` | Identifier stored with the selected prompt for comparing repeated runs. |
| `prompt` | Text sent to the engine after any engine-specific prompt formatting. |
| `contextSize` | Token window for input prompt plus generated output. |
| `maxOutputTokens` | Target number of generated tokens. |
| `cpuThreads` | llama.cpp CPU worker-thread request; `0` means auto, which leaves some cores free. |
| `gpuLayers` | llama.cpp GPU offload request; `-1` all possible layers, `0` CPU only, positive values partial offload. |
| `consecutiveGenerations` | Number of back-to-back generations saved in one run group. |
| `ramSamplingIntervalSeconds` | Delay between RAM samples during load, inference, and unload. |
| `temperature`, `topK`, `topP`, `seed` | Sampling settings passed to generation. |

Current model-specific preset values:

| Engine | Model | Context | Max output | CPU threads | GPU layers |
| --- | --- | ---: | ---: | ---: | ---: |
| `LLAMA_CPP` | `Qwen2.5-0.5B-Instruct-Q4_K_M` | 1024 | 128 | 0 | -1 |
| `LLAMA_CPP` | `Qwen2.5-0.5B-Instruct-Q8_0` | 1024 | 128 | 0 | -1 |
| `LLAMA_CPP` | `SmolLM2-1.7B-Instruct-Q4_K_M` | 512 | 100 | 0 | 16 |
| `LLAMA_CPP` | `SmolLM2-1.7B-Instruct-Q8_0` | 512 | 64 | 0 | 8 |
| `ONNX_RUNTIME` | `Qwen2.5-0.5B-Instruct-ONNX-Q4` | 256 | 64 | 0 | 0 |
| `ONNX_RUNTIME` | `Qwen2.5-0.5B-Instruct-ONNX-Q8` | 256 | 64 | 0 | 0 |
| `ONNX_RUNTIME` | `SmolLM2-1.7B-Instruct-ONNX-Q4` | 256 | 32 | 0 | 0 |
| `ONNX_RUNTIME` | `SmolLM2-1.7B-Instruct-ONNX-Q8` | 256 | 32 | 0 | 0 |

Shared default preset values:

```text
Condition:             Normal / Cold
Generations:           1
RAM sample sec:        5
CPU threads:           0
Temperature:           0.7
Top-K:                 40
Top-P:                 0.9
Seed:                  42
```

Recommended first benchmark is the default preset for the smallest llama.cpp model:

```text
Engine:                llama.cpp
Model:                 Qwen2.5-0.5B-Instruct-Q4_K_M
Condition:             Normal / Cold
Context size:          1024
Max output:            128
GPU layers:            -1
CPU threads:           0
Generations:           1
RAM sample sec:        5
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

### Background Execution

Model downloads and benchmark runs are tracked as background work. The app starts `BackgroundWorkService` as a foreground service with a persistent notification while that work is active, then stops it when all tracked work finishes. This allows long downloads and active benchmark runs to continue when the app is not the foreground screen, subject to Android foreground-service and device power-management rules.

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
Start foreground background-work service
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
          |
          v
Stop foreground service
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
