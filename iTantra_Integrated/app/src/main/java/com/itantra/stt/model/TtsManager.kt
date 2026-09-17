package com.itantra.stt.model

import android.content.Context
import android.util.Log
import com.itantra.app.models.ModelPack
import com.itantra.app.models.ModelStore
import com.itantra.stt.tts.PiperVitsEngine
import com.itantra.stt.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Resolves a language to its installed voice and manages single-model residency.
 *
 * There is deliberately no synthetic fallback: if a language has no installed
 * voice, loading fails and the caller tells the user to download it. The old
 * behaviour — quietly substituting a generated tone — made a missing voice look
 * like a working one.
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

    /** The voice pack registered for a language, whether or not it is installed. */
    fun packFor(language: Language): ModelPack? =
        ModelStore.ttsPackFor(context, language.code)

    /** True when a real voice for this language is present on the device. */
    fun isVoiceInstalled(language: Language): Boolean {
        val pack = packFor(language) ?: return false
        return ModelStore.isInstalled(context, pack)
    }

    /**
     * Switches to the voice for [language], unloading any previous one first so
     * only a single checkpoint is ever resident.
     */
    suspend fun loadLanguage(language: Language): Boolean = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (currentLanguage == language && currentEngine?.isLoaded() == true) {
                Log.d(TAG, "TTS for $language is already loaded")
                return@withContext true
            }

            val pack = ModelStore.ttsPackFor(context, language.code)
            if (pack == null) {
                Log.w(TAG, "No voice registered for $language")
                return@withContext false
            }

            val source = ModelStore.sourceFor(context, pack)
            if (source == null) {
                Log.w(TAG, "Voice pack ${pack.id} for $language is not installed")
                return@withContext false
            }

            unloadInternal()

            try {
                val modelFile = pack.files.firstOrNull { it.name.endsWith(".onnx") }?.name
                    ?: "model.onnx"

                val engine = PiperVitsEngine(
                    context = context,
                    language = language,
                    modelPath = source.pathFor(modelFile),
                    tokensPath = source.pathFor("tokens.txt"),
                    useAssets = source.usesAssets,
                    phonemizer = pack.phonemizer,
                    voiceName = pack.modelName
                )

                engine.load()
                currentEngine = engine
                currentLanguage = language
                Log.i(TAG, "TTS active for $language (${pack.modelName})")
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
