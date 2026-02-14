package org.rw3h4.echonotex.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.rw3h4.echonotex.data.local.model.Note
import org.rw3h4.echonotex.service.PlaybackService

class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private var mediaController: MediaController? =  null
    private val controllerFuture: ListenableFuture<MediaController>

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asLiveData()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration = _totalDuration.asLiveData()

    private var playerStateJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asLiveData()

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote = _currentNote.asLiveData()

    init {
        val sessionToken = SessionToken(application, ComponentName(application,
            PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                mediaController?.addListener(playerListener)
                updateStateFromController()
            },
            ContextCompat.getMainExecutor(application)
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _totalDuration.value = mediaController?.duration ?: 0L
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateStateFromController()
        }
    }

    private fun updateStateFromController() {
        if (mediaController?.currentMediaItem == null) {
            stop()
            return
        }

        val currentMediaItem = mediaController!!.currentMediaItem!!
        val note = Note(
            currentMediaItem.mediaId!!.toInt(),
            currentMediaItem.mediaMetadata.title.toString(),
            "", 0, 0, 0, false, Note.NOTE_TYPE_VOICE,
            currentMediaItem.requestMetadata.mediaUri.toString(),
            mediaController?.duration ?: 0L, ""
        );

        _currentNote.value = note
        val duration = mediaController?.duration ?: 0L
        _totalDuration.value = if (duration > 0) duration else 0L
    }

    fun play(note: Note) {
        _currentNote.value = note
        mediaController?.let { controller ->
            val metadata = MediaMetadata.Builder().setTitle(note.title).build()
            val mediaItem = MediaItem
                .Builder()
                .setUri(Uri.parse(note.filePath))
                .setMediaId(note.id.toString()).setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun resume() {
        if (mediaController?.playbackState != Player.STATE_IDLE) {
            mediaController?.play()
        }
    }

    fun pause() {
        if (mediaController?.playbackState != Player.STATE_IDLE) {
            mediaController?.pause()
        }
    }

    fun stop() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _currentNote.value = null
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    private fun startPositionUpdates() {
        playerStateJob?.cancel()
        playerStateJob = viewModelScope.launch {
            while (true) {
                _currentPosition.value = mediaController?.currentPosition ?: 0L
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        playerStateJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        playerStateJob?.cancel()
        mediaController?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
    }
}