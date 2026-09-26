package com.walsoup.ditto.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayerHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0)
    val progressMs: StateFlow<Int> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    fun playFile(file: File, onCompletion: (() -> Unit)? = null) {
        stop()
        if (!file.exists()) return

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration
                    mp.start()
                    _isPlaying.value = true
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _progressMs.value = 0
                    onCompletion?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _isPlaying.value = true
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _progressMs.value = 0
    }
}
