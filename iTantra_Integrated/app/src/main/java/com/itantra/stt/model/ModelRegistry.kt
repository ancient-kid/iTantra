package com.itantra.stt.model

import android.content.Context
import com.itantra.app.models.ModelPack
import com.itantra.app.models.ModelSource
import com.itantra.app.models.ModelStore

/**
 * Resolves a language to a loadable STT model.
 *
 * Paths come from whichever pack is actually present — the English model bundled
 * in the APK, or the shared IndicConformer checkpoint once it has been
 * downloaded — so this returns null when the pack for a language is not
 * installed yet, and the caller offers the download instead of failing to load.
 */
object ModelRegistry {

    fun getModelInfo(context: Context, language: Language): ModelInfo? {
        val pack = ModelStore.sttPackFor(context, language.code) ?: return null
        val source = ModelStore.sourceFor(context, pack) ?: return null
        return buildModelInfo(language, pack, source)
    }

    /** True when the language has an STT pack registered, installed or not. */
    fun hasModel(context: Context, language: Language): Boolean =
        ModelStore.sttPackFor(context, language.code) != null

    fun sttPack(context: Context, language: Language): ModelPack? =
        ModelStore.sttPackFor(context, language.code)

    private fun buildModelInfo(
        language: Language,
        pack: ModelPack,
        source: ModelSource
    ): ModelInfo {
        val modelFile = pack.files.firstOrNull { it.name.endsWith(".onnx") }?.name
            ?: "model.int8.onnx"
        val isEnglish = language == Language.EN

        return ModelInfo(
            id = "${language.code}_${pack.id}",
            modelName = if (isEnglish) pack.modelName else "${pack.modelName} (${language.displayName})",
            familyName = if (isEnglish) "NeMo Conformer-CTC" else "IndicConformer",
            language = language,
            modelPath = source.pathFor(modelFile),
            tokensPath = source.pathFor("tokens.txt"),
            format = "ONNX",
            quantization = "INT8",
            sampleRate = 16000,
            featureDim = 80,
            useAssets = source.usesAssets
        )
    }
}
