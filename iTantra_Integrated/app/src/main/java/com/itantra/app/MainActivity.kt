package com.itantra.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itantra.app.diagnostics.PipelineTelemetry
import com.itantra.app.models.ModelsActivity
import com.itantra.app.pipeline.VoicePipeline
import com.itantra.app.protocol.VoicePayload
import com.itantra.app.transport.MeshTransport
import com.itantra.stt.R
import com.itantra.stt.databinding.ActivityItantraBinding
import com.itantra.stt.model.Language
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The integrated iTantra app: speak into one phone, hear it out of another, with
 * no internet anywhere in the loop.
 *
 *   mic -> STT (sherpa-onnx, on device) -> text payload
 *       -> bitchat mesh (BLE + Wi-Fi Aware)
 *       -> text payload -> TTS (sherpa-onnx, on device) -> speaker
 *
 * This activity is deliberately thin: [MeshTransport] owns the radios,
 * [VoicePipeline] owns the speech models and the push-to-talk state machine, and
 * everything measurable lands in [PipelineTelemetry].
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val MAX_LOG_LINES = 200
    }

    private lateinit var binding: ActivityItantraBinding
    private lateinit var transport: MeshTransport
    private lateinit var pipeline: VoicePipeline

    private val logLines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var recordingTimerJob: Job? = null
    private var captureStartedAt = 0L
    private var lastIncoming: MeshTransport.Incoming? = null
    private var diagnosticsVisible = false
    private var selectedLanguage: Language = Language.EN
    private var pendingTalkAfterPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] != false
        if (!micGranted) {
            appendLog("Microphone permission denied - the send path is disabled.")
            Toast.makeText(this, "Microphone permission is required to talk.", Toast.LENGTH_LONG)
                .show()
        }
        startMesh()
        if (pendingTalkAfterPermission && micGranted) {
            pendingTalkAfterPermission = false
            beginTalking()
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityItantraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transport = MeshTransport(applicationContext).apply {
            onLog = { line -> runOnUiThread { appendLog(line) } }
            onStatusChanged = { status -> runOnUiThread { renderStatus(status) } }
            onMessage = { incoming -> runOnUiThread { handleIncoming(incoming) } }
        }

        pipeline = VoicePipeline(applicationContext, lifecycleScope, transport).apply {
            onLog = { line -> runOnUiThread { appendLog(line) } }
            onState = { state -> runOnUiThread { renderPipelineState(state) } }
            onTranscript = { text -> runOnUiThread { renderOutgoingTranscript(text) } }
            onTelemetryChanged = { runOnUiThread { renderDiagnostics() } }
            priorityProvider = {
                if (binding.switchAlertPriority.isChecked) VoicePayload.Priority.ALERT
                else VoicePayload.Priority.NORMAL
            }
        }

        binding.tvDeviceIdentity.text = "Device: ${transport.deviceName}"

        wireLanguageChips()
        wirePushToTalk()
        wireVadToggle()
        wirePlaybackControls()
        wireDiagnostics()

        requestRuntimePermissions()
        loadLanguage(Language.EN)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the Models screen, a language that was missing may
        // now be installed - retry it rather than leaving the button disabled.
        if (!binding.btnPushToTalk.isEnabled &&
            pipeline.missingPacksFor(selectedLanguage).isEmpty()
        ) {
            loadLanguage(selectedLanguage)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingTimerJob?.cancel()
        pipeline.release()
        transport.stop()
    }

    // ------------------------------------------------------------------
    // Permissions and mesh bring-up
    // ------------------------------------------------------------------

    private fun requestRuntimePermissions() {
        val required = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required += Manifest.permission.BLUETOOTH_SCAN
            required += Manifest.permission.BLUETOOTH_CONNECT
            required += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            required += Manifest.permission.BLUETOOTH
            required += Manifest.permission.BLUETOOTH_ADMIN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required += Manifest.permission.NEARBY_WIFI_DEVICES
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startMesh()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startMesh() {
        transport.start()
        transport.localPeerId()?.let {
            binding.tvDeviceIdentity.text = "Device: ${transport.deviceName}  |  Peer ${it.take(8)}"
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------
    // Language selection
    // ------------------------------------------------------------------

    private fun wireLanguageChips() {
        binding.chipGroupLanguages.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val language = when (checkedIds.first()) {
                R.id.chipLangEn -> Language.EN
                R.id.chipLangHi -> Language.HI
                R.id.chipLangGu -> Language.GU
                R.id.chipLangMr -> Language.MR
                R.id.chipLangBn -> Language.BN
                R.id.chipLangTa -> Language.TA
                R.id.chipLangTe -> Language.TE
                R.id.chipLangKn -> Language.KN
                R.id.chipLangMl -> Language.ML
                R.id.chipLangOr -> Language.OR
                else -> Language.EN
            }
            loadLanguage(language)
        }
    }

    private fun loadLanguage(language: Language) {
        selectedLanguage = language
        binding.btnPushToTalk.isEnabled = false
        binding.tvModelStatus.text = "Models: loading ${language.displayName}…"
        binding.tvModelStatus.setTextColor(ContextCompat.getColor(this, R.color.status_processing))

        lifecycleScope.launch {
            val started = SystemClock.elapsedRealtime()
            val result = pipeline.prepare(language)
            val elapsed = SystemClock.elapsedRealtime() - started

            when (result) {
                is VoicePipeline.PrepareResult.Ready -> {
                    binding.tvModelStatus.text =
                        "Models: ${language.displayName} STT + TTS ready (${elapsed} ms)"
                    binding.tvModelStatus.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.status_ready)
                    )
                    binding.btnPushToTalk.isEnabled = true
                }

                is VoicePipeline.PrepareResult.MissingPacks -> {
                    val size = result.packs.sumOf { it.totalBytes }
                    binding.tvModelStatus.text = String.format(
                        Locale.US,
                        "%s not installed — tap Language models (%.0f MB)",
                        language.displayName,
                        size / 1_048_576.0
                    )
                    binding.tvModelStatus.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.status_processing)
                    )
                }

                is VoicePipeline.PrepareResult.Failed -> {
                    binding.tvModelStatus.text =
                        "Models: ${language.displayName} failed to load"
                    binding.tvModelStatus.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.accent_red)
                    )
                }
            }
            renderDiagnostics()
        }
    }

    // ------------------------------------------------------------------
    // Push to talk
    // ------------------------------------------------------------------

    private fun wirePushToTalk() {
        binding.btnPushToTalk.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    if (hasMicPermission()) {
                        beginTalking()
                    } else {
                        pendingTalkAfterPermission = true
                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    view.performClick()
                    endTalking()
                    true
                }
                else -> false
            }
        }
    }

    private fun wireVadToggle() {
        pipeline.vadAutoStopEnabled = binding.switchVadAutoStop.isChecked
        binding.switchVadAutoStop.setOnCheckedChangeListener { _, checked ->
            pipeline.vadAutoStopEnabled = checked
        }
    }

    private fun beginTalking() {
        if (!pipeline.startTalking()) return

        captureStartedAt = SystemClock.elapsedRealtime()
        binding.btnPushToTalk.text = "RELEASE TO SEND"
        binding.btnPushToTalk.backgroundTint(R.color.accent_red)

        recordingTimerJob?.cancel()
        recordingTimerJob = lifecycleScope.launch {
            while (isActive) {
                val seconds = (SystemClock.elapsedRealtime() - captureStartedAt) / 1000f
                binding.tvRecordingTimer.text = String.format(Locale.US, "%.1f s", seconds)
                delay(100)
            }
        }
    }

    private fun endTalking() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        binding.btnPushToTalk.text = "HOLD TO TALK"
        binding.btnPushToTalk.backgroundTint(R.color.accent_blue)

        val priority =
            if (binding.switchAlertPriority.isChecked) VoicePayload.Priority.ALERT
            else VoicePayload.Priority.NORMAL
        pipeline.stopTalkingAndSend(priority)
    }

    // ------------------------------------------------------------------
    // Receive side
    // ------------------------------------------------------------------

    private fun handleIncoming(incoming: MeshTransport.Incoming) {
        lastIncoming = incoming
        binding.btnReplayIncoming.isEnabled = true

        val payload = incoming.payload
        val languageLabel = payload.language()?.displayName ?: "unknown language"
        val alert = payload.priority == VoicePayload.Priority.ALERT

        binding.tvIncomingHeader.text = buildString {
            append("LAST RECEIVED — ")
            append(incoming.senderLabel)
            append(" · ")
            append(languageLabel)
            append(" · ")
            append(incoming.link.displayName)
            if (incoming.relayed) append(" · relayed")
            if (alert) append(" · ALERT")
        }
        binding.tvIncomingHeader.setTextColor(
            ContextCompat.getColor(this, if (alert) R.color.accent_red else R.color.text_muted)
        )
        binding.tvIncomingText.text = payload.text

        appendLog("Received from ${incoming.senderLabel} over ${incoming.link.displayName}")
        pipeline.submitIncoming(incoming)
    }

    private fun wirePlaybackControls() {
        binding.btnReplayIncoming.setOnClickListener {
            lastIncoming?.let { pipeline.submitIncoming(it) }
        }
        binding.btnStopPlayback.setOnClickListener {
            pipeline.stopPlayback()
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    private fun wireDiagnostics() {
        binding.btnToggleDiagnostics.setOnClickListener {
            diagnosticsVisible = !diagnosticsVisible
            binding.tvDiagnostics.visibility = if (diagnosticsVisible) View.VISIBLE else View.GONE
            binding.btnToggleDiagnostics.text = if (diagnosticsVisible) "Hide" else "Show"
            renderDiagnostics()
        }
        binding.btnManageModels.setOnClickListener {
            startActivity(Intent(this, ModelsActivity::class.java))
        }
        binding.btnOpenTestBench.setOnClickListener {
            startActivity(Intent(this, com.itantra.stt.MainActivity::class.java))
        }
    }

    private fun renderDiagnostics() {
        if (!diagnosticsVisible) return
        binding.tvDiagnostics.text = PipelineTelemetry.render()
    }

    // ------------------------------------------------------------------
    // Rendering helpers
    // ------------------------------------------------------------------

    private fun renderStatus(status: MeshTransport.Status) {
        val connected = status.peerCount > 0
        binding.tvConnectionStatus.text = when {
            connected -> "● Connected — ${status.peerCount} peer(s)"
            status.bleActive || status.wifiAwareActive -> "● Searching for peers…"
            else -> "● Offline — no radio active"
        }
        binding.tvConnectionStatus.setTextColor(
            when {
                connected -> Color.parseColor("#10B981")
                status.bleActive || status.wifiAwareActive -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#EF4444")
            }
        )
        binding.tvLinkStatus.text = buildString {
            append("BLE ")
            append(if (status.bleActive) "●" else "○")
            append("   Wi-Fi Aware ")
            append(if (status.wifiAwareActive) "●" else "○")
        }
    }

    private fun renderPipelineState(state: VoicePipeline.State) {
        binding.tvPipelineState.text = when (state) {
            VoicePipeline.State.IDLE ->
                if (pipeline.vadAutoStopEnabled) "Idle — hold to talk, pause to auto-send"
                else "Idle — hold the button to talk"
            VoicePipeline.State.LOADING_MODELS -> "Loading speech models…"
            VoicePipeline.State.LISTENING -> "Listening…"
            VoicePipeline.State.TRANSCRIBING -> "Transcribing on device…"
            VoicePipeline.State.TRANSMITTING -> "Transmitting over mesh…"
            VoicePipeline.State.SYNTHESIZING -> "Synthesizing incoming speech…"
            VoicePipeline.State.SPEAKING -> "Playing incoming message…"
            VoicePipeline.State.ERROR -> "Error — see the event log"
        }
        binding.tvPipelineState.setTextColor(
            when (state) {
                VoicePipeline.State.LISTENING -> Color.parseColor("#EF4444")
                VoicePipeline.State.ERROR -> Color.parseColor("#DC2626")
                VoicePipeline.State.IDLE -> Color.parseColor("#64748B")
                else -> Color.parseColor("#F59E0B")
            }
        )
    }

    private fun renderOutgoingTranscript(text: String) {
        binding.tvOutgoingTranscript.text =
            if (text.isBlank()) "[no speech recognized]" else text
    }

    private fun appendLog(message: String) {
        logLines.addLast("${timeFormat.format(Date())}  $message")
        while (logLines.size > MAX_LOG_LINES) logLines.removeFirst()
        binding.tvEventLog.text = logLines.joinToString("\n")
        binding.scrollLog.post { binding.scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    private fun View.backgroundTint(colorRes: Int) {
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
    }
}
