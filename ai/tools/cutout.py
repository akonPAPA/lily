from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image

def trim_alpha(img: Image.Image, pad: int = 2) -> Image.Image:
    img = img.convert("RGBA")
    bbox = img.getchannel("A").getbbox()
    if not bbox:
        return img
    x0, y0, x1, y1 = bbox
    x0 = max(0, x0 - pad); y0 = max(0, y0 - pad)
    x1 = min(img.width, x1 + pad); y1 = min(img.height, y1 + pad)
    return img.crop((x0, y0, x1, y1))

def fit_height(img: Image.Image, height: int) -> Image.Image:
    if height <= 0 or img.height <= height:
        return img
    scale = height / img.height
    return img.resize((max(1, round(img.width * scale)), height), Image.Resampling.LANCZOS)

def checkerboard_preview(img: Image.Image) -> Image.Image:
    bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
    d = bg.load()
    for y in range(img.height):
        for x in range(img.width):
            if (x // 8 + y // 8) % 2 == 0:
                d[x, y] = (204, 204, 204, 255)
    bg.alpha_composite(img)
    return bg

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--name", default="lily")
    ap.add_argument("--height", type=int, default=360, help="target on-screen height (px)")
    ap.add_argument("--model", default="isnet-anime",
                    help="rembg model: isnet-anime (anime) or u2net (general)")
    ap.add_argument("--mouth-x", type=float, default=0.5)
    ap.add_argument("--mouth-y", type=float, default=0.38)
    ap.add_argument("--mouth-w", type=float, default=0.04)
    ap.add_argument("--mouth-h", type=float, default=0.04)
    args = ap.parse_args()

    from rembg import new_session, remove

    src = Image.open(args.input).convert("RGBA")
    session = new_session(args.model)
    cut = remove(src, session=session)
    cut = trim_alpha(cut)
    cut = fit_height(cut, args.height)

    out_dir = Path(__file__).resolve().parents[2] / "assets" / "characters" / args.name
    out_dir.mkdir(parents=True, exist_ok=True)
    cut.save(out_dir / "sprites.png")
    checkerboard_preview(cut).save(out_dir / "preview.png")

    frames = {
        "meta": {
            "image": "sprites.png",
            "frameWidth": cut.width,
            "frameHeight": cut.height,
            "style": "portrait-image",
            "renderScale": 1,
            "mouth": {"x": args.mouth_x, "y": args.mouth_y,
                      "w": args.mouth_w, "h": args.mouth_h},
            "note": f"Cutout of {Path(args.input).name} via ai/tools/cutout.py ({args.model}). "
                    "Tune 'mouth' with ai/tools/mouth_preview.py.",
        },
        "actions": {
            "idle": {"row": 0, "frames": 1, "fps": 1, "loop": True},
        },
    }
    (out_dir / "frames.json").write_text(json.dumps(frames, indent=2), encoding="utf-8")

    print(f"wrote {out_dir/'sprites.png'} ({cut.width}x{cut.height})")
    print(f"wrote {out_dir/'preview.png'}")
    print(f"wrote {out_dir/'frames.json'}")

if __name__ == "__main__":
    main()
