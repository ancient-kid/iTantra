package com.itantra.stt.tts

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Copies espeak-ng-data from app assets to internal storage so native C++ code can access it via filesystem path.
 */
object EspeakNgAssetHelper {

    private const val TAG = "EspeakNgAssetHelper"
    private const val ASSET_FOLDER = "espeak-ng-data"

    suspend fun getEspeakNgDataPath(context: Context): String = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, ASSET_FOLDER)
        val markerFile = File(targetDir, "phontab")

        if (targetDir.exists() && markerFile.exists() && markerFile.length() > 0) {
            Log.d(TAG, "espeak-ng-data already extracted at: ${targetDir.absolutePath}")
            return@withContext targetDir.absolutePath
        }

        Log.i(TAG, "Extracting espeak-ng-data from assets to ${targetDir.absolutePath}...")
        targetDir.mkdirs()
        copyAssetDirectory(context.assets, ASSET_FOLDER, targetDir)
        Log.i(TAG, "espeak-ng-data extraction complete.")
        return@withContext targetDir.absolutePath
    }

    private fun copyAssetDirectory(assetManager: AssetManager, assetPath: String, targetDir: File) {
        val assets = assetManager.list(assetPath) ?: return
        if (assets.isEmpty()) {
            copyAssetFile(assetManager, assetPath, targetDir)
        } else {
            targetDir.mkdirs()
            for (asset in assets) {
                val subAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                val subTarget = File(targetDir, asset)
                val subList = assetManager.list(subAssetPath)
                if (subList != null && subList.isNotEmpty()) {
                    copyAssetDirectory(assetManager, subAssetPath, subTarget)
                } else {
                    copyAssetFile(assetManager, subAssetPath, subTarget)
                }
            }
        }
    }

    private fun copyAssetFile(assetManager: AssetManager, assetPath: String, targetFile: File) {
        if (targetFile.exists() && targetFile.length() > 0) return

        try {
            assetManager.open(assetPath).use { inStream ->
                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { outStream ->
                    inStream.copyTo(outStream, bufferSize = 8192)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset file $assetPath to ${targetFile.absolutePath}", e)
        }
    }
}
