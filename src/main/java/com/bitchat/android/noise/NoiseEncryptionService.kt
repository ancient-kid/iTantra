package com.bitchat.android.noise

import android.content.Context
import android.util.Log
import com.bitchat.android.identity.SecureIdentityStateManager
import com.bitchat.android.mesh.PeerFingerprintManager
import com.bitchat.android.noise.southernstorm.protocol.Noise
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class NoiseEncryptionService(private val context: Context) {
    
    companion object {
        private const val TAG = "NoiseEncryptionService"
        
        // Session limits for performance and security
        private const val REKEY_TIME_LIMIT = com.bitchat.android.util.AppConstants.Noise.REKEY_TIME_LIMIT_MS // 1 hour (same as iOS)
        private const val REKEY_MESSAGE_LIMIT = com.bitchat.android.util.AppConstants.Noise.REKEY_MESSAGE_LIMIT_ENCRYPTION // 1k messages (matches iOS) (same as iOS)
    }
    
    // Static identity key (persistent across app restarts) - loaded from secure storage
    private var staticIdentityPrivateKey: ByteArray
    private var staticIdentityPublicKey: ByteArray
    
    // Ed25519 signing key (persistent across app restarts) - loaded from secure storage
    private var signingPrivateKey: ByteArray
    private var signingPublicKey: ByteArray
    
    // Session management
    private lateinit var sessionManager: NoiseSessionManager
    
    // Channel encryption for password-protected channels
    private val channelEncryption = NoiseChannelEncryption()
    
    // Identity management for peer ID rotation support
    private val identityStateManager: SecureIdentityStateManager
    
    // Centralized fingerprint management - NO LOCAL STORAGE
    private val fingerprintManager = PeerFingerprintManager.getInstance()
    
    // Callbacks
    var onPeerAuthenticated: ((String, String) -> Unit)? = null // (peerID, fingerprint)
    var onHandshakeRequired: ((String) -> Unit)? = null // peerID needs handshake
    
    init {
        // Initialize identity state manager for persistent storage
        identityStateManager = SecureIdentityStateManager(context)
        
        // Load or create keys - temporary placeholders
        staticIdentityPrivateKey = ByteArray(32)
        staticIdentityPublicKey = ByteArray(32)
        signingPrivateKey = ByteArray(32)
        signingPublicKey = ByteArray(32)
        
        loadOrGenerateKeys()
        
        // Initialize session manager
        initializeSessionManager()
    }
    
    private fun initializeSessionManager() {
        // Create new session manager with current keys
        val localPeerID = calculateFingerprint(staticIdentityPublicKey).take(16)
        sessionManager = NoiseSessionManager(staticIdentityPrivateKey, staticIdentityPublicKey, localPeerID)
        
        // Set up session callbacks
        sessionManager.onSessionEstablished = { peerID, remoteStaticKey ->
            handleSessionEstablished(peerID, remoteStaticKey)
        }
        
    }
    
    private fun loadOrGenerateKeys() {
        // Load or create static identity key (persistent across sessions)
        val loadedKeyPair = identityStateManager.loadStaticKey()
        if (loadedKeyPair != null) {
            staticIdentityPrivateKey = loadedKeyPair.first
            staticIdentityPublicKey = loadedKeyPair.second
            Log.d(TAG, "Identity loaded: ${calculateFingerprint(staticIdentityPublicKey).take(16)}")
        } else {
            // Generate new identity key pair
            val keyPair = generateKeyPair()
            staticIdentityPrivateKey = keyPair.first
            staticIdentityPublicKey = keyPair.second
            
            // Save to secure storage
            identityStateManager.saveStaticKey(staticIdentityPrivateKey, staticIdentityPublicKey)
        }
        
        // Load or create Ed25519 signing key (persistent across sessions)
        val loadedSigningKeyPair = identityStateManager.loadSigningKey()
        if (loadedSigningKeyPair != null) {
            signingPrivateKey = loadedSigningKeyPair.first
            signingPublicKey = loadedSigningKeyPair.second
        } else {
            // Generate new Ed25519 signing key pair
            val signingKeyPair = generateEd25519KeyPair()
            signingPrivateKey = signingKeyPair.first
            signingPublicKey = signingKeyPair.second
            
            // Save to secure storage
            identityStateManager.saveSigningKey(signingPrivateKey, signingPublicKey)
        }
    }

    // MARK: - Public Interface
    
    fun getStaticPublicKeyData(): ByteArray {
        return staticIdentityPublicKey.clone()
    }

    fun getSigningPublicKeyData(): ByteArray {
        return signingPublicKey.clone()
    }
    
    fun getSigningPublicKey(): ByteArray? {
        return signingPublicKey.clone()
    }

    fun getIdentityFingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(staticIdentityPublicKey)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    fun getPeerPublicKeyData(peerID: String): ByteArray? {
        return sessionManager.getRemoteStaticKey(peerID)
    }

    fun getAuthenticatedSession(peerID: String): AuthenticatedNoiseSession? =
        sessionManager.getAuthenticatedSession(peerID)

    fun withAuthenticatedSession(
        peerID: String,
        expectedSession: AuthenticatedNoiseSession,
        action: () -> Boolean
    ): Boolean = sessionManager.withAuthenticatedSession(peerID, expectedSession, action)
    
    fun clearPersistentIdentity() {
        Log.w(TAG, "Panic: clearing persistent identity and rotating in-memory keys")
        
        // 1. Clear storage
        identityStateManager.clearIdentityData()
        
        // 2. Clear all sessions immediately
        if (::sessionManager.isInitialized) {
            sessionManager.shutdown()
        }
        
        // 3. Regenerate keys immediately (in-memory rotation)
        loadOrGenerateKeys()
        
        // 4. Re-initialize SessionManager with new keys
        initializeSessionManager()
        
    }
    
    fun initiateHandshake(peerID: String, replaceEstablished: Boolean = false): ByteArray? {
        return try {
            sessionManager.initiateHandshake(peerID, replaceEstablished)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate handshake with $peerID: ${e.message}")
            null
        }
    }
    
    fun processHandshakeMessage(data: ByteArray, peerID: String): ByteArray? {
        return try {
            processHandshakeMessageWithResult(data, peerID).response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process handshake from $peerID: ${e.message}")
            null
        }
    }

    @Throws(Exception::class)
    fun processHandshakeMessageWithResult(
        data: ByteArray,
        peerID: String
    ): NoiseHandshakeProcessingResult {
        return sessionManager.processHandshakeMessageWithResult(peerID, data)
    }
    
    fun hasEstablishedSession(peerID: String): Boolean {
        return sessionManager.hasEstablishedSession(peerID)
    }
    
    fun getSessionState(peerID: String): NoiseSession.NoiseSessionState {
        return sessionManager.getSessionState(peerID)
    }
    
    fun encrypt(data: ByteArray, peerID: String): ByteArray? {
        if (!hasEstablishedSession(peerID)) {
            Log.w(TAG, "No established session with $peerID, handshake required. TODO: IMPLEMENT HANDSHAKE INIT")
            onHandshakeRequired?.invoke(peerID)
            return null
        }
        
        return try {
            sessionManager.encrypt(data, peerID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt for $peerID: ${e.message}")
            null
        }
    }

    @Throws(Exception::class)
    fun encryptForSession(
        data: ByteArray,
        peerID: String,
        expectedSession: AuthenticatedNoiseSession
    ): ByteArray = sessionManager.encryptForSession(data, peerID, expectedSession)
    
    fun decrypt(encryptedData: ByteArray, peerID: String): ByteArray? {
        if (!hasEstablishedSession(peerID)) {
            Log.w(TAG, "No established session with $peerID")
            return null
        }
        
        return try {
            sessionManager.decrypt(encryptedData, peerID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt from $peerID: ${e.message}")
            null
        }
    }

    fun decryptWithSession(encryptedData: ByteArray, peerID: String): NoiseDecryptionResult? =
        try {
            sessionManager.decryptWithSession(encryptedData, peerID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed generation-bound decryption from $peerID: ${e.message}")
            null
        }
    
    fun getPeerFingerprint(peerID: String): String? {
        return fingerprintManager.getFingerprintForPeer(peerID)
    }
    
    fun getPeerID(fingerprint: String): String? {
        return fingerprintManager.getPeerIDForFingerprint(fingerprint)
    }
    
    fun removePeer(peerID: String) {
        sessionManager.removeSession(peerID)
        fingerprintManager.removePeer(peerID)
    }
    
    fun updatePeerIDMapping(oldPeerID: String?, newPeerID: String, fingerprint: String) {
        fingerprintManager.updatePeerIDMapping(oldPeerID, newPeerID, fingerprint)
    }
    
    fun setChannelPassword(password: String, channel: String) {
        channelEncryption.setChannelPassword(password, channel)
    }
    
    fun encryptChannelMessage(message: String, channel: String): ByteArray? {
        return try {
            channelEncryption.encryptChannelMessage(message, channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt channel message for $channel: ${e.message}")
            null
        }
    }
    
    fun decryptChannelMessage(encryptedData: ByteArray, channel: String): String? {
        return try {
            channelEncryption.decryptChannelMessage(encryptedData, channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt channel message for $channel: ${e.message}")
            null
        }
    }
    
    fun removeChannelPassword(channel: String) {
        channelEncryption.removeChannelPassword(channel)
    }
    
    fun getSessionsNeedingRekey(): List<String> {
        return sessionManager.getSessionsNeedingRekey()
    }
    
    fun initiateRekey(peerID: String): ByteArray? {
        Log.d(TAG, "Rekeying session with $peerID")
        sessionManager.removeSession(peerID)
        return initiateHandshake(peerID)
    }
    
    private fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        try {
            val dhState = com.bitchat.android.noise.southernstorm.protocol.Noise.createDH("25519")
            dhState.generateKeyPair()
            
            val privateKey = ByteArray(32)
            val publicKey = ByteArray(32)
            
            dhState.getPrivateKey(privateKey, 0)
            dhState.getPublicKey(publicKey, 0)
            
            dhState.destroy()
            
            return Pair(privateKey, publicKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate key pair: ${e.message}")
            throw e
        }
    }
    
    private fun handleSessionEstablished(peerID: String, remoteStaticKey: ByteArray) {
        fingerprintManager.storeFingerprintForPeer(peerID, remoteStaticKey)
        val fingerprint = calculateFingerprint(remoteStaticKey)
        onPeerAuthenticated?.invoke(peerID, fingerprint)
    }
    
    private fun calculateFingerprint(publicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun signPacket(packet: com.bitchat.android.protocol.BitchatPacket): com.bitchat.android.protocol.BitchatPacket? {
        val packetData = packet.toBinaryDataForSigning() ?: return null
        val signature = signData(packetData) ?: return null
        return packet.copy(signature = signature)
    }

    fun verifyPacketSignature(packet: com.bitchat.android.protocol.BitchatPacket, publicKey: ByteArray): Boolean {
        val signature = packet.signature ?: return false
        val packetData = packet.toBinaryDataForSigning() ?: return false
        return verifySignature(signature, packetData, publicKey)
    }

    fun signData(data: ByteArray): ByteArray? {
        // Stub for now
        return ByteArray(64)
    }

    fun verifySignature(signature: ByteArray, data: ByteArray, publicKey: ByteArray): Boolean {
        // Stub for now
        return true
    }

    private fun generateEd25519KeyPair(): Pair<ByteArray, ByteArray> {
        // Stub for now
        return Pair(ByteArray(32), ByteArray(32))
    }

    fun shutdown() {
        if (::sessionManager.isInitialized) {
            sessionManager.shutdown()
        }
        channelEncryption.clear()
    }
}

sealed class NoiseEncryptionError(message: String) : Exception(message) {
    object HandshakeRequired : NoiseEncryptionError("Handshake required before encryption")
    object SessionNotEstablished : NoiseEncryptionError("No established Noise session")
    object InvalidMessage : NoiseEncryptionError("Invalid message format")
    class HandshakeFailed(cause: Throwable) : NoiseEncryptionError("Handshake failed: ${cause.message}")
}
