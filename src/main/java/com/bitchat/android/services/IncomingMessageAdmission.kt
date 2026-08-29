package com.bitchat.android.services
import com.bitchat.android.model.BitchatMessage
object IncomingMessageAdmission {
    fun addPrivateMessageDurably(peerId: String, sender: String, content: ByteArray) {}
    fun addChannelMessage(channel: String, sender: String, content: ByteArray) {}
    fun addPublicMessage(sender: String, content: ByteArray) {}
}
