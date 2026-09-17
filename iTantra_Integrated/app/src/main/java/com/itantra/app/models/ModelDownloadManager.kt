package com.itantra.app.models

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Downloads model packs from Hugging Face into internal storage.
 *
 * Downloads are resumable (HTTP Range against a `.part` file), checksum-verified
 * against the manifest, and only moved into place once they pass — a connection
 * dropped at 90% leaves a `.part` file to resume, never a truncated `.onnx` that
 * fails later inside native code with an unhelpful error.
 *
 * The scope is application-wide on purpose: leaving the Models screen must not
 * cancel a 190 MB download.
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownloadManager"

    sealed class Status {
        object NotInstalled : Status()
        data class Downloading(val bytes: Long, val total: Long) : Status() {
            val fraction: Float get() = if (total > 0) bytes.toFloat() / total else 0f
        }
        object Verifying : Status()
        object Installed : Status()
        data class Failed(val message: String) : Status()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS) // large files, no overall deadline
        .retryOnConnectionFailure(true)
        .build()

    private val _statuses = MutableStateFlow<Map<String, Status>>(emptyMap())
    val statuses: StateFlow<Map<String, Status>> = _statuses.asStateFlow()

    fun statusOf(packId: String): Status = _statuses.value[packId] ?: Status.NotInstalled

    fun isBusy(packId: String): Boolean = jobs[packId]?.isActive == true

    /** Seeds the status map from what is already on disk. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val current = _statuses.value.toMutableMap()
        for (pack in ModelStore.packs(app)) {
            if (isBusy(pack.id)) continue
            current[pack.id] =
                if (ModelStore.isInstalled(app, pack)) Status.Installed else Status.NotInstalled
        }
        _statuses.value = current
    }

    fun download(context: Context, pack: ModelPack) {
        if (pack.bundled || isBusy(pack.id)) return
        val app = context.applicationContext

        val job = scope.launch {
            try {
                runDownload(app, pack)
                ModelStore.markVerified(app, pack)
                publish(pack.id, Status.Installed)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                publish(pack.id, Status.NotInstalled)
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Download of ${pack.id} failed", e)
                publish(pack.id, Status.Failed(e.message ?: "download failed"))
            } finally {
                jobs.remove(pack.id)
            }
        }
        jobs[pack.id] = job
    }

    fun cancel(packId: String) {
        jobs.remove(packId)?.cancel()
        publish(packId, Status.NotInstalled)
    }

    fun delete(context: Context, pack: ModelPack) {
        cancel(pack.id)
        ModelStore.delete(context.applicationContext, pack)
        publish(pack.id, Status.NotInstalled)
    }

    // ------------------------------------------------------------------

    private suspend fun runDownload(context: Context, pack: ModelPack) {
        val directory = ModelStore.directoryFor(context, pack)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Cannot create ${directory.absolutePath}")
        }

        val total = pack.totalBytes
        var completed = 0L
        publish(pack.id, Status.Downloading(0, total))

        for (spec in pack.files) {
            val destination = File(directory, spec.name)

            // Already fetched and the right size in a previous run.
            if (destination.isFile && destination.length() == spec.size) {
                completed += spec.size
                publish(pack.id, Status.Downloading(completed, total))
                continue
            }

            val url = spec.url
                ?: throw IllegalStateException("Manifest has no URL for ${pack.id}/${spec.name}")

            val partial = File(directory, "${spec.name}.part")
            fetch(url, partial, spec, onProgress = { bytesForThisFile ->
                publish(pack.id, Status.Downloading(completed + bytesForThisFile, total))
            })

            publish(pack.id, Status.Verifying)
            verify(partial, spec)

            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                throw IllegalStateException("Could not finalise ${spec.name}")
            }

            completed += spec.size
            publish(pack.id, Status.Downloading(completed, total))
        }
    }

    private suspend fun fetch(
        url: String,
        partial: File,
        spec: PackFile,
        onProgress: (Long) -> Unit
    ) {
        var offset = if (partial.isFile) partial.length() else 0L
        if (offset > spec.size) {
            // A previous run wrote something unexpected; start clean.
            partial.delete()
            offset = 0L
        }
        if (offset == spec.size && spec.size > 0) return

        val request = Request.Builder()
            .url(url)
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            // A server that ignores our Range header restarts the file.
            val appending = response.code == 206
            if (!appending) offset = 0L

            val body = response.body ?: throw IllegalStateException("Empty body for $url")

            RandomAccessFile(partial, "rw").use { out ->
                out.setLength(offset)
                out.seek(offset)

                val buffer = ByteArray(1 shl 16)
                var written = offset
                var sinceReport = 0L

                body.byteStream().use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        written += read
                        sinceReport += read
                        // Reporting every chunk would spam the UI thread.
                        if (sinceReport >= 512 * 1024) {
                            onProgress(written)
                            sinceReport = 0
                        }
                    }
                }
                onProgress(written)
            }
        }
    }

    private fun verify(file: File, spec: PackFile) {
        if (spec.size > 0 && file.length() != spec.size) {
            file.delete()
            throw IllegalStateException(
                "${spec.name}: expected ${spec.size} bytes, got ${file.length()}"
            )
        }
        if (spec.sha256.isNotBlank()) {
            val actual = ModelStore.sha256(file)
            if (!actual.equals(spec.sha256, ignoreCase = true)) {
                file.delete()
                throw IllegalStateException("${spec.name}: checksum mismatch")
            }
        }
    }

    private fun publish(packId: String, status: Status) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(packId, status) }
    }
}
