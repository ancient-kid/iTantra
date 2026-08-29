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

            val pieces = statContent.split(" ")
            val utime = pieces[13].toLong()
            val stime = pieces[14].toLong()
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
                val percent = (cpuDiff.toDouble() * 1000.0 / timeDiff) / Runtime.getRuntime().availableProcessors()
                percent.coerceIn(0.0, 100.0)
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
