package com.itantra.stt.model

/**
 * Central Model Registry routing languages to their respective offline models.
 */
object ModelRegistry {

    fun getModelInfo(language: Language): ModelInfo? {
        return when (language) {
            Language.EN -> ModelInfo(
                id = "en_nemo_conformer_ctc_small",
                modelName = "NeMo Conformer-CTC Small",
                familyName = "NeMo Conformer-CTC",
                language = Language.EN,
                modelPath = "english/model.int8.onnx",
                tokensPath = "english/tokens.txt",
                format = "ONNX",
                quantization = "INT8",
                sampleRate = 16000,
                featureDim = 80
            )

            Language.HI,
            Language.GU,
            Language.MR,
            Language.KN,
            Language.ML,
            Language.TA,
            Language.TE,
            Language.BN -> ModelInfo(
                id = "${language.code}_indicconformer_int8",
                modelName = "IndicConformer (${language.displayName})",
                familyName = "IndicConformer",
                language = language,
                modelPath = "indicconformer/model.int8.onnx",
                tokensPath = "indicconformer/tokens.txt",
                format = "ONNX",
                quantization = "INT8",
                sampleRate = 16000,
                featureDim = 80
            )

            Language.OR -> null // Odia model placeholder
        }
    }
}
