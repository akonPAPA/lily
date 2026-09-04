from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--name", default="lily")
    ap.add_argument("--lx", type=float, default=0.44)
    ap.add_argument("--ly", type=float, default=0.335)
    ap.add_argument("--rx", type=float, default=0.53)
    ap.add_argument("--ry", type=float, default=0.335)
    ap.add_argument("--w", type=float, default=0.055)
    ap.add_argument("--h", type=float, default=0.05)
    args = ap.parse_args()

    d = Path(__file__).resolve().parents[2] / "assets" / "characters" / args.name
    img = Image.open(d / "sprites.png").convert("RGBA")
    W, H = img.size
    draw = ImageDraw.Draw(img)

    for (fx, fy) in ((args.lx, args.ly), (args.rx, args.ry)):
        cx, cy = fx * W, fy * H
        w, h = args.w * W, args.h * H
        draw.ellipse([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2],
                     outline=(0, 200, 0, 255), width=2)
        draw.line([cx - 6, cy, cx + 6, cy], fill=(255, 40, 40, 255))
        draw.line([cx, cy - 6, cx, cy + 6], fill=(255, 40, 40, 255))

    out = d / "eye_preview.png"
    img.save(out)
    print(f"wrote {out}  L=({args.lx*W:.0f},{args.ly*H:.0f}) R=({args.rx*W:.0f},{args.ry*H:.0f}) frame={W}x{H}")

if __name__ == "__main__":
    main()
