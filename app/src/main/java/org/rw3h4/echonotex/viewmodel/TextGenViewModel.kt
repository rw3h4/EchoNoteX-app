package org.rw3h4.echonotex.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.rw3h4.echonotex.service.textgen.LocalLlamaEngine
import java.io.File

data class TextGenUiState(
    val isModelLoaded: Boolean = false,
    val isGenerating: Boolean = false,
    val generatedText: String = "",
    val error: String? = null,
    val modelStatus: String = "Initializing..."
)

class TextGenViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TextGenUiState())
    val uiState: StateFlow<TextGenUiState> = _uiState.asStateFlow()

    private val engine = LocalLlamaEngine()

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch {
            try {
                // TODO: Placeholder. To be removed as app is refined.
                // In a real app, this path should be configurable or the model should be downloaded.
                val modelFile = File(getApplication<Application>().filesDir, "model.gguf")
                
                if (!modelFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        modelStatus = "Model file not found at ${modelFile.absolutePath}",
                        error = "Please ensure model.gguf is in the app's internal files directory."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(modelStatus = "Loading model...")
                engine.load(modelFile)
                _uiState.value = _uiState.value.copy(
                    isModelLoaded = true,
                    modelStatus = "Model Loaded"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    modelStatus = "Load Failed",
                    error = e.message
                )
            }
        }
    }

    fun generateText(prompt: String) {
        if (prompt.isBlank() || !uiState.value.isModelLoaded) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            try {
                val result = engine.generate(prompt)
                _uiState.value = _uiState.value.copy(
                    generatedText = result,
                    isGenerating = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = "Generation failed: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.unload()
    }
}
