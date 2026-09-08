package com.itantra.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bitchat.android.mesh.BluetoothMeshService

class MainActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: Button

    private var meshService: BluetoothMeshService? = null
    private var lastObservedPeers: Set<String> = emptySet()
    private val receivedMessageIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple UI Layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        
        logTextView = TextView(this).apply {
            text = "Welcome to iTantra MVP Test App\n"
        }
        scrollView.addView(logTextView)
        
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        inputEditText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            hint = "Type message..."
        }
        
        sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener {
                sendMessage()
            }
        }
        
        inputLayout.addView(inputEditText)
        inputLayout.addView(sendButton)
        
        layout.addView(scrollView)
        layout.addView(inputLayout)
        
        setContentView(layout)
        
        requestPermissions()
    }

    private fun sendMessage() {
        val text = inputEditText.text.toString()
        if (text.isNotBlank()) {
            inputEditText.text.clear()
            appendLog("You: $text")
            
            try {
                meshService?.sendMessage(text)
            } catch (e: Exception) {
                appendLog("Error sending via BLE: ${e.message}")
            }
            try {
                com.bitchat.android.wifiaware.WifiAwareController.getService()?.sendMessage(text)
            } catch (e: Exception) {
                appendLog("Error sending via Wi-Fi Aware: ${e.message}")
            }
        }
    }

    private fun appendLog(msg: String) {
        runOnUiThread {
            logTextView.append(msg + "\n")
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Wi-Fi and Location permissions for Wi-Fi Aware
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE)

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        } else {
            startMeshService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            startMeshService()
        }
    }

    private val btStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, android.bluetooth.BluetoothAdapter.ERROR)
                when (state) {
                    android.bluetooth.BluetoothAdapter.STATE_ON -> {
                        appendLog("Bluetooth turned ON -> initializing P2P mesh...")
                        startMeshService()
                    }
                    android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF,
                    android.bluetooth.BluetoothAdapter.STATE_OFF -> {
                        appendLog("⚠️ Bluetooth turned OFF. BLE Mesh paused.")
                        meshService?.stopServices()
                        meshService = null
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            btStateReceiver,
            android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(btStateReceiver)
        } catch (_: Exception) {}
    }

    private fun startMeshService() {
        val deviceName = com.bitchat.android.utils.DeviceUtils.getDeviceName(this)
        com.bitchat.android.services.NicknameProvider.setNickname(deviceName)

        // 1. Initialize BLE Mesh
        val btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (btAdapter != null && btAdapter.isEnabled) {
            if (meshService == null) {
                appendLog("Starting BLE mesh service...")
                val service = BluetoothMeshService(this)
                service.delegate = object : com.bitchat.android.mesh.BluetoothMeshDelegate {
                    override fun didReceiveMessage(message: com.bitchat.android.model.BitchatMessage) {
                        val dedupKey = "${message.senderPeerID}-${message.content}-${message.timestamp.time / 2000}"
                        if (!receivedMessageIds.add(dedupKey)) {
                            return
                        }
                        val sender = message.sender.ifBlank { message.senderPeerID?.take(8) ?: "Peer" }
                        if (message.isRelay) {
                            val relay = message.originalSender?.ifBlank { "mesh relay" } ?: "mesh relay"
                            appendLog("[$sender via $relay] (relayed mesh message, via BLE): ${message.content}")
                        } else {
                            appendLog("[$sender] (direct 1-hop, via BLE): ${message.content}")
                        }
                    }

                    override fun didUpdatePeerList(peers: List<String>) {
                        val currentSet = peers.toSet()
                        if (currentSet == lastObservedPeers) {
                            return
                        }
                        val newlyJoined = currentSet - lastObservedPeers
                        val newlyLeft = lastObservedPeers - currentSet
                        lastObservedPeers = currentSet

                        for (peer in newlyJoined) {
                            val nick = meshService?.getPeerNickname(peer) ?: peer.take(8)
                            val isDirect = meshService?.isPeerDirectlyConnected(peer) ?: false
                            if (isDirect) {
                                appendLog("🟢 Direct Peer connected: $nick (1-hop, via BLE)")
                            } else {
                                appendLog("🌐 Mesh Peer discovered: $nick (multi-hop, via BLE)")
                            }
                        }
                        for (peer in newlyLeft) {
                            val nick = meshService?.getPeerNickname(peer) ?: peer.take(8)
                            appendLog("🔴 Peer disconnected: $nick (via BLE)")
                        }
                        if (currentSet.isEmpty()) {
                            appendLog("Searching for nearby BLE peers (auto-retry active)...")
                        }
                    }

                    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
                        val nick = meshService?.getPeerNickname(fromPeer) ?: fromPeer.take(8)
                        appendLog("Peer $nick left $channel (BLE)")
                    }

                    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {
                        val nick = meshService?.getPeerNickname(recipientPeerID) ?: recipientPeerID.take(8)
                        appendLog("Delivered to $nick (via BLE)")
                    }

                    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {
                        val nick = meshService?.getPeerNickname(recipientPeerID) ?: recipientPeerID.take(8)
                        appendLog("Read by $nick (via BLE)")
                    }

                    override fun didReceiveVerifyChallenge(peerID: String, payload: ByteArray, timestampMs: Long) {}
                    override fun didReceiveVerifyResponse(peerID: String, payload: ByteArray, timestampMs: Long) {}
                    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null
                    override fun getNickname(): String = deviceName
                    override fun isFavorite(peerID: String): Boolean = false
                }
                service.startServices()
                meshService = service
                appendLog("Device: $deviceName | Peer ID: ${service.myPeerID.take(8)}")
            }
        } else {
            appendLog("⚠️ Bluetooth is OFF.")
        }

        // 2. Initialize Wi-Fi Aware Mesh
        val awareStatus = com.bitchat.android.wifiaware.WifiAwareSupport.evaluate(this)
        if (awareStatus.supported) {
            if (awareStatus.available) {
                appendLog("Starting Wi-Fi Aware mesh service...")
                com.bitchat.android.wifiaware.WifiAwareController.delegate = object : com.bitchat.android.mesh.MeshDelegate {
                    override fun didReceiveMessage(message: com.bitchat.android.model.BitchatMessage) {
                        val dedupKey = "${message.senderPeerID}-${message.content}-${message.timestamp.time / 2000}"
                        if (!receivedMessageIds.add(dedupKey)) {
                            return
                        }
                        val sender = message.sender.ifBlank { message.senderPeerID?.take(8) ?: "Peer" }
                        if (message.isRelay) {
                            val relay = message.originalSender?.ifBlank { "mesh relay" } ?: "mesh relay"
                            appendLog("[$sender via $relay] (relayed mesh message, via Wi-Fi Aware): ${message.content}")
                        } else {
                            appendLog("[$sender] (direct 1-hop, via Wi-Fi Aware): ${message.content}")
                        }
                    }

                    override fun didUpdatePeerList(peers: List<String>) {
                        for (peer in peers) {
                            appendLog("🟢 Peer connected: ${peer.take(8)} (via Wi-Fi Aware)")
                        }
                    }

                    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
                        appendLog("Peer ${fromPeer.take(8)} left $channel (Wi-Fi Aware)")
                    }

                    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {
                        appendLog("Delivered to ${recipientPeerID.take(8)} (via Wi-Fi Aware)")
                    }

                    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {
                        appendLog("Read by ${recipientPeerID.take(8)} (via Wi-Fi Aware)")
                    }

                    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null
                    override fun getNickname(): String = deviceName
                    override fun isFavorite(peerID: String): Boolean = false
                }
                com.bitchat.android.wifiaware.WifiAwareController.initialize(this, enabledByDefault = true)
                appendLog("Wi-Fi Aware active & listening.")
            } else {
                appendLog("⚠️ Wi-Fi Aware supported but currently unavailable (${awareStatus.reason}). Turn ON Wi-Fi and Location.")
            }
        } else {
            appendLog("ℹ️ Wi-Fi Aware not supported by hardware (${awareStatus.reason}).")
        }

        appendLog("Ready to chat over 7-hop P2P mesh!")
    }

    override fun onDestroy() {
        super.onDestroy()
        meshService?.stopServices()
        meshService = null
        com.bitchat.android.wifiaware.WifiAwareController.stop()
    }
}
