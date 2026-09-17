package com.bitchat.android.ui

import android.content.Context
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.RoutedPacket

object NotificationManager {
    fun postMessageNotification(context: Context, message: BitchatMessage) {}
    fun postFileTransferNotification(context: Context, packet: RoutedPacket) {}
    fun postMeshStateNotification(context: Context, activePeers: Int) {}
}
