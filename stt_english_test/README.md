# STT English + IndicConformer Test Bench

A standalone Android application for testing and benchmarking **offline Speech-to-Text (STT)** models on physical Android devices before integrating the validated speech pipeline into the main **iTantra** application.

---

## 🎯 Supported Models & Languages

| Language | Model Family | Format & Quantization | Vocabulary |
|---|---|---|---|
| **English (`en`)** | NVIDIA NeMo Conformer-CTC Small | ONNX INT8 (46.4 MB) | 1,024 BPE tokens |
| **Hindi (`hi`), Marathi (`mr`), Gujarati (`gu`), Tamil (`ta`), Telugu (`te`), Kannada (`kn`), Malayalam (`ml`), Bengali (`bn`)** | AI4Bharat IndicConformer | ONNX INT8 (187.8 MB) | 5,633 Multi-Indic tokens |

---

## 📥 Required Model & Binary Downloads

Because ML model weights (`.onnx`) and library binaries (`.aar`) exceed GitHub's file size limits, they are excluded from Git via `.gitignore`. 

Follow the steps below to download and set them up before building.

---

### Option A: Quick Automated Setup (Recommended)

#### On Windows (PowerShell):
Open PowerShell in the `stt_english_test` directory and run:

```powershell
# 1. Create target directories
New-Item -ItemType Directory -Force -Path 'app\libs', 'app\src\main\assets\english', 'app\src\main\assets\indicconformer', 'models\english\nemo-conformer-ctc-small', 'models\indicconformer\hi'

# 2. Download Sherpa-ONNX Android AAR runtime (~46 MB)
curl.exe -L -o 'app\libs\sherpa-onnx-1.12.29.aar' 'https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.12.29.aar'

# 3. Download English NeMo Conformer-CTC Small INT8 ONNX (~46 MB)
curl.exe -L -o 'app\src\main\assets\english\model.int8.onnx' 'https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-small/resolve/main/model.int8.onnx'

# 4. Download IndicConformer INT8 ONNX (~188 MB)
curl.exe -L -o 'app\src\main\assets\indicconformer\model.int8.onnx' 'https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx'
```

#### On Linux / macOS (Bash):
Open terminal in the `stt_english_test` directory and run:

```bash
# 1. Create target directories
mkdir -p app/libs app/src/main/assets/english app/src/main/assets/indicconformer models/english/nemo-conformer-ctc-small models/indicconformer/hi

# 2. Download Sherpa-ONNX Android AAR runtime (~46 MB)
curl -L -o app/libs/sherpa-onnx-1.12.29.aar https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.12.29.aar

# 3. Download English NeMo Conformer-CTC Small INT8 ONNX (~46 MB)
curl -L -o app/src/main/assets/english/model.int8.onnx https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-small/resolve/main/model.int8.onnx

# 4. Download IndicConformer INT8 ONNX (~188 MB)
curl -L -o app/src/main/assets/indicconformer/model.int8.onnx https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx
```

---

### Option B: Manual Downloads

If you prefer downloading directly from a browser, place the downloaded files in the following exact locations:

1. **Sherpa-ONNX Android Runtime (`sherpa-onnx-1.12.29.aar`)**
   - **Download Link**: [Hugging Face - sherpa-onnx-1.12.29.aar](https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.12.29.aar)
   - **Destination**: `stt_english_test/app/libs/sherpa-onnx-1.12.29.aar`

2. **English NeMo Conformer-CTC Small Model (`model.int8.onnx`)**
   - **Download Link**: [Hugging Face - model.int8.onnx](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-small/resolve/main/model.int8.onnx)
   - **Destination**: `stt_english_test/app/src/main/assets/english/model.int8.onnx`

3. **IndicConformer INT8 Model (`model.int8.onnx`)**
   - **Download Link**: [Hugging Face - model.int8.onnx](https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx)
   - **Destination**: `stt_english_test/app/src/main/assets/indicconformer/model.int8.onnx`

*(Note: Vocabulary `tokens.txt` files for both models are already tracked and included in the repository).*

---

## 🏗️ Build & Install

Make sure your Android device or emulator has USB debugging enabled:

```powershell
# In stt_english_test folder:
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

---

## 📱 In-App Evaluation Workflow

1. **Launch App**: Open **STT MODEL TEST**.
2. **Select Language**: Tap any language chip (`English`, `Hindi`, `Gujarati`, `Marathi`, `Tamil`, `Telugu`, `Kannada`, `Malayalam`, `Bengali`).
3. **Automatic Lifecycle**: The previous model cleanly unloads to free RAM, and the requested language model initializes asynchronously (`● Model Ready ✓`).
4. **Record**: Tap **START RECORDING**, speak into the microphone (16 kHz Mono PCM), then tap **STOP RECORDING**.
5. **Analyze Benchmarks**:
   - **Transcript**: View recognized text.
   - **Audio Duration**: Duration of the recorded input in seconds.
   - **Inference Time**: STT execution latency in milliseconds.
   - **RTF (Real-Time Factor)**: $\text{Inference Time} / \text{Audio Duration}$ (values $< 1.0$ indicate faster-than-real-time performance).
   - **Memory (RAM)**: Live heap and native memory telemetry.
   - **CPU**: Real-time process CPU utilization during inference.
