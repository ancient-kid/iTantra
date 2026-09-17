package com.itantra.stt.tts

import com.itantra.stt.model.Language

/**
 * Standard Text-to-Speech (TTS) Engine Interface.
 * Exposes a common contract for both Piper/VITS ONNX and Fallback Indic-TTS engines.
 */
interface TtsEngine {

    suspend fun load()

    suspend fun synthesize(
        text: String
    ): TtsResult

    fun unload()

    fun isLoaded(): Boolean

    fun engineName(): String

    fun language(): Language
}
