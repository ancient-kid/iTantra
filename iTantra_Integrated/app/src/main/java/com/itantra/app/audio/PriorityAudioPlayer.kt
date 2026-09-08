package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.itantra.app.protocol.VoicePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Plays synthesized speech with two distinct routes:
 *
 *  - NORMAL: the media stream, ducked and silenced like any other app audio.
 *  - ALERT:  the alarm stream at forced-maximum volume, which is not muted by
 *            ringer-silent mode and is allowed through Do Not Disturb by default.
 *            The previous alarm volume is restored once playback finishes.
 *
 * The alert path is a hard requirement of the problem statement, so it is a
 * first-class mode here rather than a volume tweak on top of media playback.
 */
class PriorityAudioPlayer(private val context: Context) {

    companion object {
        private const val TAG = "PriorityAudioPlayer"
    }

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var restoreAlarmVolume: Int? = null

    private val wavFile: File by lazy { File(context.cacheDir, "itantra_playback.wav") }

    fun isPlaying(): Boolean = try {
        mediaPlayer?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    /**
     * Writes the PCM buffer into a WAV container and plays it on the stream that
     * matches [priority]. Returns false when playback could not be started.
     */
    fun play(
        pcmBytes: ByteArray,
        sampleRate: Int,
        priority: VoicePayload.Priority,
        coroutineScope: CoroutineScope,
        onStarted: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        if (pcmBytes.isEmpty()) {
            Log.w(TAG, "Refusing to play an empty PCM buffer")
            return false
        }

        stop()

        val isAlert = priority == VoicePayload.Priority.ALERT

        return try {
            writeWavFile(wavFile, pcmBytes, sampleRate, channels = 1)

            val attributes = AudioAttributes.Builder()
                .setUsage(
                    if (isAlert) AudioAttributes.USAGE_ALARM
                    else AudioAttributes.USAGE_MEDIA
                )
                .setContentType(
                    if (isAlert) AudioAttributes.CONTENT_TYPE_SONIFICATION
                    else AudioAttributes.CONTENT_TYPE_SPEECH
                )
                .build()

            if (isAlert) {
                forceAlarmVolumeToMax()
                vibrateAlert()
            }
            requestFocus(attributes, isAlert)

            val player = MediaPlayer()
            player.setAudioAttributes(attributes)
            player.setDataSource(wavFile.absolutePath)
            player.setVolume(1.0f, 1.0f)

            player.setOnCompletionListener {
                stop()
                coroutineScope.launch(Dispatchers.Main) { onComplete?.invoke() }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                stop()
                coroutineScope.launch(Dispatchers.Main) { onComplete?.invoke() }
                true
            }

            player.prepare()
            player.start()
            mediaPlayer = player
            onStarted?.invoke()

            Log.i(
                TAG,
                "Playing ${wavFile.length()} bytes at $sampleRate Hz " +
                    "on the ${if (isAlert) "alarm" else "media"} stream"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            stop()
            false
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        } finally {
            mediaPlayer = null
            abandonFocus()
            restoreAlarmVolume()
        }
    }

    // ------------------------------------------------------------------
    // Alert-priority plumbing
    // ------------------------------------------------------------------

    private fun forceAlarmVolumeToMax() {
        try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (current < max) {
                // Remember the user setting so an alert does not permanently
                // change how loud their alarms are.
                restoreAlarmVolume = current
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (e: Exception) {
            // Some OEMs restrict volume changes; playback on the alarm stream
            // still bypasses silent mode, so this is not fatal.
            Log.w(TAG, "Could not raise alarm volume: ${e.message}")
        }
    }

    private fun restoreAlarmVolume() {
        val previous = restoreAlarmVolume ?: return
        restoreAlarmVolume = null
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore alarm volume: ${e.message}")
        }
    }

    private fun vibrateAlert() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            val pattern = longArrayOf(0, 300, 150, 300)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration unavailable: ${e.message}")
        }
    }

    private fun requestFocus(attributes: AudioAttributes, isAlert: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val gain =
                    if (isAlert) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                    else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                val request = AudioFocusRequest.Builder(gain)
                    .setAudioAttributes(attributes)
                    .build()
                audioManager.requestAudioFocus(request)
                focusRequest = request
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed: ${e.message}")
        }
    }

    private fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Abandoning audio focus failed: ${e.message}")
        } finally {
            focusRequest = null
        }
    }

    // ------------------------------------------------------------------
    // WAV container
    // ------------------------------------------------------------------

    private fun writeWavFile(file: File, pcmBytes: ByteArray, sampleRate: Int, channels: Int) {
        val totalAudioLen = pcmBytes.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 2).toLong()

        FileOutputStream(file).use { out ->
            val header = ByteArray(44)
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

            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1
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
            header[32] = (channels * 2).toByte()
            header[33] = 0
            header[34] = 16
            header[35] = 0

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
