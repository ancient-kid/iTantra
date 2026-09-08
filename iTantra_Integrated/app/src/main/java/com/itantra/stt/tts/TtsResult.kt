package com.itantra.stt.tts

/**
 * Structured TTS synthesis result holding synthesized PCM audio buffer and performance telemetry.
 */
data class TtsResult(
    val audio: ByteArray,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long,
    val sampleRate: Int = 22050,
    val modelLoadTimeMs: Long = 0L,
    val memoryUsageMb: Double? = null,
    val cpuUsagePercent: Double? = null
) {
    /**
     * Real-Time Factor (RTF) = Synthesis Latency (ms) / Generated Audio Duration (ms)
     * Values < 1.0 indicate faster-than-real-time generation.
     */
    val rtf: Float
        get() = if (audioDurationMs > 0) synthesisTimeMs.toFloat() / audioDurationMs else 0.0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TtsResult

        if (!audio.contentEquals(other.audio)) return false
        if (synthesisTimeMs != other.synthesisTimeMs) return false
        if (audioDurationMs != other.audioDurationMs) return false
        if (sampleRate != other.sampleRate) return false
        if (modelLoadTimeMs != other.modelLoadTimeMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = audio.contentHashCode()
        result = 31 * result + synthesisTimeMs.hashCode()
        result = 31 * result + audioDurationMs.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + modelLoadTimeMs.hashCode()
        return result
    }
}
