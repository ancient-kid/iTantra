# iTantra — SIH 2026 Idea Deck: Architecture + Slide Content

PS 26173 · Smart Automation · Software

---

## 0. Read this first (3 things that affect selection)

**1. The Bitchat name is a risk right now.** On 23 July 2026 the Home Ministry's I4C ordered GitHub to take down the Bitchat repositories under Sec 79(3)(b) of the IT Act. The reasons given were *anonymous communication, no registration, and blocking lawful interception*. SIH judges include government officials, so a deck that leads with "we took Bitchat" invites that exact objection.
- **What to do:** don't hide it. The MIT licence requires credit, and hiding it would look worse if a judge spots it. Just don't *lead* with it. Call the transport the **"iTantra Mesh — re-engineered from an open-source (MIT) BLE mesh stack"** and credit bitchat-android on the References slide.
- Position iTantra as the **accountable** version: an emergency-only, voice-first tool for authorised responders and citizens. Put "registered responder IDs + tamper-evident message log" on the roadmap (Slide 4). That answers the ministry's concern head-on.
- **Before recording the demo, rename in code:** `SERVICE_NAME = "bitchat"` and `PSK = "bitchat_secret"` in `WifiAwareMeshService.kt`. Wi-Fi Aware advertises that name to nearby phones, and the old PSK is public.

**2. The judges' weights: Accuracy 40%, Efficiency 20%, Latency 20%.** The mesh is your differentiator, but it isn't scored directly. Every number below is mapped to one of those three buckets.

**3. Two gaps against the PS wording.**
- The PS asks for STT that triggers *"after detecting pauses and stoppages"*, plus a hands-free "phone" mode when PTT is off. The app currently only has push-to-talk (no VAD).
- Adding **Silero VAD** (open-source, ~2 MB, already supported by sherpa-onnx) closes both gaps. If you can't build it in time, list it as "in progress" on Slide 3. Don't claim it.

Multi-hop: the slides say "built-in relay, up to 7 hops (TTL = 7)". That is exactly what the code does. Avoid the words "tested" or "field-proven" for multi-hop until you've run it on 3 phones.

---

## 1. Eraser.io diagram code

Paste into eraser.io → New diagram → **Cloud Architecture** (diagram-as-code). If an icon doesn't render, pick a replacement from the icon picker; the layout still works.

```
title iTantra: Speak in any language. Heard without internet.
direction right
colorMode pastel
styleMode shadow
typeface clean

// ---------- One-time, before the disaster ----------
Setup [label: "One-time setup (while online)", icon: download, color: gray] {
  packs [label: "Language packs: 10 languages", icon: hard-drive]
}

// ---------- Sender ----------
Sender [label: "Phone A: Speaker", icon: smartphone, color: blue] {
  ptt [label: "Push-to-Talk / ALERT button", icon: mic]
  stt [label: "On-device STT (Conformer INT8)", icon: cpu]
  pkt [label: "Voice packet: lang + priority + time + text", icon: package]
}

// ---------- Mesh ----------
Mesh [label: "iTantra Mesh: no towers, no internet", icon: share-2, color: green] {
  router [label: "Dual-radio router + signed packet", icon: shield]
  ble [label: "Bluetooth LE mesh", icon: bluetooth]
  nan [label: "Wi-Fi Aware", icon: wifi]
  relay [label: "Relay phones (up to 7 hops)", icon: repeat]
}

// ---------- Receiver ----------
Receiver [label: "Phone B: Listener", icon: smartphone, color: orange] {
  dedup [label: "Duplicate filter", icon: filter]
  tts [label: "On-device TTS (VITS voice)", icon: cpu]
  normal [label: "Voice note", icon: volume-2]
  alert [label: "ALERT: max volume, bypasses silent/DND", icon: bell]
}

// ---------- Flow ----------
ptt > stt: 16 kHz audio
stt > pkt: transcript
pkt > router: ~230 bytes
router > ble
router > nan
ble <> relay
relay > dedup
nan > dedup
dedup > tts: text
tts > normal: NORMAL
tts > alert: ALERT

packs --> stt: speech model
packs --> tts: voice
```

This is accurate to the code:
- `VoicePipeline` → `VoicePayload` (`ITV1|lang|priority|time|text`) → `MeshTransport` sends on BLE and Wi-Fi Aware and de-duplicates.
- Broadcast packets are Ed25519-**signed** (tamper-evident), not encrypted, so the diagram says "signed".
- `PacketRelayManager` handles TTL 7, and `PriorityAudioPlayer` handles ALERT on the alarm stream plus vibration.

---

## 2. The numbers

### A. Calculated from your code (safe to put on slides)

Example message: *"There is a fire near the northern gate. We need medical assistance."* 12 words, about 5 s of speech.

Bytes on the wire were computed with the app's real packet format: 14-byte header + 8 sender + 8 recipient + 64 signature + payload, with DEFLATE for payloads over 100 bytes.

| Language | Payload | **On-air packet** |
|---|---|---|
| English | 95 B | **189 B** |
| Hindi | 176 B → 131 B compressed | **225 B** |
| Tamil | 212 B → 133 B compressed | **227 B** |

**Headline: ≈230 bytes for 5 s of speech ≈ 0.37 kbps**

| Same 5-second message sent as… | Data | BLE packets* | iTantra is… | Data saved |
|---|---|---|---|---|
| Raw audio (16 kHz PCM) | 160 KB | 342 | **~700× smaller** | 99.9% |
| WhatsApp-style voice note (Opus 16 kbps) | 10 KB | 22 | **43× smaller** | **97.7%** |
| Mobile call (AMR-NB 12.2 kbps) | 7.6 KB | 17 | **33× smaller** | 97.0% |
| Codec2 1.2 kbps (best low-bitrate voice codec) | 750 B | 2 | **3.3× smaller** | 69% |
| **iTantra (speech → text → speech)** | **≈230 B** | **1** | — | — |

\*Your code fragments anything over 512 bytes into 469-byte pieces (`AppConstants.Fragmentation`).

**Why "1 packet" matters: a delivery model.** Assume 2% packet loss, first attempt, no retries. A message only arrives if *every* fragment arrives.

| | 1 hop | 3 hops |
|---|---|---|
| Voice note (22 packets) | 64% | **26%** |
| **iTantra (1 packet)** | **98%** | **94%** |

Label this on the slide as *"illustrative model, 2% per-packet loss"*. It's honest and very persuasive.

**App size (from your README):**
- APK 853 MB → **153 MB (−82%)**
- Native libraries 81 → 28 MB (−65%)
- One shared Indic STT model serves 9 languages instead of 9 separate models (**−89% storage**)
- INT8 quantisation: 470 → 188 MB (**−60%**)

**Model footprint (from `manifest.json`):**
- English STT 46 MB
- Indic STT 197 MB, shared by 9 languages
- Piper voices ~63–77 MB each
- A realistic Hindi install: 153 MB APK + 197 + 63 ≈ **413 MB**, then each extra language is voice only

### B. Published benchmarks (cite as "published", not "ours")
- NeMo Conformer-CTC Small (English): **WER 3.7%** on LibriSpeech test-clean and 8.1% on test-other (NVIDIA model card).
- IndicConformer family (AI4Bharat) supports all 22 scheduled languages. Don't put their Hindi WER on the slide as yours, because your INT8 conversion is a different checkpoint. Measure your own (see C).

### C. Measure these on real phones (≈2 hours, fill the `[__]` slots)
Your app already records most of these on the Diagnostics screen (`PipelineTelemetry`).

| # | Metric (PS bucket) | How | Slot |
|---|---|---|---|
| 1 | STT time + **RTF** (Latency) | Diagnostics → STT ms / RTF, median of 10 utterances per language | `[__ ms] / [0.__]` |
| 2 | TTS synthesis + **RTF** (Latency) | Diagnostics → TTS RTF, median of 10 | `[0.__]` |
| 3 | **Spoken → heard on Phone B** (Latency) | Record both screens with one camera and a visible stopwatch; release of PTT → first audio | `[__ s]` |
| 4 | **WER** per language (Accuracy) | 15 sentences from `test_audio/*/sentences.json` × 5 languages (EN, HI, MR, TA, BN minimum) → `pip install jiwer` → `jiwer.wer(refs, hyps)` | `[__%]` |
| 5 | TTS legibility (Accuracy) | 5 listeners rate 10 sentences 1–5 (MOS) and write down what they heard (intelligibility %) | `MOS [_._]`, `[__%]` |
| 6 | RAM (Efficiency) | `adb shell dumpsys meminfo com.itantra.mvp` → TOTAL PSS, idle vs speaking | `[__ MB]` |
| 7 | **Idle CPU** (Efficiency) | App armed, 5 min idle: `adb shell top -b -n 5 -d 60 \| grep itantra` | `[_._%]` |
| 8 | Battery/hour idle (Efficiency) | `adb shell dumpsys batterystats --reset`, 60 min idle, then read app estimate | `[__%/hr]` |
| 9 | Reliability | 20 PTT sends at 10 m, phones in pockets → Diagnostics `n/n OK` | `[__/20]` |
| 10 | Range | Walk apart until delivery fails (BLE, then Wi-Fi Aware) | `[__ m]` |
| 11 | ALERT in silent/DND | Pass/Fail on both phones | ✅ |

Test on a **low/mid-range phone** (the PS says so explicitly) and write its name on the slide, e.g. "Redmi 12, 4 GB RAM". If Phone B is a flagship, say so.

---

## 3. Slide-by-slide content

Design system for all slides: white background, one accent colour per idea (Blue = speak/STT, Green = mesh, Orange = listen/TTS, Red = ALERT). Use icons rather than bullets, one big number per block, and ≤25 words of body text per slide. Keep the template's header pointers unchanged (SIH rule). Export to PDF.

### Slide 1 — Title
Fill in the template fields:
- Problem Statement ID: 26173
- Title: iTantra – Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links
- Theme: Smart Automation · Category: Software · Team ID/Name: [__]

Add one hero line under the template text:

> **"Speak in your language. Be heard, even when the towers fall."**

Visual: 3 small icons in a row, 🎙️ → 📶✕ (crossed tower) → 🔊, with captions **10 languages · 0 internet · 230 bytes**.

---

### Slide 2 — Idea Title: *iTantra — Offline Voice Mesh for Disasters*

**Top strip (the problem, 3 icon tiles):**
| 🗼✕ | 🗣️ | 📉 |
|---|---|---|
| **Towers fail first.** Cyclone Fani: all phones down in Puri district; 16.5 M people affected | **1 in 5 Indians (7+) can't read.** Text SMS excludes them | **Voice is heavy.** A 5 s voice note = 10 KB, too big for weak links |

**Middle (the idea, one horizontal flow graphic):**
🎙️ Speak (any of 10 languages) → 🧠 Phone converts to text **on-device** → 📡 **230-byte** packet hops phone-to-phone over Bluetooth/Wi-Fi → 🧠 Phone turns it back into speech → 🔊 Heard. ALERT messages ring at max volume, even on silent.

**How it addresses the problem (3 ticks):**
- ✅ Works with **zero** towers, SIM or internet
- ✅ **Voice in, voice out**, so usable by non-readers
- ✅ **43× less data** than a voice note, so it fits in 1 packet

**Innovation & uniqueness (badge row, 4 hexagons):**
1. **Send meaning, not sound.** Speech → text → speech cuts data by 97.7%
2. **Dual-radio mesh:** BLE + Wi-Fi Aware, multi-hop relay (up to 7 hops)
3. **10 Indian languages, 1 shared Indic model**, fully on-device
4. **Emergency ALERT mode:** alarm stream, forced max volume, vibration, bypasses DND

Speaker note: "A walkie-talkie needs a radio. A phone call needs a tower. iTantra needs only the phones people already carry."

---

### Slide 3 — Technical Approach

**Left 60%: the Eraser diagram** (export PNG, transparent background).

**Right 40%: tech stack as logo chips**, grouped in 3 rows:
- **App:** Kotlin · Android (min SDK 26) · Coroutines
- **AI (open-source, offline):** sherpa-onnx (ONNX Runtime) · NeMo Conformer-CTC INT8 (EN) · AI4Bharat IndicConformer INT8 (9 Indic) · Piper / VITS voices
- **Network:** BLE GATT mesh · Wi-Fi Aware (NAN) · Ed25519-signed packets · DEFLATE · TTL-based relay

**Bottom strip: Methodology (5-step timeline with ✅):**
1. ✅ De-risk mesh: 2-phone text over BLE
2. ✅ STT per language (test bench, RTF logging)
3. ✅ TTS + ALERT priority playback
4. ✅ Integrate: PTT loop, telemetry, model-pack downloader
5. 🔄 Next: VAD auto-mode, field tests, INT8 voices

**Mini scorecard (3 tiles mapped to the PS metrics), the most important element on this slide:**
| ⚡ Efficiency | 🎯 Accuracy | ⏱️ Latency |
|---|---|---|
| APK **153 MB** (−82%) · Idle CPU **[_._%]** · RAM **[__ MB]** | STT WER EN **[__%]** · HI **[__%]** · TTS MOS **[_._]** | STT RTF **[0.__]** · TTS RTF **[0.__]** · Spoken→heard **[__ s]** |

Speaker note: "Everything in the pipeline runs on the phone. The only internet use in the whole app is downloading language packs *before* a disaster."

---

### Slide 4 — Feasibility and Viability

**Top: feasibility proof (4 green check tiles):**
- 📱 Runs on existing Android phones. **No new hardware**
- 🧩 **100% open-source** models + runtime (Apache-2.0 / MIT)
- 📦 English works **offline from install**; other languages are one-time packs, resumable and checksum-verified
- 🔁 Working build: full speak → mesh → speak loop, reliability **[__/20]**

**Middle: Challenges → Strategy (a 2-column table with icons, one line each):**
| ⚠️ Challenge | 🛠️ Our strategy |
|---|---|
| BLE range ~10–30 m | Multi-hop relay (every phone is a tower) + Wi-Fi Aware for longer, faster links |
| Model size on low-end phones | INT8 quantisation (−60%), one shared Indic model, download only needed languages |
| STT errors in noise | Transcript shown on both phones; roadmap: noise suppression + VAD |
| Receiver hears a synthetic voice, not the sender | Sender name + language tag shown; roadmap: emotion/urgency tag |
| Android may kill background radios | Foreground service (next build) |
| Misuse / anonymity concerns | Emergency-only design, registered responder IDs, signed packets + tamper-evident log |
| Kannada/Odia voices are non-commercial licence (CC-BY-NC) | Fine for pilots; retrain on open IndicTTS data for deployment |

**Bottom: viability line:**
> **₹0 infrastructure cost · 0 recurring cost · deploys through app store or offline APK share**

---

### Slide 5 — Impact and Benefits

**Top: target audience (5 icon circles):**
🚒 NDRF / SDRF rescue teams · 🏘️ Villages in cyclone/flood/landslide zones · 🏔️ Border & remote areas · 🧓 Non-literate & elderly citizens · 🏟️ Crowded events (Kumbh, stadiums) when networks jam

**Centre: the "Traditional vs iTantra" comparison chart (the showpiece):**

Chart 1, bar chart on a log scale. Label: "Data for one 5-second message".
- Raw audio 160 KB · Mobile call 7.6 KB · Voice note 10 KB · Codec2 0.75 KB · **iTantra 0.23 KB**

Chart 2, a two-bar pair. Label: "Chance the message arrives over 3 relays (2% packet loss, model)".
- Voice note **26%** vs **iTantra 94%**

Big-number tiles:
| **97.7%** less data than a voice note | **1 packet** vs 22 | **0** towers needed | **10** languages |

**Bottom: benefits (3 columns, 2 lines each):**
- **Social:** Voice-first, so it includes non-readers. Native language reduces panic and misunderstanding.
- **Economic:** No towers or radios to buy. Uses phones people already own; saves hours in golden-hour rescue.
- **Environmental / resilience:** Low-power radios, no diesel generators for towers. Works when the grid is down.

**Future scope (roadmap ribbon, 5 chevrons):**
1. **Auto mode (VAD):** hands-free, "works like a phone"
2. **Offline translation** (IndicTrans2): Tamil speaker → Hindi listener
3. **Location + SOS beacon** attached to ALERT (GPS works without internet)
4. **Responder registry + audit log** for govt deployment; NDMA/SACHET integration when online
5. **LoRa bridge / smaller INT8 voices** (≈4× smaller) for km-range and low-end phones

---

### Slide 6 — Research and References
Two columns, small font, grouped with icons.

**Problem evidence**
1. ReliefWeb – Odisha FANI Cyclone Assessment Report (2019): https://reliefweb.int/report/india/odisha-fani-cyclone-assessment-report
2. Literacy in India – Census 2011 (74.04%), NSO 2023-24 (80.9%): https://en.wikipedia.org/wiki/Literacy_in_India

**Models & frameworks (all open-source)**

3. sherpa-onnx (k2-fsa), on-device speech runtime: https://github.com/k2-fsa/sherpa-onnx
4. NVIDIA NeMo Conformer-CTC Small: https://huggingface.co/nvidia/stt_en_conformer_ctc_small
5. AI4Bharat IndicConformer: https://github.com/AI4Bharat/IndicConformerASR
6. IndicConformer ONNX (INT8) for sherpa: https://huggingface.co/meetsync/indic-conformer-onnx-sherpa
7. Piper TTS voices (rhasspy): https://github.com/rhasspy/piper
8. Meta MMS-TTS ONNX (Kannada, Odia): https://huggingface.co/willwade/mms-tts-multilingual-models-onnx

**Networking & codecs**

9. bitchat-android: open-source BLE mesh (MIT), base of iTantra Mesh: https://github.com/permissionlesstech/bitchat-android
10. Android Wi-Fi Aware (NAN) docs: https://developer.android.com/develop/connectivity/wifi/wifi-aware
11. Codec 2 low-bitrate speech codec: https://github.com/drowe67/codec2
12. Noise Protocol Framework: https://noiseprotocol.org

**Our work:** GitHub repo link + demo video QR code (bottom right).

(Check links 9 and 10 open for you before submitting. The bitchat repo may be geo-blocked in India after the July takedown; if so, cite the Wikipedia page https://en.wikipedia.org/wiki/BitChat instead.)

---

## 4. Demo video storyboard (3:30)

| Time | Shot | Say |
|---|---|---|
| 0:00–0:20 | Phones in **airplane mode**, then Bluetooth on (show settings) | "No SIM, no internet, no towers." |
| 0:20–1:10 | Phone A (Hindi) PTT: "उत्तरी गेट के पास आग लगी है" → Phone B shows text + speaks | "Spoken, sent as 230 bytes, spoken again." |
| 1:10–1:50 | Switch to Tamil or Bengali, repeat | "Ten languages, one shared Indic model, all on-device." |
| 1:50–2:30 | Phone B on **silent + DND**. Phone A sends ALERT → alarm plays at max + vibrates | "Alerts can't be missed." |
| 2:30–3:05 | Diagnostics screen: STT ms, RTF, spoken→heard, n/n OK | Read the real numbers aloud |
| 3:05–3:30 | Diagram overlay: relay phones, 7 hops; comparison chart | "Every phone is a tower. 43× less data than a voice note." |

Keep the stopwatch visible in the shot for the spoken→heard measurement; it doubles as proof.
