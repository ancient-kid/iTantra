package com.itantra.stt.benchmark

/**
 * Standard TTS Benchmark Record matching Section 39 evaluation format.
 */
data class TtsBenchmarkRecord(
    val language: String,
    val engine: String,
    val voice: String,
    val modelSizeMb: Double,
    val modelLoadTimeMs: Long,
    val audioDurationMs: Long,
    val synthesisTimeMs: Long,
    val rtf: Float,
    val memoryMb: Double,
    val cpuPercent: Double,
    val intelligibility: Int = 5,
    val naturalness: Int = 5,
    val pronunciation: Int = 5
)
