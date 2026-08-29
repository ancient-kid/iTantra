package com.itantra.stt

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itantra.stt.audio.AudioRecorder
import com.itantra.stt.benchmark.CpuMetrics
import com.itantra.stt.benchmark.MemoryMetrics
import com.itantra.stt.benchmark.SttMetrics
import com.itantra.stt.databinding.ActivityMainBinding
import com.itantra.stt.model.Language
import com.itantra.stt.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Dual-Model Speech-to-Text Evaluation Test Bench (English + IndicConformer).
 */
class MainActivity : AppCompatActivity() {

    private enum class AppState {
        IDLE,
        RECORDING,
        PROCESSING,
        COMPLETE,
        ERROR
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var modelManager: ModelManager
    private val audioRecorder = AudioRecorder()

    private var currentState = AppState.IDLE
    private var selectedLanguage = Language.EN
    private var recordingStartTime = 0L
    private var timerJob: Job? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                hideError()
                startRecordingFlow()
            } else {
                showError("Microphone permission is required.")
                Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelManager = ModelManager(applicationContext)

        setupLanguageChips()
        setupListeners()
        switchLanguage(Language.EN)
    }

    private fun setupLanguageChips() {
        binding.chipGroupLanguages.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val targetLang = when (checkedIds.first()) {
                R.id.chipEn -> Language.EN
                R.id.chipHi -> Language.HI
                R.id.chipGu -> Language.GU
                R.id.chipMr -> Language.MR
                R.id.chipKn -> Language.KN
                R.id.chipMl -> Language.ML
                R.id.chipTa -> Language.TA
                R.id.chipTe -> Language.TE
                R.id.chipBn -> Language.BN
                R.id.chipOr -> Language.OR
                else -> Language.EN
            }

            if (currentState == AppState.RECORDING) {
                audioRecorder.stopRecording()
                currentState = AppState.IDLE
                resetButtonUi()
            }

            switchLanguage(targetLang)
        }
    }

    private fun setupListeners() {
        binding.btnRecord.setOnClickListener {
            when (currentState) {
                AppState.IDLE, AppState.COMPLETE, AppState.ERROR -> {
                    checkAndStartRecording()
                }
                AppState.RECORDING -> {
                    stopRecordingAndTranscribe()
                }
                AppState.PROCESSING -> {
                    // Do nothing while busy
                }
            }
        }
    }

    private fun switchLanguage(language: Language) {
        selectedLanguage = language
        hideError()

        if (!language.isSupported) {
            binding.tvActiveModelName.text = "Odia IndicConformer"
            binding.tvActiveModelDetails.text = "Model pending mobile INT8 export verification"
            binding.tvActiveModelStatus.text = "⚠ Not yet available"
            binding.tvActiveModelStatus.setTextColor(Color.parseColor("#F59E0B"))
            binding.tvModelLoadDuration.text = "-"
            binding.btnRecord.isEnabled = false
            return
        }

        binding.tvActiveModelStatus.text = "Loading ${language.displayName} model…"
        binding.tvActiveModelStatus.setTextColor(Color.parseColor("#F59E0B"))
        binding.tvModelLoadDuration.text = "Loading…"
        binding.btnRecord.isEnabled = false

        lifecycleScope.launch {
            val memBefore = MemoryMetrics.captureSnapshot()
            val startTime = SystemClock.elapsedRealtime()
            val success = modelManager.loadLanguage(language)
            val loadTimeMs = SystemClock.elapsedRealtime() - startTime

            if (success) {
                val modelInfo = modelManager.getCurrentModelInfo()
                binding.tvActiveModelName.text = modelInfo?.modelName ?: language.displayName
                binding.tvActiveModelDetails.text = "Format: ${modelInfo?.format} | Quantization: ${modelInfo?.quantization} | Family: ${modelInfo?.familyName}"
                binding.tvActiveModelStatus.text = "● ${language.displayName} Model Ready ✓"
                binding.tvActiveModelStatus.setTextColor(Color.parseColor("#10B981"))
                binding.tvModelLoadDuration.text = "Load: $loadTimeMs ms"
                binding.btnRecord.isEnabled = true

                val memAfter = MemoryMetrics.captureSnapshot()
                binding.tvMemoryStats.text = String.format(
                    Locale.US,
                    "Heap: %.1f MB (Loaded: %.1f MB) | Native: %.1f MB",
                    memAfter.usedHeapMb,
                    memAfter.usedHeapMb - memBefore.usedHeapMb,
                    memAfter.nativeHeapMb
                )
            } else {
                binding.tvActiveModelStatus.text = "Failed to load model"
                binding.tvActiveModelStatus.setTextColor(Color.parseColor("#DC2626"))
                binding.tvModelLoadDuration.text = "Error"
                showError("Could not load STT model for ${language.displayName}")
            }
        }
    }

    private fun checkAndStartRecording() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            hideError()
            startRecordingFlow()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingFlow() {
        val started = audioRecorder.startRecording(lifecycleScope)
        if (!started) {
            showError("Failed to initialize microphone.")
            return
        }

        currentState = AppState.RECORDING
        recordingStartTime = SystemClock.elapsedRealtime()

        // Update UI
        binding.btnRecord.text = "STOP RECORDING"
        binding.btnRecord.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
        binding.tvRecordingStatus.text = "Status: Recording speech (${selectedLanguage.displayName})…"
        binding.tvRecordingStatus.setTextColor(Color.parseColor("#DC2626"))
        binding.tvTranscript.text = "Listening for speech…"

        // Start timer
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isActive && currentState == AppState.RECORDING) {
                val elapsedSec = (SystemClock.elapsedRealtime() - recordingStartTime) / 1000f
                binding.tvRecordingTimer.text = String.format(Locale.US, "%.1f s", elapsedSec)
                val cpu = CpuMetrics.sampleProcessCpuUsage()
                binding.tvCpuUsage.text = String.format(Locale.US, "%.1f %%", cpu)
                delay(100)
            }
        }
    }

    private fun stopRecordingAndTranscribe() {
        timerJob?.cancel()
        timerJob = null

        val pcmAudio = audioRecorder.stopRecording()
        val finalRecordingSec = (SystemClock.elapsedRealtime() - recordingStartTime) / 1000f
        binding.tvRecordingTimer.text = String.format(Locale.US, "%.2f s", finalRecordingSec)

        if (pcmAudio.isEmpty()) {
            currentState = AppState.IDLE
            resetButtonUi()
            showError("No speech recorded.")
            binding.tvTranscript.text = "No audio recorded."
            return
        }

        currentState = AppState.PROCESSING
        binding.btnRecord.isEnabled = false
        binding.tvRecordingStatus.text = "Status: Processing STT inference…"
        binding.tvRecordingStatus.setTextColor(Color.parseColor("#F59E0B"))
        binding.tvTranscript.text = "Transcribing with ${modelManager.getCurrentModelInfo()?.modelName}…"

        lifecycleScope.launch {
            val engine = modelManager.getCurrentEngine()
            if (engine == null || !engine.isLoaded()) {
                showError("STT model is not ready.")
                resetButtonUi()
                currentState = AppState.ERROR
                return@launch
            }

            val result = engine.transcribe(pcmAudio, AudioRecorder.SAMPLE_RATE)
            val memAfterInfer = MemoryMetrics.captureSnapshot()
            val cpuUsage = CpuMetrics.sampleProcessCpuUsage()

            withContext(Dispatchers.Main) {
                currentState = AppState.COMPLETE
                resetButtonUi()

                // Display transcript
                if (result.text.isNotEmpty()) {
                    binding.tvTranscript.text = result.text
                } else {
                    binding.tvTranscript.text = "[No speech recognized]"
                }

                // Display benchmark metrics
                val audioDurationSec = result.audioDurationMs / 1000f
                binding.tvAudioDuration.text = SttMetrics.formatDuration(audioDurationSec)
                binding.tvInferenceTime.text = "${result.inferenceTimeMs} ms"
                binding.tvRtf.text = SttMetrics.formatRtf(result.rtf)

                if (result.rtf <= 1.0f) {
                    val speedup = if (result.rtf > 0) 1.0f / result.rtf else 1.0f
                    binding.tvRtfBadge.text = String.format(Locale.US, "✓ Real-time (%.1fx)", speedup)
                    binding.tvRtfBadge.setTextColor(Color.parseColor("#10B981"))
                } else {
                    binding.tvRtfBadge.text = "⚠ Slower than real-time"
                    binding.tvRtfBadge.setTextColor(Color.parseColor("#F59E0B"))
                }

                binding.tvCpuUsage.text = String.format(Locale.US, "%.1f %%", cpuUsage)
                binding.tvMemoryStats.text = String.format(
                    Locale.US,
                    "Heap: %.1f MB (Used: %.1f MB) | Native: %.1f MB",
                    memAfterInfer.totalHeapMb,
                    memAfterInfer.usedHeapMb,
                    memAfterInfer.nativeHeapMb
                )
            }
        }
    }

    private fun resetButtonUi() {
        binding.btnRecord.isEnabled = true
        binding.btnRecord.text = "START RECORDING"
        binding.btnRecord.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))
        binding.tvRecordingStatus.text = "Status: Idle (${selectedLanguage.displayName})"
        binding.tvRecordingStatus.setTextColor(Color.parseColor("#64748B"))
    }

    private fun showError(message: String) {
        binding.tvErrorMessage.text = message
        binding.tvErrorMessage.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvErrorMessage.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        if (audioRecorder.isRecording()) {
            audioRecorder.stopRecording()
        }
        lifecycleScope.launch {
            modelManager.unloadCurrentModel()
        }
    }
}
