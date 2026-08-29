package com.itantra.stt.stt

import com.itantra.stt.model.Language

/**
 * Standard STT Engine Interface for all model families (English & IndicConformer).
 */
interface SttEngine {

    suspend fun load()

    suspend fun transcribe(
        audio: ShortArray,
        sampleRate: Int = 16000
    ): SttResult

    fun unload()

    fun isLoaded(): Boolean

    fun modelName(): String

    fun language(): Language
}
