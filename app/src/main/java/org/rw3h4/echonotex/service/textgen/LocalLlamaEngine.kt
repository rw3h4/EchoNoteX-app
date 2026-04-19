package org.rw3h4.echonotex.service.textgen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.File
import java.util.concurrent.atomic.AtomicLong

class LocalLlamaEngine {

    companion object {
        init {
            System.loadLibrary("echollm")
        }
    }

    private val handle = AtomicLong(0L)

    private external fun nativeInit(modelPath: String, contextSize: Int, nThreads: Int): Long
    private external fun nativeFree(handle: Long)
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temperature: Float): String

    fun load(modelFile: File, contextSize: Int = 2048, nThreads: Int = 4) {
        require(modelFile.exists()) { "Model file not found: ${modelFile.absolutePath} "}
        require(handle.get() == 0L) { "Model already loaded. Call unload() first" }

        val h = nativeInit(modelFile.absolutePath, contextSize, nThreads)
        check(h != 0L) { "nativeInit failed" }
        handle.set(h)
    }

    fun isLoaded(): Boolean = handle.get() != 0L

    fun unload() {
        val h = handle.getAndSet(0L)
        if (h != 0L) nativeFree(h)
    }

    suspend fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.0f
    ): String = withContext(Dispatchers.Default) {
        val h = handle.get()
        check(h != 0L) { "Model not loaded. Call load() first" }
        nativeGenerate(h, prompt, maxTokens, temperature)
    }
}