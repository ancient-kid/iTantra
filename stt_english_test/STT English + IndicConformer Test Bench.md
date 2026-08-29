# STT English + IndicConformer Test Bench

Standalone Android application for testing and benchmarking **offline Speech-to-Text (STT)** models on physical Android devices before integrating the validated speech pipeline into the main **iTantra** application.

This project contains **two STT model families only**:

1. **English** → previously implemented **NeMo Conformer-CTC Small**
2. **Indian languages** → **IndicConformer INT8 ONNX**

There is no Vakyansh integration.

---

# 1. Objective

The application is a standalone speech-recognition test bench.

Its job is:

```text
User selects language
        ↓
Application selects corresponding STT model
        ↓
User presses START RECORDING
        ↓
Microphone
        ↓
AudioRecord
        ↓
16 kHz Mono PCM
        ↓
Selected offline STT model
        ↓
Transcript
        ↓
Performance metrics
```

The application is **not** the final iTantra application.

Its purpose is to answer:

> **Can the selected STT models run accurately, efficiently, and with low latency on low/mid-range Android hardware?**

---

# 2. Model Strategy

## English

Use the model that has already been implemented and validated in the previous English STT test:

> **NeMo Conformer-CTC Small**

This model is the dedicated English STT backend.

```text
English
   ↓
NeMo Conformer-CTC Small
   ↓
ONNX / existing implementation
   ↓
Transcript
```

Do not replace the existing English implementation unless there is a demonstrated technical reason.

---

# 3. Indian Languages

For the Indian languages, use:

> **IndicConformer INT8 ONNX**

The application should use language-specific IndicConformer INT8 ONNX model files.

```text
Hindi
   ↓
IndicConformer INT8 ONNX
```

```text
Marathi
   ↓
IndicConformer INT8 ONNX
```

and so on.

The application must not use a large multilingual checkpoint if a language-specific optimized model is available.

The intended deployment path is:

```text
IndicConformer
      ↓
language-specific model
      ↓
INT8 quantization
      ↓
ONNX
      ↓
Android inference
```

---

# 4. Supported Languages

The language selector should contain the target languages from the iTantra problem statement:

```text
┌────────────┐
│ English    │
│ Hindi      │
│ Gujarati   │
│ Marathi    │
│ Kannada    │
│ Malayalam  │
│ Tamil      │
│ Telugu     │
│ Odia       │
│ Bengali    │
└────────────┘
```

Recommended UI:

```text
[ English ]
[ Hindi ] [ Gujarati ]
[ Marathi ] [ Kannada ]
[ Malayalam ] [ Tamil ]
[ Telugu ] [ Odia ]
[ Bengali ]
```

Only one language can be selected at a time.

---

# 5. Model Selection Logic

The user should **not manually select between two model families**.

The model family is determined by the selected language.

```text
                    Language
                       │
            ┌──────────┴──────────┐
            │                     │
        English              Indian Language
            │                     │
            ▼                     ▼
 NeMo Conformer-CTC       IndicConformer INT8
       Small                    ONNX
```

Therefore:

```text
English → NeMo Conformer-CTC Small
Hindi → IndicConformer INT8
Gujarati → IndicConformer INT8
Marathi → IndicConformer INT8
Kannada → IndicConformer INT8
Malayalam → IndicConformer INT8
Tamil → IndicConformer INT8
Telugu → IndicConformer INT8
Odia → IndicConformer INT8*
Bengali → IndicConformer INT8
```

`*` Only enable Odia when a verified compatible IndicConformer INT8 ONNX model has been added to the project.

---

# 6. No Vakyansh

This project does **not** contain Vakyansh.

Do not add:

```text
Vakyansh ASR
Vakyansh TTS
Vakyansh language models
Vakyansh comparison logic
```

The only STT model families are:

```text
NeMo Conformer-CTC Small
IndicConformer INT8 ONNX
```

---

# 7. No VAD

There is deliberately **no Voice Activity Detection model**.

Recording is controlled manually by the user.

```text
IDLE
 ↓
START RECORDING
 ↓
RECORDING
 ↓
STOP RECORDING
 ↓
PROCESSING
 ↓
TRANSCRIPT
 ↓
IDLE
```

The application must never automatically stop recording because of silence.

This keeps the test bench simple and removes unnecessary inference overhead.

---

# 8. No TTS

This repository is STT-only.

Do not add:

```text
TTS
audio synthesis
voice cloning
prosody generation
speaker identification
```

TTS will be implemented later in the main iTantra speech pipeline.

---

# 9. No Networking

This repository must have absolutely no communication layer.

Do not add:

```text
Bluetooth
Wi-Fi
Wi-Fi Direct
BitChat
TCP
UDP
WebSocket
HTTP
REST API
```

The output of this application is simply:

```text
STT result → String
```

The main iTantra project will later consume the validated STT implementation.

---

# 10. Technology Stack

## Android

- Native Android
- Kotlin
- Gradle Kotlin DSL
- Android Views/XML
- Minimum SDK: API 26

## Audio

```text
AudioRecord
16 kHz
Mono
PCM 16-bit
```

## STT Runtime

Preferred:

```text
sherpa-onnx / ONNX Runtime
```

Use whichever runtime is already compatible with the existing English model and the selected IndicConformer ONNX models.

Do not introduce unnecessary inference frameworks.

---

# 11. Project Structure

```text
stt_english_test/
│
├── app/
│   ├── build.gradle.kts
│   │
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           │
│           ├── java/
│           │   └── com/
│           │       └── itantra/
│           │           └── stt/
│           │               ├── MainActivity.kt
│           │               │
│           │               ├── audio/
│           │               │   └── AudioRecorder.kt
│           │               │
│           │               ├── stt/
│           │               │   ├── SttEngine.kt
│           │               │   ├── SttResult.kt
│           │               │   ├── NemoConformerEngine.kt
│           │               │   └── IndicConformerEngine.kt
│           │               │
│           │               ├── model/
│           │               │   ├── Language.kt
│           │               │   ├── ModelInfo.kt
│           │               │   ├── ModelRegistry.kt
│           │               │   └── ModelManager.kt
│           │               │
│           │               └── benchmark/
│           │                   ├── SttMetrics.kt
│           │                   ├── MemoryMetrics.kt
│           │                   └── CpuMetrics.kt
│           │
│           └── res/
│               ├── layout/
│               │   └── activity_main.xml
│               ├── values/
│               │   ├── strings.xml
│               │   └── themes.xml
│               └── ...
│
├── models/
│   ├── english/
│   │   └── nemo-conformer-ctc-small/
│   │       ├── model files
│   │       ├── tokens.txt
│   │       └── config files
│   │
│   └── indicconformer/
│       ├── hi/
│       ├── gu/
│       ├── mr/
│       ├── kn/
│       ├── ml/
│       ├── ta/
│       ├── te/
│       ├── or/
│       └── bn/
│
├── benchmark/
│   └── results/
│
├── test_audio/
│   └── ...
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── README.md
```

---

# 12. Model Directory Rules

All model files must remain inside:

```text
stt_english_test/models/
```

### English

```text
models/
└── english/
    └── nemo-conformer-ctc-small/
```

### Indic languages

```text
models/
└── indicconformer/
    ├── hi/
    ├── gu/
    ├── mr/
    ├── kn/
    ├── ml/
    ├── ta/
    ├── te/
    ├── or/
    └── bn/
```

Do not duplicate model files elsewhere.

---

# 13. Language Enum

Use a centralized language definition.

Example:

```kotlin
enum class Language(
    val code: String,
    val displayName: String
) {
    EN("en", "English"),
    HI("hi", "Hindi"),
    GU("gu", "Gujarati"),
    MR("mr", "Marathi"),
    KN("kn", "Kannada"),
    ML("ml", "Malayalam"),
    TA("ta", "Tamil"),
    TE("te", "Telugu"),
    OR("or", "Odia"),
    BN("bn", "Bengali")
}
```

The registry should determine whether the corresponding model actually exists.

---

# 14. Model Registry

Use a centralized model registry.

Example:

```kotlin
data class ModelInfo(
    val id: String,
    val modelName: String,
    val language: Language,
    val modelPath: String,
    val tokensPath: String,
    val format: String,
    val quantization: String
)
```

Examples:

```text
EN:
model = nemo-conformer-ctc-small
format = ONNX
quantization = existing implementation
```

```text
HI:
model = indicconformer
format = ONNX
quantization = INT8
```

---

# 15. Model Manager

The application must never load all language models simultaneously.

Only the selected model should be loaded.

```text
User selects Hindi
       ↓
Unload previous model
       ↓
Load Hindi IndicConformer INT8
       ↓
Ready
```

Then:

```text
User selects Tamil
       ↓
Unload Hindi
       ↓
Load Tamil IndicConformer INT8
       ↓
Ready
```

For English:

```text
English selected
       ↓
Load NeMo Conformer-CTC Small
```

This is essential for low-memory testing.

---

# 16. STT Interface

Both model families must implement the same interface.

```kotlin
interface SttEngine {

    suspend fun load()

    suspend fun transcribe(
        audio: ShortArray,
        sampleRate: Int
    ): SttResult

    fun unload()

    fun isLoaded(): Boolean

    fun modelName(): String

    fun language(): Language
}
```

Implementations:

```text
NemoConformerEngine
IndicConformerEngine
```

This allows the rest of the application to remain model-independent.

---

# 17. STT Result

Use a structured result.

```kotlin
data class SttResult(
    val text: String,
    val inferenceTimeMs: Long,
    val audioDurationMs: Long
)
```

Optional later fields:

```kotlin
val modelLoadTimeMs: Long
val memoryUsageMb: Double?
val cpuUsagePercent: Double?
```

---

# 18. Audio Recording

Use Android `AudioRecord`.

Configuration:

```text
Sample Rate = 16000 Hz
Channels = Mono
Encoding = PCM 16-bit
```

The recorded audio remains on the device.

No network upload.

---

# 19. Main Screen

Recommended UI:

```text
┌─────────────────────────────────────┐
│          STT MODEL TEST             │
│                                     │
│ LANGUAGE                            │
│                                     │
│ [ English ] [ Hindi ]               │
│ [ Gujarati ] [ Marathi ]            │
│ [ Kannada ] [ Malayalam ]           │
│ [ Tamil ] [ Telugu ]                │
│ [ Odia ] [ Bengali ]                │
│                                     │
│ MODEL                               │
│ NeMo Conformer-CTC Small            │
│                                     │
│ STATUS                              │
│ ● Model Ready                       │
│                                     │
│        [ START RECORDING ]          │
│                                     │
│ Status: Idle                        │
│                                     │
│ TRANSCRIPT                          │
│ ┌─────────────────────────────────┐ │
│ │                                 │ │
│ │ Transcribed text appears here  │ │
│ │                                 │ │
│ └─────────────────────────────────┘ │
│                                     │
│ PERFORMANCE                         │
│ Audio Duration : 0.00 s             │
│ Inference Time : 0 ms               │
│ RTF            : 0.00               │
│ Model Load     : 0 ms               │
│ Memory         : -- MB              │
│ CPU            : -- %               │
└─────────────────────────────────────┘
```

---

# 20. Model Display

The UI must dynamically show which model is active.

### English

```text
Language:
English

Model:
NeMo Conformer-CTC Small

Quantization:
Existing implementation
```

### Hindi

```text
Language:
Hindi

Model:
IndicConformer

Quantization:
INT8
Format:
ONNX
```

### Tamil

```text
Language:
Tamil

Model:
IndicConformer

Quantization:
INT8
Format:
ONNX
```

---

# 21. Recording State Machine

```text
IDLE
  │
  │ START
  ▼
RECORDING
  │
  │ STOP
  ▼
PROCESSING
  │
  ▼
RESULT
  │
  ▼
IDLE
```

During recording:

```text
[ STOP RECORDING ]
```

During inference:

```text
[ PROCESSING... ]
```

Disable recording controls while inference is running.

---

# 22. Model Switching

When the user changes language:

```text
Current Model
      ↓
Unload
      ↓
Load New Language Model
      ↓
Ready
```

Do not run inference during model switching.

Display:

```text
Loading Hindi model...
```

followed by:

```text
Hindi model ready ✓
```

---

# 23. RTF Measurement

Calculate:

```text
RTF = inferenceTime / audioDuration
```

Example:

```text
Audio = 5 seconds
Inference = 1 second

RTF = 1 / 5
    = 0.20
```

Interpretation:

```text
RTF < 1
→ faster than real time

RTF = 1
→ real-time

RTF > 1
→ slower than real time
```

---

# 24. Model Load Time

Measure:

```text
loadStart
loadComplete
```

Calculate:

```text
modelLoadTimeMs
```

Display it.

Example:

```text
Model Load: 820 ms
```

Never hard-code the value.

---

# 25. Memory Benchmark

Measure:

```text
Memory before model load
Memory after model load
Memory after inference
```

Example display:

```text
Memory before: 120 MB
Memory loaded: 215 MB
Memory after: 225 MB
```

The real values must be collected from the device.

---

# 26. CPU Benchmark

Measure approximately:

```text
Idle
Recording
Inference
```

The most important stage is:

```text
STT inference CPU usage
```

This contributes to evaluating edge-device efficiency.

---

# 27. Accuracy Testing

Use the same sentence bank repeatedly.

Example:

```text
test_audio/
├── en/
├── hi/
├── gu/
├── mr/
├── kn/
├── ml/
├── ta/
├── te/
├── or/
└── bn/
```

Each language should eventually contain approximately 15 sentences.

---

# 28. Test Sentence Categories

Include:

### Normal

```text
The meeting starts at ten o'clock.
```

### Numbers

```text
Send twenty five units to sector three.
```

### Emergency

```text
There is a fire near the northern gate.
```

### Names

```text
Contact Rahul Kumar immediately.
```

### Difficult speech

Test:

- long sentences
- fast speech
- pauses
- background noise
- names
- numbers
- uncommon words

---

# 29. WER

Word Error Rate:

```text
WER = (S + D + I) / N
```

Where:

```text
S = substitutions
D = deletions
I = insertions
N = reference word count
```

Use the same recordings and references for each model.

The Android application may display transcription results, while the benchmark tooling can calculate WER separately.

---

# 30. Benchmark Matrix

For every supported model/language combination:

```text
Language
    ↓
Model
    ↓
Device
    ↓
Environment
```

Example:

```text
Hindi
 └── IndicConformer INT8
      ├── Quiet
      └── Background noise
```

English:

```text
English
 └── NeMo Conformer-CTC Small
      ├── Quiet
      └── Background noise
```

---

# 31. Required Metrics

For each model record:

| Metric | Required |
|---|---|
| Model size | ✅ |
| Model load time | ✅ |
| Inference time | ✅ |
| RTF | ✅ |
| Memory usage | ✅ |
| CPU usage | ✅ |
| WER | ✅ |
| Crash count | ✅ |

---

# 32. Device Information

Diagnostics should record:

```text
Device model
Android version
CPU ABI
Total RAM
Available RAM
```

Example:

```text
Device: XYZ
Android: 14
ABI: arm64-v8a
RAM: 6 GB
```

---

# 33. Offline Requirement

The application must function with network access disabled.

Test:

```text
1. Install APK
2. Enable Airplane Mode
3. Open application
4. Select language
5. Load model
6. Start recording
7. Stop recording
8. Run STT
```

Expected:

```text
Internet required: NO
```

No STT request may leave the device.

---

# 34. Permissions

The only required runtime permission for this project should initially be:

```xml
<uses-permission
    android:name="android.permission.RECORD_AUDIO" />
```

Do not request:

```text
Bluetooth
Location
Camera
Contacts
Network
Storage
```

because this test application does not need them.

---

# 35. No Main iTantra Dependency

This project must remain independent.

Do not:

```text
import iTantra code
import BitChat code
modify iTantra
modify networking code
share modules with iTantra
```

The eventual integration happens after the STT implementation has been validated.

---

# 36. Workspace Isolation

## ABSOLUTE RULE

The only permitted project directory is:

```text
stt_english_test/
```

All code, models, benchmark data, logs and generated files must stay within this directory.

Never modify:

```text
../iTantra/
../other-project/
```

Never modify global:

```text
Gradle
Android Studio
SDK
environment variables
```

unless explicitly required by the developer environment and explicitly approved.

Before modifying commands, verify:

Windows PowerShell:

```powershell
Get-Location
```

Linux/macOS:

```bash
pwd
```

The working directory must point to:

```text
stt_english_test
```

---

# 37. Gradle Commands

Windows:

```powershell
.\gradlew.bat assembleDebug
```

Install:

```powershell
.\gradlew.bat installDebug
```

Release:

```powershell
.\gradlew.bat assembleRelease
```

Do not use a globally installed Gradle version when the wrapper is available.

---

# 38. Development Phases

## Phase 1 — Existing English implementation

Verify:

```text
English
↓
NeMo Conformer-CTC Small
↓
Android
↓
Transcript
```

Do not rewrite the existing English implementation unnecessarily.

---

## Phase 2 — IndicConformer runtime

Implement:

```text
IndicConformer INT8 ONNX
```

for one language first.

Recommended starting point:

```text
Hindi
```

Pipeline:

```text
Hindi audio
↓
IndicConformer INT8
↓
Transcript
```

---

## Phase 3 — Language Manager

Add:

```text
English
Hindi
Gujarati
Marathi
Kannada
Malayalam
Tamil
Telugu
Odia
Bengali
```

The language determines the model.

---

## Phase 4 — Performance instrumentation

Measure:

```text
Load
Inference
RTF
Memory
CPU
```

---

## Phase 5 — Accuracy evaluation

Run the sentence bank.

Calculate:

```text
WER
```

for each supported language.

---

## Phase 6 — Device testing

Test on:

```text
Low-end Android
Mid-range Android
```

Record differences.

---

# 39. Recommended Initial Implementation Order

Do not implement all ten languages at once.

Use:

```text
1. Existing English model
       ↓
2. Hindi IndicConformer
       ↓
3. Marathi
       ↓
4. Tamil
       ↓
5. Telugu
       ↓
6. Gujarati
       ↓
7. Bengali
       ↓
8. Kannada
       ↓
9. Malayalam
       ↓
10. Odia
```

Odia should only become active once its exact mobile-compatible INT8 model has been verified.

---

# 40. Definition of Done

## English

- [ ] NeMo Conformer-CTC Small works
- [ ] Existing implementation remains functional
- [ ] Offline inference works
- [ ] Transcript is displayed
- [ ] Metrics are logged

## IndicConformer

- [ ] INT8 ONNX runtime works
- [ ] One Indic language works end-to-end
- [ ] Language switching works
- [ ] Models load/unload correctly
- [ ] No simultaneous loading of all models

## Audio

- [ ] Microphone permission works
- [ ] 16 kHz
- [ ] Mono
- [ ] PCM 16-bit
- [ ] Manual start/stop recording

## Benchmarking

- [ ] Model size measured
- [ ] Load time measured
- [ ] Inference time measured
- [ ] RTF measured
- [ ] Memory measured
- [ ] CPU measured
- [ ] WER benchmark available

## Offline

- [ ] Airplane-mode test passes
- [ ] No cloud APIs
- [ ] No HTTP inference
- [ ] No external STT service

## Isolation

- [ ] No changes outside `stt_english_test`
- [ ] No dependency on main iTantra
- [ ] No BitChat
- [ ] No networking
- [ ] No Vakyansh
- [ ] No VAD
- [ ] No TTS

---

# 41. Final Architecture

```text
                    STT TEST BENCH
                           │
                    ┌──────▼──────┐
                    │   Language  │
                    │   Selector  │
                    └──────┬──────┘
                           │
                ┌──────────▼──────────┐
                │    Model Manager    │
                └──────────┬──────────┘
                           │
              ┌────────────┴────────────┐
              │                         │
          English                  Indic Languages
              │                         │
              ▼                         ▼
    NeMo Conformer-CTC         IndicConformer INT8
           Small                      ONNX
              │                         │
              └────────────┬────────────┘
                           │
                           ▼
                      STT Engine
                           │
                      Transcript
                           │
              ┌────────────┴─────────────┐
              │                          │
              ▼                          ▼
         Transcript                  Metrics
                                       │
                        ┌──────────────┼──────────────┐
                        ▼              ▼              ▼
                       RTF           Memory          CPU
```

---

# 42. Final Purpose

This project exists to validate two STT paths for iTantra:

```text
English
   ↓
NeMo Conformer-CTC Small
```

and:

```text
Indian Languages
   ↓
IndicConformer INT8 ONNX
```

The output of this project will determine:

```text
Which model
      ↓
On which Android device
      ↓
For which language
      ↓
With what WER
      ↓
At what latency
      ↓
Using how much RAM/CPU
```

Once validated, the selected STT engines can be moved into the main iTantra application and connected to the networking layer.

**This repository is strictly an STT evaluation environment and must remain independent from the final iTantra application.**