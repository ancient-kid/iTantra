package com.itantra.stt.stt

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.itantra.stt.model.Language
import com.itantra.stt.model.ModelInfo
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated STT Engine for Indian Languages using IndicConformer INT8 ONNX.
 */
class IndicConformerEngine(
    private val context: Context,
    private val modelInfo: ModelInfo
) : SttEngine {

    companion object {
        private const val TAG = "IndicConformerEngine"
    }

    private var recognizer: OfflineRecognizer? = null
    private var loadTimeMs: Long = 0L

    @Volatile
    private var loaded = false

    override suspend fun load() = withContext(Dispatchers.Default) {
        if (loaded && recognizer != null) return@withContext

        val startTime = SystemClock.elapsedRealtime()
        try {
            Log.i(TAG, "Loading IndicConformer (${modelInfo.language.displayName}) from ${modelInfo.modelPath}...")

            val featConfig = FeatureConfig(
                sampleRate = modelInfo.sampleRate,
                featureDim = modelInfo.featureDim
            )

            val nemoConfig = OfflineNemoEncDecCtcModelConfig(
                model = modelInfo.modelPath
            )

            val modelConfig = OfflineModelConfig(
                tokens = modelInfo.tokensPath,
                nemo = nemoConfig,
                numThreads = 2,
                provider = "cpu",
                debug = false
            )

            val config = OfflineRecognizerConfig(
                featConfig = featConfig,
                modelConfig = modelConfig
            )

            recognizer = OfflineRecognizer(context.assets, config)
            loaded = true
            loadTimeMs = SystemClock.elapsedRealtime() - startTime
            Log.i(TAG, "Loaded IndicConformer (${modelInfo.language.displayName}) in ${loadTimeMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load IndicConformer for ${modelInfo.language.displayName}", e)
            unload()
            throw e
        }
    }

    override suspend fun transcribe(audio: ShortArray, sampleRate: Int): SttResult = withContext(Dispatchers.Default) {
        val currentRecognizer = recognizer ?: throw IllegalStateException("IndicConformer model is not loaded")
        if (audio.isEmpty()) {
            return@withContext SttResult(
                text = "",
                inferenceTimeMs = 0L,
                audioDurationMs = 0L,
                modelLoadTimeMs = loadTimeMs
            )
        }

        val audioDurationMs = (audio.size * 1000L) / sampleRate

        // Convert PCM 16-bit to float [-1.0, 1.0]
        val floatSamples = FloatArray(audio.size) { i ->
            audio[i].toFloat() / 32768.0f
        }

        val startTime = SystemClock.elapsedRealtime()
        val stream = currentRecognizer.createStream()

        try {
            stream.acceptWaveform(floatSamples, sampleRate)
            currentRecognizer.decode(stream)
            val result = currentRecognizer.getResult(stream)
            val inferenceTimeMs = SystemClock.elapsedRealtime() - startTime
            val text = result.text.trim()

            Log.i(TAG, "Indic [${modelInfo.language.displayName}] Transcribed: '$text' (${inferenceTimeMs}ms)")

            SttResult(
                text = text,
                inferenceTimeMs = inferenceTimeMs,
                audioDurationMs = audioDurationMs,
                modelLoadTimeMs = loadTimeMs
            )
        } finally {
            stream.release()
        }
    }

    override fun unload() {
        try {
            recognizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing IndicConformerEngine", e)
        } finally {
            recognizer = null
            loaded = false
        }
    }

    override fun isLoaded(): Boolean = loaded

    override fun modelName(): String = modelInfo.modelName

    override fun language(): Language = modelInfo.language
}
