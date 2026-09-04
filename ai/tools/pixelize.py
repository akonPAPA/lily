from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image

def remove_flat_background(img: Image.Image, tolerance: int = 24) -> Image.Image:
    """Make pixels matching the top-left corner colour transparent (simple key)."""
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    r0, g0, b0, _ = px[0, 0]
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if abs(r - r0) <= tolerance and abs(g - g0) <= tolerance and abs(b - b0) <= tolerance:
                px[x, y] = (r, g, b, 0)
    return img

def pixelize(src: Image.Image, target_px: int, colors: int) -> Image.Image:
    """Downscale so the longest side == target_px, optionally reduce palette."""
    src = src.convert("RGBA")
    w, h = src.size
    scale = target_px / max(w, h)
    small = src.resize((max(1, round(w * scale)), max(1, round(h * scale))),
                        Image.Resampling.LANCZOS)

    if colors and colors > 0:

        alpha = small.getchannel("A")
        rgb = small.convert("RGB").quantize(colors=colors, method=Image.Quantize.MEDIANCUT)
        rgb = rgb.convert("RGB")
        small = rgb.convert("RGBA")
        small.putalpha(alpha)

        a = small.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
        small.putalpha(a)
    return small

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="source character image")
    ap.add_argument("--name", default="lily", help="character folder under assets/characters/")
    ap.add_argument("--pixels", type=int, default=128,
                    help="longest side of the pixel sprite (64-160 typical)")
    ap.add_argument("--colors", type=int, default=48,
                    help="palette size (0 = keep full colour)")
    ap.add_argument("--bg-remove", action="store_true",
                    help="chroma-key out the flat top-left corner colour")
    ap.add_argument("--bg-tolerance", type=int, default=24)
    args = ap.parse_args()

    src = Image.open(args.input)
    if args.bg_remove:
        src = remove_flat_background(src, args.bg_tolerance)

    small = pixelize(src, args.pixels, args.colors)

    out_dir = Path(__file__).resolve().parents[2] / "assets" / "characters" / args.name
    out_dir.mkdir(parents=True, exist_ok=True)
    small.save(out_dir / "sprites.png")
    small.resize((small.width * 4, small.height * 4), Image.Resampling.NEAREST) \
        .save(out_dir / "preview.png")

    frames = {
        "meta": {
            "image": "sprites.png",
            "frameWidth": small.width,
            "frameHeight": small.height,
            "style": "pixelized-image",
            "note": f"Pixelized from {Path(args.input).name} via ai/tools/pixelize.py",
        },
        "actions": {
            "idle": {"row": 0, "frames": 1, "fps": 1, "loop": True},
        },
    }
    (out_dir / "frames.json").write_text(json.dumps(frames, indent=2), encoding="utf-8")

    print(f"wrote {out_dir/'sprites.png'} ({small.width}x{small.height})")
    print(f"wrote {out_dir/'preview.png'} (4x preview)")
    print(f"wrote {out_dir/'frames.json'}")

if __name__ == "__main__":
    main()
