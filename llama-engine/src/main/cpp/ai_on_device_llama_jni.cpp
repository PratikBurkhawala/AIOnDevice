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

static bool has_handle(const jlong handle) {
    return handle != 0L;
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_aiondevicebenchmark_llama_NativeLlamaBridge_loadModel(
    JNIEnv * env,
    jobject,
    jstring j_model_path,
    jint context_size,
    jint max_output_tokens
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

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;

        llama_model * model = llama_model_load_from_file(model_path.c_str(), model_params);
        if (!model) {
            return make_failure(env, "llama.cpp failed to load model");
        }

        const int prompt_capacity = std::max(1, context_size);
        const int prediction_capacity = std::max(1, max_output_tokens);
        const uint32_t n_ctx = static_cast<uint32_t>(std::max(prompt_capacity, prompt_capacity + prediction_capacity));
        const uint32_t n_batch = n_ctx;
        const int threads = std::max(1, static_cast<int>(std::thread::hardware_concurrency()));

        auto * session = new LlamaSession();
        session->model = model;
        session->threads = threads;
        session->gpu_layers = model_params.n_gpu_layers;

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = n_ctx;
        ctx_params.n_batch = n_batch;
        ctx_params.n_ubatch = n_batch;
        ctx_params.n_threads = threads;
        ctx_params.n_threads_batch = threads;
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
            return make_failure(env, "llama.cpp failed to create context");
        }

        session->ctx = ctx;
        session->vocab = llama_model_get_vocab(model);
        session->threads = llama_n_threads(ctx);

        const auto load_end = Clock::now();
        jobject result = make_load_result(
            env,
            reinterpret_cast<int64_t>(session),
            elapsed_ms(load_start, load_end),
            "CPU",
            session->threads,
            session->gpu_layers,
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
        llama_batch batch = llama_batch_get_one(prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()));

        if (llama_model_has_encoder(session->model)) {
            if (llama_encode(session->ctx, batch) != 0) {
                return make_failure(env, "llama.cpp encoder prefill failed");
            }
            llama_token decoder_start_token = llama_model_decoder_start_token(session->model);
            if (decoder_start_token == LLAMA_TOKEN_NULL) {
                decoder_start_token = llama_vocab_bos(session->vocab);
            }
            batch = llama_batch_get_one(&decoder_start_token, 1);
        }

        if (llama_decode(session->ctx, batch) != 0) {
            const std::string message = session->abort_requested.load()
                ? "llama.cpp prompt prefill aborted"
                : "llama.cpp prompt prefill failed";
            return make_failure(env, message);
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
            batch = llama_batch_get_one(&token, 1);
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
