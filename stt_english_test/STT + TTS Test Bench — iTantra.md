# STT + TTS Test Bench — iTantra

Android application for independently testing and benchmarking the **offline speech models** that will later be integrated into the main **iTantra** application.

This is an extension of the existing `stt_english_test` project.

**Do NOT create another Android project or standalone application.**

The project now contains two functionality areas:

```text
STT
 ├── English → NeMo Conformer-CTC Small
 └── Indian Languages → IndicConformer INT8 ONNX

TTS
 ├── Primary → Piper/VITS ONNX
 └── Fallback → Indic-TTS
```

The application remains a **model evaluation and development environment**, not the final iTantra communication application.

---

# 1. Project Scope

The application provides two independent modules:

## STT Module

```text
Language Selection
       ↓
Select corresponding model automatically
       ↓
START RECORDING
       ↓
Microphone
       ↓
AudioRecord
       ↓
Offline STT
       ↓
Transcript
       ↓
Performance Metrics
```

## TTS Module

```text
Language Selection
       ↓
TTS Model Manager
       ↓
Enter Text
       ↓
Select/resolve best available TTS engine
       ↓
Offline TTS
       ↓
Generated Audio
       ↓
Playback
       ↓
Performance + Quality Metrics
```

There is **no networking, BitChat, Bluetooth or Wi-Fi functionality** in this project.

---

# 2. Critical Workspace Boundary

This is an extension of the existing project:

```text
stt_english_test/
```

All implementation must remain inside this directory.

## Do NOT

- create another Android project
- create another Gradle project
- modify the parent directory
- modify the main iTantra project
- modify BitChat
- create files outside `stt_english_test`
- place models outside `stt_english_test/models`
- place benchmark results outside `stt_english_test/benchmark`
- modify global Gradle configuration
- modify unrelated projects
- upload audio to a server

If an operation appears to require changes outside this workspace, stop and report it.

---

# 3. Technology Stack

## Android

- Native Android
- Kotlin
- Gradle Kotlin DSL
- Android Views/XML
- Minimum SDK: API 26

## Audio

### STT

```text
AudioRecord
16 kHz
Mono
PCM 16-bit
```

### TTS

Use the output format required by the selected TTS model and convert only when necessary.

Playback should use Android's native audio APIs.

---

# 4. STT Model Architecture

The existing STT functionality must remain intact.

## English

Use the previously implemented:

> **NeMo Conformer-CTC Small**

```text
English
 ↓
NeMo Conformer-CTC Small
 ↓
Offline inference
 ↓
English transcript
```

Do not unnecessarily rewrite the existing English implementation.

---

# 5. Indic STT

For Indian languages:

> **IndicConformer INT8 ONNX**

Use language-specific model files.

```text
Hindi       → IndicConformer Hindi INT8
Gujarati    → IndicConformer Gujarati INT8
Marathi     → IndicConformer Marathi INT8
Kannada     → IndicConformer Kannada INT8
Malayalam   → IndicConformer Malayalam INT8
Tamil       → IndicConformer Tamil INT8
Telugu      → IndicConformer Telugu INT8
Odia        → IndicConformer Odia INT8
Bengali     → IndicConformer Bengali INT8
```

Only activate a language when the required model is actually available.

---

# 6. STT Languages

The UI should contain:

```text
[ English ]
[ Hindi ]
[ Gujarati ]
[ Marathi ]
[ Kannada ]
[ Malayalam ]
[ Tamil ]
[ Telugu ]
[ Odia ]
[ Bengali ]
```

Only one language is active at a time.

The selected language determines the STT model.

---

# 7. STT Language-to-Model Mapping

The application must implement this relationship:

```text
English
    ↓
NeMo Conformer-CTC Small

Hindi
    ↓
IndicConformer INT8

Gujarati
    ↓
IndicConformer INT8

Marathi
    ↓
IndicConformer INT8

Kannada
    ↓
IndicConformer INT8

Malayalam
    ↓
IndicConformer INT8

Tamil
    ↓
IndicConformer INT8

Telugu
    ↓
IndicConformer INT8

Odia
    ↓
IndicConformer INT8

Bengali
    ↓
IndicConformer INT8
```

The application must never load the Hindi model for another selected language.

---

# 8. STT Transcript Requirement

The transcript must be displayed in the **language/script produced by the selected model**.

Examples:

### Hindi

```text
मैं कल मुंबई जा रहा हूँ।
```

### Tamil

```text
நான் நாளை சென்னை செல்கிறேன்.
```

### Gujarati

```text
હું આજે મુંબઈ જઈ રહ્યો છું.
```

### Bengali

```text
আমি আজ কলকাতা যাচ্ছি।
```

### English

```text
I am going to Mumbai tomorrow.
```

There is no translation layer.

---

# 9. No VAD

The application does not use Voice Activity Detection.

Recording is controlled manually:

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
RESULT
 ↓
IDLE
```

Do not add:

- Silero VAD
- automatic endpointing
- automatic silence detection

The button determines the recording duration.

---

# 10. TTS Architecture

TTS is a **new functionality inside this existing project**.

It must not be implemented as another application.

The architecture is:

```text
                    TTS Manager
                         │
                  Selected Language
                         │
                         ▼
                Is Piper available?
                     /        \
                   YES         NO
                    │           │
                    ▼           ▼
              Piper/VITS     Indic-TTS
                 ONNX         fallback
                    │           │
                    └─────┬─────┘
                          ▼
                    TTS Runtime
                          │
                          ▼
                        Audio
                          │
                          ▼
                     AudioTrack
```

---

# 11. TTS Primary Engine

Primary engine:

> **Piper/VITS ONNX**

Use Piper/VITS whenever a suitable voice exists for the selected language.

Advantages relevant to this project:

- offline
- ONNX-compatible deployment
- suitable for edge inference
- low-latency inference
- relatively small models
- Android-compatible runtime path
- multiple voices available through the ecosystem

The actual selected voice must be recorded in the benchmark results.

---

# 12. TTS Fallback Engine

Fallback:

> **AI4Bharat Indic-TTS**

Use Indic-TTS only when:

```text
Piper voice is unavailable
OR
Piper fails the quality benchmark
OR
Piper fails the performance benchmark
```

Do not automatically install/use Indic-TTS for every language.

The goal is to minimize:

- APK size
- model storage
- RAM usage
- CPU usage

---

# 13. TTS Decision Policy

The application must implement:

```text
Selected Language
       ↓
Check Piper voice
       ↓
Available?
   /       \
 YES       NO
  │         │
  ▼         ▼
Benchmark Indic-TTS
  │
Pass?
 /   \
YES   NO
 │     │
 ▼     ▼
Piper Indic-TTS
```

The selected engine must be visible in the UI.

Example:

```text
Language:
Hindi

TTS Engine:
Piper/VITS

Status:
Primary
```

Or:

```text
Language:
Odia

TTS Engine:
Indic-TTS

Status:
Fallback

Reason:
Piper voice unavailable
```

---

# 14. TTS Interface

Both engines must expose the same interface.

```kotlin
interface TtsEngine {

    suspend fun load()

    suspend fun synthesize(
        text: String
    ): TtsResult

    fun unload()

    fun isLoaded(): Boolean

    fun engineName(): String

    fun language(): Language
}
```

Implementations:

```text
PiperVitsEngine
IndicTtsEngine
```

The UI must not directly depend on either implementation.

---

# 15. TTS Result

Use:

```kotlin
data class TtsResult(
    val audio: ByteArray,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long
)
```

Optional future metrics:

```kotlin
val modelLoadTimeMs: Long
val memoryUsageMb: Double?
val cpuUsagePercent: Double?
```

---

# 16. TTS Screen

The existing app should gain a TTS section/tab.

Top-level navigation:

```text
┌──────────────────────────────────┐
│        STT + TTS TEST BENCH      │
│                                  │
│     [ STT ]        [ TTS ]       │
└──────────────────────────────────┘
```

---

# 17. TTS UI

Recommended layout:

```text
┌────────────────────────────────────┐
│              TTS TEST              │
│                                    │
│ LANGUAGE                           │
│                                    │
│ [ English ] [ Hindi ]              │
│ [ Gujarati ] [ Marathi ]           │
│ [ Kannada ] [ Malayalam ]          │
│ [ Tamil ] [ Telugu ]               │
│ [ Odia ] [ Bengali ]               │
│                                    │
│ ACTIVE ENGINE                      │
│ Piper/VITS ONNX                    │
│                                    │
│ STATUS                             │
│ ● Model Ready                      │
│                                    │
│ TEXT                               │
│ ┌────────────────────────────────┐ │
│ │ Enter text here...             │ │
│ │                                │ │
│ └────────────────────────────────┘ │
│                                    │
│      [ GENERATE SPEECH ]           │
│                                    │
│       [ ▶ PLAY ] [ ■ STOP ]        │
│                                    │
│ PERFORMANCE                        │
│ Audio Duration : 0.00 s            │
│ Synthesis Time : 0 ms              │
│ RTF            : 0.00              │
│ Model Load     : 0 ms              │
│ Memory         : -- MB             │
│ CPU            : -- %              │
└────────────────────────────────────┘
```

---

# 18. TTS Workflow

When the user selects a language:

```text
Language selected
       ↓
Resolve TTS engine
       ↓
Unload previous TTS model
       ↓
Load selected engine/model
       ↓
Show READY
```

Then:

```text
User enters text
       ↓
GENERATE SPEECH
       ↓
TTS inference
       ↓
Audio generated
       ↓
Show metrics
       ↓
PLAY
```

---

# 19. TTS Text Requirement

The text must remain in the selected language.

Example:

### Hindi

```text
कृपया तुरंत इमारत खाली करें।
```

### Tamil

```text
தயவுசெய்து உடனடியாக கட்டிடத்தை காலி செய்யுங்கள்.
```

### Gujarati

```text
કૃપા કરીને તરત જ ઇમારત ખાલી કરો.
```

### English

```text
Please evacuate the building immediately.
```

There is no translation.

---

# 20. TTS Playback

Use Android audio playback.

Pipeline:

```text
Text
 ↓
TTS
 ↓
PCM/WAV
 ↓
AudioTrack
 ↓
Speaker
```

Controls:

```text
[ PLAY ]
[ STOP ]
```

The latest generated audio should be replayable without regenerating it.

---

# 21. TTS RTF

Calculate:

```text
RTF = synthesisTime / generatedAudioDuration
```

Example:

```text
Audio duration = 4 seconds
Synthesis time = 1 second

RTF = 0.25
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

# 22. TTS Quality Benchmark

Piper should pass the following checks before being marked as the primary engine.

## Intelligibility

Can a listener understand the sentence?

## Pronunciation

Are:

- numbers
- names
- locations
- emergency words

pronounced correctly?

## Naturalness

Does the output sound reasonably fluent?

## Latency

Is the synthesis fast enough?

## Memory

Does the model fit the target device?

## CPU

Is CPU utilization reasonable?

---

# 23. TTS Human Evaluation

For each available language/engine combination, evaluate:

```text
Intelligibility: 1–5
Naturalness:     1–5
Pronunciation:   1–5
```

Use approximately:

```text
15 test sentences per language
```

Sentence categories:

- normal conversation
- numbers
- locations
- names
- emergency messages
- short phrases
- long sentences

---

# 24. TTS Model Benchmark

For each model record:

| Metric | Required |
|---|---|
| Engine | ✅ |
| Voice | ✅ |
| Language | ✅ |
| Model size | ✅ |
| Model load time | ✅ |
| Synthesis time | ✅ |
| Audio duration | ✅ |
| RTF | ✅ |
| Memory | ✅ |
| CPU | ✅ |
| Intelligibility | ✅ |
| Naturalness | ✅ |
| Pronunciation | ✅ |

---

# 25. STT Benchmark

For STT record:

| Metric | Required |
|---|---|
| Language | ✅ |
| Model | ✅ |
| Model size | ✅ |
| Model load time | ✅ |
| Inference time | ✅ |
| RTF | ✅ |
| Memory | ✅ |
| CPU | ✅ |
| WER | ✅ |
| Crash count | ✅ |

---

# 26. Unified Benchmark Screen

Add an optional diagnostics/comparison page.

```text
┌─────────────────────────────────────────┐
│             MODEL BENCHMARK             │
├─────────────────────────────────────────┤
│                                         │
│ STT                                     │
│                                         │
│ Language: Hindi                         │
│ Model: IndicConformer INT8              │
│ WER: XX%                                │
│ RTF: 0.XX                               │
│ RAM: XX MB                              │
│ CPU: XX%                                │
│                                         │
│ TTS                                     │
│                                         │
│ Language: Hindi                         │
│ Engine: Piper/VITS                      │
│ RTF: 0.XX                               │
│ RAM: XX MB                              │
│ CPU: XX%                                │
│ Quality: X/5                            │
│                                         │
└─────────────────────────────────────────┘
```

All numbers must be measured.

Never hard-code results.

---

# 27. Model Loading Strategy

Do not load every language model simultaneously.

Default:

```text
Maximum active STT model = 1
Maximum active TTS model = 1
```

When switching language:

```text
Current model
     ↓
Unload
     ↓
Load new model
     ↓
Ready
```

This is necessary for low-memory testing.

---

# 28. Model Directory

Keep all models within:

```text
stt_english_test/models/
```

Recommended:

```text
models/
│
├── stt/
│   ├── english/
│   │   └── nemo-conformer-ctc-small/
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
└── tts/
    ├── piper/
    │   ├── en/
    │   ├── hi/
    │   ├── gu/
    │   ├── mr/
    │   ├── kn/
    │   ├── ml/
    │   ├── ta/
    │   ├── te/
    │   ├── or/
    │   └── bn/
    │
    └── indic-tts/
        ├── en/
        ├── hi/
        ├── gu/
        ├── mr/
        ├── kn/
        ├── ml/
        ├── ta/
        ├── te/
        └── bn/
```

Only tested/required models should be stored.

---

# 29. APK Size Strategy

Do not package every language model immediately.

During development:

```text
Download
 ↓
Test one model
 ↓
Benchmark
 ↓
Keep if useful
```

Do not keep duplicate model files.

Prefer `arm64-v8a` for the primary target device unless another ABI is required.

---

# 30. Runtime Abstraction

The application should have independent model interfaces.

```text
Speech
│
├── STT
│   ├── NemoConformerEngine
│   └── IndicConformerEngine
│
└── TTS
    ├── PiperVitsEngine
    └── IndicTtsEngine
```

This keeps the application maintainable.

---

# 31. Recommended Package Structure

```text
com.itantra.stt/
│
├── MainActivity.kt
│
├── audio/
│   ├── AudioRecorder.kt
│   └── AudioPlayer.kt
│
├── stt/
│   ├── SttEngine.kt
│   ├── SttResult.kt
│   ├── NemoConformerEngine.kt
│   └── IndicConformerEngine.kt
│
├── tts/
│   ├── TtsEngine.kt
│   ├── TtsResult.kt
│   ├── PiperVitsEngine.kt
│   └── IndicTtsEngine.kt
│
├── model/
│   ├── Language.kt
│   ├── ModelInfo.kt
│   ├── ModelRegistry.kt
│   └── ModelManager.kt
│
├── benchmark/
│   ├── SttMetrics.kt
│   ├── TtsMetrics.kt
│   ├── MemoryMetrics.kt
│   └── CpuMetrics.kt
│
├── diagnostics/
│   └── DeviceInfo.kt
│
└── ui/
    ├── MainScreen.kt
    ├── SttScreen.kt
    └── TtsScreen.kt
```

---

# 32. Permissions

STT requires:

```xml
<uses-permission
    android:name="android.permission.RECORD_AUDIO" />
```

TTS does not require microphone permission.

No networking permissions should be added.

Do not add:

```text
Bluetooth
Wi-Fi
Location
Camera
Contacts
```

---

# 33. Offline Requirement

Both pipelines must work without internet.

Test:

```text
1. Install application
2. Enable Airplane Mode
3. Open application
4. Select language
5. Run STT
6. Run TTS
```

Expected:

```text
STT Internet dependency = NONE
TTS Internet dependency = NONE
```

No inference request can leave the phone.

---

# 34. No Networking

This project does not contain:

```text
BitChat
Bluetooth
Wi-Fi
Wi-Fi Direct
TCP
UDP
WebSocket
REST
HTTP inference
```

Networking will be connected later in the main iTantra application.

---

# 35. No VAD

Do not introduce any VAD model.

Manual recording controls the STT pipeline.

---

# 36. No Translation

Neither STT nor TTS performs translation.

```text
Speech in Hindi
 ↓
Hindi STT
 ↓
Hindi text
```

and:

```text
Hindi text
 ↓
Hindi TTS
 ↓
Hindi speech
```

---

# 37. No LLM

Do not introduce LLM-based:

- transcript correction
- translation
- text rewriting
- TTS preprocessing

The purpose is to benchmark the actual speech models.

---

# 38. Benchmark Storage

All benchmark results remain local.

```text
benchmark/
└── results/
    ├── device_info.json
    │
    ├── stt/
    │   ├── en.json
    │   ├── hi.json
    │   ├── gu.json
    │   ├── mr.json
    │   └── ...
    │
    └── tts/
        ├── en.json
        ├── hi.json
        ├── gu.json
        ├── mr.json
        └── ...
```

---

# 39. Example TTS Benchmark Record

```json
{
  "language": "hi",
  "engine": "piper-vits",
  "voice": "voice-name",
  "modelSizeMb": 0,
  "modelLoadTimeMs": 0,
  "audioDurationMs": 0,
  "synthesisTimeMs": 0,
  "rtf": 0.0,
  "memoryMb": 0,
  "cpuPercent": 0,
  "intelligibility": 0,
  "naturalness": 0,
  "pronunciation": 0
}
```

Zero values here are placeholders only.

The application must write actual measured values.

---

# 40. Development Order

Do not implement everything simultaneously.

## Phase 1 — Preserve Existing STT

Verify:

```text
English
↓
NeMo Conformer-CTC Small
↓
Transcript
```

---

## Phase 2 — IndicConformer

Implement one language first:

```text
Hindi
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

---

## Phase 4 — TTS Foundation

Implement:

```text
TTS screen
↓
Text input
↓
Piper/VITS
↓
Audio playback
```

Start with English or Hindi.

---

## Phase 5 — Piper Language Expansion

Add Piper voices where available.

Each voice must be tested independently.

---

## Phase 6 — Indic-TTS Fallback

For languages where Piper:

```text
is unavailable
OR
fails benchmark
```

add Indic-TTS.

---

## Phase 7 — Benchmarking

Measure:

```text
STT:
WER
RTF
RAM
CPU
Latency

TTS:
RTF
RAM
CPU
Latency
Intelligibility
Naturalness
Pronunciation
```

---

# 41. Recommended Initial Implementation Order

```text
1. Existing English STT
       ↓
2. Hindi IndicConformer
       ↓
3. TTS screen
       ↓
4. English Piper/VITS
       ↓
5. Hindi Piper/VITS
       ↓
6. Indic-TTS fallback
       ↓
7. Other languages
       ↓
8. Performance benchmarking
       ↓
9. Model selection
```

---

# 42. Definition of Done

## STT

- [ ] Existing English NeMo Conformer-CTC Small works.
- [ ] IndicConformer INT8 ONNX works.
- [ ] Language selector works.
- [ ] Correct model loads for each language.
- [ ] Transcript appears in the selected language/script.
- [ ] Manual recording works.
- [ ] No VAD.
- [ ] Offline inference works.
- [ ] RTF is measured.
- [ ] WER benchmark exists.
- [ ] Memory is measured.
- [ ] CPU is measured.

## TTS

- [ ] TTS functionality exists inside this same application.
- [ ] Language selector works.
- [ ] Piper/VITS engine works.
- [ ] Piper is the primary engine.
- [ ] Indic-TTS fallback works.
- [ ] Fallback occurs only when required.
- [ ] Correct language voice is used.
- [ ] Text input works.
- [ ] Speech generation works.
- [ ] Playback works.
- [ ] RTF is measured.
- [ ] Memory is measured.
- [ ] CPU is measured.
- [ ] Human quality scores can be recorded.

## Offline

- [ ] STT works in Airplane Mode.
- [ ] TTS works in Airplane Mode.
- [ ] No cloud APIs.
- [ ] No remote inference.
- [ ] No uploaded audio.

## Isolation

- [ ] No BitChat.
- [ ] No Bluetooth.
- [ ] No Wi-Fi.
- [ ] No VAD.
- [ ] No networking.
- [ ] No Vakyansh.
- [ ] No LLM.
- [ ] No translation.
- [ ] No second Android project.
- [ ] No files modified outside `stt_english_test`.

---

# 43. Final Architecture

```text
                     stt_english_test
                            │
                ┌───────────┴───────────┐
                │                       │
               STT                     TTS
                │                       │
          Language                Language
          Selector                Selector
                │                       │
                ▼                       ▼
          Model Manager            TTS Manager
                │                       │
       ┌────────┴────────┐       ┌──────┴───────┐
       │                 │       │              │
    English           Indic    Piper/VITS   Indic-TTS
       │             Conformer    ONNX       fallback
       │               INT8         │            │
       ▼                 ▼          └─────┬──────┘
     NeMo           IndicConformer       │
       │                 │               │
       └────────┬────────┘               │
                │                        │
                ▼                        ▼
           Transcript                  Audio
                │                        │
                ▼                        ▼
             Metrics                 Playback
                │                        │
                └──────────┬─────────────┘
                           ▼
                      Benchmarking
```

---

# 44. Final Model Policy

## STT

```text
English
→ NeMo Conformer-CTC Small

Indian Languages
→ IndicConformer INT8 ONNX
```

## TTS

```text
Selected Language
       ↓
Piper/VITS available?
       │
       ├── NO → Indic-TTS
       │
       └── YES
             ↓
          Benchmark
             ↓
       Pass quality/
       performance?
          /       \
        YES        NO
         │          │
      Piper     Indic-TTS
```

---

# 45. Final Purpose

The application should ultimately provide empirical evidence for the models that will be integrated into iTantra.

For STT:

```text
Language
 ↓
Correct STT Model
 ↓
WER
 ↓
RTF
 ↓
Memory
 ↓
CPU
```

For TTS:

```text
Language
 ↓
Piper/VITS
       OR
Indic-TTS
 ↓
RTF
 ↓
Memory
 ↓
CPU
 ↓
Human quality
```

The final result should allow the team to determine:

> **Which speech model should be used for each language on the target Android hardware while satisfying the iTantra accuracy, efficiency and latency requirements.**

This remains a single Android project: `stt_english_test`.