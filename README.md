# AI on Device Benchmark Android App

Android Jetpack Compose benchmark harness based on `AI_On_Device_Benchmark_README.md`.

## What Is Implemented

- Compose configuration screen for engine, model, quantization, condition, prompt, and generation controls.
- Engine and GGUF model selection from code-defined dropdown mappings.
- Layered benchmark architecture:
  - `benchmark/` controller, runner, config, and state.
  - `llm/` generic `LlmEngine` interface and `EngineFactory`.
  - `telemetry/` memory, battery, thermal, and device collectors.
  - `data/` serializable JSON result model and repository.
- One JSON record per generation in app external storage under `benchmark-results/`.
- Saved JSON browser, key-value JSON detail screen, aggregate report table, delete action, and CSV report sharing.
- A `LlamaCppEngine` adapter stub that exercises the benchmark pipeline until native llama.cpp bindings and GGUF loading are wired in.

## Build

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Next Integration Step

Replace the simulated logic in `app/src/main/java/com/example/aiondevicebenchmark/llm/LlamaCppEngine.kt` with JNI or library calls into llama.cpp. The benchmark layer should not change as long as the `LlmEngine` contract remains stable.

Add future engines and model path mappings in:

```text
app/src/main/java/com/example/aiondevicebenchmark/llm/LlmEngine.kt
app/src/main/java/com/example/aiondevicebenchmark/llm/SupportedEngines.kt
```

## View JSON On Laptop

When a phone is connected with USB debugging enabled:

```bash
adb shell ls /sdcard/Android/data/com.example.aiondevicebenchmark/files/benchmark-results
adb pull /sdcard/Android/data/com.example.aiondevicebenchmark/files/benchmark-results ./benchmark-results
```

You can also open the app's `Report` screen and use `Share CSV` to export a table-form report.
