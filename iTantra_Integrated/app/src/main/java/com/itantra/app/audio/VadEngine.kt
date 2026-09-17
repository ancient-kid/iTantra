package com.itantra.app.audio

import android.content.Context
import android.util.Log
import com.itantra.stt.audio.AudioRecorder
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Thin edge-detector on top of sherpa-onnx's bundled Silero VAD.
 *
 * This never decides *whether* to send a message - it only decides *when* an
 * utterance that the user has already started (by holding the push-to-talk
 * button) sounds finished, so [com.itantra.app.pipeline.VoicePipeline] can stop
 * recording a beat sooner than waiting for the manual release. If it never
 * fires, or fires wrong, the manual release is still the source of truth: this
 * class only ever produces an *earlier* stop, never a different outcome.
 */
class VadEngine(context: Context) {

    companion object {
        private const val TAG = "VadEngine"

        private const val ASSET_PATH = "vad/silero_vad.onnx"

        /** Model's own speech/silence probability cut-off. */
        private const val THRESHOLD = 0.5f

        /** Ignore blips shorter than this - coughs, taps, wind gusts. */
        private const val MIN_SPEECH_DURATION_SEC = 0.25f

        /** How long a pause has to last before we call the utterance finished. */
        private const val MIN_SILENCE_DURATION_SEC = 0.7f

        /** Samples per acceptWaveform() call; the model was trained on this window. */
        private const val WINDOW_SIZE = 512

        /** Safety cap so the model itself force-ends a runaway utterance. */
        private const val MAX_SPEECH_DURATION_SEC = 20f
    }

    private val vad: Vad? = try {
        Vad(
            context.assets,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = ASSET_PATH,
                    threshold = THRESHOLD,
                    minSilenceDuration = MIN_SILENCE_DURATION_SEC,
                    minSpeechDuration = MIN_SPEECH_DURATION_SEC,
                    windowSize = WINDOW_SIZE,
                    maxSpeechDuration = MAX_SPEECH_DURATION_SEC
                ),
                sampleRate = AudioRecorder.SAMPLE_RATE,
                numThreads = 1
            )
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load Silero VAD model - auto-stop disabled for this session", e)
        null
    }

    /** True once acceptWaveform has reported speech at least once since [reset]. */
    private var sawSpeech = false

    /** Samples buffered until they reach [WINDOW_SIZE], since AudioRecorder reads 1024-sample chunks. */
    private val pending = ArrayDeque<Short>(WINDOW_SIZE * 2)

    val isAvailable: Boolean get() = vad != null

    fun reset() {
        sawSpeech = false
        pending.clear()
        vad?.reset()
    }

    fun release() {
        vad?.release()
    }

    /**
     * Feeds one chunk of 16 kHz mono PCM16 samples, as delivered by
     * [AudioRecorder]'s recording loop.
     *
     * @return true exactly once per utterance: on the transition from having
     * heard speech to a sustained silence long enough to call it finished.
     */
    fun onChunk(samples: ShortArray): Boolean {
        val engine = vad ?: return false

        pending.addAll(samples.asIterable())
        var finished = false

        while (pending.size >= WINDOW_SIZE) {
            val window = FloatArray(WINDOW_SIZE) { i -> pending[i].toFloat() / 32768.0f }
            repeat(WINDOW_SIZE) { pending.removeFirst() }

            engine.acceptWaveform(window)
            val speaking = engine.isSpeechDetected()

            if (speaking) {
                sawSpeech = true
            } else if (sawSpeech) {
                // The model's own minSilenceDuration has already been satisfied
                // internally before isSpeechDetected() flips back to false, so
                // this edge is the "utterance finished" signal.
                finished = true
                sawSpeech = false
            }

            // Drain any segments the native side finalized, so internal state
            // (front()/pop()) doesn't grow unbounded - their audio is unused
            // here since AudioRecorder already holds the authoritative buffer.
            while (!engine.empty()) {
                engine.pop()
            }
        }

        return finished
    }
}
