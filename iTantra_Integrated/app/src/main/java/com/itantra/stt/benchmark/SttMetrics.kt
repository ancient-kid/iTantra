package com.itantra.stt.benchmark

import java.util.Locale

/**
 * Benchmark calculations and time formatting utilities.
 */
object SttMetrics {

    fun calculateRtf(inferenceTimeMs: Long, audioDurationMs: Long): Float {
        return if (audioDurationMs > 0) {
            inferenceTimeMs.toFloat() / audioDurationMs.toFloat()
        } else {
            0.0f
        }
    }

    fun formatDuration(seconds: Float): String {
        return String.format(Locale.US, "%.2f s", seconds)
    }

    fun formatRtf(rtf: Float): String {
        return String.format(Locale.US, "%.2f", rtf)
    }
}
