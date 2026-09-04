from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--name", default="lily")
    ap.add_argument("--x", type=float, default=0.5)
    ap.add_argument("--y", type=float, default=0.35)
    ap.add_argument("--w", type=float, default=0.045)
    ap.add_argument("--h", type=float, default=0.05)
    args = ap.parse_args()

    d = Path(__file__).resolve().parents[2] / "assets" / "characters" / args.name
    img = Image.open(d / "sprites.png").convert("RGBA")
    W, H = img.size
    draw = ImageDraw.Draw(img)

    cx, cy = args.x * W, args.y * H
    w, h = args.w * W, args.h * H
    draw.ellipse([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], fill=(96, 44, 60, 255))
    draw.ellipse([cx - w / 2 + 1, cy - h / 2 + 1, cx + w / 2 - 1, cy + h / 2 - 1],
                 fill=(150, 70, 86, 255))

    draw.line([cx - 8, cy, cx + 8, cy], fill=(0, 200, 0, 255))
    draw.line([cx, cy - 8, cx, cy + 8], fill=(0, 200, 0, 255))

    out = d / "mouth_preview.png"
    img.save(out)
    print(f"wrote {out}  center=({cx:.0f},{cy:.0f}) size=({w:.0f}x{h:.0f})  frame={W}x{H}")

if __name__ == "__main__":
    main()
