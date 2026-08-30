package com.itantra.stt.model

import android.content.Context
import android.util.Log
import com.itantra.stt.tts.IndicTtsEngine
import com.itantra.stt.tts.PiperVitsEngine
import com.itantra.stt.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages TTS engine resolution and single-model memory lifecycle.
 */
class TtsManager(private val context: Context) {

    companion object {
        private const val TAG = "TtsManager"
    }

    private val mutex = Mutex()
    private var currentEngine: TtsEngine? = null
    private var currentLanguage: Language? = null

    fun getCurrentEngine(): TtsEngine? = currentEngine
    fun getCurrentLanguage(): Language? = currentLanguage

    fun isLoaded(): Boolean = currentEngine?.isLoaded() == true

    /**
     * Determines if a primary Piper/VITS voice is available for the given language.
     */
    fun isPiperAvailable(language: Language): Boolean {
        return when (language) {
            Language.EN, Language.HI, Language.ML, Language.TE,
            Language.MR, Language.BN, Language.TA, Language.GU -> true
            else -> false
        }
    }

    /**
     * Switches and loads the optimal TTS engine for the requested language.
     * Enforces strict single-model RAM residency.
     */
    suspend fun loadLanguage(language: Language): Boolean = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (currentLanguage == language && currentEngine?.isLoaded() == true) {
                Log.d(TAG, "TTS for $language is already loaded")
                return@withContext true
            }

            // 1. Cleanly unload any existing active model to free RAM
            unloadInternal()

            // 2. Select primary Piper or fallback Indic-TTS based on policy
            try {
                val newEngine: TtsEngine = if (isPiperAvailable(language)) {
                    val modelPath = when (language) {
                        Language.EN -> "tts/piper/en/model.onnx"
                        Language.HI -> "tts/piper/hi/model.onnx"
                        Language.ML -> "tts/piper/ml/model.onnx"
                        Language.TE -> "tts/piper/te/model.onnx"
                        Language.MR -> "tts/piper/mr/model.onnx"
                        Language.BN -> "tts/piper/bn/model.onnx"
                        Language.TA -> "tts/piper/ta/model.onnx"
                        Language.GU -> "tts/piper/gu/model.onnx"
                        else -> "tts/piper/en/model.onnx"
                    }
                    val tokensPath = when (language) {
                        Language.EN -> "tts/piper/en/tokens.txt"
                        Language.HI -> "tts/piper/hi/tokens.txt"
                        Language.ML -> "tts/piper/ml/tokens.txt"
                        Language.TE -> "tts/piper/te/tokens.txt"
                        Language.MR -> "tts/piper/mr/tokens.txt"
                        Language.BN -> "tts/piper/bn/tokens.txt"
                        Language.TA -> "tts/piper/ta/tokens.txt"
                        Language.GU -> "tts/piper/gu/tokens.txt"
                        else -> "tts/piper/en/tokens.txt"
                    }

                    PiperVitsEngine(
                        context = context,
                        language = language,
                        modelPath = modelPath,
                        tokensPath = tokensPath
                    )
                } else {
                    IndicTtsEngine(
                        context = context,
                        language = language
                    )
                }

                newEngine.load()
                currentEngine = newEngine
                currentLanguage = language
                Log.i(TAG, "TTS successfully activated for $language (${newEngine.engineName()})")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load TTS for $language", e)
                unloadInternal()
                false
            }
        }
    }

    suspend fun unloadCurrentModel() = mutex.withLock {
        unloadInternal()
    }

    private fun unloadInternal() {
        try {
            currentEngine?.unload()
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading TTS engine", e)
        } finally {
            currentEngine = null
            currentLanguage = null
            System.gc()
        }
    }
}
