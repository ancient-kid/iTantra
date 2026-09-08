package com.itantra.stt.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Robust Speech Audio Player utilizing Android's MediaPlayer and standard WAV encapsulation.
 * Compatible across all Android OEMs (including ColorOS / Dirac audio processors).
 */
class AudioPlayer(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private val wavFile: File by lazy { File(context.cacheDir, "tts_synthesized.wav") }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encapsulates raw PCM 16-bit mono audio into a standard RIFF/WAVE container
     * and streams it through the system audio pipeline via MediaPlayer.
     */
    fun play(
        pcmBytes: ByteArray,
        sampleRate: Int = 22050,
        coroutineScope: CoroutineScope,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        if (pcmBytes.isEmpty()) {
            Log.w(TAG, "Cannot play empty PCM audio buffer")
            return false
        }

        stop()

        return try {
            // Write WAV file with 44-byte standard header
            writeWavFile(wavFile, pcmBytes, sampleRate, channels = 1)

            val player = MediaPlayer()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            player.setAudioAttributes(attributes)
            player.setDataSource(wavFile.absolutePath)
            player.setVolume(1.0f, 1.0f)

            player.setOnCompletionListener {
                Log.d(TAG, "Playback completed successfully")
                stop()
                coroutineScope.launch(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                stop()
                coroutineScope.launch(Dispatchers.Main) {
                    onComplete?.invoke()
                }
                true
            }

            player.prepare()
            player.start()
            mediaPlayer = player

            val durationSec = player.duration / 1000f
            Log.i(TAG, "Playing WAV audio: ${wavFile.length()} bytes, sampleRate: $sampleRate Hz, duration: ${durationSec}s")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
            stop()
            false
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    private fun writeWavFile(file: File, pcmBytes: ByteArray, sampleRate: Int, channels: Int) {
        val totalAudioLen = pcmBytes.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 2).toLong()

        FileOutputStream(file).use { out ->
            val header = ByteArray(44)
            // RIFF chunk descriptor
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            // "fmt " sub-chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // Subchunk1Size for PCM
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // AudioFormat 1 = PCM
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * 2).toByte() // BlockAlign
            header[33] = 0
            header[34] = 16 // BitsPerSample
            header[35] = 0

            // "data" sub-chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            out.write(header)
            out.write(pcmBytes)
            out.flush()
        }
    }
}
