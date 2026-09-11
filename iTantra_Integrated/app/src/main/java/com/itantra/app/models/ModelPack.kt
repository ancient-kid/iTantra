package com.itantra.app.models

import com.google.gson.annotations.SerializedName

enum class ModelKind { STT, TTS }

/**
 * How a TTS voice turns text into model input.
 *
 * Piper and mimic3 voices are phoneme-based and need the espeak-ng data
 * directory. MMS voices (Kannada, Odia) are character-based and must run with
 * an empty dataDir — handing them the espeak path mangles their output.
 */
enum class Phonemizer { ESPEAK, NONE }

data class PackFile(
    val name: String,
    val size: Long,
    val sha256: String,
    val url: String? = null
)

/**
 * One downloadable (or APK-bundled) unit of model files, as described by
 * `assets/models/manifest.json`.
 */
data class ModelPack(
    val id: String,
    val kind: ModelKind,
    val languages: List<String>,
    val displayName: String,
    val modelName: String,
    val phonemizer: Phonemizer,
    val license: String?,
    val bundled: Boolean,
    @SerializedName("assetDir") val assetDir: String? = null,
    val files: List<PackFile> = emptyList()
) {
    val totalBytes: Long get() = files.sumOf { it.size }

    /** True for the MMS voices, which ship under a non-commercial licence. */
    val isNonCommercial: Boolean get() = license?.contains("NC", ignoreCase = true) == true

    fun serves(languageCode: String): Boolean =
        languages.any { it.equals(languageCode, ignoreCase = true) }

    fun fileNamed(name: String): PackFile? = files.firstOrNull { it.name == name }
}

data class ModelManifest(
    val version: Int = 1,
    val packs: List<ModelPack> = emptyList()
)

/**
 * Where a loaded model's files actually live. sherpa-onnx reads from either an
 * [android.content.res.AssetManager] or the filesystem, so engines need both.
 */
sealed class ModelSource {

    abstract fun pathFor(fileName: String): String

    /** Files packaged in the APK; sherpa-onnx is handed the AssetManager. */
    data class Bundled(val assetDir: String) : ModelSource() {
        override fun pathFor(fileName: String): String {
            val cleanDir = assetDir.replace('\\', '/').trim('/')
            val cleanFile = fileName.replace('\\', '/').trimStart('/')
            return if (cleanDir.isEmpty()) cleanFile else "$cleanDir/$cleanFile"
        }
    }

    /** Files downloaded to internal storage; sherpa-onnx gets a null AssetManager. */
    data class Downloaded(val directory: java.io.File) : ModelSource() {
        override fun pathFor(fileName: String): String =
            java.io.File(directory, fileName).absolutePath
    }

    val usesAssets: Boolean get() = this is Bundled
}
