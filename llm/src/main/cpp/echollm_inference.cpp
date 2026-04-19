//
// Created by rw3h4 on 3/1/26.
//

#include "echollm_inference.h"

#include <android/log.h>
#include <vector>
#include <mutex>
#include <thread>
#include <algorithm>

#include "llama.h"
#include "ggml.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "EchoLLM", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "EchoLLM", __VA_ARGS__)

struct EchoLlmHandle {
    llama_model* model = nullptr;
    const llama_vocab* vocab = nullptr;

    llama_context* ctx = nullptr;
    int32_t ctx_size = 2048;
    int32_t n_threads = 4;

    std::mutex mtx;
};

static int32_t clamp_threads(int32_t requested) {
    const auto hc = (int32_t) std::thread::hardware_concurrency();
    if (hc <= 0) return std::max(1, requested);
    return std::max(1, std::min(requested, hc));
}

EchoLlmHandle* echollm_init(const char* model_path, int32_t context_size, int32_t n_threads) {
    ggml_backend_load_all();

    auto* h = new EchoLlmHandle();
    h->ctx_size = std::max<int32_t>(512, context_size);
    h->n_threads = clamp_threads(n_threads);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // On android CPU only for now

    h->model = llama_model_load_from_file(model_path, mp);
    if (!h->model) {
        LOGE("Failed to load model: %s", model_path);
        delete h;
        return nullptr;
    }

    h->vocab = llama_model_get_vocab(h->model);

    LOGI("Model loaded OK. ctx=%d threads=%d", h->ctx_size, h->n_threads);
    return h;
}

static bool ensure_context(EchoLlmHandle* h, int32_t required_ctx) {
    if (!h) return false;

    // Recreate context if missing or too small
    if (h->ctx && h->ctx_size >= required_ctx) {
        return true;
    }

    if (h->ctx) {
        llama_free(h->ctx);
        h->ctx = nullptr;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = required_ctx;
    cp.n_batch = std::min(required_ctx, 1024);
    cp.no_perf = true;

    // thread params
    cp.n_threads = h->n_threads;
    cp.n_threads_batch = h->n_threads;

    h->ctx = llama_init_from_model(h->model, cp);
    if (!h->ctx) {
        LOGE("Failed to create llama_context");
        return false;
    }

    h->ctx_size = required_ctx;
    return true;
}

void echollm_free(EchoLlmHandle* h) {
    if (!h) return;

    std::lock_guard<std::mutex> lock(h->mtx);

    if (h->ctx) {
        llama_free(h->ctx);
        h->ctx = nullptr;
    }

    if (h->model) {
        llama_model_free(h->model);
        h->model = nullptr;
    }
    delete h;
}

static std::vector<llama_token> tokenize_prompt(const llama_vocab* vocab, const std::string& prompt) {
    const int n_prompt = -llama_tokenize(
            vocab,
            prompt.c_str(),
            (int)prompt.size(),
            nullptr,
            0,
            /*add_special*/ false,
            /*parse_special*/ true
            );

    std::vector<llama_token> tokens(std::max(0, n_prompt));
    if (tokens.empty()) return tokens;

    const int rc = llama_tokenize(
            vocab,
            prompt.c_str(),
            (int)prompt.size(),
            tokens.data(),
            (int)tokens.size(),
            /*add_special*/ false,
            /*parse_special*/ true
            );

    if (rc < 0) {
        return {};
    }
    return tokens;
}

std::string echollm_generate(
        EchoLlmHandle* h,
        const std::string& prompt,
        int32_t max_tokens,
        float temperature
        ) {
    if (!h || !h->model || !h->vocab) return "";

    std::lock_guard<std::mutex> lock(h->mtx);

    const auto prompt_tokens = tokenize_prompt(h->vocab, prompt);

    if (prompt_tokens.empty()) return "";

    // Ensure context can hold prompt + generation
    const int32_t required_ctx  = std::max<int32_t>(h->ctx_size, (int32_t)prompt_tokens.size() + max_tokens + 32);
    if (!ensure_context(h, required_ctx)) return "";

    llama_memory_clear(llama_get_memory(h->ctx), true);

    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;

    llama_sampler* smpl = llama_sampler_chain_init(sparams);

    if (temperature > 0.0f) {
        // Simple sampling chain: Top-K -> Top-P -> Temp -> Dist
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    }

    // Prepare batch from prompt
    llama_batch batch = llama_batch_get_one(const_cast<llama_token*>(prompt_tokens.data()), (int)prompt_tokens.size());

    std::string out;
    out.reserve((size_t)max_tokens * 4);

    int32_t produced = 0;
    llama_token new_token_id;

    // Main decode loop
    for (int n_pos = 0; produced < max_tokens; ) {
        if (llama_decode(h->ctx, batch)) {
            LOGE("llama_decode failed");
            break;
        }

        n_pos += batch.n_tokens;

        new_token_id = llama_sampler_sample(smpl, h->ctx, -1);

        if (llama_vocab_is_eog(h->vocab, new_token_id)) {
            break;
        }

        char buf[256];
        const int n = llama_token_to_piece(h->vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            out.append(buf, (size_t)n);
        }

        batch = llama_batch_get_one(&new_token_id, 1);
        produced++;
    }

    llama_sampler_free(smpl);
    return out;
}
