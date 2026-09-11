import urllib.request

repos = [
    "vits-piper-te_IN-maya-medium",
    "vits-piper-mr_IN-google-medium",
    "vits-piper-bn_BD-google-medium",
    "vits-piper-ta_IN-rasa_male-medium",
    "vits-piper-ml_IN-meera-medium",
    "vits-piper-hi_IN-pratham-medium",
    "vits-mimic3-gu_IN-cmu-indic_low",
    "vits-piper-gu_IN-cmu-indic_low",
]

for r in repos:
    u = f"https://huggingface.co/csukuangfj/{r}/resolve/main/tokens.txt"
    req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as resp:
            print(f"[EXISTS] csukuangfj/{r}: {resp.status}")
    except Exception as e:
        print(f"[MISSING] csukuangfj/{r}: {e}")
