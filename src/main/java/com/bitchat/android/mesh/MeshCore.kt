package com.bitchat.android.mesh

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import com.bitchat.android.crypto.EncryptionService
import com.bitchat.android.sync.GossipSyncManager

class MeshCore(
    private val context: Context,
    private val scope: CoroutineScope,
    private val transport: MeshTransport,
    private val encryptionService: EncryptionService,
    val myPeerID: String,
    private val maxTtl: UByte,
    sharedGossipManager: Any?,
    gossipConfigProvider: Any?,
    private val hooks: Hooks = Hooks()
) {
    class Hooks()

    val fragmentManager = Any()
    var delegate: Any? = null
}
