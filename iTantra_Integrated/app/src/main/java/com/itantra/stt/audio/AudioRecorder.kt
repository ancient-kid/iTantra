package com.itantra.stt.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Microphone Audio Recorder
 * Captures 16 kHz, Mono, 16-bit PCM audio via AudioRecord.
 */
class AudioRecorder {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    @Volatile
    private var isRecording = false

    private val pcmOutputStream = ByteArrayOutputStream()
    private val bufferLock = Any()

    fun isRecording(): Boolean = isRecording

    @SuppressLint("MissingPermission")
    fun startRecording(coroutineScope: CoroutineScope, onChunk: ((ShortArray) -> Unit)? = null): Boolean {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid AudioRecord buffer size")
            return false
        }

        val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            synchronized(bufferLock) {
                pcmOutputStream.reset()
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(1024)
                while (isActive && isRecording) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readCount > 0) {
                        val byteBuffer = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until readCount) {
                            byteBuffer.putShort(buffer[i])
                        }
                        synchronized(bufferLock) {
                            pcmOutputStream.write(byteBuffer.array())
                        }
                        onChunk?.invoke(buffer.copyOf(readCount))
                    }
                }
            }

            Log.i(TAG, "Recording started at $SAMPLE_RATE Hz mono PCM 16-bit")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording(): ShortArray {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }

        val bytes: ByteArray
        synchronized(bufferLock) {
            bytes = pcmOutputStream.toByteArray()
            pcmOutputStream.reset()
        }

        val shortCount = bytes.size / 2
        val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(shortCount)
        shortBuffer.get(samples)

        Log.i(TAG, "Recording stopped. Captured ${samples.size} PCM samples (${samples.size.toFloat() / SAMPLE_RATE} s)")
        return samples
    }
}
