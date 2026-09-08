package com.itantra.stt.stt

/**
 * Structured transcription result with benchmark metrics.
 */
data class SttResult(
    val text: String,
    val inferenceTimeMs: Long,
    val audioDurationMs: Long,
    val modelLoadTimeMs: Long = 0L,
    val memoryUsageMb: Double? = null,
    val cpuUsagePercent: Double? = null
) {
    /**
     * Real-Time Factor (RTF) = Inference Time / Audio Duration
     */
    val rtf: Float
        get() = if (audioDurationMs > 0) inferenceTimeMs.toFloat() / audioDurationMs else 0.0f
}
