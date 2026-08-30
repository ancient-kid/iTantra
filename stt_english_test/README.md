# STT + TTS Test Bench — iTantra

A standalone Android evaluation benchmark for **offline Speech-to-Text (STT)** and **offline Text-to-Speech (TTS)** neural pipelines on physical Android devices before core integration into **iTantra**.

---

## 🎯 Supported Languages & Models

```text
Speech-to-Text (STT)
 ├── English → NVIDIA NeMo Conformer-CTC Small INT8 ONNX
 └── Indian Languages → AI4Bharat IndicConformer INT8 ONNX (Hindi, Marathi, Gujarati, Telugu, Tamil, Malayalam, Bengali, Kannada, Odia)

Text-to-Speech (TTS)
 ├── Primary Engine → Piper / VITS Neural ONNX Voices
 └── Fallback Policy → AI4Bharat Indic-TTS
```

### Full Multi-Language Matrix:

| Language Code | Language Name | STT Model Family | TTS Model Family | TTS Voice Identifier | Voice Gender |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `en` | **English** | NeMo Conformer-CTC Small | Piper / VITS ONNX | `en_US-amy-low` | Female |
| `hi` | **Hindi** | AI4Bharat IndicConformer | Piper / VITS ONNX | `hi_IN-pratham-medium` | Male |
| `mr` | **Marathi** | AI4Bharat IndicConformer | Piper / VITS ONNX | `mr_IN-google-medium` | Female |
| `gu` | **Gujarati** | AI4Bharat IndicConformer | Piper / VITS ONNX | `gu_IN-cmu-indic_low` | Male |
| `te` | **Telugu** | AI4Bharat IndicConformer | Piper / VITS ONNX | `te_IN-maya-medium` | Female |
| `ta` | **Tamil** | AI4Bharat IndicConformer | Piper / VITS ONNX | `ta_IN-rasa_male-medium` | Male |
| `ml` | **Malayalam** | AI4Bharat IndicConformer | Piper / VITS ONNX | `ml_IN-meera-medium` | Female |
| `bn` | **Bengali** | AI4Bharat IndicConformer | Piper / VITS ONNX | `bn_BD-google-medium` | Female |
| `kn` | **Kannada** | AI4Bharat IndicConformer | AI4Bharat Indic-TTS | Fallback | - |
| `or` | **Odia** | AI4Bharat IndicConformer | AI4Bharat Indic-TTS | Fallback | - |

---

## 📁 Expected Asset Directory Layout

Once downloaded, the assets directory inside `app/src/main/assets/` will match the structure below:

```text
app/src/main/
├── libs/
│   └── sherpa-onnx-1.12.29.aar                 # Android sherpa-onnx JNI runtime
└── assets/
    ├── english/
    │   ├── model.int8.onnx                      # NeMo Conformer-CTC Small (~46 MB)
    │   └── tokens.txt                           # NeMo BPE tokens
    ├── indicconformer/
    │   ├── model.int8.onnx                      # IndicConformer INT8 (~188 MB)
    │   └── tokens.txt                           # IndicConformer 10-language tokens
    ├── espeak-ng-data/                          # Phoneme dictionaries & phonemizer data
    │   ├── phontab
    │   ├── phonindex
    │   └── ...
    └── tts/
        └── piper/
            ├── en/
            │   ├── model.onnx                   # en_US-amy-low (~63 MB)
            │   └── tokens.txt
            ├── hi/
            │   ├── model.onnx                   # hi_IN-pratham-medium (~63 MB)
            │   └── tokens.txt
            ├── ml/
            │   ├── model.onnx                   # ml_IN-meera-medium (~63 MB)
            │   └── tokens.txt
            ├── te/
            │   ├── model.onnx                   # te_IN-maya-medium (~63 MB)
            │   └── tokens.txt
            ├── mr/
            │   ├── model.onnx                   # mr_IN-google-medium (~77 MB)
            │   └── tokens.txt
            ├── bn/
            │   ├── model.onnx                   # bn_BD-google-medium (~77 MB)
            │   └── tokens.txt
            ├── ta/
            │   ├── model.onnx                   # ta_IN-rasa_male-medium (~64 MB)
            │   └── tokens.txt
            └── gu/
                ├── model.onnx                   # gu_IN-cmu-indic_low (~76 MB)
                └── tokens.txt
```

---

## 📥 Model Installation Instructions

Because ML model binaries (`.onnx`, `.aar`, `.tar.bz2`) exceed GitHub's 100 MB file limit, they are excluded from Git repository tracking via `.gitignore`.

Choose one of the installation methods below:

---

### Option 1: 1-Click Automated Python Script (Recommended)

From the `stt_english_test` folder, run:

```bash
python scripts/download_all_models.py
```

This single command automatically:
1. Downloads the `sherpa-onnx` Android AAR native runtime into `app/libs/`.
2. Downloads NeMo Small English STT & IndicConformer Indic STT models into `app/src/main/assets/`.
3. Downloads all 8 Piper/VITS neural voice models (`en`, `hi`, `ml`, `te`, `mr`, `bn`, `ta`, `gu`).
4. Generates all token dictionary mappings (`tokens.txt`) from model JSON metadata.
5. Extracts and prepares `espeak-ng-data` phoneme files.

---

### Option 2: PowerShell (Windows)

Open PowerShell in the `stt_english_test` folder:

```powershell
# 1. Create target directories
New-Item -ItemType Directory -Force -Path `
  'app\libs', `
  'app\src\main\assets\english', `
  'app\src\main\assets\indicconformer', `
  'app\src\main\assets\tts\piper\en', `
  'app\src\main\assets\tts\piper\hi', `
  'app\src\main\assets\tts\piper\ml', `
  'app\src\main\assets\tts\piper\gu', `
  'models'

# 2. Sherpa-ONNX Android AAR Native Runtime (~46 MB)
curl.exe -L -o 'app\libs\sherpa-onnx-1.12.29.aar' 'https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.12.29.aar'

# 3. STT English: NeMo Conformer Small INT8 (~46 MB)
curl.exe -L -o 'app\src\main\assets\english\model.int8.onnx' 'https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-small/resolve/main/model.int8.onnx'

# 4. STT Indic: AI4Bharat IndicConformer INT8 (~188 MB)
curl.exe -L -o 'app\src\main\assets\indicconformer\model.int8.onnx' 'https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx'

# 5. TTS English (vits-piper-en_US-amy-low) + espeak-ng-data (~64 MB)
curl.exe -L -o 'models\vits-piper-en_US-amy-low.tar.bz2' 'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2'
tar.exe -xf 'models\vits-piper-en_US-amy-low.tar.bz2' -C 'models\'
Copy-Item 'models\vits-piper-en_US-amy-low\en_US-amy-low.onnx' 'app\src\main\assets\tts\piper\en\model.onnx' -Force
Copy-Item 'models\vits-piper-en_US-amy-low\tokens.txt' 'app\src\main\assets\tts\piper\en\tokens.txt' -Force
robocopy 'models\vits-piper-en_US-amy-low\espeak-ng-data' 'app\src\main\assets\espeak-ng-data' /E /NFL /NDL /NJH /NJS

# 6. TTS Hindi (vits-piper-hi_IN-pratham-medium) (~64 MB)
curl.exe -L -o 'models\vits-piper-hi_IN-pratham-medium.tar.bz2' 'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-pratham-medium.tar.bz2'
tar.exe -xf 'models\vits-piper-hi_IN-pratham-medium.tar.bz2' -C 'models\'
Copy-Item 'models\vits-piper-hi_IN-pratham-medium\hi_IN-pratham-medium.onnx' 'app\src\main\assets\tts\piper\hi\model.onnx' -Force
Copy-Item 'models\vits-piper-hi_IN-pratham-medium\tokens.txt' 'app\src\main\assets\tts\piper\hi\tokens.txt' -Force

# 7. TTS Malayalam (vits-piper-ml_IN-meera-medium) (~64 MB)
curl.exe -L -o 'models\vits-piper-ml_IN-meera-medium.tar.bz2' 'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ml_IN-meera-medium.tar.bz2'
tar.exe -xf 'models\vits-piper-ml_IN-meera-medium.tar.bz2' -C 'models\'
Copy-Item 'models\vits-piper-ml_IN-meera-medium\ml_IN-meera-medium.onnx' 'app\src\main\assets\tts\piper\ml\model.onnx' -Force
Copy-Item 'models\vits-piper-ml_IN-meera-medium\tokens.txt' 'app\src\main\assets\tts\piper\ml\tokens.txt' -Force

# 8. TTS Gujarati (vits-mimic3-gu_IN-cmu-indic_low) (~76 MB)
curl.exe -L -o 'models\vits-mimic3-gu_IN-cmu-indic_low.tar.bz2' 'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mimic3-gu_IN-cmu-indic_low.tar.bz2'
tar.exe -xf 'models\vits-mimic3-gu_IN-cmu-indic_low.tar.bz2' -C 'models\'
Copy-Item 'models\vits-mimic3-gu_IN-cmu-indic_low\gu_IN-cmu-indic_low.onnx' 'app\src\main\assets\tts\piper\gu\model.onnx' -Force
Copy-Item 'models\vits-mimic3-gu_IN-cmu-indic_low\tokens.txt' 'app\src\main\assets\tts\piper\gu\tokens.txt' -Force

# 9. Complete remaining Indic voices (Telugu, Marathi, Bengali, Tamil) via script
python scripts/download_indic_voices.py
```

---

### Option 3: Bash (Linux / macOS)

Open terminal in the `stt_english_test` folder:

```bash
# 1. Create target directories
mkdir -p app/libs \
  app/src/main/assets/english \
  app/src/main/assets/indicconformer \
  app/src/main/assets/tts/piper/en \
  app/src/main/assets/tts/piper/hi \
  app/src/main/assets/tts/piper/ml \
  app/src/main/assets/tts/piper/gu \
  models

# 2. Sherpa-ONNX Android AAR Native Runtime
curl -L -o app/libs/sherpa-onnx-1.12.29.aar https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.12.29.aar

# 3. STT English: NeMo Conformer Small INT8
curl -L -o app/src/main/assets/english/model.int8.onnx https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-small/resolve/main/model.int8.onnx

# 4. STT Indic: IndicConformer INT8
curl -L -o app/src/main/assets/indicconformer/model.int8.onnx https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx

# 5. Run Python setup script to complete all TTS voices
python3 scripts/download_all_models.py
```

---

## 📋 Direct Manual Download Reference

| Model / Asset | Target Path | Direct Download URL | Size |
| :--- | :--- | :--- | :--- |
| **Sherpa Android AAR** | `app/libs/sherpa-onnx-1.12.29.aar` | [Hugging Face AAR Link](https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.12.29.aar) | ~46 MB |
| **STT English NeMo Small** | `app/src/main/assets/english/model.int8.onnx` | [Hugging Face NeMo INT8 Link](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-small/resolve/main/model.int8.onnx) | ~46 MB |
| **STT IndicConformer INT8** | `app/src/main/assets/indicconformer/model.int8.onnx` | [Hugging Face IndicConformer Link](https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx) | ~188 MB |
| **TTS English (`en`)** | `app/src/main/assets/tts/piper/en/model.onnx` | [GitHub Release Link](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2) | ~63 MB |
| **TTS Hindi (`hi`)** | `app/src/main/assets/tts/piper/hi/model.onnx` | [GitHub Release Link](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-pratham-medium.tar.bz2) | ~63 MB |
| **TTS Malayalam (`ml`)** | `app/src/main/assets/tts/piper/ml/model.onnx` | [GitHub Release Link](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ml_IN-meera-medium.tar.bz2) | ~63 MB |
| **TTS Gujarati (`gu`)** | `app/src/main/assets/tts/piper/gu/model.onnx` | [GitHub Release Link](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mimic3-gu_IN-cmu-indic_low.tar.bz2) | ~76 MB |
| **TTS Telugu (`te`)** | `app/src/main/assets/tts/piper/te/model.onnx` | [Hugging Face Maya ONNX](https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx) | ~63 MB |
| **TTS Marathi (`mr`)** | `app/src/main/assets/tts/piper/mr/model.onnx` | [Hugging Face Google ONNX](https://huggingface.co/rhasspy/piper-voices/resolve/main/mr/mr_IN/google/medium/mr_IN-google-medium.onnx) | ~77 MB |
| **TTS Bengali (`bn`)** | `app/src/main/assets/tts/piper/bn/model.onnx` | [Hugging Face Google ONNX](https://huggingface.co/rhasspy/piper-voices/resolve/main/bn/bn_BD/google/medium/bn_BD-google-medium.onnx) | ~77 MB |
| **TTS Tamil (`ta`)** | `app/src/main/assets/tts/piper/ta/model.onnx` | [Hugging Face Rasa ONNX](https://huggingface.co/tinisoft/piper-ta_IN-rasa_male-medium/resolve/main/ta_IN-rasa_male-medium.onnx) | ~64 MB |

---

## 🏗️ Build & Run

Connect your physical Android device with USB debugging enabled, then execute:

```powershell
# From stt_english_test directory:
.\gradlew.bat installDebug
```

---

## 📱 In-App Evaluation Workflow

### 🎙️ Speech-to-Text (STT) Tab:
1. Tap the **STT** tab toggle.
2. Select target language chip (`English`, `Hindi`, `Marathi`, `Gujarati`, `Tamil`, `Telugu`, `Malayalam`, `Bengali`, etc.).
3. Notice the single active model lifecycle (previous model is safely unloaded from memory before the selected model loads).
4. Tap **START RECORDING**, speak into the microphone, and tap **STOP RECORDING**.
5. Inspect the generated transcript and stage telemetry (Audio duration, Inference time, Real-Time Factor / Speedup, Process Heap RAM, Native Heap RAM, CPU usage).

### 🔊 Text-to-Speech (TTS) Tab:
1. Tap the **TTS** tab toggle.
2. Select target language chip (`English`, `Hindi`, `Marathi`, `Tamil`, `Telugu`, `Malayalam`, `Bengali`, `Gujarati`).
3. Tap a sample phrase chip (**Normal**, **Emergency**, **Numbers**, **Names**) or type custom text.
4. Tap **🔊 GENERATE SPEECH**.
5. Audio is synthesized completely offline on-device and plays automatically through the phone speaker.
6. Tap **▶ PLAY AUDIO** or **■ STOP** to replay and benchmark audio naturalness and clarity.
