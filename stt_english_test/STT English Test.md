# STT English Test

A standalone Android application for evaluating an **offline English Speech-to-Text (STT)** model on a physical Android device.

The purpose of this project is to test the English STT model independently before integrating speech recognition into the main **iTantra** application.

---

## 1. Project Objective

This project has one responsibility:

> **Microphone → user presses button → record speech → offline STT inference → display transcript**

There is deliberately **no VAD**, because recording is controlled manually by a button.

### Core flow

```text
┌──────────────────────┐
│      User taps       │
│    START RECORDING   │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│     Microphone       │
│      AudioRecord     │
└──────────┬───────────┘
           │
       PCM audio
           │
           ▼
┌──────────────────────┐
│     STT / ASR        │
│ Conformer-CTC Small  │
│      INT8 / ONNX     │
└──────────┬───────────┘
           │
        Text
           │
           ▼
┌──────────────────────┐
│   Transcript UI      │
│ "Hello, this is..."  │
└──────────────────────┘
```

---

# 2. Workspace Isolation — CRITICAL

This project is intentionally standalone.

## Absolute rule

**All work must remain inside the `stt_english_test` folder.**

The coding agent must NOT:

- modify files outside this folder
- modify the parent directory
- modify the main `iTantra` project
- modify BitChat source code
- modify any other Git repository
- create files in the user's home directory
- create model files outside this project
- modify global Gradle configuration
- modify global Android Studio configuration
- modify system environment variables
- install global packages unnecessarily
- change unrelated projects

### Allowed scope

```text
stt_english_test/
├── app/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
└── all project-generated files
```

### Forbidden scope

```text
../iTantra/
../anything-else/
C:/Users/<user>/
global Gradle configuration
global Android Studio configuration
other repositories
```

### Command rule

Commands must be executed with:

```text
stt_english_test
```

as the working directory whenever possible.

Before running commands, confirm the current working directory.

On Windows:

```powershell
Get-Location
```

On Linux/macOS:

```bash
pwd
```

The expected location must end with:

```text
stt_english_test
```

---

# 3. Technology Stack

## Android

- Native Android
- Kotlin
- Gradle
- Android SDK
- Android Views/XML initially

## Audio

- `AudioRecord`
- PCM audio
- Mono channel
- 16 kHz sample rate
- 16-bit PCM

## STT

Primary target:

> **NeMo Conformer-CTC Small**

Deployment target:

> **INT8 ONNX**

The model should run entirely locally on the Android device.

## Runtime

Preferred:

> **sherpa-onnx**

Alternative:

> ONNX Runtime Mobile

The first implementation should prefer sherpa-onnx if the selected model is compatible with it.

---

# 4. Non-Goals

Do NOT implement the following in this project.

- ❌ VAD
- ❌ automatic speech endpoint detection
- ❌ TTS
- ❌ BitChat
- ❌ Bluetooth
- ❌ Wi-Fi Direct
- ❌ networking
- ❌ multilingual support
- ❌ language identification
- ❌ cloud APIs
- ❌ backend server
- ❌ database
- ❌ login/authentication
- ❌ user accounts
- ❌ voice cloning
- ❌ prosody
- ❌ LLM post-processing

This is a **single-language offline STT test application**.

---

# 5. Target Language

```text
Language: English
```

The application should assume English input.

There is no language detection in this project.

---

# 6. Target Hardware

The application must be tested on a **real physical Android device**.

Recommended testing devices:

### Primary

Mid-range Android phone.

### Secondary

Low-end/older Android phone.

The project is specifically intended to determine whether the selected model is practical for the iTantra low/mid-range device requirement.

Do not rely only on:

- Android Emulator
- desktop CPU
- high-end gaming phone

---

# 7. Android Requirements

## Minimum SDK

Use:

```text
API 26
```

unless a dependency forces a higher minimum version.

## Target SDK

Use the latest stable Android SDK available in the local development environment.

## Architecture

Prioritize:

```text
arm64-v8a
```

If practical, support:

```text
armeabi-v7a
```

but do not complicate the MVP unnecessarily.

---

# 8. Initial Project Structure

Create the project with this structure:

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
│           │           └── sttenglish/
│           │               ├── MainActivity.kt
│           │               │
│           │               ├── audio/
│           │               │   └── AudioRecorder.kt
│           │               │
│           │               ├── stt/
│           │               │   ├── SttEngine.kt
│           │               │   └── ConformerSttEngine.kt
│           │               │
│           │               ├── benchmark/
│           │               │   └── SttMetrics.kt
│           │               │
│           │               └── model/
│           │                   └── ModelManager.kt
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
│   └── english/
│       ├── model.int8.onnx
│       ├── tokens.txt
│       └── README.md
│
├── test_audio/
│   └── README.md
│
├── benchmark/
│   └── results/
│
├── gradle/
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── README.md
```

---

# 9. Package Structure

Use:

```text
com.itantra.sttenglish
```

Avoid placing classes directly into the root package once the project grows.

---

# 10. Module Responsibilities

## `audio/`

Responsible only for microphone capture.

Example responsibility:

```kotlin
class AudioRecorder {
    fun startRecording()
    fun stopRecording(): ShortArray
}
```

It should produce:

```text
16 kHz
mono
PCM 16-bit
```

It must not know anything about:

- STT
- networking
- UI
- TTS

---

## `stt/`

Contains the speech recognition abstraction.

### Interface

```kotlin
interface SttEngine {

    suspend fun transcribe(
        audio: ShortArray
    ): SttResult
}
```

### Result

```kotlin
data class SttResult(
    val text: String,
    val inferenceTimeMs: Long,
    val audioDurationMs: Long
)
```

---

# 11. STT Model

## Primary model

Use:

> **NeMo Conformer-CTC Small**

Target deployment:

```text
INT8
ONNX
```

The purpose of the test is to evaluate whether this model is suitable for:

- low-memory Android devices
- low CPU usage
- low latency
- offline inference
- real-time or near-real-time use

The model must be kept inside:

```text
stt_english_test/models/
```

or copied into Android assets from there.

Do not put model files in unrelated directories.

---

# 12. Model Loading

Use a dedicated model manager.

Example:

```kotlin
class ModelManager {

    fun loadModel() {
        // Load model from local application assets
    }

    fun isLoaded(): Boolean {
        return true
    }

    fun unloadModel() {
        // Release model resources
    }
}
```

The application should:

1. Start.
2. Load the STT model.
3. Show model status.
4. Allow recording after successful initialization.

Example UI:

```text
┌───────────────────────────────┐
│ STT English Test              │
│                               │
│ Model: Conformer-CTC Small    │
│ Status: Loaded ✓              │
│                               │
│       [ START RECORDING ]      │
│                               │
│ Transcript:                   │
│                               │
│ Hello this is an STT test.   │
│                               │
│ ───────────────────────────── │
│                               │
│ Inference: 312 ms             │
│ Audio:     2.80 s             │
│ RTF:       0.11               │
└───────────────────────────────┘
```

---

# 13. Recording Behaviour

Because there is no VAD:

### Press START

```text
IDLE
 ↓
RECORDING
```

Button changes to:

```text
STOP RECORDING
```

### Press STOP

```text
RECORDING
 ↓
PROCESSING
 ↓
TRANSCRIPTION COMPLETE
 ↓
IDLE
```

The application must not automatically stop recording based on silence.

---

# 14. Audio Format

Use:

```text
Sample rate: 16000 Hz
Channels: 1
Encoding: PCM 16-bit
```

The model adapter should convert the captured PCM representation to whatever input format the runtime requires.

Keep this conversion inside the STT/audio layer.

---

# 15. Main UI Requirements

Keep the UI deliberately simple.

Required elements:

### Model status

```text
Model: Loading...
Model: Ready ✓
Model: Error
```

### Recording button

```text
START RECORDING
```

and:

```text
STOP RECORDING
```

### Processing status

```text
Idle
Recording
Processing
Complete
Error
```

### Transcript

Show the final recognized text.

### Metrics

Show:

```text
Audio duration
Inference duration
RTF
```

---

# 16. RTF Calculation

Real-Time Factor:

```text
RTF = Inference Time / Audio Duration
```

Example:

```text
Audio duration = 5,000 ms
Inference time = 1,000 ms
```

Therefore:

```text
RTF = 1000 / 5000
    = 0.20
```

Interpretation:

```text
RTF < 1.0
    ↓
faster than real time

RTF = 1.0
    ↓
real-time boundary

RTF > 1.0
    ↓
slower than real time
```

Display it clearly.

Example:

```text
Audio Duration : 5.02 s
Inference Time : 1.10 s
RTF            : 0.22
```

---

# 17. Latency Measurement

Record timestamps for:

```text
recordingStarted
recordingStopped
inferenceStarted
inferenceFinished
```

Calculate:

```text
recordingDuration
inferenceDuration
```

Do not include UI animation time in inference latency.

The benchmark must measure the actual STT processing stage.

---

# 18. Benchmark Metrics

The application should eventually measure:

| Metric | Purpose |
|---|---|
| Model size | Storage efficiency |
| Model loading time | Startup performance |
| Peak/loaded memory | RAM requirement |
| CPU usage | Efficiency |
| Audio duration | Input duration |
| Inference time | STT latency |
| RTF | Real-time capability |
| Transcript | Accuracy evaluation |
| Crash count | Reliability |

---

# 19. WER Evaluation

The Android application itself does not need to calculate WER initially.

Maintain a test sentence bank separately.

For example:

```text
test_audio/
```

can contain:

```text
001.wav
002.wav
003.wav
...
```

Maintain matching references:

```text
001.txt
002.txt
003.txt
...
```

Example:

```text
001.wav
Reference:
The emergency team has arrived at the station.
```

Run the same audio through the application and compare:

```text
Reference:
The emergency team has arrived at the station.

Hypothesis:
The emergency team has arrived at the station.
```

WER can then be calculated offline using a tool such as `jiwer`.

---

# 20. Initial English Test Sentence Bank

Create test categories.

## Normal sentences

```text
Hello, how are you today?

The weather is clear this morning.

I am going to the railway station.

Please send the report before five o'clock.
```

## Numbers

```text
There are twenty five people here.

The meeting starts at ten thirty.

Send five units to sector three.
```

## Emergency

```text
There is a fire near the northern gate.

Please evacuate the building immediately.

We need medical assistance.

The bridge has been damaged.
```

## Difficult speech

Include:

- fast speech
- long sentences
- pauses
- uncommon words
- names
- numbers
- background noise
- different accents

---

# 21. Offline Requirement

The application must operate without internet access after installation.

Testing procedure:

1. Install APK.
2. Turn on Airplane Mode.
3. Enable microphone.
4. Start application.
5. Load model.
6. Record speech.
7. Run STT.

Expected:

```text
Internet dependency: NONE
```

No:

```text
REST API
HTTP request
cloud inference
remote STT
```

---

# 22. Permissions

Initially, the main runtime permission required is:

```xml
android.permission.RECORD_AUDIO
```

Request it at runtime.

Do not request unnecessary permissions.

Do not request:

```text
Bluetooth
Location
Network
Contacts
Storage
Camera
```

because this standalone project does not need them.

---

# 23. Error Handling

The app must gracefully handle:

### Microphone permission denied

Display:

```text
Microphone permission is required.
```

### Model failed to load

Display:

```text
STT model could not be loaded.
```

### Unsupported device

Display an understandable error.

### Empty recording

Display:

```text
No speech recorded.
```

### Inference failure

Display:

```text
Transcription failed.
```

Do not crash the application.

---

# 24. Performance Requirements

The application should avoid:

- unnecessary threads
- unnecessary allocations
- repeated model loading
- copying large audio buffers unnecessarily
- running inference on the main UI thread

STT inference must execute away from the Android main thread.

Use Kotlin coroutines where appropriate.

Conceptually:

```text
Main Thread
   │
   ├── UI
   │
   └── user interaction
          │
          ▼
Background Dispatcher
          │
          ▼
       STT Model
```

---

# 25. Model Loading Strategy

Do not repeatedly load the model for every sentence.

Preferred:

```text
Application start
       ↓
Load model
       ↓
Keep model ready
       ↓
Record
       ↓
Inference
       ↓
Record
       ↓
Inference
```

This makes latency measurements meaningful.

---

# 26. Memory Measurement

At minimum, record:

```text
Before model load
After model load
After inference
```

Example:

```text
Memory before model : 120 MB
Memory after model  : 215 MB
Memory after infer  : 228 MB
```

The exact values must come from the actual test device.

Never hard-code benchmark numbers.

---

# 27. CPU Measurement

Measure CPU consumption during:

### Idle

```text
App open
Model loaded
No recording
```

### Recording

```text
Microphone active
```

### Inference

```text
STT running
```

These numbers are useful for evaluating the efficiency criterion of iTantra.

---

# 28. Model Size Reporting

Record:

```text
Original model size
Quantized model size
```

Example:

```text
FP32: XX MB
INT8: XX MB
Reduction: XX%
```

Only display measured values.

---

# 29. Project Independence

This project must remain independent from the main iTantra repository.

Do not:

```text
import ../iTantra
copy source from ../iTantra
modify ../iTantra
link against ../iTantra
share Gradle modules with ../iTantra
```

The eventual iTantra integration should happen later through a clearly defined interface/API.

For now the output is simply:

```text
String transcript
```

---

# 30. Future Integration Contract

The eventual iTantra application should be able to consume the STT functionality through an interface similar to:

```kotlin
interface SttEngine {

    suspend fun transcribe(
        audio: ShortArray
    ): SttResult
}
```

This allows the networking layer to remain completely independent.

Future architecture:

```text
             STT Test App
                  │
                  │ proven implementation
                  ▼
          iTantra Speech Module
                  │
             transcript
                  │
                  ▼
              BitChat
```

---

# 31. Gradle Requirements

Use:

```text
Gradle Kotlin DSL
.gradle.kts
```

The project must contain the Gradle wrapper.

Windows build:

```powershell
.\gradlew.bat assembleDebug
```

Linux/macOS:

```bash
./gradlew assembleDebug
```

Do not depend on a globally installed Gradle version if the wrapper can be used.

---

# 32. Build Validation

After project initialization:

```powershell
.\gradlew.bat assembleDebug
```

Expected result:

```text
BUILD SUCCESSFUL
```

Then:

```powershell
.\gradlew.bat installDebug
```

Run the application on a connected physical phone.

---

# 33. Development Order

Follow this order strictly.

## Phase 1 — Android shell

```text
Gradle project
    ↓
MainActivity
    ↓
Build
    ↓
Install
```

## Phase 2 — Microphone

```text
Permission
    ↓
AudioRecord
    ↓
Record PCM
    ↓
Stop recording
```

## Phase 3 — STT runtime

```text
Load ONNX model
    ↓
Run inference
    ↓
Return transcript
```

## Phase 4 — UI integration

```text
Button
 ↓
Record
 ↓
Inference
 ↓
Transcript
```

## Phase 5 — Benchmarking

```text
Latency
RTF
Memory
CPU
Model size
```

## Phase 6 — Accuracy

```text
Sentence bank
 ↓
Recorded audio
 ↓
STT
 ↓
WER
```

---

# 34. Definition of Done

The project is considered successful when all of the following are true.

### Functional

- [ ] Android application builds.
- [ ] Application installs on a real Android phone.
- [ ] Microphone permission works.
- [ ] START RECORDING works.
- [ ] STOP RECORDING works.
- [ ] Audio is captured as 16 kHz mono PCM.
- [ ] STT model loads locally.
- [ ] Speech is transcribed locally.
- [ ] Transcript appears on screen.
- [ ] No internet connection is required.

### Performance

- [ ] Model loading time is measured.
- [ ] Inference time is measured.
- [ ] RTF is calculated.
- [ ] Memory usage is recorded.
- [ ] CPU usage is recorded.
- [ ] Model size is recorded.

### Accuracy

- [ ] English sentence bank exists.
- [ ] Test recordings exist.
- [ ] Reference transcripts exist.
- [ ] WER can be calculated.
- [ ] At least 20 repeated test runs complete without crashes.

### Isolation

- [ ] No files outside `stt_english_test` are modified.
- [ ] No dependency on the main iTantra application exists.
- [ ] No network/API dependency exists.

---

# 35. Recommended First Milestone

Do not attempt optimization immediately.

First achieve:

```text
Physical Phone
      ↓
Button
      ↓
Microphone
      ↓
Conformer-CTC Small INT8
      ↓
Transcript
```

Then measure.

Only after the baseline works should you investigate:

```text
INT8 optimization
ONNX optimization
threading
buffer sizes
memory optimization
model loading optimization
```

---

# 36. Final Architecture

```text
                ┌─────────────────────────┐
                │      STT English Test   │
                └────────────┬────────────┘
                             │
                             ▼
                    ┌────────────────┐
                    │ MainActivity   │
                    └───────┬────────┘
                            │
                   START / STOP
                            │
                            ▼
                    ┌────────────────┐
                    │ AudioRecorder  │
                    │   AudioRecord  │
                    └───────┬────────┘
                            │
                        PCM 16 kHz
                            │
                            ▼
                    ┌────────────────┐
                    │  SttEngine     │
                    └───────┬────────┘
                            │
                            ▼
               ┌────────────────────────┐
               │ Conformer-CTC Small    │
               │      INT8 / ONNX       │
               └────────────┬───────────┘
                            │
                            ▼
                       Transcript
                            │
              ┌─────────────┴────────────┐
              │                          │
              ▼                          ▼
       Display Text               Benchmark Metrics
                                      │
                         ┌────────────┼─────────────┐
                         ▼            ▼             ▼
                       RTF        Memory          CPU
```

---

# 37. Agent Instruction

When working on this repository, follow these rules:

> **WORKSPACE BOUNDARY: `stt_english_test/` ONLY**

Before modifying anything:

1. Confirm the current working directory.
2. Never access or modify sibling/parent projects.
3. Never modify the main iTantra project.
4. Never modify BitChat source code.
5. Keep all generated source code, models, build configuration, test data and benchmark results inside this folder.
6. Use the project's Gradle wrapper.
7. Do not introduce cloud APIs.
8. Do not introduce proprietary STT SDKs.
9. Do not add VAD.
10. Do not implement networking.
11. Do not implement TTS.
12. Do not expand to other languages.
13. Do not make architectural changes outside this project.
14. If a dependency or build operation appears to require modifying something outside this folder, stop and report it instead of making the modification.
15. Prefer the smallest change required to achieve the current milestone.

The purpose of this repository is **only to benchmark and validate offline English STT on Android** before transferring the proven speech implementation into iTantra.