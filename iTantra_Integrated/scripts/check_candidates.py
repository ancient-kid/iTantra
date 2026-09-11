import urllib.request
import hashlib

candidates = [
    ("hi_model", "https://huggingface.co/csukuangfj/vits-piper-hi_IN-pratham-medium/resolve/main/hi_IN-pratham-medium.onnx"),
    ("hi_tokens", "https://huggingface.co/csukuangfj/vits-piper-hi_IN-pratham-medium/resolve/main/tokens.txt"),
    ("ml_model", "https://huggingface.co/csukuangfj/vits-piper-ml_IN-meera-medium/resolve/main/ml_IN-meera-medium.onnx"),
    ("ml_tokens", "https://huggingface.co/csukuangfj/vits-piper-ml_IN-meera-medium/resolve/main/tokens.txt"),
    ("gu_model", "https://huggingface.co/csukuangfj/vits-mimic3-gu_IN-cmu-indic_low/resolve/main/gu_IN-cmu-indic_low.onnx"),
    ("gu_tokens", "https://huggingface.co/csukuangfj/vits-mimic3-gu_IN-cmu-indic_low/resolve/main/tokens.txt"),
    ("te_model", "https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx"),
    ("mr_model", "https://huggingface.co/rhasspy/piper-voices/resolve/main/mr/mr_IN/google/medium/mr_IN-google-medium.onnx"),
    ("bn_model", "https://huggingface.co/rhasspy/piper-voices/resolve/main/bn/bn_BD/google/medium/bn_BD-google-medium.onnx"),
    ("ta_model", "https://huggingface.co/tinisoft/piper-ta_IN-rasa_male-medium/resolve/main/ta_IN-rasa_male-medium.onnx"),
]

for name, u in candidates:
    req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as resp:
            print(f"[OK 200] {name}: length={resp.headers.get('Content-Length')}")
    except Exception as e:
        print(f"[FAIL] {name}: {e}")
