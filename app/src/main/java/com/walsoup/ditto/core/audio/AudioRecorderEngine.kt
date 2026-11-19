package com.walsoup.ditto.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

class AudioRecorderEngine(private val context: Context) {

    private companion object {
        const val TAG = "Ditto"
        const val UI_UPDATE_INTERVAL_MS = 50L
        const val EMPTY_READ_RETRY_MS = 5L
        const val RMS_FULL_SCALE = 12000.0
    }

    private val sampleRate = AudioConfig.SAMPLE_RATE
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recordingJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private val stopMutex = Mutex()

    @Volatile
    private var activeSampleRate = AudioConfig.SAMPLE_RATE

    /** Actual capture sample rate of the last take; feed this to the encoder. */
    val lastSampleRate: Int get() = activeSampleRate

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    private val _limitReached = MutableStateFlow(false)

    /** True when the take was auto-stopped because it hit [AudioConfig.MAX_DURATION_MS]. */
    val limitReached: StateFlow<Boolean> = _limitReached.asStateFlow()

    /** Rolling window of the most recent levels (fixed size, emitted as a copy). */
    private val levelWindow = ArrayDeque<Float>(AudioConfig.LEVELS_PER_TAKE)

    /** Every level of the current take, used to draw the saved-take waveform. */
    private val takeLevels = ArrayList<Float>()

    fun recordedLevels(): List<Float> = synchronized(takeLevels) { takeLevels.toList() }

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
            Log.w(TAG, "Microphone buffer size unavailable: $minBufferSize")
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
                Log.w(TAG, "AudioRecord failed to initialise")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val audioDir = File(context.cacheDir, "audio").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val pcmFile = File(audioDir, "temp_rec_$timestamp.pcm")
            val wavFile = File(audioDir, "rec_$timestamp.wav")
            currentPcmFile = pcmFile
            currentWavFile = wavFile

            activeSampleRate = sampleRate
            audioRecord?.startRecording()
            _isRecording.value = true
            _isPaused.value = false
            _limitReached.value = false
            accumulatedDurationMs = 0L
            segmentStartTime = System.currentTimeMillis()
            _durationMs.value = 0L
            _levels.value = emptyList()
            synchronized(levelWindow) { levelWindow.clear() }
            synchronized(takeLevels) { takeLevels.clear() }

            recordingJob = scope.launch {
                val pcmOut = FileOutputStream(pcmFile)
                val buffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)
                var lastUiUpdate = 0L
                var hitLimit = false

                try {
                    while (isActive && _isRecording.value) {
                        val record = audioRecord
                        if (record == null) {
                            Log.w(TAG, "AudioRecord disappeared mid-take")
                            break
                        }

                        val readCount = try {
                            record.read(buffer, 0, buffer.size)
                        } catch (t: Throwable) {
                            Log.w(TAG, "AudioRecord.read threw", t)
                            break
                        }

                        if (readCount < 0) {
                            Log.w(TAG, "AudioRecord.read failed with code $readCount")
                            break
                        }
                        if (readCount == 0) {
                            // No data ready: yield instead of spinning at 100% CPU.
                            delay(EMPTY_READ_RETRY_MS)
                            continue
                        }

                        val now = System.currentTimeMillis()
                        val elapsed = accumulatedDurationMs + (now - segmentStartTime)
                        if (elapsed >= AudioConfig.MAX_DURATION_MS) {
                            accumulatedDurationMs = AudioConfig.MAX_DURATION_MS
                            _durationMs.value = accumulatedDurationMs
                            _limitReached.value = true
                            _isRecording.value = false
                            hitLimit = true
                            break
                        }

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
                        pcmOut.write(byteBuffer, 0, readCount * AudioConfig.BYTES_PER_SAMPLE)

                        if (now - lastUiUpdate >= UI_UPDATE_INTERVAL_MS) {
                            val rms = sqrt(sumSquares / readCount)
                            val normalized = (rms / RMS_FULL_SCALE).coerceIn(0.0, 1.0).toFloat()
                            _amplitude.value = normalized
                            synchronized(takeLevels) { takeLevels.add(normalized) }
                            synchronized(levelWindow) {
                                levelWindow.addLast(normalized)
                                while (levelWindow.size > AudioConfig.LEVELS_PER_TAKE) {
                                    levelWindow.removeFirst()
                                }
                                _levels.value = levelWindow.toList()
                            }
                            _durationMs.value = minOf(
                                accumulatedDurationMs + (now - segmentStartTime),
                                AudioConfig.MAX_DURATION_MS
                            )
                            lastUiUpdate = now
                        }
                    }
                } catch (t: Throwable) {
                    // Never let the writer take the process down (e.g. disk full mid-take).
                    Log.w(TAG, "Recording writer failed", t)
                    _isRecording.value = false
                } finally {
                    try {
                        pcmOut.flush()
                        pcmOut.close()
                    } catch (t: Throwable) {
                        Log.w(TAG, "Closing PCM writer failed", t)
                    }
                    if (hitLimit) {
                        // The writer hit the cap on its own; stop the microphone here so capture
                        // really ends before the UI asks for the file.
                        runCatching { audioRecord?.stop() }
                            .onFailure { Log.w(TAG, "AudioRecord.stop failed at the duration cap", it) }
                    }
                }
            }
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "startRecording failed", t)
            cleanup()
            return false
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        val now = System.currentTimeMillis()
        accumulatedDurationMs += (now - segmentStartTime)
        segmentStartTime = now
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

    /**
     * Stops capture, waits for the writer coroutine to flush and close, then writes the WAV on
     * [Dispatchers.IO]. This is the only place PCM becomes WAV.
     */
    suspend fun stopRecordingAsync(): File? = stopMutex.withLock {
        val pcm = currentPcmFile
        val wav = currentWavFile
        if (pcm == null || wav == null) return@withLock null

        finishTakeTiming()
        _isRecording.value = false
        _isPaused.value = false

        runCatching { audioRecord?.stop() }
            .onFailure { Log.w(TAG, "AudioRecord.stop failed", it) }
        runCatching { audioRecord?.release() }
            .onFailure { Log.w(TAG, "AudioRecord.release failed", it) }
        audioRecord = null

        val job = recordingJob
        recordingJob = null
        // Join instead of cancel: the writer's finally must flush and close the PCM first.
        job?.join()
        _amplitude.value = 0f

        currentPcmFile = null
        currentWavFile = null

        if (!pcm.exists() || pcm.length() <= 0L) {
            Log.w(TAG, "Take has no PCM data to convert: ${pcm.name}")
            runCatching { pcm.delete() }
            runCatching { wav.delete() }
            return@withLock null
        }

        val converted = withContext(Dispatchers.IO) {
            try {
                WavAudioHelper.convertPcmToWav(
                    pcmFile = pcm,
                    wavFile = wav,
                    sampleRate = activeSampleRate,
                    channels = AudioConfig.CHANNELS.toShort(),
                    bitsPerSample = AudioConfig.BITS_PER_SAMPLE.toShort()
                )
                pcm.delete()
                true
            } catch (t: Throwable) {
                Log.w(TAG, "PCM to WAV conversion failed", t)
                false
            }
        }

        if (!converted || !wav.exists() || wav.length() < AudioConfig.WAV_HEADER_BYTES) {
            runCatching { wav.delete() }
            runCatching { pcm.delete() }
            return@withLock null
        }
        wav
    }

    /** Blocking wrapper kept so existing call sites compile. */
    fun stopRecording(): File? = runBlocking { stopRecordingAsync() }

    fun cancelRecording() {
        _isRecording.value = false
        _isPaused.value = false
        _limitReached.value = false
        accumulatedDurationMs = 0L

        runCatching { audioRecord?.stop() }
            .onFailure { Log.w(TAG, "AudioRecord.stop failed during cancel", it) }
        runCatching { audioRecord?.release() }
            .onFailure { Log.w(TAG, "AudioRecord.release failed during cancel", it) }
        audioRecord = null

        val job = recordingJob
        recordingJob = null
        _amplitude.value = 0f

        runBlocking {
            // Let the writer's finally flush and close before we delete the files under it.
            runCatching { job?.join() }
                .onFailure { Log.w(TAG, "Waiting for the writer to finish failed", it) }
            withContext(Dispatchers.IO) { cleanup() }
        }
    }

    private fun finishTakeTiming() {
        if (_isRecording.value && !_isPaused.value) {
            accumulatedDurationMs += (System.currentTimeMillis() - segmentStartTime)
        }
        segmentStartTime = System.currentTimeMillis()
        _durationMs.value = accumulatedDurationMs.coerceAtMost(AudioConfig.MAX_DURATION_MS)
    }

    private fun cleanup() {
        currentPcmFile?.delete()
        currentPcmFile = null
        currentWavFile?.delete()
        currentWavFile = null
    }
}
