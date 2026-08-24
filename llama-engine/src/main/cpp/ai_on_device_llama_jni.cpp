#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"

#define LOG_TAG "AIOnDeviceLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

using Clock = std::chrono::steady_clock;

struct LlamaSession {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int threads = 1;
    int gpu_layers = 0;
    std::atomic_bool abort_requested = false;
    std::mutex mutex;
};

static std::once_flag g_backend_once;

struct GpuBackendInfo {
    bool available = false;
    std::string name;
    std::string diagnostics;
    ggml_backend_dev_t device = nullptr;
};

struct LoadedSession {
    LlamaSession * session = nullptr;
    std::string backend;
    int gpu_layers = 0;
};

static int64_t elapsed_ms(const Clock::time_point start, const Clock::time_point end) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
}

static std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (!value) {
        return "";
    }
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

static jobject box_boolean(JNIEnv * env, bool value) {
    jclass clazz = env->FindClass("java/lang/Boolean");
    jmethodID value_of = env->GetStaticMethodID(clazz, "valueOf", "(Z)Ljava/lang/Boolean;");
    return env->CallStaticObjectMethod(clazz, value_of, static_cast<jboolean>(value));
}

static jobject box_long(JNIEnv * env, int64_t value) {
    jclass clazz = env->FindClass("java/lang/Long");
    jmethodID value_of = env->GetStaticMethodID(clazz, "valueOf", "(J)Ljava/lang/Long;");
    return env->CallStaticObjectMethod(clazz, value_of, static_cast<jlong>(value));
}

static jobject box_int(JNIEnv * env, int value) {
    jclass clazz = env->FindClass("java/lang/Integer");
    jmethodID value_of = env->GetStaticMethodID(clazz, "valueOf", "(I)Ljava/lang/Integer;");
    return env->CallStaticObjectMethod(clazz, value_of, static_cast<jint>(value));
}

static jobject make_triple(JNIEnv * env, bool success, const std::string & error, jobject value) {
    jclass clazz = env->FindClass("kotlin/Triple");
    jmethodID ctor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
    jobject j_success = box_boolean(env, success);
    jstring j_error = env->NewStringUTF(error.c_str());
    jobject result = env->NewObject(clazz, ctor, j_success, j_error, value);
    env->DeleteLocalRef(j_success);
    env->DeleteLocalRef(j_error);
    return result;
}

static jobject make_success(JNIEnv * env, jobject value) {
    return make_triple(env, true, "", value);
}

static jobject make_failure(JNIEnv * env, const std::string & error) {
    return make_triple(env, false, error, nullptr);
}

static jobject make_load_result(
    JNIEnv * env,
    const int64_t handle,
    const int64_t load_time_ms,
    const std::string & backend,
    const int threads,
    const int gpu_layers,
    const std::string & version
) {
    jclass clazz = env->FindClass("com/example/aiondevicebenchmark/llama/NativeLoadResult");
    jmethodID ctor = env->GetMethodID(clazz, "<init>", "(JJLjava/lang/String;IILjava/lang/String;)V");
    jstring j_backend = env->NewStringUTF(backend.c_str());
    jstring j_version = env->NewStringUTF(version.c_str());
    jobject result = env->NewObject(clazz, ctor, handle, load_time_ms, j_backend, threads, gpu_layers, j_version);
    env->DeleteLocalRef(j_backend);
    env->DeleteLocalRef(j_version);
    return result;
}

static jobject make_generation_result(
    JNIEnv * env,
    const std::string & output_text,
    const int64_t ttft_ms,
    const int64_t prefill_duration_ms,
    const int prefill_tokens,
    const int64_t decode_duration_ms,
    const int output_tokens,
    const int64_t total_duration_ms
) {
    jclass clazz = env->FindClass("com/example/aiondevicebenchmark/llama/NativeGenerationResult");
    jmethodID ctor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/String;JJIJIJ)V");
    jstring j_output = env->NewStringUTF(output_text.c_str());
    jobject result = env->NewObject(
        clazz,
        ctor,
        j_output,
        ttft_ms,
        prefill_duration_ms,
        prefill_tokens,
        decode_duration_ms,
        output_tokens,
        total_duration_ms
    );
    env->DeleteLocalRef(j_output);
    return result;
}

static bool tokenize_checked(
    const LlamaSession * session,
    const std::string & text,
    std::vector<llama_token> & tokens,
    std::string & error
) {
    const int count = llama_tokenize(
        session->vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        nullptr,
        0,
        true,
        true
    );
    if (count == INT32_MIN) {
        error = "llama.cpp tokenization overflow";
        return false;
    }

    const int needed = count < 0 ? -count : count;
    tokens.resize(std::max(1, needed));
    const int actual = llama_tokenize(
        session->vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        true
    );
    if (actual < 0) {
        error = "llama.cpp failed to tokenize prompt";
        return false;
    }
    tokens.resize(actual);
    return true;
}

static std::string token_to_piece(const LlamaSession * session, const llama_token token) {
    char stack_buffer[256];
    int n = llama_token_to_piece(session->vocab, token, stack_buffer, sizeof(stack_buffer), 0, true);
    if (n >= 0) {
        return std::string(stack_buffer, n);
    }

    std::vector<char> buffer(static_cast<size_t>(-n) + 1);
    n = llama_token_to_piece(session->vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    if (n < 0) {
        return "";
    }
    return std::string(buffer.data(), n);
}

static llama_sampler * make_sampler(const float temperature, const int top_k, const float top_p, const int seed) {
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = false;
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);

    if (top_k > 0) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    }
    if (top_p > 0.0f && top_p < 1.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    }
    if (temperature > 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    }

    return sampler;
}

static bool decode_tokens_in_chunks(
    LlamaSession * session,
    llama_token * tokens,
    const int32_t token_count,
    const bool encoder,
    std::string & error
) {
    const int32_t max_batch = std::max<int32_t>(1, static_cast<int32_t>(llama_n_batch(session->ctx)));
    for (int32_t offset = 0; offset < token_count; offset += max_batch) {
        if (session->abort_requested.load()) {
            error = encoder ? "llama.cpp encoder prefill aborted" : "llama.cpp prompt prefill aborted";
            return false;
        }

        const int32_t chunk_size = std::min(max_batch, token_count - offset);
        llama_batch batch = llama_batch_get_one(tokens + offset, chunk_size);
        const int status = encoder
            ? llama_encode(session->ctx, batch)
            : llama_decode(session->ctx, batch);
        if (status != 0) {
            error = encoder ? "llama.cpp encoder prefill failed" : "llama.cpp prompt prefill failed";
            return false;
        }
    }
    return true;
}

static int resolve_thread_count(const int requested_threads) {
    const int cores = std::max(1, static_cast<int>(std::thread::hardware_concurrency()));
    if (requested_threads > 0) {
        return std::max(1, std::min(requested_threads, cores));
    }
    if (cores <= 2) {
        return 1;
    }
    if (cores <= 4) {
        return cores - 1;
    }
    return cores - 2;
}

static bool has_handle(const jlong handle) {
    return handle != 0L;
}

static std::string device_type_name(const enum ggml_backend_dev_type type) {
    switch (type) {
        case GGML_BACKEND_DEVICE_TYPE_CPU:
            return "CPU";
        case GGML_BACKEND_DEVICE_TYPE_GPU:
            return "GPU";
        case GGML_BACKEND_DEVICE_TYPE_IGPU:
            return "IGPU";
        case GGML_BACKEND_DEVICE_TYPE_ACCEL:
            return "ACCEL";
        case GGML_BACKEND_DEVICE_TYPE_META:
            return "META";
        default:
            return "UNKNOWN";
    }
}

static std::string describe_registered_backends() {
    std::string diagnostics = "Registered backends:";
    const size_t backend_count = ggml_backend_reg_count();
    if (backend_count == 0) {
        return diagnostics + " none";
    }

    for (size_t i = 0; i < backend_count; ++i) {
        ggml_backend_reg_t reg = ggml_backend_reg_get(i);
        diagnostics += " ";
        diagnostics += ggml_backend_reg_name(reg);
        diagnostics += "(" + std::to_string(ggml_backend_reg_dev_count(reg)) + ")";
    }
    return diagnostics;
}

static GpuBackendInfo find_gpu_backend() {
    const std::string backend_diagnostics = describe_registered_backends();
    LOGI("%s", backend_diagnostics.c_str());

    const size_t device_count = ggml_backend_dev_count();
    if (device_count == 0) {
        return GpuBackendInfo{false, "", backend_diagnostics + "; devices: none", nullptr};
    }

    std::string diagnostics = backend_diagnostics + "; devices:";
    for (size_t i = 0; i < device_count; ++i) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        if (!device) {
            continue;
        }

        ggml_backend_dev_props props{};
        ggml_backend_dev_get_props(device, &props);
        ggml_backend_reg_t reg = ggml_backend_dev_backend_reg(device);
        const char * reg_name = reg ? ggml_backend_reg_name(reg) : "unknown";
        const char * name = props.name ? props.name : ggml_backend_dev_name(device);
        const char * description = props.description ? props.description : ggml_backend_dev_description(device);
        const std::string display_name = description ? description : (name ? name : "Vulkan GPU");
        diagnostics += " [";
        diagnostics += std::to_string(i);
        diagnostics += ":";
        diagnostics += reg_name ? reg_name : "unknown";
        diagnostics += "/";
        diagnostics += device_type_name(props.type);
        diagnostics += "/";
        diagnostics += display_name;
        diagnostics += "]";
        LOGI(
            "llama.cpp backend device %zu: reg=%s type=%s name=%s description=%s memory=%zu/%zu",
            i,
            reg_name ? reg_name : "unknown",
            device_type_name(props.type).c_str(),
            name ? name : "",
            description ? description : "",
            props.memory_free,
            props.memory_total
        );

        if (props.type == GGML_BACKEND_DEVICE_TYPE_GPU || props.type == GGML_BACKEND_DEVICE_TYPE_IGPU) {
            return GpuBackendInfo{true, display_name, diagnostics, device};
        }
    }
    return GpuBackendInfo{false, "", diagnostics, nullptr};
}

static LoadedSession try_load_session(
    const std::string & model_path,
    const uint32_t n_ctx,
    const uint32_t n_batch,
    const uint32_t n_ubatch,
    const int threads,
    const int gpu_layers,
    ggml_backend_dev_t gpu_device
) {
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = gpu_layers < 0 ? -1 : gpu_layers;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    std::vector<ggml_backend_dev_t> devices;
    if (model_params.n_gpu_layers != 0 && gpu_device) {
        devices.push_back(gpu_device);
        devices.push_back(nullptr);
        model_params.devices = devices.data();
        model_params.split_mode = LLAMA_SPLIT_MODE_NONE;
        model_params.main_gpu = 0;
    }

    llama_model * model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!model) {
        return LoadedSession{};
    }

    auto * session = new LlamaSession();
    session->model = model;
    session->threads = threads;
    session->gpu_layers = model_params.n_gpu_layers;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = n_batch;
    ctx_params.n_ubatch = n_ubatch;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    ctx_params.offload_kqv = model_params.n_gpu_layers != 0;
    ctx_params.no_perf = false;
    ctx_params.abort_callback = [](void * data) -> bool {
        auto * abort_requested = static_cast<std::atomic_bool *>(data);
        return abort_requested && abort_requested->load();
    };
    ctx_params.abort_callback_data = &session->abort_requested;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        llama_model_free(model);
        delete session;
        return LoadedSession{};
    }

    session->ctx = ctx;
    session->vocab = llama_model_get_vocab(model);
    session->threads = llama_n_threads(ctx);
    return LoadedSession{session, model_params.n_gpu_layers != 0 ? "GPU" : "CPU", session->gpu_layers};
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_aiondevicebenchmark_llama_NativeLlamaBridge_loadModel(
    JNIEnv * env,
    jobject,
    jstring j_model_path,
    jint context_size,
    jint max_output_tokens,
    jint requested_cpu_threads,
    jint requested_gpu_layers
) {
    try {
        std::call_once(g_backend_once, [] {
            llama_log_set([](ggml_log_level level, const char * text, void *) {
                const int android_level = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO;
                __android_log_print(android_level, LOG_TAG, "%s", text);
            }, nullptr);
            ggml_backend_load_all();
            llama_backend_init();
            LOGI("llama.cpp backend initialized");
        });

        const std::string model_path = jstring_to_string(env, j_model_path);
        if (model_path.empty()) {
            return make_failure(env, "Model path is blank");
        }

        const auto load_start = Clock::now();

        const int prompt_capacity = std::max(1, context_size);
        const int prediction_capacity = std::max(1, max_output_tokens);
        const uint32_t n_ctx = static_cast<uint32_t>(std::max(prompt_capacity, prompt_capacity + prediction_capacity));
        const uint32_t n_batch = std::min<uint32_t>(n_ctx, 256);
        const uint32_t n_ubatch = std::min<uint32_t>(n_batch, 128);
        const int threads = resolve_thread_count(static_cast<int>(requested_cpu_threads));
        const int gpu_layers = requested_gpu_layers < 0 ? -1 : requested_gpu_layers;

        LOGI(
            "llama.cpp load config: n_ctx=%u n_batch=%u n_ubatch=%u threads=%d requested_threads=%d gpu_layers=%d",
            n_ctx,
            n_batch,
            n_ubatch,
            threads,
            static_cast<int>(requested_cpu_threads),
            gpu_layers
        );

        const GpuBackendInfo gpu = find_gpu_backend();
        LoadedSession loaded{};
        std::string backend = "CPU";
        if (gpu.available && gpu_layers != 0) {
            LOGI("llama.cpp GPU backend available: %s", gpu.name.c_str());
            loaded = try_load_session(model_path, n_ctx, n_batch, n_ubatch, threads, gpu_layers, gpu.device);
            if (loaded.session) {
                backend = "GPU: " + gpu.name;
            } else {
                LOGE("llama.cpp GPU load failed; falling back to CPU. %s", gpu.diagnostics.c_str());
            }
        } else if (gpu.available) {
            LOGI("llama.cpp GPU backend available but disabled for this model: %s", gpu.name.c_str());
        } else {
            LOGI("llama.cpp GPU backend unavailable; using CPU. %s", gpu.diagnostics.c_str());
        }

        if (!loaded.session) {
            loaded = try_load_session(model_path, n_ctx, n_batch, n_ubatch, threads, 0, nullptr);
            backend = gpu.available && gpu_layers != 0
                ? "CPU (GPU fallback: " + gpu.name + ")"
                : "CPU (GPU unavailable)";
        }

        if (!loaded.session) {
            return make_failure(env, "llama.cpp failed to load model or create context");
        }

        const auto load_end = Clock::now();
        jobject result = make_load_result(
            env,
            reinterpret_cast<int64_t>(loaded.session),
            elapsed_ms(load_start, load_end),
            backend,
            loaded.session->threads,
            loaded.gpu_layers,
            "native"
        );
        jobject triple = make_success(env, result);
        env->DeleteLocalRef(result);
        return triple;
    } catch (const std::exception & error) {
        return make_failure(env, error.what());
    } catch (...) {
        return make_failure(env, "Unknown native error while loading model");
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_aiondevicebenchmark_llama_NativeLlamaBridge_unloadModel(JNIEnv * env, jobject, jlong handle) {
    if (!has_handle(handle)) {
        return make_failure(env, "No llama.cpp model is loaded");
    }

    try {
        const auto start = Clock::now();
        auto * session = reinterpret_cast<LlamaSession *>(handle);
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            session->abort_requested.store(true);
            llama_free(session->ctx);
            llama_model_free(session->model);
            session->ctx = nullptr;
            session->model = nullptr;
            session->vocab = nullptr;
        }
        delete session;
        jobject value = box_long(env, elapsed_ms(start, Clock::now()));
        jobject triple = make_success(env, value);
        env->DeleteLocalRef(value);
        return triple;
    } catch (const std::exception & error) {
        return make_failure(env, error.what());
    } catch (...) {
        return make_failure(env, "Unknown native error while unloading model");
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_aiondevicebenchmark_llama_NativeLlamaBridge_tokenize(JNIEnv * env, jobject, jlong handle, jstring j_prompt) {
    if (!has_handle(handle)) {
        return make_failure(env, "No llama.cpp model is loaded");
    }

    try {
        auto * session = reinterpret_cast<LlamaSession *>(handle);
        std::lock_guard<std::mutex> lock(session->mutex);
        const std::string prompt = jstring_to_string(env, j_prompt);
        std::vector<llama_token> tokens;
        std::string error;
        if (!tokenize_checked(session, prompt, tokens, error)) {
            return make_failure(env, error);
        }
        jobject value = box_int(env, static_cast<int>(tokens.size()));
        jobject triple = make_success(env, value);
        env->DeleteLocalRef(value);
        return triple;
    } catch (const std::exception & error) {
        return make_failure(env, error.what());
    } catch (...) {
        return make_failure(env, "Unknown native error while tokenizing");
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_aiondevicebenchmark_llama_NativeLlamaBridge_requestAbort(JNIEnv * env, jobject, jlong handle) {
    if (!has_handle(handle)) {
        return make_failure(env, "No llama.cpp model is loaded");
    }
    auto * session = reinterpret_cast<LlamaSession *>(handle);
    session->abort_requested.store(true);
    jobject value = box_boolean(env, true);
    jobject triple = make_success(env, value);
    env->DeleteLocalRef(value);
    return triple;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_aiondevicebenchmark_llama_NativeLlamaBridge_generate(
    JNIEnv * env,
    jobject,
    jlong handle,
    jstring j_prompt,
    jint max_output_tokens,
    jfloat temperature,
    jint top_k,
    jfloat top_p,
    jint seed,
    jobject callback
) {
    if (!has_handle(handle)) {
        return make_failure(env, "No llama.cpp model is loaded");
    }

    try {
        auto * session = reinterpret_cast<LlamaSession *>(handle);
        std::lock_guard<std::mutex> lock(session->mutex);
        session->abort_requested.store(false);

        const std::string prompt = jstring_to_string(env, j_prompt);
        std::vector<llama_token> prompt_tokens;
        std::string error;
        if (!tokenize_checked(session, prompt, prompt_tokens, error)) {
            return make_failure(env, error);
        }
        if (prompt_tokens.empty()) {
            return make_failure(env, "Prompt produced no tokens");
        }

        const int n_predict = std::max(1, static_cast<int>(max_output_tokens));
        if (prompt_tokens.size() + static_cast<size_t>(n_predict) > llama_n_ctx(session->ctx)) {
            return make_failure(env, "Prompt and output target exceed llama.cpp context size");
        }

        llama_memory_clear(llama_get_memory(session->ctx), true);

        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            make_sampler(temperature, top_k, top_p, seed),
            llama_sampler_free
        );
        if (!sampler) {
            return make_failure(env, "llama.cpp failed to create sampler");
        }

        const auto total_start = Clock::now();
        const auto prefill_start = Clock::now();

        if (llama_model_has_encoder(session->model)) {
            if (!decode_tokens_in_chunks(
                    session,
                    prompt_tokens.data(),
                    static_cast<int32_t>(prompt_tokens.size()),
                    true,
                    error
                )) {
                return make_failure(env, error);
            }
            llama_token decoder_start_token = llama_model_decoder_start_token(session->model);
            if (decoder_start_token == LLAMA_TOKEN_NULL) {
                decoder_start_token = llama_vocab_bos(session->vocab);
            }
            if (!decode_tokens_in_chunks(session, &decoder_start_token, 1, false, error)) {
                return make_failure(env, error);
            }
        } else if (!decode_tokens_in_chunks(
                session,
                prompt_tokens.data(),
                static_cast<int32_t>(prompt_tokens.size()),
                false,
                error
            )) {
            return make_failure(env, error);
        }
        const auto prefill_end = Clock::now();

        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;I)V");
        if (!on_token) {
            return make_failure(env, "Native token callback is missing onToken(String, Int)");
        }

        std::string output;
        int output_tokens = 0;
        int n_pos = static_cast<int>(prompt_tokens.size());
        Clock::time_point first_token_time{};

        for (int i = 0; i < n_predict; ++i) {
            if (session->abort_requested.load()) {
                return make_failure(env, "llama.cpp generation aborted");
            }

            llama_token token = llama_sampler_sample(sampler.get(), session->ctx, -1);
            if (llama_vocab_is_eog(session->vocab, token)) {
                break;
            }

            const std::string piece = token_to_piece(session, token);
            output += piece;
            output_tokens += 1;

            if (output_tokens == 1) {
                first_token_time = Clock::now();
            }

            jstring j_piece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, on_token, j_piece, output_tokens);
            env->DeleteLocalRef(j_piece);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return make_failure(env, "Kotlin token callback failed");
            }

            llama_sampler_accept(sampler.get(), token);
            llama_batch batch = llama_batch_get_one(&token, 1);
            if (llama_decode(session->ctx, batch) != 0) {
                const std::string message = session->abort_requested.load()
                    ? "llama.cpp decode aborted"
                    : "llama.cpp decode failed";
                return make_failure(env, message);
            }
            n_pos += 1;
            if (n_pos >= static_cast<int>(llama_n_ctx(session->ctx))) {
                break;
            }
        }

        const auto total_end = Clock::now();
        const int64_t prefill_ms = std::max<int64_t>(1, elapsed_ms(prefill_start, prefill_end));
        const int64_t total_ms = std::max<int64_t>(1, elapsed_ms(total_start, total_end));
        const int64_t ttft_ms = output_tokens > 0 ? std::max<int64_t>(1, elapsed_ms(total_start, first_token_time)) : total_ms;
        const int64_t decode_ms = std::max<int64_t>(1, total_ms - prefill_ms);

        jobject result = make_generation_result(
            env,
            output,
            ttft_ms,
            prefill_ms,
            static_cast<int>(prompt_tokens.size()),
            decode_ms,
            output_tokens,
            total_ms
        );
        jobject triple = make_success(env, result);
        env->DeleteLocalRef(result);
        return triple;
    } catch (const std::exception & error) {
        return make_failure(env, error.what());
    } catch (...) {
        return make_failure(env, "Unknown native error while generating");
    }
}
