package com.itantra.stt

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itantra.stt.audio.AudioPlayer
import com.itantra.stt.audio.AudioRecorder
import com.itantra.stt.benchmark.CpuMetrics
import com.itantra.stt.benchmark.MemoryMetrics
import com.itantra.stt.benchmark.SttMetrics
import com.itantra.stt.benchmark.TtsMetrics
import com.itantra.stt.databinding.ActivityMainBinding
import com.itantra.stt.model.Language
import com.itantra.stt.model.ModelManager
import com.itantra.stt.model.TtsManager
import com.itantra.stt.tts.TtsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Unified Speech-to-Text (STT) + Text-to-Speech (TTS) Test Bench for iTantra.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private enum class SttState {
        IDLE,
        RECORDING,
        PROCESSING,
        COMPLETE,
        ERROR
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var sttModelManager: ModelManager
    private lateinit var ttsManager: TtsManager

    private val audioRecorder = AudioRecorder()
    private lateinit var audioPlayer: AudioPlayer

    private var currentSttState = SttState.IDLE
    private var selectedSttLanguage = Language.EN
    private var selectedTtsLanguage = Language.EN

    private var sttRecordingStartTime = 0L
    private var sttTimerJob: Job? = null

    private var lastTtsResult: TtsResult? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                hideSttError()
                startRecordingFlow()
            } else {
                showSttError("Microphone permission is required for STT.")
                Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sttModelManager = ModelManager(applicationContext)
        ttsManager = TtsManager(applicationContext)
        audioPlayer = AudioPlayer(applicationContext)

        setupNavigationTabs()
        setupSttUi()
        setupTtsUi()

        // Load STT default language on startup (TTS is loaded lazily on tab switch)
        switchSttLanguage(Language.EN)
    }

    // ========================================================
    // TAB NAVIGATION (STT vs TTS)
    // ========================================================
    private fun setupNavigationTabs() {
        binding.tabToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            when (checkedId) {
                R.id.btnTabStt -> {
                    binding.layoutSttContainer.visibility = View.VISIBLE
                    binding.layoutTtsContainer.visibility = View.GONE
                    audioPlayer.stop()
                    binding.btnPlaySpeech.isEnabled = lastTtsResult != null && lastTtsResult!!.audio.isNotEmpty()
                    binding.btnStopSpeech.isEnabled = false
                }
                R.id.btnTabTts -> {
                    binding.layoutSttContainer.visibility = View.GONE
                    binding.layoutTtsContainer.visibility = View.VISIBLE
                    if (currentSttState == SttState.RECORDING) {
                        audioRecorder.stopRecording()
                        currentSttState = SttState.IDLE
                        resetSttButtonUi()
                    }
                    if (!ttsManager.isLoaded()) {
                        switchTtsLanguage(selectedTtsLanguage)
                    }
                }
            }
        }
    }

    // ========================================================
    // STT MODULE
    // ========================================================
    private fun setupSttUi() {
        binding.chipGroupSttLanguages.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val targetLang = when (checkedIds.first()) {
                R.id.chipSttEn -> Language.EN
                R.id.chipSttHi -> Language.HI
                R.id.chipSttGu -> Language.GU
                R.id.chipSttMr -> Language.MR
                R.id.chipSttKn -> Language.KN
                R.id.chipSttMl -> Language.ML
                R.id.chipSttTa -> Language.TA
                R.id.chipSttTe -> Language.TE
                R.id.chipSttBn -> Language.BN
                R.id.chipSttOr -> Language.OR
                else -> Language.EN
            }

            if (currentSttState == SttState.RECORDING) {
                audioRecorder.stopRecording()
                currentSttState = SttState.IDLE
                resetSttButtonUi()
            }

            switchSttLanguage(targetLang)
        }

        binding.btnRecord.setOnClickListener {
            when (currentSttState) {
                SttState.IDLE, SttState.COMPLETE, SttState.ERROR -> checkAndStartRecording()
                SttState.RECORDING -> stopRecordingAndTranscribe()
                SttState.PROCESSING -> {}
            }
        }
    }

    private fun switchSttLanguage(language: Language) {
        selectedSttLanguage = language
        hideSttError()

        if (!language.isSupported) {
            binding.tvActiveSttModelName.text = "Odia IndicConformer"
            binding.tvActiveSttModelDetails.text = "Model pending mobile export verification"
            binding.tvActiveSttModelStatus.text = "⚠ Not yet available"
            binding.tvActiveSttModelStatus.setTextColor(Color.parseColor("#F59E0B"))
            binding.tvSttModelLoadDuration.text = "-"
            binding.btnRecord.isEnabled = false
            return
        }

        binding.tvActiveSttModelStatus.text = "Loading ${language.displayName} model…"
        binding.tvActiveSttModelStatus.setTextColor(Color.parseColor("#F59E0B"))
        binding.tvSttModelLoadDuration.text = "Loading…"
        binding.btnRecord.isEnabled = false

        lifecycleScope.launch {
            try {
                val memBefore = MemoryMetrics.captureSnapshot()
                val startTime = SystemClock.elapsedRealtime()
                val success = sttModelManager.loadLanguage(language)
                val loadTimeMs = SystemClock.elapsedRealtime() - startTime

                if (success) {
                    val modelInfo = sttModelManager.getCurrentModelInfo()
                    binding.tvActiveSttModelName.text = modelInfo?.modelName ?: language.displayName
                    binding.tvActiveSttModelDetails.text = "Format: ${modelInfo?.format} | Quantization: ${modelInfo?.quantization} | Family: ${modelInfo?.familyName}"
                    binding.tvActiveSttModelStatus.text = "● ${language.displayName} Model Ready ✓"
                    binding.tvActiveSttModelStatus.setTextColor(Color.parseColor("#10B981"))
                    binding.tvSttModelLoadDuration.text = "Load: $loadTimeMs ms"
                    binding.btnRecord.isEnabled = true

                    val memAfter = MemoryMetrics.captureSnapshot()
                    binding.tvSttMemoryStats.text = String.format(
                        Locale.US,
                        "Heap: %.1f MB (Loaded: %.1f MB) | Native: %.1f MB",
                        memAfter.usedHeapMb,
                        memAfter.usedHeapMb - memBefore.usedHeapMb,
                        memAfter.nativeHeapMb
                    )
                } else {
                    binding.tvActiveSttModelStatus.text = "Failed to load model"
                    binding.tvActiveSttModelStatus.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvSttModelLoadDuration.text = "Error"
                    showSttError("Could not load STT model for ${language.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception switching STT language", e)
                showSttError("STT loading error: ${e.message}")
            }
        }
    }

    private fun checkAndStartRecording() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            hideSttError()
            startRecordingFlow()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingFlow() {
        val started = audioRecorder.startRecording(lifecycleScope)
        if (!started) {
            showSttError("Failed to initialize microphone.")
            return
        }

        currentSttState = SttState.RECORDING
        sttRecordingStartTime = SystemClock.elapsedRealtime()

        binding.btnRecord.text = "STOP RECORDING"
        binding.btnRecord.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
        binding.tvRecordingStatus.text = "Status: Recording speech (${selectedSttLanguage.displayName})…"
        binding.tvRecordingStatus.setTextColor(Color.parseColor("#DC2626"))
        binding.tvTranscript.text = "Listening for speech…"

        sttTimerJob?.cancel()
        sttTimerJob = lifecycleScope.launch {
            while (isActive && currentSttState == SttState.RECORDING) {
                val elapsedSec = (SystemClock.elapsedRealtime() - sttRecordingStartTime) / 1000f
                binding.tvRecordingTimer.text = String.format(Locale.US, "%.1f s", elapsedSec)
                val cpu = CpuMetrics.sampleProcessCpuUsage()
                binding.tvSttCpuUsage.text = String.format(Locale.US, "%.1f %%", cpu)
                delay(100)
            }
        }
    }

    private fun stopRecordingAndTranscribe() {
        sttTimerJob?.cancel()
        sttTimerJob = null

        val pcmAudio = audioRecorder.stopRecording()
        val finalSec = (SystemClock.elapsedRealtime() - sttRecordingStartTime) / 1000f
        binding.tvRecordingTimer.text = String.format(Locale.US, "%.2f s", finalSec)

        if (pcmAudio.isEmpty()) {
            currentSttState = SttState.IDLE
            resetSttButtonUi()
            showSttError("No speech recorded.")
            binding.tvTranscript.text = "No audio recorded."
            return
        }

        currentSttState = SttState.PROCESSING
        binding.btnRecord.isEnabled = false
        binding.tvRecordingStatus.text = "Status: Processing STT inference…"
        binding.tvRecordingStatus.setTextColor(Color.parseColor("#F59E0B"))
        binding.tvTranscript.text = "Transcribing with ${sttModelManager.getCurrentModelInfo()?.modelName}…"

        lifecycleScope.launch {
            val engine = sttModelManager.getCurrentEngine()
            if (engine == null || !engine.isLoaded()) {
                showSttError("STT model is not ready.")
                resetSttButtonUi()
                currentSttState = SttState.ERROR
                return@launch
            }

            try {
                val result = engine.transcribe(pcmAudio, AudioRecorder.SAMPLE_RATE)
                val memAfterInfer = MemoryMetrics.captureSnapshot()
                val cpuUsage = CpuMetrics.sampleProcessCpuUsage()

                withContext(Dispatchers.Main) {
                    currentSttState = SttState.COMPLETE
                    resetSttButtonUi()

                    binding.tvTranscript.text = if (result.text.isNotEmpty()) result.text else "[No speech recognized]"

                    val audioDurationSec = result.audioDurationMs / 1000f
                    binding.tvSttAudioDuration.text = SttMetrics.formatDuration(audioDurationSec)
                    binding.tvSttInferenceTime.text = "${result.inferenceTimeMs} ms"
                    binding.tvSttRtf.text = SttMetrics.formatRtf(result.rtf)

                    if (result.rtf <= 1.0f) {
                        val speedup = if (result.rtf > 0) 1.0f / result.rtf else 1.0f
                        binding.tvSttRtfBadge.text = String.format(Locale.US, "✓ Real-time (%.1fx)", speedup)
                        binding.tvSttRtfBadge.setTextColor(Color.parseColor("#10B981"))
                    } else {
                        binding.tvSttRtfBadge.text = "⚠ Slower than real-time"
                        binding.tvSttRtfBadge.setTextColor(Color.parseColor("#F59E0B"))
                    }

                    binding.tvSttCpuUsage.text = String.format(Locale.US, "%.1f %%", cpuUsage)
                    binding.tvSttMemoryStats.text = String.format(
                        Locale.US,
                        "Heap: %.1f MB (Used: %.1f MB) | Native: %.1f MB",
                        memAfterInfer.totalHeapMb,
                        memAfterInfer.usedHeapMb,
                        memAfterInfer.nativeHeapMb
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "STT transcription error", e)
                withContext(Dispatchers.Main) {
                    currentSttState = SttState.ERROR
                    resetSttButtonUi()
                    showSttError("STT transcription error: ${e.message}")
                }
            }
        }
    }

    private fun resetSttButtonUi() {
        binding.btnRecord.isEnabled = true
        binding.btnRecord.text = "START RECORDING"
        binding.btnRecord.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))
        binding.tvRecordingStatus.text = "Status: Idle (${selectedSttLanguage.displayName})"
        binding.tvRecordingStatus.setTextColor(Color.parseColor("#64748B"))
    }

    private fun showSttError(message: String) {
        binding.tvSttErrorMessage.text = message
        binding.tvSttErrorMessage.visibility = View.VISIBLE
    }

    private fun hideSttError() {
        binding.tvSttErrorMessage.visibility = View.GONE
    }

    // ========================================================
    // TTS MODULE
    // ========================================================
    private fun setupTtsUi() {
        binding.chipGroupTtsLanguages.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val targetLang = when (checkedIds.first()) {
                R.id.chipTtsEn -> Language.EN
                R.id.chipTtsHi -> Language.HI
                R.id.chipTtsGu -> Language.GU
                R.id.chipTtsMr -> Language.MR
                R.id.chipTtsKn -> Language.KN
                R.id.chipTtsMl -> Language.ML
                R.id.chipTtsTa -> Language.TA
                R.id.chipTtsTe -> Language.TE
                R.id.chipTtsBn -> Language.BN
                R.id.chipTtsOr -> Language.OR
                else -> Language.EN
            }

            audioPlayer.stop()
            switchTtsLanguage(targetLang)
        }

        // Sentence sample chips
        binding.chipSampleNormal.setOnClickListener {
            binding.etTtsInput.setText(getSampleText(selectedTtsLanguage, "Normal"))
        }
        binding.chipSampleEmergency.setOnClickListener {
            binding.etTtsInput.setText(getSampleText(selectedTtsLanguage, "Emergency"))
        }
        binding.chipSampleNumbers.setOnClickListener {
            binding.etTtsInput.setText(getSampleText(selectedTtsLanguage, "Numbers"))
        }
        binding.chipSampleNames.setOnClickListener {
            binding.etTtsInput.setText(getSampleText(selectedTtsLanguage, "Names"))
        }

        // Generate speech action
        binding.btnGenerateSpeech.setOnClickListener {
            val text = binding.etTtsInput.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) {
                showTtsError("Please enter text to synthesize.")
                return@setOnClickListener
            }
            generateSpeech(text)
        }

        // Audio playback controls
        binding.btnPlaySpeech.setOnClickListener {
            val result = lastTtsResult ?: return@setOnClickListener
            startPlayback(result)
        }

        binding.btnStopSpeech.setOnClickListener {
            audioPlayer.stop()
            binding.btnPlaySpeech.isEnabled = lastTtsResult != null && lastTtsResult!!.audio.isNotEmpty()
            binding.btnStopSpeech.isEnabled = false
        }
    }

    private fun switchTtsLanguage(language: Language) {
        selectedTtsLanguage = language
        hideTtsError()

        // Populate sample text in input
        binding.etTtsInput.setText(getSampleText(language, "Normal"))

        val isPiper = ttsManager.isVoiceInstalled(language)
        if (isPiper) {
            val voiceName = when (language) {
                Language.EN -> "en_US-amy-low"
                Language.HI -> "hi_IN-pratham-medium"
                Language.ML -> "ml_IN-meera-medium"
                Language.TE -> "te_IN-maya-medium"
                Language.MR -> "mr_IN-google-medium"
                Language.BN -> "bn_BD-google-medium"
                Language.TA -> "ta_IN-rasa_male-medium"
                Language.GU -> "gu_epoch229-medium"
                else -> "vits-piper"
            }
            binding.tvActiveTtsEngineName.text = "Piper/VITS ONNX (Primary)"
            binding.tvActiveTtsPolicyDetails.text = "Voice: $voiceName | Policy: Primary Engine"
        } else {
            binding.tvActiveTtsEngineName.text = "AI4Bharat Indic-TTS (Fallback)"
            binding.tvActiveTtsPolicyDetails.text = "Reason: Piper voice pending export | Policy: Fallback Engine"
        }

        binding.tvActiveTtsStatus.text = "Loading ${language.displayName} TTS…"
        binding.tvActiveTtsStatus.setTextColor(Color.parseColor("#F59E0B"))
        binding.tvTtsModelLoadDuration.text = "Loading…"
        binding.btnGenerateSpeech.isEnabled = false

        lifecycleScope.launch {
            try {
                val memBefore = MemoryMetrics.captureSnapshot()
                val startTime = SystemClock.elapsedRealtime()
                val success = ttsManager.loadLanguage(language)
                val loadTimeMs = SystemClock.elapsedRealtime() - startTime

                if (success) {
                    val engine = ttsManager.getCurrentEngine()
                    binding.tvActiveTtsEngineName.text = engine?.engineName() ?: "TTS Engine"
                    binding.tvActiveTtsStatus.text = "● ${language.displayName} TTS Ready ✓"
                    binding.tvActiveTtsStatus.setTextColor(Color.parseColor("#10B981"))
                    binding.tvTtsModelLoadDuration.text = "Load: $loadTimeMs ms"
                    binding.btnGenerateSpeech.isEnabled = true

                    val memAfter = MemoryMetrics.captureSnapshot()
                    binding.tvTtsMemoryStats.text = String.format(
                        Locale.US,
                        "Heap: %.1f MB (Loaded: %.1f MB) | Native: %.1f MB",
                        memAfter.usedHeapMb,
                        memAfter.usedHeapMb - memBefore.usedHeapMb,
                        memAfter.nativeHeapMb
                    )
                } else {
                    binding.tvActiveTtsStatus.text = "Failed to load TTS"
                    binding.tvActiveTtsStatus.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvTtsModelLoadDuration.text = "Error"
                    showTtsError("Could not load TTS model for ${language.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception switching TTS language", e)
                showTtsError("TTS loading error: ${e.message}")
            }
        }
    }

    private fun generateSpeech(text: String) {
        hideTtsError()
        binding.btnGenerateSpeech.isEnabled = false
        binding.btnPlaySpeech.isEnabled = false
        binding.btnStopSpeech.isEnabled = false
        binding.tvActiveTtsStatus.text = "Synthesizing audio…"
        binding.tvActiveTtsStatus.setTextColor(Color.parseColor("#F59E0B"))

        lifecycleScope.launch {
            val engine = ttsManager.getCurrentEngine()
            if (engine == null || !engine.isLoaded()) {
                showTtsError("TTS engine is not ready.")
                binding.btnGenerateSpeech.isEnabled = true
                return@launch
            }

            try {
                val result = engine.synthesize(text)
                lastTtsResult = result
                val memAfter = MemoryMetrics.captureSnapshot()
                val cpuUsage = CpuMetrics.sampleProcessCpuUsage()

                withContext(Dispatchers.Main) {
                    binding.btnGenerateSpeech.isEnabled = true
                    binding.tvActiveTtsStatus.text = "● Speech Generated Successfully ✓"
                    binding.tvActiveTtsStatus.setTextColor(Color.parseColor("#10B981"))

                    val durationSec = result.audioDurationMs / 1000f
                    binding.tvTtsAudioDuration.text = TtsMetrics.formatDuration(durationSec)
                    binding.tvTtsSynthesisTime.text = "${result.synthesisTimeMs} ms"
                    binding.tvTtsRtf.text = TtsMetrics.formatRtf(result.rtf)

                    if (result.rtf <= 1.0f) {
                        val speedup = if (result.rtf > 0) 1.0f / result.rtf else 1.0f
                        binding.tvTtsRtfBadge.text = String.format(Locale.US, "✓ Real-time (%.1fx)", speedup)
                        binding.tvTtsRtfBadge.setTextColor(Color.parseColor("#10B981"))
                    } else {
                        binding.tvTtsRtfBadge.text = "⚠ Slower than real-time"
                        binding.tvTtsRtfBadge.setTextColor(Color.parseColor("#F59E0B"))
                    }

                    binding.tvTtsCpuUsage.text = String.format(Locale.US, "%.1f %%", cpuUsage)
                    binding.tvTtsMemoryStats.text = String.format(
                        Locale.US,
                        "Heap: %.1f MB (Used: %.1f MB) | Native: %.1f MB",
                        memAfter.totalHeapMb,
                        memAfter.usedHeapMb,
                        memAfter.nativeHeapMb
                    )

                    // Automatically start playback
                    if (result.audio.isNotEmpty()) {
                        startPlayback(result)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Speech synthesis exception", e)
                withContext(Dispatchers.Main) {
                    binding.btnGenerateSpeech.isEnabled = true
                    showTtsError("Speech synthesis failed: ${e.message}")
                }
            }
        }
    }

    private fun startPlayback(result: TtsResult) {
        if (result.audio.isEmpty()) return
        binding.btnPlaySpeech.isEnabled = false
        binding.btnStopSpeech.isEnabled = true
        audioPlayer.play(result.audio, result.sampleRate, lifecycleScope) {
            binding.btnPlaySpeech.isEnabled = true
            binding.btnStopSpeech.isEnabled = false
        }
    }

    private fun getSampleText(language: Language, category: String): String {
        return when (language) {
            Language.EN -> when (category) {
                "Emergency" -> "Please evacuate the building immediately via the northern exit."
                "Numbers" -> "Confirm delivery of 450 rations to sector 12."
                "Names" -> "Officer Sharma and Lieutenant Khan are on patrol."
                else -> "The primary objective is to maintain network integrity."
            }
            Language.HI -> when (category) {
                "Emergency" -> "कृपया तुरंत इमारत खाली करें और सुरक्षित स्थान पर जाएं।"
                "Numbers" -> "सेक्टर बारह में तीन सौ पचास पैकेट पहुंचाएं।"
                "Names" -> "राहुल वर्मा और अमित पाटिल से तुरंत संपर्क करें।"
                else -> "मुख्य नियंत्रण कक्ष से नया संदेश प्राप्त हुआ है।"
            }
            Language.GU -> when (category) {
                "Emergency" -> "કૃપા કરીને તરત જ ઇમારત ખાલી કરો અને સુરક્ષિત સ્થળે જાઓ."
                else -> "નમસ્તે, આ ઓફલાઇન સ્પીચ ટેસ્ટ બેન્ચ છે."
            }
            Language.MR -> when (category) {
                "Emergency" -> "कृपया लगेच इमारत रिकामी करा आणि सुरक्षित ठिकाणी जा."
                else -> "नमस्कार, मुख्य नियंत्रण कक्षातून नवीन संदेश आला आहे."
            }
            Language.TA -> when (category) {
                "Emergency" -> "தயவுசெய்து உடனடியாக கட்டிடத்தை காலி செய்யுங்கள்."
                else -> "வணக்கம், இது ஆஃப்லைன் பேச்சு சோதனை பெஞ்ச் ஆகும்."
            }
            Language.TE -> when (category) {
                "Emergency" -> "దయచేసి వెంటనే భవనాన్ని ఖాళీ చేయండి."
                else -> "నమస్కారం, ఇది ఆఫ్‌లైన్ స్పీచ్ పరీక్ష."
            }
            Language.KN -> when (category) {
                "Emergency" -> "ದಯವಿಟ್ಟು ತಕ್ಷಣವೇ ಕಟ್ಟಡವನ್ನು ಖಾಲಿ ಮಾಡಿ."
                else -> "ನಮಸ್ಕಾರ, ಇದು ಆಫ್‌ಲೈನ್ ಧ್ವನಿ ಪರೀಕ್ಷೆ."
            }
            Language.ML -> when (category) {
                "Emergency" -> "ദയവായി ഉടൻ കെട്ടിടം ഒഴിഞ്ഞുപോവുക."
                else -> "നമസ്കാരം, ഇത് ഓഫ്‌ലൈൻ ശബ്ദ പരിശോധനയാണ്."
            }
            Language.BN -> when (category) {
                "Emergency" -> "অনুগ্রহ করে অবিলম্বে ভবনটি খালি করুন।"
                else -> "নমস্কার, এটি অফলাইন স্পিচ টেস্ট বেঞ্চ।"
            }
            Language.OR -> "ନମସ୍କାର, ଏହା ଏକ ପରୀକ୍ଷା।"
        }
    }

    private fun showTtsError(message: String) {
        binding.tvTtsErrorMessage.text = message
        binding.tvTtsErrorMessage.visibility = View.VISIBLE
    }

    private fun hideTtsError() {
        binding.tvTtsErrorMessage.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        sttTimerJob?.cancel()
        if (audioRecorder.isRecording()) {
            audioRecorder.stopRecording()
        }
        audioPlayer.stop()
        lifecycleScope.launch {
            sttModelManager.unloadCurrentModel()
            ttsManager.unloadCurrentModel()
        }
    }
}
