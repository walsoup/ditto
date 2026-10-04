package com.walsoup.ditto.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

class AudioRecorderEngine(private val context: Context) {

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var currentPcmFile: File? = null
    private var currentWavFile: File? = null

    @Volatile
    private var segmentStartTime = 0L
    @Volatile
    private var accumulatedDurationMs = 0L

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_isRecording.value) return true

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return false
        }
        val bufferSize = minBufferSize * 2

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val audioDir = File(context.cacheDir, "audio").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            currentPcmFile = File(audioDir, "temp_rec_$timestamp.pcm")
            currentWavFile = File(audioDir, "rec_$timestamp.wav")

            audioRecord?.startRecording()
            _isRecording.value = true
            _isPaused.value = false
            accumulatedDurationMs = 0L
            segmentStartTime = System.currentTimeMillis()
            _durationMs.value = 0L

            recordingJob = scope.launch {
                val pcmOut = FileOutputStream(currentPcmFile)
                val buffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)
                var lastUiUpdate = 0L

                try {
                    while (isActive && _isRecording.value) {
                        val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readCount > 0) {
                            if (_isPaused.value) {
                                _amplitude.value = 0f
                                continue
                            }

                            var sumSquares = 0.0
                            for (i in 0 until readCount) {
                                val s = buffer[i]
                                byteBuffer[i * 2] = (s.toInt() and 0xFF).toByte()
                                byteBuffer[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                                sumSquares += (s.toDouble() * s.toDouble())
                            }
                            pcmOut.write(byteBuffer, 0, readCount * 2)

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate >= 50L) {
                                val rms = sqrt(sumSquares / readCount)
                                val normalized = (rms / 12000.0).coerceIn(0.0, 1.0).toFloat()
                                _amplitude.value = normalized
                                _durationMs.value = accumulatedDurationMs + (now - segmentStartTime)
                                lastUiUpdate = now
                            }
                        }
                    }
                } finally {
                    try {
                        pcmOut.flush()
                        pcmOut.close()
                    } catch (_: Exception) {}
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            return false
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        val now = System.currentTimeMillis()
        accumulatedDurationMs += (now - segmentStartTime)
        _durationMs.value = accumulatedDurationMs
        _amplitude.value = 0f
        _isPaused.value = true
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        segmentStartTime = System.currentTimeMillis()
        _isPaused.value = false
    }

    fun togglePause() {
        if (_isPaused.value) {
            resumeRecording()
        } else {
            pauseRecording()
        }
    }

    fun stopRecording(): File? {
        if (!_isRecording.value) return null
        if (!_isPaused.value) {
            accumulatedDurationMs += (System.currentTimeMillis() - segmentStartTime)
        }
        _durationMs.value = accumulatedDurationMs
        _isRecording.value = false
        _isPaused.value = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        recordingJob?.cancel()
        recordingJob = null
        _amplitude.value = 0f

        val pcm = currentPcmFile
        val wav = currentWavFile

        if (pcm != null && pcm.exists() && wav != null) {
            WavAudioHelper.convertPcmToWav(
                pcmFile = pcm,
                wavFile = wav,
                sampleRate = sampleRate,
                channels = 1,
                bitsPerSample = 16
            )
            pcm.delete() // clean up raw PCM
            return wav
        }
        return null
    }

    fun cancelRecording() {
        _isRecording.value = false
        _isPaused.value = false
        accumulatedDurationMs = 0L

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        recordingJob?.cancel()
        recordingJob = null
        _amplitude.value = 0f

        cleanup()
    }

    private fun cleanup() {
        currentPcmFile?.delete()
        currentPcmFile = null
        currentWavFile?.delete()
        currentWavFile = null
    }
}
