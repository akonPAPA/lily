from __future__ import annotations

import argparse
import os
from pathlib import Path

os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")

DEFAULT_REPO = "unsloth/Phi-4-mini-instruct-GGUF"
DEFAULT_FILE = "Phi-4-mini-instruct-Q4_K_M.gguf"

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=DEFAULT_REPO)
    ap.add_argument("--file", default=DEFAULT_FILE)
    args = ap.parse_args()

    from huggingface_hub import hf_hub_download

    models_dir = Path(__file__).resolve().parents[2] / "models"
    models_dir.mkdir(parents=True, exist_ok=True)

    print(f"Downloading {args.file} from {args.repo} -> {models_dir}")
    path = hf_hub_download(
        repo_id=args.repo,
        filename=args.file,
        local_dir=str(models_dir),
    )
    size = Path(path).stat().st_size
    print(f"DONE {path} ({size/1e9:.2f} GB)")

if __name__ == "__main__":
    main()
