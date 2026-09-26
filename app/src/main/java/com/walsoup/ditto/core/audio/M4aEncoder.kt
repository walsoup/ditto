package com.walsoup.ditto.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

object M4aEncoder {

    /**
     * Encodes a 16-bit PCM (or WAV) file into an M4A (AAC) file using Android's native MediaCodec and MediaMuxer.
     */
    fun encodePcmToM4a(
        inputFile: File,
        outputFile: File,
        sampleRate: Int = 44100,
        channelCount: Int = 1,
        bitRate: Int = 128000
    ): Boolean {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputStream: FileInputStream? = null

        return try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            inputStream = FileInputStream(inputFile)
            // Skip 44-byte WAV header if present
            val probe = ByteArray(4)
            inputStream.read(probe)
            if (String(probe) == "RIFF") {
                inputStream.skip(40) // Remaining 40 bytes of 44-byte WAV header
            } else {
                inputStream.close()
                inputStream = FileInputStream(inputFile)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val inputBuffer = ByteArray(4096)
            var isInputDone = false
            var isOutputDone = false
            var presentationTimeUs = 0L
            var trackIndex = -1
            var muxerStarted = false

            while (!isOutputDone) {
                if (!isInputDone) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000L)
                    if (inputBufferIndex >= 0) {
                        val codecBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                        codecBuffer.clear()
                        val bytesRead = inputStream.read(inputBuffer)
                        if (bytesRead < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputDone = true
                        } else {
                            codecBuffer.put(inputBuffer, 0, bytesRead)
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                bytesRead,
                                presentationTimeUs,
                                0
                            )
                            // 2 bytes per sample, 1 channel
                            val sampleCount = bytesRead / (2 * channelCount)
                            presentationTimeUs += (sampleCount * 1_000_000L) / sampleRate
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputDone = true
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            false
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try {
                if (muxer != null) {
                    muxer.stop()
                    muxer.release()
                }
            } catch (_: Exception) {}
        }
    }
}
