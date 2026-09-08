package com.itantra.app.pipeline

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.itantra.app.audio.PriorityAudioPlayer
import com.itantra.app.diagnostics.PipelineTelemetry
import com.itantra.app.models.ModelPack
import com.itantra.app.models.ModelStore
import com.itantra.app.protocol.VoicePayload
import com.itantra.app.transport.MeshTransport
import com.itantra.stt.audio.AudioRecorder
import com.itantra.stt.model.Language
import com.itantra.stt.model.ModelManager
import com.itantra.stt.model.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The full emergency walkie-talkie loop, in one place.
 *
 *   PTT held  ->  microphone  ->  on-device STT  ->  VoicePayload  ->  mesh
 *   mesh      ->  VoicePayload  ->  on-device TTS  ->  speaker (alert-aware)
 *
 * Everything here is offline: the only I/O is the microphone, the speaker, and
 * the BLE / Wi-Fi Aware radios owned by [MeshTransport].
 */
class VoicePipeline(
    private val context: Context,
    private val scope: CoroutineScope,
    private val transport: MeshTransport
) {

    companion object {
        private const val TAG = "VoicePipeline"
        private const val MIN_SAMPLES = 1600 // 100 ms at 16 kHz
    }

    enum class State {
        IDLE,
        LOADING_MODELS,
        LISTENING,
        TRANSCRIBING,
        TRANSMITTING,
        SYNTHESIZING,
        SPEAKING,
        ERROR
    }

    private val sttManager = ModelManager(context.applicationContext)
    private val ttsManager = TtsManager(context.applicationContext)
    private val recorder = AudioRecorder()
    private val player = PriorityAudioPlayer(context.applicationContext)

    /** Incoming turns are played one at a time, in arrival order. */
    private val inbox = Channel<MeshTransport.Incoming>(Channel.UNLIMITED)

    var onState: ((State) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    var onTranscript: ((String) -> Unit)? = null
    var onIncoming: ((MeshTransport.Incoming) -> Unit)? = null
    var onTelemetryChanged: (() -> Unit)? = null

    @Volatile
    var state: State = State.IDLE
        private set(value) {
            field = value
            onState?.invoke(value)
        }

    /** Language used for the microphone side, and the fallback for untagged messages. */
    @Volatile
    var activeLanguage: Language = Language.EN
        private set

    private var captureStartedAt = 0L

    init {
        scope.launch { consumeInbox() }
    }

    // ------------------------------------------------------------------
    // Model lifecycle
    // ------------------------------------------------------------------

    /** Outcome of preparing a language, so the UI can offer a download. */
    sealed class PrepareResult {
        object Ready : PrepareResult()
        data class MissingPacks(val packs: List<ModelPack>) : PrepareResult()
        data class Failed(val message: String) : PrepareResult()
    }

    /** Packs a language needs that are not installed on this device. */
    fun missingPacksFor(language: Language): List<ModelPack> = listOfNotNull(
        ModelStore.sttPackFor(context, language.code),
        ModelStore.ttsPackFor(context, language.code)
    ).filterNot { ModelStore.isInstalled(context, it) }

    /**
     * Loads the STT model, and the matching TTS voice, for [language].
     * The two managers hold one model each, so switching never leaves two
     * checkpoints resident at the same time.
     */
    suspend fun prepare(language: Language): PrepareResult {
        activeLanguage = language

        val missing = missingPacksFor(language)
        if (missing.isNotEmpty()) {
            state = State.IDLE
            emit(
                "${language.displayName} needs ${missing.joinToString(" and ") { it.displayName }} " +
                    "— download it from Language models."
            )
            return PrepareResult.MissingPacks(missing)
        }

        state = State.LOADING_MODELS
        emit("Loading ${language.displayName} models...")

        val sttStart = SystemClock.elapsedRealtime()
        val sttOk = sttManager.loadLanguage(language)
        val sttMs = SystemClock.elapsedRealtime() - sttStart
        PipelineTelemetry.recordSttLoad(language.displayName, sttMs)

        val ttsStart = SystemClock.elapsedRealtime()
        val ttsOk = ttsManager.loadLanguage(language)
        val ttsMs = SystemClock.elapsedRealtime() - ttsStart
        PipelineTelemetry.recordTtsLoad(language.displayName, ttsMs)

        onTelemetryChanged?.invoke()

        return if (sttOk && ttsOk) {
            emit("${language.displayName} ready (STT ${sttMs} ms, TTS ${ttsMs} ms).")
            state = State.IDLE
            PrepareResult.Ready
        } else {
            val detail = "STT ${if (sttOk) "ok" else "failed"}, TTS ${if (ttsOk) "ok" else "failed"}"
            emit("Model load failed for ${language.displayName} ($detail).")
            state = State.ERROR
            PrepareResult.Failed(detail)
        }
    }

    fun release() {
        try {
            if (recorder.isRecording()) recorder.stopRecording()
        } catch (_: Exception) {
        }
        player.stop()
        inbox.close()
        scope.launch {
            sttManager.unloadCurrentModel()
            ttsManager.unloadCurrentModel()
        }
    }

    // ------------------------------------------------------------------
    // Push to talk: send side
    // ------------------------------------------------------------------

    fun canStartTalking(): Boolean = state == State.IDLE || state == State.ERROR

    /** Starts capturing from the microphone. Caller must already hold RECORD_AUDIO. */
    fun startTalking(): Boolean {
        if (!canStartTalking()) {
            emit("Busy (${state.name.lowercase()}) - not starting a new capture.")
            return false
        }
        if (!sttManager.isLoaded()) {
            emit("STT model is not loaded yet.")
            return false
        }
        if (player.isPlaying()) player.stop()

        val started = recorder.startRecording(scope)
        if (!started) {
            emit("Microphone could not be opened.")
            state = State.ERROR
            return false
        }

        captureStartedAt = SystemClock.elapsedRealtime()
        state = State.LISTENING
        return true
    }

    /**
     * Ends capture, transcribes on-device, and broadcasts the transcript over the
     * mesh with its language tag, priority flag and capture timestamp.
     */
    fun stopTalkingAndSend(priority: VoicePayload.Priority) {
        if (state != State.LISTENING) return

        val captureMs = SystemClock.elapsedRealtime() - captureStartedAt
        val samples = recorder.stopRecording()

        if (samples.size < MIN_SAMPLES) {
            emit("Too short - nothing captured.")
            state = State.IDLE
            return
        }

        state = State.TRANSCRIBING
        scope.launch {
            try {
                val engine = sttManager.getCurrentEngine()
                if (engine == null || !engine.isLoaded()) {
                    emit("STT engine unavailable.")
                    state = State.ERROR
                    PipelineTelemetry.recordSendFailure()
                    return@launch
                }

                val result = withContext(Dispatchers.Default) {
                    engine.transcribe(samples, AudioRecorder.SAMPLE_RATE)
                }
                val transcript = result.text.trim()
                onTranscript?.invoke(transcript)

                if (transcript.isEmpty()) {
                    emit("No speech recognized - nothing sent.")
                    state = State.IDLE
                    PipelineTelemetry.recordSendFailure()
                    return@launch
                }

                state = State.TRANSMITTING
                val payload = VoicePayload.create(
                    text = transcript,
                    language = activeLanguage,
                    priority = priority
                )

                val transmitStart = SystemClock.elapsedRealtime()
                val links = transport.send(payload)
                val transmitMs = SystemClock.elapsedRealtime() - transmitStart

                PipelineTelemetry.recordOutbound(
                    PipelineTelemetry.OutboundTrace(
                        captureMs = captureMs,
                        sttInferenceMs = result.inferenceTimeMs,
                        sttRtf = result.rtf,
                        transmitMs = transmitMs,
                        transcriptChars = transcript.length,
                        links = links.map { it.displayName }
                    )
                )
                if (links.isEmpty()) PipelineTelemetry.recordSendFailure()
                onTelemetryChanged?.invoke()

                emit(
                    if (links.isEmpty()) "No active mesh link - message not transmitted."
                    else "Sent over ${links.joinToString(" + ") { it.displayName }} " +
                        "(STT ${result.inferenceTimeMs} ms)."
                )
                state = State.IDLE
            } catch (e: Exception) {
                Log.e(TAG, "Send path failed", e)
                emit("Send failed: ${e.message}")
                PipelineTelemetry.recordSendFailure()
                state = State.ERROR
            }
        }
    }

    /** Cancels an in-flight capture without transmitting anything. */
    fun cancelTalking() {
        if (state != State.LISTENING) return
        try {
            recorder.stopRecording()
        } catch (_: Exception) {
        }
        emit("Capture cancelled.")
        state = State.IDLE
    }

    // ------------------------------------------------------------------
    // Receive side
    // ------------------------------------------------------------------

    fun submitIncoming(incoming: MeshTransport.Incoming) {
        onIncoming?.invoke(incoming)
        val queued = inbox.trySend(incoming).isSuccess
        if (!queued) {
            emit("Dropped an incoming message: playback queue is closed.")
        }
    }

    private suspend fun consumeInbox() {
        for (incoming in inbox) {
            try {
                speak(incoming)
            } catch (e: Exception) {
                Log.e(TAG, "Playback path failed", e)
                emit("Playback failed: ${e.message}")
                PipelineTelemetry.recordReceiveFailure()
                state = State.IDLE
            }
        }
    }

    private suspend fun speak(incoming: MeshTransport.Incoming) {
        val payload = incoming.payload
        if (payload.text.isBlank()) return

        // An untagged (legacy) message is spoken in whatever language the user
        // currently has selected.
        val language = payload.language() ?: activeLanguage
        val alert = payload.priority == VoicePayload.Priority.ALERT

        state = State.SYNTHESIZING

        val swapStart = SystemClock.elapsedRealtime()
        val ttsReady = ttsManager.loadLanguage(language)
        val swapMs = SystemClock.elapsedRealtime() - swapStart
        if (swapMs > 0) PipelineTelemetry.recordTtsLoad(language.displayName, swapMs)

        val engine = ttsManager.getCurrentEngine()
        if (!ttsReady || engine == null || !engine.isLoaded()) {
            // The text is already on screen; say plainly why there is no audio
            // rather than substituting something that only sounds like speech.
            val pack = ModelStore.ttsPackFor(context, language.code)
            emit(
                when {
                    pack == null ->
                        "No voice exists for ${language.displayName} - text shown only."
                    !ModelStore.isInstalled(context, pack) ->
                        "${language.displayName} voice is not installed - " +
                            "download it from Language models. Text shown only."
                    else ->
                        "${language.displayName} voice failed to load - text shown only."
                }
            )
            PipelineTelemetry.recordReceiveFailure()
            state = State.IDLE
            return
        }

        val result = withContext(Dispatchers.Default) { engine.synthesize(payload.text) }

        if (result.audio.isEmpty()) {
            emit("TTS produced no audio.")
            PipelineTelemetry.recordReceiveFailure()
            state = State.IDLE
            return
        }

        state = State.SPEAKING
        val played = player.play(
            pcmBytes = result.audio,
            sampleRate = result.sampleRate,
            priority = payload.priority,
            coroutineScope = scope,
            onStarted = {
                val audibleAt = System.currentTimeMillis()
                PipelineTelemetry.recordInbound(
                    PipelineTelemetry.InboundTrace(
                        modelSwapMs = swapMs,
                        ttsSynthesisMs = result.synthesisTimeMs,
                        ttsRtf = result.rtf,
                        audioDurationMs = result.audioDurationMs,
                        receiveToAudibleMs = audibleAt - incoming.receivedAtMillis,
                        crossDeviceMs = (audibleAt - payload.capturedAtMillis)
                            .takeIf { it in 0..600_000 }
                    )
                )
                onTelemetryChanged?.invoke()
            },
            onComplete = { state = State.IDLE }
        )

        if (!played) {
            emit("Audio playback could not start.")
            PipelineTelemetry.recordReceiveFailure()
            state = State.IDLE
        } else {
            emit(
                "Playing ${language.displayName} message from ${incoming.senderLabel}" +
                    if (alert) " [ALERT - alarm stream]" else ""
            )
        }
    }

    fun stopPlayback() {
        player.stop()
        if (state == State.SPEAKING) state = State.IDLE
    }

    private fun emit(message: String) {
        Log.i(TAG, message)
        onLog?.invoke(message)
    }
}
