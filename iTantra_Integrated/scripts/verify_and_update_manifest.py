import urllib.request
import hashlib
import json
import os

def check_url(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    h = hashlib.sha256()
    size = 0
    with urllib.request.urlopen(req) as resp:
        while True:
            chunk = resp.read(1024 * 1024)
            if not chunk:
                break
            size += len(chunk)
            h.update(chunk)
    return size, h.hexdigest()

def test_head(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.headers.get("Content-Length")
    except Exception as e:
        return 0, str(e)

if __name__ == "__main__":
    test_urls = [
        ("hi_model", "https://huggingface.co/csukuangfj/vits-piper-hi_IN-pratham-medium/resolve/main/hi_IN-pratham-medium.onnx"),
        ("hi_tokens", "https://huggingface.co/csukuangfj/vits-piper-hi_IN-pratham-medium/resolve/main/tokens.txt"),
        ("indic_stt", "https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx"),
        ("indic_tokens", "https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/tokens.txt"),
        ("kn_model", "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/kan/model.onnx"),
        ("kn_tokens", "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/kan/tokens.txt"),
        ("or_model", "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/ory/model.onnx"),
        ("or_tokens", "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/ory/tokens.txt"),
    ]
    for name, u in test_urls:
        status, length = test_head(u)
        print(f"{name}: status={status}, length={length}")
