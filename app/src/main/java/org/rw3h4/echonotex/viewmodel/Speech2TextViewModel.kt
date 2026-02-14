package org.rw3h4.echonotex.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class Speech2TextViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(DictationState())
    val state = _state.asStateFlow()

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(application)

    fun startListening() {
        _state.value = DictationState()

        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            _state.value = _state.value.copy(error = "Speech Recognition is not available.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000000)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(isListening = true, error = null)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // Do nothing, wait for the user to tap "Done"
            }

            override fun onError(error: Int) {
                _state.value = _state.value.copy(isListening = false, error = "Error: $error")
            }

            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text ->
                    _state.value = _state.value.copy(transcribedText = text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text ->
                    _state.value = _state.value.copy(transcribedText = text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    fun stopListening() {
        _state.value = _state.value.copy(isListening = false)
        recognizer.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.destroy()
    }
}

data class DictationState(
    val transcribedText: String = "",
    val isListening: Boolean = false,
    val error: String? = null
)