package com.walsoup.ditto.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object M4aEncoder {

    private const val TAG = "Ditto"

    /** How many bytes of input must be consumed before another progress callback fires. */
    private const val PROGRESS_STEP_BYTES = 32 * 1024L

    /** ~2 s of idle polling (10 ms each) before we give up instead of hanging forever. */
    private const val MAX_IDLE_PASSES = 200
    private const val IDLE_SLEEP_MS = 10L

    /** Upper bound on how many buffers a single drain pass may move. */
    private const val MAX_PASS_SPINS = 256

    /** How many empty input reads before we treat the source as exhausted. */
    private const val MAX_STALLED_READS = 64

    private const val DEFAULT_PCM_OFFSET = 44L

    /** Where the PCM samples live inside the input file. */
    private data class PcmRegion(val offset: Long, val length: Long)

    /**
     * Encodes a 16-bit PCM (raw or WAV) file into an M4A (AAC) file using Android's native
     * MediaCodec and MediaMuxer.
     *
     * The pump drains every available codec buffer on each pass instead of feeding one fixed chunk
     * per loop, which used to cap throughput near realtime on long takes.
     *
     * @return true only when the muxer finalised a non-empty file. Never throws, never blocks
     *         indefinitely (aborts after ~2 s of no codec progress).
     */
    fun encodePcmToM4a(
        inputFile: File,
        outputFile: File,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        channelCount: Int = AudioConfig.CHANNELS,
        bitRate: Int = AudioConfig.AAC_BIT_RATE,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val channels = channelCount.coerceAtLeast(1)
        val blockAlign = (AudioConfig.BYTES_PER_SAMPLE * channels).coerceAtLeast(2)

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputStream: FileInputStream? = null
        var trackIndex = -1
        var muxerStarted = false
        var muxerStopped = false
        var reachedEndOfStream = false

        try {
            runCatching { outputFile.delete() }

            // Never encode an already-encoded container: resolvePcmRegion would treat its
            // bytes as headerless PCM and produce a corrupted double-encoded file.
            if (isEncodedContainerExtension(inputFile.extension)) {
                Log.w(TAG, "AAC encode refused: ${inputFile.name} is an encoded container, not PCM/WAV")
                return false
            }

            val region = if (inputFile.exists()) resolvePcmRegion(inputFile) else PcmRegion(0L, 0L)
            if (region.length <= 0L) {
                Log.w(TAG, "AAC encode skipped: ${inputFile.name} is missing or has no PCM data")
                return false
            }

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val outputMuxer =
                MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = outputMuxer

            val stream = FileInputStream(inputFile)
            inputStream = stream
            val channel: FileChannel = stream.channel
            channel.position(region.offset)

            val info = MediaCodec.BufferInfo()
            val carry = ByteArray(blockAlign)
            var carryLength = 0
            var bytesRead = 0L
            var presentationTimeUs = 0L
            var endOfFile = false
            var eosQueued = false
            var progressFloor = 0L
            var idlePasses = 0
            var stalledReads = 0

            passes@ while (!reachedEndOfStream) {
                var madeProgress = false

                // 1. Drain every input buffer the codec will hand us.
                if (!eosQueued) {
                    var spins = 0
                    while (spins++ < MAX_PASS_SPINS) {
                        val inputIndex = encoder.dequeueInputBuffer(0L)
                        if (inputIndex < 0) break
                        val codecBuffer = encoder.getInputBuffer(inputIndex)
                        if (codecBuffer == null) {
                            Log.w(TAG, "Codec returned a null input buffer")
                            break
                        }
                        madeProgress = true
                        codecBuffer.clear()

                        var queuedBytes = 0
                        if (carryLength > 0) {
                            codecBuffer.put(carry, 0, carryLength)
                            queuedBytes = carryLength
                            carryLength = 0
                        }

                        val fileRemaining = region.length - bytesRead
                        if (fileRemaining > 0L) {
                            val space = codecBuffer.remaining()
                            var want = minOf(space.toLong(), fileRemaining).toInt()
                            want -= want % blockAlign
                            if (want > 0) {
                                val previousLimit = codecBuffer.limit()
                                codecBuffer.limit(codecBuffer.position() + want)
                                val read = try {
                                    channel.read(codecBuffer)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Reading PCM failed at $bytesRead bytes", t)
                                    -1
                                } finally {
                                    codecBuffer.limit(previousLimit)
                                }
                                if (read > 0) {
                                    bytesRead += read.toLong()
                                    queuedBytes += read
                                } else if (read < 0) {
                                    endOfFile = true
                                }
                            }
                        }

                        // Keep whole frames only; stash a partial frame for the next buffer.
                        val leftover = queuedBytes % blockAlign
                        if (leftover != 0 && queuedBytes >= leftover) {
                            codecBuffer.position(queuedBytes - leftover)
                            codecBuffer.get(carry, 0, leftover)
                            carryLength = leftover
                            queuedBytes -= leftover
                        }

                        if (queuedBytes > 0) {
                            stalledReads = 0
                            encoder.queueInputBuffer(inputIndex, 0, queuedBytes, presentationTimeUs, 0)
                            val samples = queuedBytes / blockAlign
                            presentationTimeUs += samples * 1_000_000L / sampleRate
                        } else if (endOfFile || bytesRead >= region.length) {
                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            eosQueued = true
                            break
                        } else {
                            stalledReads++
                            if (stalledReads >= MAX_STALLED_READS) {
                                // The channel keeps handing us nothing: treat it as EOF rather
                                // than spinning on empty buffers forever.
                                Log.w(TAG, "PCM source stopped yielding data after $bytesRead bytes")
                                encoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    presentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                eosQueued = true
                                break
                            }
                            // Nothing readable yet: hand the buffer straight back rather than
                            // leaking the index.
                            encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
                        }
                    }
                }

                // 2. Drain every output buffer the codec will hand us.
                var outSpins = 0
                while (outSpins++ < MAX_PASS_SPINS) {
                    val outputIndex = try {
                        encoder.dequeueOutputBuffer(info, 0L)
                    } catch (t: Throwable) {
                        Log.w(TAG, "dequeueOutputBuffer failed", t)
                        break
                    }

                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) continue

                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            trackIndex = outputMuxer.addTrack(encoder.outputFormat)
                            outputMuxer.start()
                            muxerStarted = true
                        }
                        madeProgress = true
                        continue
                    }

                    if (outputIndex < 0) break

                    madeProgress = true
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0
                    }
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0 && muxerStarted) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        outputMuxer.writeSampleData(trackIndex, outputBuffer, info)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        reachedEndOfStream = true
                        break
                    }
                }

                // 3. Progress, throttled to roughly one call per 32 KB.
                if (onProgress != null) {
                    val consumed = bytesRead - progressFloor
                    if (consumed >= PROGRESS_STEP_BYTES || reachedEndOfStream) {
                        progressFloor = bytesRead
                        val fraction =
                            (bytesRead.toFloat() / region.length.toFloat()).coerceIn(0f, 1f)
                        runCatching { onProgress.invoke(fraction) }
                    }
                }

                // 4. Stall guard.
                if (madeProgress) {
                    idlePasses = 0
                } else {
                    idlePasses++
                    if (idlePasses >= MAX_IDLE_PASSES) {
                        Log.w(TAG, "AAC encode stalled on ${inputFile.name} after $bytesRead bytes")
                        break@passes
                    }
                    runCatching { Thread.sleep(IDLE_SLEEP_MS) }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AAC encode failed for ${inputFile.name}", t)
            reachedEndOfStream = false
        } finally {
            // Drain whatever the codec still holds, then shut down in dependency order.
            val activeCodec = codec
            val activeMuxer = muxer
            if (activeCodec != null) {
                runCatching {
                    val info = MediaCodec.BufferInfo()
                    var spins = 0
                    while (spins++ < MAX_PASS_SPINS) {
                        val outputIndex = activeCodec.dequeueOutputBuffer(info, 0L)
                        if (outputIndex < 0) break
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0
                        }
                        val outputBuffer = activeCodec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0 && muxerStarted) {
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            activeMuxer?.writeSampleData(trackIndex, outputBuffer, info)
                        }
                        activeCodec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }

            if (muxerStarted && activeMuxer != null) {
                runCatching { activeMuxer.stop() }
                    .onSuccess { muxerStopped = true }
                    .onFailure { Log.w(TAG, "Muxer stop failed for ${outputFile.name}", it) }
            }
            runCatching { activeMuxer?.release() }
            runCatching { activeCodec?.stop() }
            runCatching { activeCodec?.release() }
            runCatching { inputStream?.close() }
        }

        val ok = reachedEndOfStream && muxerStopped && outputFile.length() > 0L
        if (!ok) {
            Log.w(TAG, "AAC encode produced no usable output for ${inputFile.name}")
            runCatching { outputFile.delete() }
        }
        return ok
    }

    /**
     * Walks the RIFF header (when present) to find the `data` chunk, falling back to offset 44.
     * Files without RIFF magic are treated as headerless raw PCM.
     */
    private fun resolvePcmRegion(file: File): PcmRegion {
        val totalLength = file.length()
        if (totalLength <= 0L) return PcmRegion(0L, 0L)

        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (totalLength < 12L) {
                    return@use PcmRegion(0L, totalLength)
                }

                val riff = ByteArray(12)
                raf.readFully(riff)
                val isWav = String(riff, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(riff, 8, 4, Charsets.US_ASCII) == "WAVE"
                if (!isWav) {
                    return@use PcmRegion(0L, totalLength)
                }

                var position = 12L
                val chunkHeader = ByteArray(8)
                while (position + 8L <= totalLength) {
                    raf.seek(position)
                    if (raf.read(chunkHeader) < 8) break

                    val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val declaredSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                        .toLong() and 0xFFFF_FFFFL
                    val bodyOffset = position + 8L
                    val available = totalLength - bodyOffset
                    if (available <= 0L) break

                    if (chunkId == "data") {
                        val length = if (declaredSize in 1..available) declaredSize else available
                        return@use PcmRegion(bodyOffset, length)
                    }

                    if (declaredSize <= 0L) break
                    val next = bodyOffset + declaredSize + (declaredSize and 1L)
                    if (next <= position) break
                    position = next
                }

                val fallbackOffset = minOf(DEFAULT_PCM_OFFSET, totalLength)
                PcmRegion(fallbackOffset, totalLength - fallbackOffset)
            }
        }.getOrElse { t ->
            Log.w(TAG, "Could not parse WAV header for ${file.name}; assuming raw PCM", t)
            PcmRegion(0L, totalLength)
        }
    }
}
