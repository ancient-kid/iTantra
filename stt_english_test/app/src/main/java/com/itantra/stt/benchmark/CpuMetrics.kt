package com.itantra.stt.benchmark

import android.os.Process
import android.os.SystemClock
import java.io.RandomAccessFile

/**
 * CPU metrics sampler tracking process execution time across stages.
 */
object CpuMetrics {

    private var lastCpuTime: Long = 0L
    private var lastSampleTime: Long = 0L

    fun sampleProcessCpuUsage(): Double {
        return try {
            val statFile = RandomAccessFile("/proc/${Process.myPid()}/stat", "r")
            val statContent = statFile.readLine()
            statFile.close()

            if (statContent.isNullOrEmpty()) return 0.0

            val closeParen = statContent.lastIndexOf(')')
            if (closeParen == -1 || closeParen + 2 >= statContent.length) return 0.0

            val pieces = statContent.substring(closeParen + 2).trim().split("\\s+".toRegex())
            if (pieces.size < 13) return 0.0

            // In /proc/pid/stat:
            // pieces[0] = state (field 3)
            // pieces[11] = utime (field 14)
            // pieces[12] = stime (field 15)
            val utime = pieces[11].toLongOrNull() ?: return 0.0
            val stime = pieces[12].toLongOrNull() ?: return 0.0
            val totalCpuTime = utime + stime

            val now = SystemClock.elapsedRealtime()
            if (lastSampleTime == 0L) {
                lastCpuTime = totalCpuTime
                lastSampleTime = now
                return 0.0
            }

            val timeDiff = now - lastSampleTime
            val cpuDiff = totalCpuTime - lastCpuTime

            lastCpuTime = totalCpuTime
            lastSampleTime = now

            if (timeDiff > 0) {
                val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                val percent = (cpuDiff.toDouble() * 1000.0 / timeDiff) / cores
                percent.coerceIn(0.0, 100.0)
            } else {
                0.0
            }
        } catch (e: Throwable) {
            0.0
        }
    }
}
