package com.itantra.app.models

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

/**
 * Knows which model packs exist, which are installed, and where their files are.
 *
 * The catalogue is read once from `assets/models/manifest.json`. Bundled packs
 * live in the APK and are always available; everything else lives under
 * `filesDir/models/<packId>/` once downloaded.
 */
object ModelStore {

    private const val TAG = "ModelStore"
    private const val MANIFEST_ASSET = "models/manifest.json"
    private const val MARKER = ".verified"

    @Volatile
    private var manifest: ModelManifest? = null

    fun packs(context: Context): List<ModelPack> = manifest(context).packs

    fun packById(context: Context, id: String): ModelPack? =
        packs(context).firstOrNull { it.id == id }

    fun sttPackFor(context: Context, languageCode: String): ModelPack? =
        packs(context).firstOrNull { it.kind == ModelKind.STT && it.serves(languageCode) }

    fun ttsPackFor(context: Context, languageCode: String): ModelPack? =
        packs(context).firstOrNull { it.kind == ModelKind.TTS && it.serves(languageCode) }

    /** Packs the user can download, in a sensible display order. */
    fun downloadablePacks(context: Context): List<ModelPack> =
        packs(context)
            .filter { !it.bundled }
            .sortedWith(compareBy({ it.kind != ModelKind.STT }, { it.displayName }))

    fun directoryFor(context: Context, pack: ModelPack): File =
        File(File(context.filesDir, "models"), pack.id)

    /**
     * A pack counts as installed when every file is present at its manifest size
     * and the verification marker written after a successful checksum is intact.
     * Sizes are cheap to check on every launch; re-hashing 190 MB is not.
     */
    fun isInstalled(context: Context, pack: ModelPack): Boolean {
        if (pack.bundled) return true
        val dir = directoryFor(context, pack)
        if (!dir.isDirectory) return false
        val allPresent = pack.files.all { spec ->
            val file = File(dir, spec.name)
            file.isFile && file.length() == spec.size
        }
        if (!allPresent) return false
        return File(dir, MARKER).takeIf { it.isFile }?.readText()?.trim() == expectedMarker(pack)
    }

    /** Resolves where a pack's files should be loaded from, or null if missing. */
    fun sourceFor(context: Context, pack: ModelPack): ModelSource? = when {
        pack.bundled -> pack.assetDir?.let { ModelSource.Bundled(it) }
        isInstalled(context, pack) -> ModelSource.Downloaded(directoryFor(context, pack))
        else -> null
    }

    fun markVerified(context: Context, pack: ModelPack) {
        runCatching {
            File(directoryFor(context, pack), MARKER).writeText(expectedMarker(pack))
        }.onFailure { Log.w(TAG, "Could not write verification marker for ${pack.id}", it) }
    }

    fun delete(context: Context, pack: ModelPack): Boolean {
        if (pack.bundled) return false
        return directoryFor(context, pack).deleteRecursively()
    }

    /** Total bytes occupied by downloaded packs. */
    fun bytesOnDisk(context: Context): Long =
        File(context.filesDir, "models")
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun expectedMarker(pack: ModelPack): String =
        pack.files.joinToString(",") { "${it.name}:${it.sha256}" }

    private fun manifest(context: Context): ModelManifest {
        manifest?.let { return it }
        synchronized(this) {
            manifest?.let { return it }
            val loaded = runCatching {
                context.assets.open(MANIFEST_ASSET).bufferedReader().use { reader ->
                    Gson().fromJson(reader, ModelManifest::class.java)
                }
            }.getOrElse {
                Log.e(TAG, "Could not read $MANIFEST_ASSET", it)
                ModelManifest()
            }
            manifest = loaded
            return loaded
        }
    }
}
