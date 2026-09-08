#!/usr/bin/env python3
"""
Builds the downloadable model packs and the manifest the app ships with.

What it does
------------
1. Copies the seven voice packs that must be self-hosted out of the original
   test-bench assets, byte for byte, into ``model_packs/`` ready to upload.
2. Computes SHA-256 and size for every file in every pack.
3. Verifies the locally held IndicConformer checkpoint against the published
   upstream checksum, so we know the direct-download URL serves the same bytes.
4. Writes ``app/src/main/assets/models/manifest.json``.

Packs sourced directly from upstream (nothing to host):
    stt-indic  meetsync/indic-conformer-onnx-sherpa
    tts-kn     willwade/mms-tts-multilingual-models-onnx  (kan)
    tts-or     willwade/mms-tts-multilingual-models-onnx  (ory)

Usage
-----
    python scripts/build_model_packs.py --repo <your-hf-user>/itantra-models

Then upload the contents of model_packs/ to that Hugging Face model repo,
preserving the directory names.
"""

import argparse
import hashlib
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
INTEGRATED_ASSETS = os.path.join(PROJECT, "app", "src", "main", "assets")
SOURCE_ASSETS = os.path.normpath(
    os.path.join(PROJECT, "..", "stt_english_test", "app", "src", "main", "assets")
)
OUTPUT_DIR = os.path.join(PROJECT, "model_packs")

HF = "https://huggingface.co"
MMS_REPO = "willwade/mms-tts-multilingual-models-onnx"
INDIC_STT_REPO = "meetsync/indic-conformer-onnx-sherpa"

# Published upstream checksums (Hugging Face LFS oids), used to prove the
# direct-download URLs serve exactly what we validated.
UPSTREAM_SHA256 = {
    "stt-indic/model.int8.onnx":
        "b99a01834cd1a72cd9be682a0b9543df6b152ef7dfceba88d3dbf59fbb77075d",
    "tts-kn/model.onnx":
        "9644cfa46c5802c4c2c8196f91eedc0cd54d46527bbc32589ebaf967ed07591f",
    "tts-or/model.onnx":
        "a90e1a48d4aa404c12504f47a89bbe310110a9d43dd2e093346f539efc8728a1",
    # Not LFS-tracked upstream, so hashed from a verified download instead.
    "tts-kn/tokens.txt":
        "a4a44037f7492c9e5b69a4483250e54af9a2d528bd4e19de95a21ff0c5e82a8a",
    "tts-or/tokens.txt":
        "bc90e5423446ca730aef6b56a4656cb07421c7538429a50b9c396fe0758fd587",
}

ALL_INDIC = ["hi", "bn", "mr", "ta", "te", "ml", "gu", "kn", "or"]

# Voice packs that must be self-hosted: the upstream form is either a .tar.bz2
# archive (no bzip2 in the Android runtime) or lacks a ready-made tokens.txt.
SELF_HOSTED_VOICES = [
    ("tts-hi", "hi", "Hindi",     "hi_IN-pratham-medium",   "MIT"),
    ("tts-ml", "ml", "Malayalam", "ml_IN-meera-medium",     "MIT"),
    ("tts-gu", "gu", "Gujarati",  "gu_IN-cmu-indic_low",    "CC-BY-SA-4.0"),
    ("tts-te", "te", "Telugu",    "te_IN-maya-medium",      "MIT"),
    ("tts-mr", "mr", "Marathi",   "mr_IN-google-medium",    "MIT"),
    ("tts-bn", "bn", "Bengali",   "bn_BD-google-medium",    "MIT"),
    ("tts-ta", "ta", "Tamil",     "ta_IN-rasa_male-medium", "MIT"),
]

VOICE_FILES = ["model.onnx", "tokens.txt"]


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def describe(path, url=None):
    entry = {
        "name": os.path.basename(path),
        "size": os.path.getsize(path),
        "sha256": sha256_of(path),
    }
    if url:
        entry["url"] = url
    return entry


def bundled_pack(pack_id, kind, languages, display, model_name, phonemizer,
                 license_id, asset_dir, filenames):
    files = []
    for name in filenames:
        path = os.path.join(INTEGRATED_ASSETS, asset_dir, name)
        if not os.path.isfile(path):
            sys.exit(f"ERROR: bundled asset missing: {path}")
        files.append(describe(path))
    return {
        "id": pack_id,
        "kind": kind,
        "languages": languages,
        "displayName": display,
        "modelName": model_name,
        "phonemizer": phonemizer,
        "license": license_id,
        "bundled": True,
        "assetDir": asset_dir,
        "files": files,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repo",
        default=os.environ.get("ITANTRA_MODELS_REPO", "CHANGEME/itantra-models"),
        help="Hugging Face model repo that will host the self-hosted voice packs",
    )
    args = parser.parse_args()

    if not os.path.isdir(SOURCE_ASSETS):
        sys.exit(f"ERROR: source assets not found at {SOURCE_ASSETS}")

    packs = []

    # ---------------------------------------------------------------- bundled
    print("== Bundled packs (shipped inside the APK) ==")
    packs.append(bundled_pack(
        "stt-en", "STT", ["en"], "English speech recognition",
        "NeMo Conformer-CTC small (INT8)", "NONE", "Apache-2.0",
        "english", ["model.int8.onnx", "tokens.txt"],
    ))
    packs.append(bundled_pack(
        "tts-en", "TTS", ["en"], "English voice",
        "piper en_US-amy-low", "ESPEAK", "MIT",
        os.path.join("tts", "piper", "en"), VOICE_FILES,
    ))
    for pack in packs:
        total = sum(f["size"] for f in pack["files"])
        print(f"   {pack['id']:10s} {total / 1048576:7.1f} MB  (in APK)")

    # ------------------------------------------------- direct upstream packs
    print("\n== Direct upstream packs (nothing to host) ==")
    indic_stt = {
        "id": "stt-indic",
        "kind": "STT",
        "languages": ALL_INDIC,
        "displayName": "Indic speech recognition (9 languages)",
        "modelName": "IndicConformer CTC (INT8)",
        "phonemizer": "NONE",
        "license": "Apache-2.0",
        "bundled": False,
        "files": [
            {
                "name": "model.int8.onnx",
                "size": 196977855,
                "sha256": UPSTREAM_SHA256["stt-indic/model.int8.onnx"],
                "url": f"{HF}/{INDIC_STT_REPO}/resolve/main/model.int8.onnx",
            },
            {
                "name": "tokens.txt",
                "size": 0,
                "sha256": "",
                "url": f"{HF}/{INDIC_STT_REPO}/resolve/main/tokens.txt",
            },
        ],
    }
    # Fill in the tokens.txt digest from the copy we already hold and trust.
    local_tokens = os.path.join(SOURCE_ASSETS, "indicconformer", "tokens.txt")
    if os.path.isfile(local_tokens):
        indic_stt["files"][1]["size"] = os.path.getsize(local_tokens)
        indic_stt["files"][1]["sha256"] = sha256_of(local_tokens)

    # Prove the local checkpoint matches what the URL will serve.
    local_model = os.path.join(SOURCE_ASSETS, "indicconformer", "model.int8.onnx")
    if os.path.isfile(local_model):
        print("   verifying local IndicConformer against upstream checksum...")
        digest = sha256_of(local_model)
        expected = UPSTREAM_SHA256["stt-indic/model.int8.onnx"]
        if digest == expected:
            print("   OK  local copy is byte-identical to the published model")
        else:
            print(f"   WARNING  local={digest}\n            upstream={expected}")
            print("   The download will fetch the upstream bytes, not your local copy.")
    packs.append(indic_stt)

    for pack_id, code, name, voice, mms_dir, size, digest, tok_size in [
        ("tts-kn", "kn", "Kannada", "MMS VITS (kan)", "kan", 114045444,
         UPSTREAM_SHA256["tts-kn/model.onnx"], 487),
        ("tts-or", "or", "Odia", "MMS VITS (ory)", "ory", 114046136,
         UPSTREAM_SHA256["tts-or/model.onnx"], 495),
    ]:
        packs.append({
            "id": pack_id,
            "kind": "TTS",
            "languages": [code],
            "displayName": f"{name} voice",
            "modelName": voice,
            # MMS models are character-based, not phoneme-based: they must run
            # with an empty dataDir or espeak will mangle the input.
            "phonemizer": "NONE",
            "license": "CC-BY-NC-4.0",
            "bundled": False,
            "files": [
                {
                    "name": "model.onnx",
                    "size": size,
                    "sha256": digest,
                    "url": f"{HF}/{MMS_REPO}/resolve/main/{mms_dir}/model.onnx",
                },
                {
                    "name": "tokens.txt",
                    "size": tok_size,
                    "sha256": UPSTREAM_SHA256[f"{pack_id}/tokens.txt"],
                    "url": f"{HF}/{MMS_REPO}/resolve/main/{mms_dir}/tokens.txt",
                },
            ],
        })

    for pack in packs[2:]:
        total = sum(f["size"] for f in pack["files"])
        note = "  [non-commercial]" if pack["license"] == "CC-BY-NC-4.0" else ""
        print(f"   {pack['id']:10s} {total / 1048576:7.1f} MB  upstream{note}")

    # ---------------------------------------------------- self-hosted packs
    print(f"\n== Self-hosted packs -> {args.repo} ==")
    if os.path.isdir(OUTPUT_DIR):
        shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)

    for pack_id, code, name, voice, license_id in SELF_HOSTED_VOICES:
        src_dir = os.path.join(SOURCE_ASSETS, "tts", "piper", code)
        if not os.path.isdir(src_dir):
            sys.exit(f"ERROR: source voice missing: {src_dir}")

        dest_dir = os.path.join(OUTPUT_DIR, pack_id)
        os.makedirs(dest_dir)

        files = []
        for filename in VOICE_FILES:
            src = os.path.join(src_dir, filename)
            if not os.path.isfile(src):
                sys.exit(f"ERROR: {src} not found")
            dest = os.path.join(dest_dir, filename)
            shutil.copyfile(src, dest)
            files.append(describe(
                dest,
                url=f"{HF}/{args.repo}/resolve/main/{pack_id}/{filename}",
            ))

        packs.append({
            "id": pack_id,
            "kind": "TTS",
            "languages": [code],
            "displayName": f"{name} voice",
            "modelName": voice,
            "phonemizer": "ESPEAK",
            "license": license_id,
            "bundled": False,
            "files": files,
        })
        total = sum(f["size"] for f in files)
        print(f"   {pack_id:10s} {total / 1048576:7.1f} MB  -> model_packs/{pack_id}/")

    # ----------------------------------------------------------- manifest
    manifest_dir = os.path.join(INTEGRATED_ASSETS, "models")
    os.makedirs(manifest_dir, exist_ok=True)
    manifest_path = os.path.join(manifest_dir, "manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as handle:
        json.dump({"version": 1, "packs": packs}, handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    hosted = sum(
        sum(f["size"] for f in p["files"])
        for p in packs if not p["bundled"] and p["id"] not in ("stt-indic", "tts-kn", "tts-or")
    )
    catalog = sum(sum(f["size"] for f in p["files"]) for p in packs if not p["bundled"])

    print(f"\nWrote {manifest_path}")
    print(f"  {len(packs)} packs, downloadable catalog {catalog / 1048576:.0f} MB")
    print(f"\nNext: upload model_packs/* to https://huggingface.co/{args.repo}")
    print(f"      ({hosted / 1048576:.0f} MB, directory names must be preserved)")


if __name__ == "__main__":
    main()
