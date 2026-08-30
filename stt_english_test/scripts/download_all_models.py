import os
import shutil
import tarfile
import urllib.request
import json
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_DIR = os.path.join(BASE_DIR, "app")
ASSETS_DIR = os.path.join(APP_DIR, "src", "main", "assets")
LIBS_DIR = os.path.join(APP_DIR, "libs")
MODELS_CACHE_DIR = os.path.join(BASE_DIR, "models")

def download_file(url, dest_path, description=""):
    os.makedirs(os.path.dirname(dest_path), exist_ok=True)
    if os.path.exists(dest_path) and os.path.getsize(dest_path) > 1000:
        print(f"[EXISTS] {description or dest_path} ({os.path.getsize(dest_path)} bytes)")
        return
    print(f"[DOWNLOADING] {description or url} -> {dest_path}...")
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as resp, open(dest_path, 'wb') as out:
        chunk_size = 1024 * 1024
        while True:
            chunk = resp.read(chunk_size)
            if not chunk:
                break
            out.write(chunk)
    print(f"[DOWNLOADED] {dest_path} ({os.path.getsize(dest_path)} bytes)")

def extract_tar_bz2(archive_path, extract_to):
    print(f"[EXTRACTING] {archive_path} -> {extract_to}...")
    with tarfile.open(archive_path, "r:bz2") as tar:
        if hasattr(tarfile, 'data_filter'):
            tar.extractall(path=extract_to, filter='data')
        else:
            tar.extractall(path=extract_to)

def generate_tokens_from_json(json_path, tokens_path):
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    phoneme_id_map = data.get("phoneme_id_map", {})
    id_to_sym = {}
    for sym, ids in phoneme_id_map.items():
        id_val = ids[0] if isinstance(ids, list) else ids
        id_to_sym[id_val] = sym
    with open(tokens_path, 'w', encoding='utf-8') as f:
        for id_val in sorted(id_to_sym.keys()):
            f.write(f"{id_to_sym[id_val]} {id_val}\n")
    print(f"[GENERATED] {tokens_path} ({len(id_to_sym)} tokens)")

def setup_aar():
    aar_url = "https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.12.29.aar"
    dest = os.path.join(LIBS_DIR, "sherpa-onnx-1.12.29.aar")
    download_file(aar_url, dest, "Sherpa-ONNX Android AAR Native Runtime")

def setup_stt_models():
    # 1. English NeMo Conformer Small INT8
    en_onnx = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-small/resolve/main/model.int8.onnx"
    download_file(en_onnx, os.path.join(ASSETS_DIR, "english", "model.int8.onnx"), "English STT Model (NeMo Small INT8)")
    
    # 2. IndicConformer INT8 ONNX
    indic_onnx = "https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx"
    download_file(indic_onnx, os.path.join(ASSETS_DIR, "indicconformer", "model.int8.onnx"), "IndicConformer STT Model (INT8)")

def setup_tts_models():
    # 1. English (vits-piper-en_US-amy-low) + espeak-ng-data
    en_archive = os.path.join(MODELS_CACHE_DIR, "vits-piper-en_US-amy-low.tar.bz2")
    download_file("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2", en_archive, "English Piper Voice (en_US-amy-low)")
    extract_tar_bz2(en_archive, MODELS_CACHE_DIR)
    
    en_extracted = os.path.join(MODELS_CACHE_DIR, "vits-piper-en_US-amy-low")
    en_dest = os.path.join(ASSETS_DIR, "tts", "piper", "en")
    os.makedirs(en_dest, exist_ok=True)
    shutil.copyfile(os.path.join(en_extracted, "en_US-amy-low.onnx"), os.path.join(en_dest, "model.onnx"))
    shutil.copyfile(os.path.join(en_extracted, "tokens.txt"), os.path.join(en_dest, "tokens.txt"))
    
    # Copy espeak-ng-data to assets/espeak-ng-data
    espeak_src = os.path.join(en_extracted, "espeak-ng-data")
    espeak_dst = os.path.join(ASSETS_DIR, "espeak-ng-data")
    if not os.path.exists(espeak_dst):
        shutil.copytree(espeak_src, espeak_dst)
    
    # 2. Hindi (vits-piper-hi_IN-pratham-medium)
    hi_archive = os.path.join(MODELS_CACHE_DIR, "vits-piper-hi_IN-pratham-medium.tar.bz2")
    download_file("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-pratham-medium.tar.bz2", hi_archive, "Hindi Piper Voice (hi_IN-pratham-medium)")
    extract_tar_bz2(hi_archive, MODELS_CACHE_DIR)
    hi_extracted = os.path.join(MODELS_CACHE_DIR, "vits-piper-hi_IN-pratham-medium")
    hi_dest = os.path.join(ASSETS_DIR, "tts", "piper", "hi")
    os.makedirs(hi_dest, exist_ok=True)
    shutil.copyfile(os.path.join(hi_extracted, "hi_IN-pratham-medium.onnx"), os.path.join(hi_dest, "model.onnx"))
    shutil.copyfile(os.path.join(hi_extracted, "tokens.txt"), os.path.join(hi_dest, "tokens.txt"))

    # 3. Malayalam (vits-piper-ml_IN-meera-medium)
    ml_archive = os.path.join(MODELS_CACHE_DIR, "vits-piper-ml_IN-meera-medium.tar.bz2")
    download_file("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ml_IN-meera-medium.tar.bz2", ml_archive, "Malayalam Piper Voice (ml_IN-meera-medium)")
    extract_tar_bz2(ml_archive, MODELS_CACHE_DIR)
    ml_extracted = os.path.join(MODELS_CACHE_DIR, "vits-piper-ml_IN-meera-medium")
    ml_dest = os.path.join(ASSETS_DIR, "tts", "piper", "ml")
    os.makedirs(ml_dest, exist_ok=True)
    shutil.copyfile(os.path.join(ml_extracted, "ml_IN-meera-medium.onnx"), os.path.join(ml_dest, "model.onnx"))
    shutil.copyfile(os.path.join(ml_extracted, "tokens.txt"), os.path.join(ml_dest, "tokens.txt"))

    # 4. Gujarati (vits-mimic3-gu_IN-cmu-indic_low)
    gu_archive = os.path.join(MODELS_CACHE_DIR, "vits-mimic3-gu_IN-cmu-indic_low.tar.bz2")
    download_file("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mimic3-gu_IN-cmu-indic_low.tar.bz2", gu_archive, "Gujarati VITS Voice (gu_IN-cmu-indic_low)")
    extract_tar_bz2(gu_archive, MODELS_CACHE_DIR)
    gu_extracted = os.path.join(MODELS_CACHE_DIR, "vits-mimic3-gu_IN-cmu-indic_low")
    gu_dest = os.path.join(ASSETS_DIR, "tts", "piper", "gu")
    os.makedirs(gu_dest, exist_ok=True)
    shutil.copyfile(os.path.join(gu_extracted, "gu_IN-cmu-indic_low.onnx"), os.path.join(gu_dest, "model.onnx"))
    shutil.copyfile(os.path.join(gu_extracted, "tokens.txt"), os.path.join(gu_dest, "tokens.txt"))

    # 5. Direct HF Piper Voices: Telugu, Marathi, Bengali, Tamil
    hf_voices = {
        "te": {
            "name": "Telugu Piper Voice (te_IN-maya-medium)",
            "onnx": "https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx",
            "json": "https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx.json"
        },
        "mr": {
            "name": "Marathi Piper Voice (mr_IN-google-medium)",
            "onnx": "https://huggingface.co/rhasspy/piper-voices/resolve/main/mr/mr_IN/google/medium/mr_IN-google-medium.onnx",
            "json": "https://huggingface.co/rhasspy/piper-voices/resolve/main/mr/mr_IN/google/medium/mr_IN-google-medium.onnx.json"
        },
        "bn": {
            "name": "Bengali Piper Voice (bn_BD-google-medium)",
            "onnx": "https://huggingface.co/rhasspy/piper-voices/resolve/main/bn/bn_BD/google/medium/bn_BD-google-medium.onnx",
            "json": "https://huggingface.co/rhasspy/piper-voices/resolve/main/bn/bn_BD/google/medium/bn_BD-google-medium.onnx.json"
        },
        "ta": {
            "name": "Tamil Piper Voice (ta_IN-rasa_male-medium)",
            "onnx": "https://huggingface.co/tinisoft/piper-ta_IN-rasa_male-medium/resolve/main/ta_IN-rasa_male-medium.onnx",
            "json": "https://huggingface.co/tinisoft/piper-ta_IN-rasa_male-medium/resolve/main/ta_IN-rasa_male-medium.onnx.json"
        }
    }

    for lang, v in hf_voices.items():
        dest_dir = os.path.join(ASSETS_DIR, "tts", "piper", lang)
        os.makedirs(dest_dir, exist_ok=True)
        model_p = os.path.join(dest_dir, "model.onnx")
        json_p = os.path.join(dest_dir, "model.onnx.json")
        tokens_p = os.path.join(dest_dir, "tokens.txt")
        download_file(v["onnx"], model_p, v["name"])
        download_file(v["json"], json_p, f"{v['name']} Config")
        generate_tokens_from_json(json_p, tokens_p)

def main():
    print("=================================================================")
    print("  STT + TTS Test Bench - Automated Model Setup")
    print("=================================================================")
    setup_aar()
    setup_stt_models()
    setup_tts_models()
    print("=================================================================")
    print("  [SUCCESS] All STT & TTS Models successfully verified/installed!")
    print("=================================================================")

if __name__ == "__main__":
    main()
