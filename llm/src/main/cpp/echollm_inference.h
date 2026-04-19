//
// Created by rw3h4 on 3/1/26.
//
#pragma once

#include <string>
#include <cstdint>

struct EchoLlmHandle;

EchoLlmHandle* echollm_init(const char* model_path, int32_t context_size, int32_t n_threads);
void echollm_free(EchoLlmHandle* h);

std::string echollm_generate(
        EchoLlmHandle* h,
        const std::string& prompt,
        int32_t max_tokens,
        float temperature
        );
