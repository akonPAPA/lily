from __future__ import annotations

import io
import tarfile
import urllib.request
from pathlib import Path

BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download"

ITEMS = [
    ("vad", f"{BASE}/asr-models/silero_vad.onnx", False),
    ("asr", f"{BASE}/asr-models/sherpa-onnx-whisper-base.tar.bz2", True),
    ("tts", f"{BASE}/tts-models/vits-piper-en_US-amy-medium-int8.tar.bz2", True),

    ("tts", f"{BASE}/tts-models/vits-piper-en_GB-jenny_dioco-medium.tar.bz2", True),
    ("tts", f"{BASE}/tts-models/vits-piper-ru_RU-irina-medium-int8.tar.bz2", True),
    ("kws", f"{BASE}/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2", True),
]

def download(url: str) -> bytes:
    print(f"  downloading {url.rsplit('/', 1)[-1]} ...", flush=True)
    req = urllib.request.Request(url, headers={"User-Agent": "companionos"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()

def main() -> None:
    speech = Path(__file__).resolve().parents[2] / "speech"
    for sub, url, is_tar in ITEMS:
        dst = speech / sub
        dst.mkdir(parents=True, exist_ok=True)
        name = url.rsplit("/", 1)[-1]
        if is_tar:
            top = name.replace(".tar.bz2", "")
            if (dst / top).is_dir():
                print(f"  skip {top} (exists)")
                continue
            data = download(url)
            with tarfile.open(fileobj=io.BytesIO(data), mode="r:bz2") as tf:
                tf.extractall(dst)
            print(f"  extracted {top} -> {dst}")
        else:
            out = dst / name
            if out.is_file():
                print(f"  skip {name} (exists)")
                continue
            out.write_bytes(download(url))
            print(f"  wrote {out}")
    print("DONE speech models ready under", speech)

if __name__ == "__main__":
    main()
