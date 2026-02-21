package org.rw3h4.echonotex.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class Speech2TextViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(DictationState())
    val state = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var confirmedText = ""
    private var lastPartialText = ""
    private var shouldRestart = false
    private val handler = Handler(Looper.getMainLooper())

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000000)

            if (android.os.Build.VERSION.SDK_INT >= 33) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING, "android.speech.extra.FORMATTING_OPTIMIZE_QUALITY"
                )
                putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
            }
        }
    }

    private fun commitLastPartialIfAny() {
        val text = lastPartialText.trim()
        if (text.isNotEmpty()) {
            if (!confirmedText.endsWith(text)) {
                val sep = if (confirmedText.isNotEmpty()) " " else ""
                confirmedText += sep + text
                _state.value = _state.value.copy(transcribedText = confirmedText)
            }
        }

        lastPartialText = ""
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(isListening = true, error = null)
        }

        override fun onBeginningOfSpeech() {
            _state.value = _state.value.copy(isSpeaking = true)
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(isSpeaking = false)
            commitLastPartialIfAny()
        }

        override fun onError(error: Int) {
            // Recoverable errors — restart the recognizer silently
            if (shouldRestart && error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                )
            ) {
                commitLastPartialIfAny()

                val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 300L else 100L
                handler.postDelayed({ restartListening() }, delay)
                return
            }

            _state.value = _state.value.copy(
                isListening = false,
                isSpeaking = false,
                error = mapErrorCode(error)
            )
        }

        override fun onResults(results: Bundle?) {
            lastPartialText = ""

            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { text ->
                    val separator = if (confirmedText.isNotEmpty()) " " else ""
                    confirmedText += separator + text
                    _state.value = _state.value.copy(transcribedText = confirmedText)
                }

            // Auto-restart for continuous dictation
            if (shouldRestart) {
                handler.postDelayed({ restartListening() }, 100)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.let { text ->
                    lastPartialText = text
                    val separator = if (confirmedText.isNotEmpty()) " " else ""
                    _state.value = _state.value.copy(
                        transcribedText = confirmedText + separator + text
                    )
                }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        confirmedText = ""
        lastPartialText = ""
        _state.value = DictationState()

        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            _state.value = _state.value.copy(error = "Speech recognition is not available on this device.")
            return
        }

        shouldRestart = true

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(getApplication())
        }
        recognizer?.setRecognitionListener(recognitionListener)
        recognizer?.startListening(createRecognizerIntent())
    }

    private fun restartListening() {
        if (!shouldRestart) return
        recognizer?.startListening(createRecognizerIntent())
    }

    fun stopListening() {
        shouldRestart = false
        handler.removeCallbacksAndMessages(null)
        _state.value = _state.value.copy(isListening = false, isSpeaking = false)
        recognizer?.stopListening()
    }

    private fun mapErrorCode(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out. Please check your connection."
            SpeechRecognizer.ERROR_NETWORK -> "Network error. Please check your connection."
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "Server error. Please try again."
            SpeechRecognizer.ERROR_CLIENT -> "An error occurred. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
            SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand speech. Please try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Please wait."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            else -> "An unexpected error occurred. Please try again."
        }
    }

    override fun onCleared() {
        super.onCleared()
        shouldRestart = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }
}

data class DictationState(
    val transcribedText: String = "",
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val error: String? = null
)