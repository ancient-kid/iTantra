package com.itantra.stt.benchmark

import android.os.Debug

/**
 * Memory telemetry capturing heap and native memory utilization.
 */
object MemoryMetrics {

    data class MemorySnapshot(
        val totalHeapMb: Double,
        val usedHeapMb: Double,
        val freeHeapMb: Double,
        val nativeHeapMb: Double
    )

    fun captureSnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val totalHeap = runtime.totalMemory().toDouble() / (1024 * 1024)
        val freeHeap = runtime.freeMemory().toDouble() / (1024 * 1024)
        val usedHeap = totalHeap - freeHeap
        val nativeAllocated = Debug.getNativeHeapAllocatedSize().toDouble() / (1024 * 1024)

        return MemorySnapshot(
            totalHeapMb = totalHeap,
            usedHeapMb = usedHeap,
            freeHeapMb = freeHeap,
            nativeHeapMb = nativeAllocated
        )
    }

    fun getUsedMemoryMb(): Long {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        return usedBytes / (1024 * 1024)
    }
}
