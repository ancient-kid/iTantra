# STT English Test

A standalone Android application for evaluating an **offline English Speech-to-Text (STT)** model on a physical Android device.

## Overview
- **Model**: NVIDIA NeMo Conformer-CTC Small INT8 (`model.int8.onnx` + `tokens.txt`)
- **Engine**: sherpa-onnx offline inference runtime
- **Audio Capture**: 16 kHz Mono 16-bit PCM via `AudioRecord`
- **Control**: Manual START / STOP button (No VAD)
- **Metrics**: Live Transcript, Audio Duration (s), Inference Duration (ms), Real-Time Factor (RTF), and RAM (MB).

## Build & Install
```powershell
# Inside stt_english_test directory:
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```
