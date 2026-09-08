package com.bitchat.android.utils
import java.io.File
object FileUtils {
    fun getSharedFile(fileName: String): File = File(fileName)
    fun getReceivedFile(fileName: String): File = File(fileName)
}
