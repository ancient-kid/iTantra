package com.bitchat.android.service
object MeshServiceHolder {
    fun tryGetInstance(): MeshServiceHolder = this
// removed onSessionEstablished
    fun clearTransportPeers(transport: String) {}
    fun clearTransportDirectPeers(transport: String) {}
}
