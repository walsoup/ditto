package com.walsoup.ditto.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayerHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0)
    val progressMs: StateFlow<Int> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private fun startProgressPolling() {
        stopProgressPolling()
        progressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _progressMs.value = mp.currentPosition
                        handler.postDelayed(this, 100)
                    }
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

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
                    startProgressPolling()
                }
                setOnCompletionListener {
                    stopProgressPolling()
                    _isPlaying.value = false
                    _progressMs.value = 0
                    onCompletion?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    stopProgressPolling()
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopProgressPolling()
            _isPlaying.value = false
        }
    }

    fun pause() {
        stopProgressPolling()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _progressMs.value = it.currentPosition
                _isPlaying.value = false
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _isPlaying.value = true
                startProgressPolling()
            }
        }
    }

    fun stop() {
        stopProgressPolling()
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

