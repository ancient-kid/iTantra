package com.itantra.stt.model

/**
 * Metadata descriptor for an offline STT model.
 *
 * [modelPath] and [tokensPath] are asset-relative when [useAssets] is true, and
 * absolute filesystem paths when the pack was downloaded instead of bundled.
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
    val featureDim: Int = 80,
    val useAssets: Boolean = true
)
