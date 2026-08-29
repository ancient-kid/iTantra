package com.itantra.stt.model

/**
 * Metadata descriptor for an offline STT model.
 */
data class ModelInfo(
    val id: String,
    val modelName: String,
    val familyName: String,
    val language: Language,
    val modelPath: String,
    val tokensPath: String,
    val format: String,
    val quantization: String,
    val sampleRate: Int = 16000,
    val featureDim: Int = 80
)
