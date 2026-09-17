package com.itantra.app.transport

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bitchat.android.mesh.BluetoothMeshDelegate
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.mesh.MeshDelegate
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.services.NicknameProvider
import com.bitchat.android.utils.DeviceUtils
import com.bitchat.android.wifiaware.WifiAwareController
import com.bitchat.android.wifiaware.WifiAwareSupport
import com.itantra.app.protocol.VoicePayload
import java.util.Collections

/**
 * Single facade over the two bitchat mesh transports (BLE and Wi-Fi Aware).
 *
 * The rest of the app talks in [VoicePayload]s and never touches bitchat types:
 * this class owns transport lifecycle, encodes/decodes the wire format, and
 * de-duplicates messages that arrive over both radios at once.
 */
class MeshTransport(private val context: Context) {

    companion object {
        private const val TAG = "MeshTransport"

        /** Messages with the same sender and body inside this window are treated as one. */
        private const val DEDUPE_WINDOW_MS = 4000L
        private const val DEDUPE_HISTORY = 256
    }

    /** Which radio a message arrived on, or a status line refers to. */
    enum class Link(val displayName: String) {
        BLE("BLE"),
        WIFI_AWARE("Wi-Fi Aware")
    }

    data class Incoming(
        val payload: VoicePayload,
        val senderLabel: String,
        val link: Link,
        val relayed: Boolean,
        val receivedAtMillis: Long = System.currentTimeMillis()
    )

    data class Status(
        val bleActive: Boolean,
        val wifiAwareActive: Boolean,
        val peerCount: Int,
        val detail: String
    )

    var onMessage: ((Incoming) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    var onStatusChanged: ((Status) -> Unit)? = null

    val deviceName: String by lazy { DeviceUtils.getDeviceName(context) }

    private var meshService: BluetoothMeshService? = null
    private var wifiAwareActive = false
    private var started = false
    private var receiverRegistered = false

    private var knownPeers: Set<String> = emptySet()
    private val seenKeys = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(DEDUPE_HISTORY, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > DEDUPE_HISTORY
        }
    )

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    log("Bluetooth turned ON - restarting BLE mesh...")
                    startBleMesh()
                }
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    log("Bluetooth turned OFF - BLE mesh paused.")
                    meshService?.stopServices()
                    meshService = null
                    knownPeers = emptySet()
                    publishStatus("Bluetooth off")
                }
            }
        }
    }

    val peerCount: Int get() = knownPeers.size

    fun peerNickname(peerID: String): String =
        meshService?.getPeerNickname(peerID) ?: peerID.take(8)

    /** Local mesh identity, surfaced on the diagnostics panel. */
    fun localPeerId(): String? = meshService?.myPeerID

    fun start() {
        if (started) return
        started = true

        NicknameProvider.setNickname(deviceName)

        if (!receiverRegistered) {
            context.registerReceiver(
                bluetoothStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            )
            receiverRegistered = true
        }

        startBleMesh()
        startWifiAwareMesh()
        publishStatus("Mesh ready")
    }

    fun stop() {
        started = false
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothStateReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        meshService?.stopServices()
        meshService = null
        if (wifiAwareActive) {
            WifiAwareController.delegate = null
            WifiAwareController.stop()
            wifiAwareActive = false
        }
        knownPeers = emptySet()
        publishStatus("Stopped")
    }

    /**
     * Broadcasts a payload over every active transport. Returns the links that
     * accepted it, so the caller can report which radios carried the message.
     */
    fun send(payload: VoicePayload): List<Link> {
        val body = payload.encode()
        val delivered = mutableListOf<Link>()

        // A locally originated message must not bounce back into TTS if another
        // node relays it to us.
        markSeen(dedupeKey(meshService?.myPeerID ?: "self", body))

        try {
            meshService?.let {
                it.sendMessage(body)
                delivered += Link.BLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "BLE send failed", e)
            log("BLE send failed: ${e.message}")
        }

        try {
            WifiAwareController.getService()?.let {
                it.sendMessage(body)
                delivered += Link.WIFI_AWARE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wi-Fi Aware send failed", e)
            log("Wi-Fi Aware send failed: ${e.message}")
        }

        return delivered
    }

    // ------------------------------------------------------------------
    // Transport bring-up
    // ------------------------------------------------------------------

    private fun startBleMesh() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth is OFF - enable it for the BLE mesh.")
            publishStatus("Bluetooth off")
            return
        }
        if (meshService != null) return

        log("Starting BLE mesh service...")
        val service = BluetoothMeshService(context)
        service.delegate = bleDelegate()
        service.startServices()
        meshService = service
        log("Device: $deviceName | Peer ID: ${service.myPeerID.take(8)}")
        publishStatus("BLE mesh started")
    }

    private fun startWifiAwareMesh() {
        val status = WifiAwareSupport.evaluate(context)
        if (!status.supported) {
            log("Wi-Fi Aware unsupported on this device (${status.reason}).")
            return
        }
        if (!status.available) {
            log("Wi-Fi Aware unavailable (${status.reason}). Turn on Wi-Fi and Location.")
            return
        }

        log("Starting Wi-Fi Aware mesh service...")
        WifiAwareController.delegate = wifiAwareDelegate()
        WifiAwareController.initialize(context, enabledByDefault = true)
        wifiAwareActive = true
        log("Wi-Fi Aware active and listening.")
        publishStatus("Wi-Fi Aware started")
    }

    // ------------------------------------------------------------------
    // Delegates
    // ------------------------------------------------------------------

    private fun bleDelegate() = object : BluetoothMeshDelegate {
        override fun didReceiveMessage(message: BitchatMessage) =
            handleIncoming(message, Link.BLE)

        override fun didUpdatePeerList(peers: List<String>) {
            val current = peers.toSet()
            if (current == knownPeers) return
            val joined = current - knownPeers
            val left = knownPeers - current
            knownPeers = current

            for (peer in joined) {
                val nick = peerNickname(peer)
                val direct = meshService?.isPeerDirectlyConnected(peer) ?: false
                log(
                    if (direct) "Peer connected: $nick (1-hop, BLE)"
                    else "Peer discovered: $nick (multi-hop, BLE)"
                )
            }
            for (peer in left) {
                log("Peer lost: ${peerNickname(peer)} (BLE)")
            }
            publishStatus(
                if (current.isEmpty()) "Searching for peers..."
                else "${current.size} peer(s) in mesh"
            )
        }

        override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
            log("${peerNickname(fromPeer)} left $channel")
        }

        override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {
            log("Delivered to ${peerNickname(recipientPeerID)}")
        }

        override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {
            log("Read by ${peerNickname(recipientPeerID)}")
        }

        override fun didReceiveVerifyChallenge(peerID: String, payload: ByteArray, timestampMs: Long) {}
        override fun didReceiveVerifyResponse(peerID: String, payload: ByteArray, timestampMs: Long) {}
        override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null
        override fun getNickname(): String = deviceName
        override fun isFavorite(peerID: String): Boolean = false
    }

    private fun wifiAwareDelegate() = object : MeshDelegate {
        override fun didReceiveMessage(message: BitchatMessage) =
            handleIncoming(message, Link.WIFI_AWARE)

        override fun didUpdatePeerList(peers: List<String>) {
            if (peers.isNotEmpty()) {
                publishStatus("${peers.size} peer(s) over Wi-Fi Aware")
            }
        }

        override fun didReceiveChannelLeave(channel: String, fromPeer: String) {}
        override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {}
        override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {}
        override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null
        override fun getNickname(): String = deviceName
        override fun isFavorite(peerID: String): Boolean = false
    }

    // ------------------------------------------------------------------
    // Incoming handling
    // ------------------------------------------------------------------

    private fun handleIncoming(message: BitchatMessage, link: Link) {
        val senderId = message.senderPeerID ?: message.sender
        if (!markSeen(dedupeKey(senderId, message.content))) {
            Log.d(TAG, "Dropping duplicate message from $senderId over ${link.displayName}")
            return
        }

        val payload = VoicePayload.decode(message.content)
        val senderLabel = message.sender.ifBlank { message.senderPeerID?.take(8) ?: "Peer" }
        onMessage?.invoke(
            Incoming(
                payload = payload,
                senderLabel = senderLabel,
                link = link,
                relayed = message.isRelay
            )
        )
    }

    private fun dedupeKey(senderId: String, body: String): String = "$senderId#${body.hashCode()}"

    /** Returns true when the key is new, i.e. the message should be processed. */
    private fun markSeen(key: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(seenKeys) {
            val previous = seenKeys[key]
            if (previous != null && now - previous < DEDUPE_WINDOW_MS) return false
            seenKeys[key] = now
        }
        return true
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        onLog?.invoke(message)
    }

    private fun publishStatus(detail: String) {
        onStatusChanged?.invoke(
            Status(
                bleActive = meshService != null,
                wifiAwareActive = wifiAwareActive,
                peerCount = knownPeers.size,
                detail = detail
            )
        )
    }
}
