package com.itantra.app.diagnostics

import com.itantra.stt.benchmark.MemoryMetrics
import java.util.Locale

/**
 * Per-stage timing for the walkie-talkie loop.
 *
 * The roadmap calls for timestamps to be logged from day one rather than
 * reconstructed later, so every send and every receive records its own trace and
 * the last one is rendered on the diagnostics panel.
 */
object PipelineTelemetry {

    /** Microphone -> transcript -> mesh, all on the sending device. */
    data class OutboundTrace(
        val captureMs: Long,
        val sttInferenceMs: Long,
        val sttRtf: Float,
        val transmitMs: Long,
        val transcriptChars: Int,
        val links: List<String>,
        val autoStopped: Boolean = false
    ) {
        /** Speech-end to packet-handed-to-radio. */
        val speechEndToSendMs: Long get() = sttInferenceMs + transmitMs
    }

    /** Mesh -> synthesized audio -> speaker, all on the receiving device. */
    data class InboundTrace(
        val modelSwapMs: Long,
        val ttsSynthesisMs: Long,
        val ttsRtf: Float,
        val audioDurationMs: Long,
        val receiveToAudibleMs: Long,
        val crossDeviceMs: Long?
    )

    data class ModelLoadTrace(
        val sttLanguage: String?,
        val sttLoadMs: Long,
        val ttsLanguage: String?,
        val ttsLoadMs: Long
    )

    @Volatile
    var lastOutbound: OutboundTrace? = null
        private set

    @Volatile
    var lastInbound: InboundTrace? = null
        private set

    @Volatile
    var lastModelLoad: ModelLoadTrace? = null
        private set

    private var sends = 0
    private var sendFailures = 0
    private var receives = 0
    private var receiveFailures = 0

    fun recordOutbound(trace: OutboundTrace) {
        lastOutbound = trace
        sends++
    }

    fun recordInbound(trace: InboundTrace) {
        lastInbound = trace
        receives++
    }

    fun recordSendFailure() {
        sends++
        sendFailures++
    }

    fun recordReceiveFailure() {
        receives++
        receiveFailures++
    }

    fun recordSttLoad(language: String?, loadMs: Long) {
        val previous = lastModelLoad
        lastModelLoad = ModelLoadTrace(
            sttLanguage = language,
            sttLoadMs = loadMs,
            ttsLanguage = previous?.ttsLanguage,
            ttsLoadMs = previous?.ttsLoadMs ?: 0L
        )
    }

    fun recordTtsLoad(language: String?, loadMs: Long) {
        val previous = lastModelLoad
        lastModelLoad = ModelLoadTrace(
            sttLanguage = previous?.sttLanguage,
            sttLoadMs = previous?.sttLoadMs ?: 0L,
            ttsLanguage = language,
            ttsLoadMs = loadMs
        )
    }

    fun reset() {
        lastOutbound = null
        lastInbound = null
        sends = 0
        sendFailures = 0
        receives = 0
        receiveFailures = 0
    }

    /** Reliability as the roadmap wants it cited: successes out of attempts. */
    fun reliabilityLine(): String = String.format(
        Locale.US,
        "Sent %d/%d OK  |  Received %d/%d OK",
        sends - sendFailures, sends,
        receives - receiveFailures, receives
    )

    fun render(): String {
        val builder = StringBuilder()

        lastModelLoad?.let {
            builder.append("MODELS\n")
            builder.append(
                String.format(
                    Locale.US,
                    "  STT %s load %d ms   |   TTS %s load %d ms\n",
                    it.sttLanguage ?: "-", it.sttLoadMs,
                    it.ttsLanguage ?: "-", it.ttsLoadMs
                )
            )
        }

        lastOutbound?.let {
            builder.append("LAST SEND\n")
            builder.append(
                String.format(
                    Locale.US,
                    "  Speech captured  %d ms (%s)\n" +
                        "  STT inference    %d ms (RTF %.2f)\n" +
                        "  Mesh handoff     %d ms over %s\n" +
                        "  Speech-end to air %d ms for %d chars\n",
                    it.captureMs,
                    if (it.autoStopped) "VAD auto-stop" else "manual release",
                    it.sttInferenceMs, it.sttRtf,
                    it.transmitMs,
                    if (it.links.isEmpty()) "no active link" else it.links.joinToString(" + "),
                    it.speechEndToSendMs,
                    it.transcriptChars
                )
            )
        }

        lastInbound?.let {
            builder.append("LAST RECEIVE\n")
            builder.append(
                String.format(
                    Locale.US,
                    "  TTS model swap   %d ms\n" +
                        "  TTS synthesis    %d ms (RTF %.2f)\n" +
                        "  Audio generated  %d ms\n" +
                        "  Packet to audible %d ms\n",
                    it.modelSwapMs,
                    it.ttsSynthesisMs, it.ttsRtf,
                    it.audioDurationMs,
                    it.receiveToAudibleMs
                )
            )
            it.crossDeviceMs?.let { total ->
                builder.append(
                    String.format(
                        Locale.US,
                        "  Spoken to heard  %d ms (depends on phone clock sync)\n",
                        total
                    )
                )
            }
        }

        val memory = MemoryMetrics.captureSnapshot()
        builder.append(
            String.format(
                Locale.US,
                "MEMORY\n  Heap %.1f MB used of %.1f MB   |   Native %.1f MB\n",
                memory.usedHeapMb, memory.totalHeapMb, memory.nativeHeapMb
            )
        )
        builder.append("RELIABILITY\n  ").append(reliabilityLine())

        return builder.toString()
    }
}
