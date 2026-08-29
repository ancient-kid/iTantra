# iTantra MVP — Development Roadmap (Approach ①: STT → Mesh → TTS)

**Scope:** the PS-literal pipeline only — no prosody, no voice cloning, no codec. Get this rock-solid first; ② gets built on top once this is reliable.
**Timeline:** today (Aug 29) → self-imposed deadline Sep 10, real deadline Sep 20 (10 extra days as buffer, not slack — see the last section).

---

## The one thing to internalize before you write any code

There are two genuinely hard parts to this project and eight "just execution" parts. The two hard parts are: **(a)** getting the bitchat mesh code extracted and reliably sending text between two real phones over Bluetooth, and **(b)** getting one language's STT+TTS models running correctly and quickly on-device. Everything else — the PTT state machine, the UI, expanding to 9 more languages, the diagnostics screen — is mechanical repetition of a pattern once those two things work.

The instinct as a beginner is to build the "real" ML work first and leave the "borrowed" networking code for later, since it feels like someone else already solved it. **Do the opposite.** The mesh extraction is the single most unpredictable item on this whole roadmap — we already established it's ~17,500 lines of Kotlin with real coupling to parts of bitchat's app you don't want, and its own maintainers say BLE behavior can only be verified on real hardware, not emulators. If it turns out to be harder than expected, you want to discover that on Day 2, with 10 days of runway left to fall back to a simpler custom Bluetooth socket — not on Day 8, with two days left and a recording deadline bearing down. So: **de-risk the transport first, prove one language's ML pipeline second, then integrate.**

The second thing to internalize: you have roughly 10 working days. Getting all 10 languages to genuinely excellent quality in that time, as essentially a first attempt at on-device multilingual ASR/TTS, is not realistic — and judges can tell the difference between a real demo and an overclaimed one. The honest strategy is: **prove the full pipeline end-to-end on one pilot language, get 2–3 more languages to genuinely camera-ready quality for the video, and get the remaining languages to "functionally working and tested on your sentence bank" even if not deeply polished.** Say exactly that in your demo narration if asked — "the architecture is language-agnostic, here are 3 fully tuned, the rest are integrated and functional" is a stronger, more credible answer than pretending uniform polish you haven't actually verified.

---

## Suggested day-by-day map

| Days | Phase | Goal | Go/No-Go before moving on |
|---|---|---|---|
| 1 | 0 — Setup | Tooling ready, pilot models chosen and sanity-checked off-device | Models produce sane output on your laptop for a plain sentence |
| 2–4 | 1 — De-risk transport | Two phones reliably exchange a plain text message over BT mesh, no ML involved | ≥8/10 trials succeed at normal demo distance |
| 5–6 | 2 — STT (pilot language) | Mic → VAD → on-device STT → transcript on screen | WER on your test-sentence bank is stable, no crashes over 20 trials |
| 7 | 3 — TTS (pilot language) | Text → on-device TTS → audio, including alert-priority playback | Legible by ear; alert mode audibly overrides silent mode |
| 8 | 4 — Full integration | STT output → transport → TTS input, PTT state machine wired together | Full walkie-talkie loop works ≥8/10 trials, latency logged |
| 9–10 | 5 — Expand languages | 2–3 more languages fully polished, remaining languages functional | Each added language passes its own sentence-bank test |
| 11 | 6 — Instrumentation & hardening | Diagnostics screen, idle-battery check, adversarial condition tests | Numbers you can actually cite in the video exist and are logged |
| 12 | 7 — Demo | Script, rehearse, record multiple takes, pick the best | A dress-rehearsal run succeeds cleanly on camera |

Treat this as a template, not a contract — if Phase 1 eats an extra day, that day has to come from somewhere else, most likely by trimming how many extra languages get deep polish in Phase 5.

---

## Phase 0 — Setup (Day 1)

**Do:**
- [ ] Install Android Studio, create a Kotlin project, min SDK 26 (matches bitchat-android's choice, and is the practical floor for reliable BLE APIs).
- [ ] Get **two physical Android phones** in hand — a mid-range one and, if you can get one, a genuinely low-end/older one. Emulators cannot do real Bluetooth between two devices, so this is non-negotiable, not optional.
- [ ] Create a GitHub repo, a plain README with this roadmap's checklist pasted in, and commit at the end of every phase so you always have a rollback point.
- [ ] Pick your **pilot language** (Hindi or English are the safest choices — most open-resource availability, easiest for you to personally judge output quality by ear).
- [ ] Download your chosen STT and TTS checkpoints for the pilot language and run them **outside Android first** — plain Python/Colab. Record yourself saying a handful of test sentences, run STT, eyeball the transcript; feed a few sentences into TTS and listen.

**Build your test-sentence bank now** — this is the single tool you'll reuse for the rest of the project. Write ~15 sentences per language covering: plain statements, alert/distress-style urgent phrases, numbers, and a couple of sentences with background-noise-prone words. Keep it in a shared doc/spreadsheet, one column per language, so you can check regressions consistently as you go.

**Go/no-go:** if the off-device model output is garbled or unintelligible on clean audio with no Android complexity involved yet, swap models now. Fixing a bad model choice is cheap on Day 1 and very expensive on Day 8.

---

## Phase 1 — De-risk the transport (Days 2–4)

This is the phase most likely to run long, so treat the days as a budget, not a guarantee.

**Do:**
- [ ] Clone bitchat-android, extract `mesh/`, `protocol/`, `noise/`, `crypto/`, `identity/` into your own project as a standalone module.
- [ ] Stub out/delete the calls into `ui/` (debug settings, notifications) and `nostr/` (identity bridging) that we found earlier — get it compiling standalone.
- [ ] Build the **absolute minimum test app**: a text box, a "send" button, a log of received messages. No STT, no TTS, no UI polish. Just: type on Phone A, see it appear on Phone B over Bluetooth.
- [ ] Run this test repeatedly under realistic conditions, not just side-by-side on a desk:
  - [ ] Both phones on a table, close range
  - [ ] Phones in pockets/bags (real-world attenuation)
  - [ ] Airplane mode + Bluetooth only, confirming true offline operation
  - [ ] Toggle one phone's Bluetooth off and back on — does it reconnect?
  - [ ] Send 10 messages back-to-back rapidly — do any get dropped or duplicated?
  - [ ] Leave the app idle for 10 minutes, then send — does it still work, or did the connection silently die?

**Log a simple success count** — e.g. "8/10 clean sends at normal room distance" — and treat this as a real metric you improve toward 10/10, not a one-off pass/fail.

**Go/No-Go — this is the most important decision point in the whole roadmap:** if by the end of Day 4 you don't have reliable two-phone text exchange, **stop extracting bitchat and fall back to a simpler custom Bluetooth Classic SPP socket** (the kind of design originally sketched in the architecture doc's transport section). A fully-understood, reliable, simpler transport you built yourself will make a far better demo than a powerful mesh implementation you're still fighting on Sep 9. This isn't a failure if you have to make this call — it's exactly the kind of risk management that separates a working demo from a broken one.

---

## Phase 2 — STT module, pilot language, isolated (Days 5–6)

**Do:**
- [ ] Build a separate screen: press a button (or auto-detect via VAD), record, run your on-device STT model (TFLite/ONNX, quantized), show the transcript. Nothing else — no networking yet.
- [ ] Add timestamp logging at each internal step (recording start, speech-end detected, transcript-final) from the start — you'll need these numbers later and it's much cheaper to log them now than reconstruct them in Phase 6.
- [ ] Add the VAD-based pause/endpointing on top of the manual button, and deliberately test it against: a natural end-of-sentence pause, a mid-sentence hesitation pause (does it falsely cut you off?), and background noise (fan, street sounds).
- [ ] Run your Phase-1 test-sentence bank through the on-device pipeline and compare against the off-device (laptop) transcripts from Phase 0 — quantization sometimes measurably hurts accuracy, and you want to know now, not later. If you have Python handy, the open-source `jiwer` package computes WER automatically — worth setting up once since you'll re-run this same check for every language later.

**Go/No-Go:** WER on your sentence bank is stable and acceptable in a normal (not silent-studio) room, and the app doesn't crash across ~20 repeated trials, before moving on.

---

## Phase 3 — TTS module, pilot language, isolated (Day 7)

**Do:**
- [ ] Separate screen: type or paste text, run on-device TTS, play the audio.
- [ ] Feed your test-sentence bank through it and listen critically — is it legible, does it sound like flowing speech or robotic stitching, especially on short urgent-style phrases?
- [ ] Log synthesis time vs. audio duration to get your RTF number.
- [ ] Build and test the **alert-priority path specifically** as its own test case: put the phone in silent mode / Do Not Disturb, trigger an alert-flagged message, confirm it plays at max volume anyway and isn't ducked by something else playing. This is a literal requirement in the PS — treat it as a pass/fail test, not a nice-to-have.

**Go/No-Go:** every sentence in your test bank is intelligible by ear, and the alert-mode override genuinely works under silent/DND conditions on both test phones.

---

## Phase 4 — Full pipeline integration, pilot language (Day 8)

**Do:**
- [ ] Wire Phase 2's transcript output into Phase 1's packet payload (text + language tag + priority flag + timestamp).
- [ ] Wire Phase 1's received payload into Phase 3's TTS input.
- [ ] Build the PTT state machine cleanly: idle → talk (PTT held) → send (on release/endpoint) → idle; receive → TTS playback → idle.
- [ ] Run the **full two-phone walkie-talkie loop** using your test-sentence bank, timing the complete spoken-to-heard delta (stopwatch, or record on a second camera with a visible clock/timestamp overlay — this doubles as demo-ready evidence).

**Go/No-Go:** the full loop succeeds in at least 8 of 10 repeated trials at normal demo distance before you start expanding languages. This is your MVP — everything after this is expansion and polish, not core risk.

---

## Phase 5 — Expand to remaining languages (Days 9–10)

Because Phases 2–4 are already generic (they load whichever language's model files are configured), this phase is mostly repetition:

- [ ] Prioritize 2–3 additional languages for full, camera-ready polish — run them through the exact same test-sentence-bank process as the pilot.
- [ ] Get the remaining languages to "loads correctly, produces reasonable output, passes a basic pass through the sentence bank" — functional and honestly testable, even if not deeply tuned.
- [ ] Keep a simple per-language status table (Working great / Working / Not yet tested) so you know exactly what you can confidently show on camera and what you should quietly leave out of the recorded demo.

---

## Phase 6 — Instrumentation & reliability hardening (Day 11)

- [ ] Build the lightweight diagnostics screen (model load time, per-stage latency, RTF, memory use) — this is both a debugging tool and literal on-screen evidence for the Efficiency/Latency judging criteria.
- [ ] Idle-battery check: leave the app armed and idle for 10–15 minutes, check CPU/battery via Android Studio's Profiler or `adb shell dumpsys batterystats`, and note the number.
- [ ] Run the adversarial checklist one more time end-to-end (airplane mode, background noise, rapid messages, app backgrounded/screen off, low-battery/battery-saver mode — some OEMs aggressively kill background BLE services under battery saver, worth knowing about before it surprises you on camera).
- [ ] Add a clear connection-status indicator in the UI (searching / connected / lost) — for a wireless live demo, judges being able to *see* the connection state is worth more than you'd expect.

---

## Phase 7 — Demo scripting and recording (Day 12)

- [ ] Script what you'll show and in what order: the problem in one sentence, a live two-phone PTT exchange in 2+ languages, an alert-mode message that visibly overrides silent mode, and the diagnostics screen showing real efficiency/latency numbers.
- [ ] Do a full **dress rehearsal** under the exact conditions you'll record in (same room, same phones, same battery levels) — wireless demos are sensitive to environment, and you want the "surprises" to happen in rehearsal, not on the take you keep.
- [ ] Record multiple takes. Live BLE demos glitch once and work the second time all the time — that's normal, not evidence something is broken. Keep the clean take.

---

## General testing practices that apply throughout (not just one phase)

- **Test each module in isolation before wiring it to the next one.** Every phase above builds a standalone, testable slice before integration — this is what lets you tell "the STT is wrong" from "the transport dropped the packet" from "the TTS mangled the text," instead of debugging three unknowns at once.
- **Keep one test-sentence bank per language, reused every time.** It's your only way to tell if a later change (a new quantization setting, a model swap) made things better or worse, rather than guessing from vibes.
- **Log timestamps from day one**, not retroactively — recovering "what was our actual latency two weeks ago" from memory is impossible; a simple Logcat line or debug overlay at each pipeline stage costs almost nothing to add early.
- **Test on your real target hardware, not just your best phone.** The PS explicitly asks for low/mid-range performance — a flagship dev phone will hide problems that show up immediately on older hardware.
- **Track reliability as a number** (`n/10 trials succeeded`), not a binary "it works." A demo that's worked once in testing is not the same as a demo that reliably works, and the difference matters enormously for something being recorded on camera.

---

## Using the Sep 10 → Sep 20 buffer

Don't treat the extra 10 days as slack. Use them, in order, for: a second and better demo recording once you've seen how the first one plays back; pushing 1–2 more languages from "functional" to "camera-ready"; and only after both of those, starting on approach ② (prosody) on top of this now-stable base — exactly as you planned.
