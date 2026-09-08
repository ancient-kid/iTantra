package com.itantra.stt.tts

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.itantra.app.models.Phonemizer
import com.itantra.stt.model.Language
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Primary Offline TTS Engine using Piper / VITS ONNX neural vocoder.
 */
class PiperVitsEngine(
    private val context: Context,
    private val language: Language,
    private val modelPath: String = "tts/piper/en/model.onnx",
    private val tokensPath: String = "tts/piper/en/tokens.txt",
    private val useAssets: Boolean = true,
    private val phonemizer: Phonemizer = Phonemizer.ESPEAK,
    private val voiceName: String = "Piper/VITS"
) : TtsEngine {

    companion object {
        private const val TAG = "PiperVitsEngine"
    }

    private var tts: OfflineTts? = null
    private var loadTimeMs: Long = 0L

    @Volatile
    private var loaded = false

    override suspend fun load() = withContext(Dispatchers.Default) {
        if (loaded && tts != null) return@withContext

        val startTime = SystemClock.elapsedRealtime()
        try {
            Log.i(TAG, "Loading Piper/VITS TTS for ${language.displayName} from assets: $modelPath...")

            // Piper and mimic3 voices phonemize through espeak-ng and need its
            // data directory. MMS voices are character-based: handing them the
            // espeak path makes them mispronounce everything, so it stays empty.
            val dataDirPath = if (phonemizer == Phonemizer.ESPEAK) {
                EspeakNgAssetHelper.getEspeakNgDataPath(context)
            } else {
                ""
            }

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelPath,
                tokens = tokensPath,
                dataDir = dataDirPath,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )

            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            val ttsConfig = OfflineTtsConfig(
                model = modelConfig,
                maxNumSentences = 2,
                silenceScale = 0.2f
            )

            tts = OfflineTts(if (useAssets) context.assets else null, ttsConfig)
            loaded = true
            loadTimeMs = SystemClock.elapsedRealtime() - startTime
            Log.i(TAG, "Loaded Piper/VITS TTS in ${loadTimeMs}ms (Sample rate: ${tts?.sampleRate()} Hz)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Piper/VITS TTS", e)
            unload()
            throw e
        }
    }

    override suspend fun synthesize(text: String): TtsResult = withContext(Dispatchers.Default) {
        val currentTts = tts ?: throw IllegalStateException("Piper/VITS TTS model is not loaded")
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext TtsResult(
                audio = ByteArray(0),
                synthesisTimeMs = 0L,
                audioDurationMs = 0L,
                modelLoadTimeMs = loadTimeMs
            )
        }

        val startTime = SystemClock.elapsedRealtime()
        val generatedAudio = currentTts.generate(cleanText, 0, 1.0f)
        val synthesisTimeMs = SystemClock.elapsedRealtime() - startTime

        val samples = generatedAudio.samples
        val sampleRate = generatedAudio.sampleRate
        val audioDurationMs = if (sampleRate > 0) (samples.size * 1000L) / sampleRate else 0L

        // Convert float samples [-1.0f, 1.0f] to 16-bit Little-Endian PCM byte array
        val pcmBytes = ByteArray(samples.size * 2)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val s = (clamped * 32767.0f).toInt().toShort()
            buffer.putShort(s)
        }

        val rtf = if (audioDurationMs > 0) synthesisTimeMs.toFloat() / audioDurationMs else 0.0f
        Log.i(TAG, "Synthesized '${cleanText.take(30)}' (${samples.size} samples, ${synthesisTimeMs}ms, RTF=${String.format("%.2f", rtf)})")

        TtsResult(
            audio = pcmBytes,
            synthesisTimeMs = synthesisTimeMs,
            audioDurationMs = audioDurationMs,
            sampleRate = sampleRate,
            modelLoadTimeMs = loadTimeMs
        )
    }

    override fun unload() {
        try {
            tts?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Piper/VITS TTS engine", e)
        } finally {
            tts = null
            loaded = false
        }
    }

    override fun isLoaded(): Boolean = loaded

    override fun engineName(): String = voiceName

    override fun language(): Language = language
}
