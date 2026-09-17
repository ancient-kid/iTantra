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

        /**
         * Speech detection confidence cut-off.
         * Tuned to 0.35f for mobile microphones where unboosted speech amplitude
         * can be significantly lower than studio recordings.
         */
        private const val THRESHOLD = 0.30f

        /**
         * Minimum speech duration (150ms) to filter out quick transient noises
         * (taps, clicks) while promptly acknowledging short words (e.g. "हां", "Help").
         */
        private const val MIN_SPEECH_DURATION_SEC = 0.15f

        /** How long a pause must last before the utterance is considered finished. */
        private const val MIN_SILENCE_DURATION_SEC = 0.60f

        /** Samples per acceptWaveform() call; Silero VAD v4 window is 512 samples at 16 kHz. */
        private const val WINDOW_SIZE = 512

        /** Safety cap so the model itself force-ends a runaway utterance. */
        private const val MAX_SPEECH_DURATION_SEC = 20f
    }

    private val vad: Vad? = try {
        val instance = Vad(
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
        Log.i(TAG, "Silero VAD loaded successfully (threshold=$THRESHOLD, minSpeech=${MIN_SPEECH_DURATION_SEC}s, minSilence=${MIN_SILENCE_DURATION_SEC}s)")
        instance
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load Silero VAD model - auto-stop disabled for this session", e)
        null
    }

    /** True once acceptWaveform has reported speech at least once since [reset]. */
    private var sawSpeech = false

    /** Samples buffered until they reach [WINDOW_SIZE], since AudioRecorder reads 1024-sample chunks. */
    private val pending = ArrayDeque<Short>(WINDOW_SIZE * 2)

    val isAvailable: Boolean get() = vad != null

    /** Invoked on the first transition to speech in an utterance. */
    var onSpeechDetected: (() -> Unit)? = null

    @Synchronized
    fun reset() {
        sawSpeech = false
        pending.clear()
        vad?.reset()
        Log.d(TAG, "VAD state reset")
    }

    @Synchronized
    fun release() {
        pending.clear()
        vad?.release()
    }

    /**
     * Feeds one chunk of 16 kHz mono PCM16 samples, as delivered by
     * [AudioRecorder]'s recording loop.
     *
     * @return true exactly once per utterance: when an utterance has finished,
     * detected either via sherpa-onnx's native segment queue or the speech-to-silence edge.
     */
    @Synchronized
    fun onChunk(samples: ShortArray): Boolean {
        val engine = vad ?: return false

        pending.addAll(samples.asIterable())
        var finished = false

        while (pending.size >= WINDOW_SIZE) {
            // Apply a modest 1.25x gain boost for VAD normalization so phone mics cleanly cross threshold
            val window = FloatArray(WINDOW_SIZE) { i ->
                val norm = (pending[i].toFloat() / 32768.0f) * 1.25f
                norm.coerceIn(-1.0f, 1.0f)
            }
            repeat(WINDOW_SIZE) { pending.removeFirst() }

            engine.acceptWaveform(window)

            val hasCompletedSegment = !engine.empty()
            val speaking = engine.isSpeechDetected()

            if (speaking && !sawSpeech) {
                sawSpeech = true
                Log.d(TAG, "VAD: Speech started")
                onSpeechDetected?.invoke()
            }

            if (hasCompletedSegment) {
                // Native sherpa-onnx segment queue finalized a complete speech segment
                finished = true
                sawSpeech = false
                Log.i(TAG, "VAD: Utterance finalized via segment queue (auto-stop triggered)")
                while (!engine.empty()) {
                    engine.pop()
                }
            } else if (sawSpeech && !speaking) {
                // Speech finished falling edge after minSilenceDuration
                finished = true
                sawSpeech = false
                Log.i(TAG, "VAD: Utterance finished via falling edge (auto-stop triggered)")
                while (!engine.empty()) {
                    engine.pop()
                }
            }
        }

        return finished
    }
}
