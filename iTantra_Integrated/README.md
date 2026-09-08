# iTantra — Integrated Offline Voice Mesh

The two halves of the project, merged into one buildable Android app:

- **the transport half** — the bitchat-derived BLE + Wi-Fi Aware mesh that lived in the
  repository root (`../src/main/java/com/bitchat/**`)
- **the speech half** — the on-device sherpa-onnx STT and TTS engines that lived in
  `../stt_english_test`

Neither original project was modified. This folder is a self-contained Gradle project.

## The flow

```
  SENDER                                  MESH                              RECEIVER
  ------                                  ----                              --------
  hold PTT
    -> AudioRecorder      16 kHz mono PCM
    -> SttEngine          NeMo Conformer-CTC (en) / IndicConformer (indic)
    -> VoicePayload       ITV1|<lang>|<priority>|<capturedAt>|<transcript>
    -> MeshTransport  ------ BLE mesh (7-hop) ------>  MeshTransport
                      ------ Wi-Fi Aware      ------>    (de-duplicated)
                                                      -> VoicePayload.decode
                                                      -> TtsEngine   VITS
                                                      -> PriorityAudioPlayer
                                                         media stream, or the
                                                         alarm stream for ALERT
```

Only three things ever touch the outside world: the microphone, the speaker, and the
radios. **The pipeline itself makes no network calls** — the only use of the internet in
the whole app is downloading language packs, which happens ahead of time on the Models
screen and never during operation.

## Language coverage

English is bundled in the APK and works offline the moment the app is installed.
Everything else is downloaded on demand from the **Language models** screen.

| Language | STT | Voice | Pack |
|---|---|---|---|
| English | NeMo Conformer-CTC small INT8 | piper `en_US-amy-low` | **bundled** |
| Hindi | IndicConformer¹ | piper `hi_IN-pratham-medium` | `stt-indic` + `tts-hi` |
| Bengali | IndicConformer¹ | piper `bn_BD-google-medium` | `stt-indic` + `tts-bn` |
| Marathi | IndicConformer¹ | piper `mr_IN-google-medium` | `stt-indic` + `tts-mr` |
| Tamil | IndicConformer¹ | piper `ta_IN-rasa_male-medium` | `stt-indic` + `tts-ta` |
| Telugu | IndicConformer¹ | piper `te_IN-maya-medium` | `stt-indic` + `tts-te` |
| Malayalam | IndicConformer¹ | piper `ml_IN-meera-medium` | `stt-indic` + `tts-ml` |
| Gujarati | IndicConformer¹ | mimic3 `gu_IN-cmu-indic_low` | `stt-indic` + `tts-gu` |
| Kannada | IndicConformer¹ | MMS VITS `kan` ² | `stt-indic` + `tts-kn` |
| Odia | IndicConformer¹ | MMS VITS `ory` ² | `stt-indic` + `tts-or` |

¹ One shared 188 MB checkpoint serves all nine Indic languages — download it once.
² **Licensed CC-BY-NC 4.0** (non-commercial). Flagged with a badge in the Models screen
and behind a confirmation dialog. Fine for research, demos and evaluation; not for a
commercial deployment.

Two notes on how this changed from the test bench:

- **Kannada and Odia are new.** Neither has a voice in `rhasspy/piper-voices` or the mimic3
  set, and neither exists in the sherpa-onnx release. They come from
  `willwade/mms-tts-multilingual-models-onnx`, a bulk ONNX conversion of Meta's MMS-TTS,
  already in the exact `model.onnx` + `tokens.txt` layout sherpa-onnx wants. Odia is now a
  complete language: its STT was always covered — the shared IndicConformer vocabulary
  contains 457 Odia tokens — it was simply disabled in code.
- **The synthetic fallback is gone.** `IndicTtsEngine` was not a TTS engine: it generated a
  220 Hz sine wave and reported itself as "AI4Bharat Indic-TTS". Kannada was wired to it,
  so Kannada appeared to work and played a beep. A language with no installed voice now
  shows its text and says so plainly.

## APK size

| | Before | Now |
|---|---|---|
| APK | 853 MB | **153 MB** |
| native libs | 81 MB (3 ABIs) | 28 MB (arm64-v8a only) |
| models in APK | 771 MB | 122 MB (English + espeak-ng data) |

Downloadable catalogue is 866 MB total, but a realistic install is far smaller: English
alone is 0 MB extra, and one Indic language costs 188 MB (shared STT) + ~65 MB (voice).
Each additional Indic language after that is voice-only.

The ABI change is worth knowing about: `armeabi-v7a` and `x86_64` were dropped. Every
realistic target phone is arm64, but this does mean the app **no longer runs on an x86
emulator** — restore the ABI in `app/build.gradle.kts` if you need that for the test bench.

## Hosting the packs

Six of the downloadable files come straight from upstream Hugging Face and need no hosting
(`stt-indic`, `tts-kn`, `tts-or` — all verified HTTP 200 with matching sizes). The seven
Piper/mimic3 voices must be self-hosted, because upstream they exist only inside `.tar.bz2`
archives (no bzip2 in the Android runtime) or ship a `.onnx.json` from which `tokens.txt`
has to be generated.

```
python scripts/build_model_packs.py --repo <your-hf-user>/itantra-models
```

This copies those seven packs out of the test-bench assets **byte for byte** — so they are
the exact files already tested, not a rebuild — into `model_packs/`, checksums everything,
and regenerates `app/src/main/assets/models/manifest.json`. Then upload `model_packs/*` to
that Hugging Face model repo, preserving the directory names (~460 MB, once).

The script also verifies the local IndicConformer checkpoint against the published upstream
SHA-256, confirming the download URL serves the same bytes you validated. It currently
passes.

Until the upload happens, those seven packs will fail with HTTP 401 in the app; the other
six work immediately.

## What was added to glue the halves together

Everything new lives under `com.itantra.app`.

| File | Role |
|---|---|
| `protocol/VoicePayload.kt` | The wire format. Transcript + language tag + priority flag + capture timestamp in the single string the mesh carries. Unrecognised bodies decode as legacy plain text. |
| `transport/MeshTransport.kt` | One facade over both radios: lifecycle, Bluetooth on/off receiver, peer bookkeeping, and cross-transport de-duplication. |
| `pipeline/VoicePipeline.kt` | Push-to-talk state machine and both speech paths. Reports missing packs as a distinct outcome so the UI can offer the download. |
| `audio/PriorityAudioPlayer.kt` | ALERT messages play on the **alarm stream** at forced-max volume (restored afterwards) plus vibration — audible through silent mode and DND. |
| `diagnostics/PipelineTelemetry.kt` | Per-stage timings, model load times, memory, `n/n OK` reliability counter. |
| `models/ModelPack.kt` · `ModelStore.kt` | The pack catalogue, install state, and path resolution (bundled assets vs downloaded files). |
| `models/ModelDownloadManager.kt` | Resumable, checksum-verified downloads. Writes to `.part`, verifies SHA-256, then renames — a dropped connection leaves a resumable partial, never a truncated `.onnx`. Application-scoped, so leaving the screen does not cancel a 188 MB download. |
| `models/ModelsActivity.kt` | The Models screen: every pack with size, licence badge, status and progress. Nothing downloads without an explicit tap. |
| `MainActivity.kt` + `activity_itantra.xml` | The walkie-talkie UI. |

Modified in the copied STT/TTS sources, minimally: the three engines now accept either an
`AssetManager` or a filesystem path (sherpa-onnx annotates that parameter `@Nullable`
precisely for this), `PiperVitsEngine` gained a phonemizer flag, and `ModelRegistry` /
`TtsManager` resolve paths through `ModelStore`.

**The phonemizer flag matters.** Piper and mimic3 voices are phoneme-based and need the
espeak-ng data directory. The MMS voices are character-based — 75 Kannada and 76 Odia
tokens, all script characters — and must run with an empty `dataDir`. Passing them the
espeak path makes them mispronounce everything.

## Running it

```
cd iTantra_Integrated
./gradlew installDebug        # two physical phones; BLE cannot be tested on emulators
```

Grant microphone, Bluetooth and location/nearby-devices permissions on first launch —
Wi-Fi Aware discovery needs location even though nothing here uses GPS.

To test: launch on both phones, wait for the header to read **Connected — 1 peer(s)**, hold
the button on phone A and speak, release. Phone B shows the transcript and speaks it.

**Provision before going offline.** Download every language you plan to demo while you still
have internet, then confirm the Models screen shows them installed. Once you are in airplane
mode there is no way to fetch a missing pack.

## Current limits

- **Verified by build and inspection, not on hardware.** The APK builds and every download
  URL and checksum was checked, but the two-phone loop, the Kannada/Odia voices, and Odia
  STT have not been run on real devices yet.
- **The mesh runs in the activity scope**, matching the original MVP. Long backgrounding can
  let the OS reclaim the radios; a foreground service is Phase 6 hardening work.
- **Downloads are not a foreground service either** — they survive leaving the Models screen,
  but not the app being killed. They resume from where they stopped on the next attempt.
- **"Spoken to heard" in diagnostics depends on the two phones' clocks agreeing.** The
  per-device numbers beside it are measured locally and are the trustworthy ones.
- **The MMS voices are fp32**, ~109 MB each versus ~65 MB for a Piper voice. Int8
  quantisation would cut that roughly 4× but needs listening tests; post-deadline polish.
- The original **STT/TTS test bench is still in the app** (Diagnostics → *Open STT / TTS model
  test bench*) for per-module WER and RTF work.
