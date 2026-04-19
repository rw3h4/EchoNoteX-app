//
// Created by rw3h4 on 3/1/26.
//

#include <jni.h>
#include <string>

#include "echollm_inference.h"

static EchoLlmHandle* from_handle(jlong h) {
    return reinterpret_cast<EchoLlmHandle*>(h);
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_rw3h4_echonotex_service_textgen_LocalLlamaEngine_nativeInit(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring modelPath,
        jint contextSize,
        jint nThreads
        ) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    EchoLlmHandle* h = echollm_init(path, contextSize, nThreads);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT void JNICALL
Java_org_rw3h4_echonotex_service_textgen_LocalLlamaEngine_nativeFree(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle
        ) {
    echollm_free(from_handle(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_rw3h4_echonotex_service_textgen_LocalLlamaEngine_nativeGenerate(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle,
        jstring prompt,
        jint maxTokens,
        jfloat temperature
        ) {
    EchoLlmHandle* h = from_handle(handle);
    if (!h) return env->NewStringUTF("");

    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string out = echollm_generate(h, std::string(p), maxTokens, temperature);
    env->ReleaseStringUTFChars(prompt, p);

    return env->NewStringUTF(out.c_str());
}
