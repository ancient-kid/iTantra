import os
import json
import urllib.request

VOICES = {
    "te": {
        "onnx_url": "https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx",
        "json_url": "https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx.json",
        "dest_dir": "app/src/main/assets/tts/piper/te"
    },
    "mr": {
        "onnx_url": "https://huggingface.co/rhasspy/piper-voices/resolve/main/mr/mr_IN/google/medium/mr_IN-google-medium.onnx",
        "json_url": "https://huggingface.co/rhasspy/piper-voices/resolve/main/mr/mr_IN/google/medium/mr_IN-google-medium.onnx.json",
        "dest_dir": "app/src/main/assets/tts/piper/mr"
    },
    "bn": {
        "onnx_url": "https://huggingface.co/rhasspy/piper-voices/resolve/main/bn/bn_BD/google/medium/bn_BD-google-medium.onnx",
        "json_url": "https://huggingface.co/rhasspy/piper-voices/resolve/main/bn/bn_BD/google/medium/bn_BD-google-medium.onnx.json",
        "dest_dir": "app/src/main/assets/tts/piper/bn"
    },
    "ta": {
        "onnx_url": "https://huggingface.co/tinisoft/piper-ta_IN-rasa_male-medium/resolve/main/ta_IN-rasa_male-medium.onnx",
        "json_url": "https://huggingface.co/tinisoft/piper-ta_IN-rasa_male-medium/resolve/main/ta_IN-rasa_male-medium.onnx.json",
        "dest_dir": "app/src/main/assets/tts/piper/ta"
    },
    "gu": {
        "onnx_url": "https://huggingface.co/Arjun4707/piper-gujarati-male/resolve/main/gu_epoch229.onnx",
        "json_url": "https://huggingface.co/Arjun4707/piper-gujarati-male/resolve/main/gu_epoch229.onnx.json",
        "dest_dir": "app/src/main/assets/tts/piper/gu"
    }
}

def generate_tokens_txt(json_path, tokens_path):
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    phoneme_id_map = data.get("phoneme_id_map", {})
    # Map id -> symbol
    id_to_sym = {}
    for sym, ids in phoneme_id_map.items():
        id_val = ids[0] if isinstance(ids, list) else ids
        id_to_sym[id_val] = sym
    
    with open(tokens_path, 'w', encoding='utf-8') as f:
        for id_val in sorted(id_to_sym.keys()):
            sym = id_to_sym[id_val]
            f.write(f"{sym} {id_val}\n")
    print(f"Generated {tokens_path} with {len(id_to_sym)} tokens.")

def download_file(url, dest_path):
    print(f"Downloading {url} -> {dest_path}...")
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as resp, open(dest_path, 'wb') as out:
        chunk_size = 1024 * 1024
        while True:
            chunk = resp.read(chunk_size)
            if not chunk:
                break
            out.write(chunk)
    print(f"Downloaded {os.path.getsize(dest_path)} bytes.")

def main():
    for lang, info in VOICES.items():
        dest_dir = info["dest_dir"]
        os.makedirs(dest_dir, exist_ok=True)
        model_path = os.path.join(dest_dir, "model.onnx")
        json_path = os.path.join(dest_dir, "model.onnx.json")
        tokens_path = os.path.join(dest_dir, "tokens.txt")
        
        if not os.path.exists(model_path) or os.path.getsize(model_path) < 1000:
            download_file(info["onnx_url"], model_path)
        else:
            print(f"{model_path} already exists.")
            
        if not os.path.exists(json_path) or os.path.getsize(json_path) < 10:
            download_file(info["json_url"], json_path)
            
        generate_tokens_txt(json_path, tokens_path)

if __name__ == "__main__":
    main()
