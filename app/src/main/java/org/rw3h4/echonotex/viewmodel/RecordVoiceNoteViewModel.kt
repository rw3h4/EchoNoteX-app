package org.rw3h4.echonotex.viewmodel

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.internal.concurrent.formatDuration
import org.rw3h4.echonotex.data.local.model.Category
import org.rw3h4.echonotex.data.local.model.Note
import org.rw3h4.echonotex.repository.NoteRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class RecordingState {
    READY_TO_RECORD,
    RECORDING,
    STOPPED
}

data class RecordUiState(
    val recordingState: RecordingState = RecordingState.READY_TO_RECORD,
    val formattedTime: String = "00:00",
    val amplitudes: List<Float> = emptyList(),
    val noteTitle: String = "",
    val selectedCategoryName: String = "",
    val saveFinished: Boolean = false
)

class RecordVoiceNoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository:  NoteRepository = NoteRepository(application)
    val allCategories: LiveData<List<Category>> = repository.allCategories

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private var recordingStartTime: Long = 0
    private var timerJob: Job? = null

    fun updateTitle(newTitle: String) {
        _uiState.value = _uiState.value.copy(noteTitle = newTitle)
    }

    fun updateCategory(newCategory: String) {
        _uiState.value = _uiState.value.copy(selectedCategoryName = newCategory)
    }

    fun startRecording() {
        try {
            val file = File(getApplication<Application>().cacheDir, "VoiceNote_${System.currentTimeMillis()}.m4a")
            audioFilePath = file.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(getApplication())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(recordingState = RecordingState.RECORDING, amplitudes = emptyList())
            startTimeAndWaveformUpdates()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startTimeAndWaveformUpdates() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.recordingState == RecordingState.RECORDING) {
                val elapsedTime = System.currentTimeMillis() - recordingStartTime
                val currentAmplitudes = _uiState.value.amplitudes.toMutableList()
                val maxAmplitude = mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
                val normalized = (maxAmplitude / 32767).coerceIn(0f, 1f)
                currentAmplitudes.add(normalized)

                _uiState.value = _uiState.value.copy(
                    formattedTime = formatDuration(elapsedTime),
                    amplitudes = currentAmplitudes
                )
                delay(100)
            }
        }
    }

    fun stopRecording() {
        if (_uiState.value.recordingState != RecordingState.RECORDING) return
        timerJob?.cancel()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        _uiState.value = _uiState.value.copy(recordingState = RecordingState.STOPPED)
    }

    fun saveVoiceNote() {
        var title = _uiState.value.noteTitle.trim()
        if (title.isEmpty()) {
            title = "Voice Note" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date())
        }

        val categoryName =  _uiState.value.selectedCategoryName.ifEmpty { "None" }
        val duration = System.currentTimeMillis() - recordingStartTime
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val voiceNote = Note(title, 0, audioFilePath, duration, userId)

        repository.saveNoteWithCategory(voiceNote, categoryName)
        _uiState.value = _uiState.value.copy(saveFinished = true)
    }

    fun onSaveComplete() {
        _uiState.value = _uiState.value.copy(saveFinished = false)
    }

    fun discardRecording() {
        stopRecording()
        if (audioFilePath.isNotEmpty()) {
            File(audioFilePath).delete()
        }
        _uiState.value = RecordUiState()
    }

    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaRecorder = null
        timerJob?.cancel()
    }
}