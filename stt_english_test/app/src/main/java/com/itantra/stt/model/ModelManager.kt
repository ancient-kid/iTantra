package com.itantra.stt.model

import android.content.Context
import android.util.Log
import com.itantra.stt.stt.IndicConformerEngine
import com.itantra.stt.stt.NemoConformerEngine
import com.itantra.stt.stt.SttEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages model switching and lifecycle, ensuring strictly only one model is loaded at a time.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
    }

    private val mutex = Mutex()
    private var currentEngine: SttEngine? = null
    private var currentModelInfo: ModelInfo? = null
    private var currentLanguage: Language? = null

    fun getCurrentEngine(): SttEngine? = currentEngine
    fun getCurrentModelInfo(): ModelInfo? = currentModelInfo
    fun getCurrentLanguage(): Language? = currentLanguage

    fun isLoaded(): Boolean = currentEngine?.isLoaded() == true

    /**
     * Switches and loads the model for the requested language.
     * Unloads any existing model first to preserve device RAM.
     */
    suspend fun loadLanguage(language: Language): Boolean = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (currentLanguage == language && currentEngine?.isLoaded() == true) {
                Log.d(TAG, "Language $language is already loaded")
                return@withContext true
            }

            val modelInfo = ModelRegistry.getModelInfo(language)
            if (modelInfo == null) {
                Log.w(TAG, "No model registered or supported for language: $language")
                return@withContext false
            }

            // 1. Unload previous model cleanly
            unloadCurrentModelInternal()

            // 2. Instantiate and load appropriate engine
            try {
                val newEngine: SttEngine = if (language == Language.EN) {
                    NemoConformerEngine(context, modelInfo)
                } else {
                    IndicConformerEngine(context, modelInfo)
                }

                newEngine.load()
                currentEngine = newEngine
                currentModelInfo = modelInfo
                currentLanguage = language
                Log.i(TAG, "Successfully activated model for $language (${modelInfo.modelName})")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model for $language", e)
                unloadCurrentModelInternal()
                false
            }
        }
    }

    suspend fun unloadCurrentModel() = mutex.withLock {
        unloadCurrentModelInternal()
    }

    private fun unloadCurrentModelInternal() {
        try {
            currentEngine?.unload()
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading current engine", e)
        } finally {
            currentEngine = null
            currentModelInfo = null
            currentLanguage = null
            System.gc() // Hint GC to reclaim native buffer references
        }
    }
}
