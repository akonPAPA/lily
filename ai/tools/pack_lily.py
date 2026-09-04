from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image

Entry = tuple
MANIFEST: list[Entry] = [
    ("1788468978", "idle",         4,  4, True,  0.88),
    ("1788469013", "blink",        3, 10, False, 0.88),
    ("1788469021", "talk",         5, 12, True,  0.88),
    ("1788469029", "walk",         5, 10, True,  0.88),
    ("1788469066", "wave",         5, 12, False, 0.88),
    ("1788469074", "nod",          3, 10, False, 0.88),
    ("1788469081", "sit",          2,  3, True,  0.72),
    ("1788469088", "sleep",        4,  3, True,  0.88),
    ("1788469127", "happy",        4, 10, False, 0.88),
    ("1788469134", "sad",          3, 10, False, 0.88),
    ("1788469143", "angry",        3, 10, False, 0.88),
    ("1788469151", "surprised",    3, 12, False, 0.88),
    ("1788469192", "grab_cursor",  4, 12, False, 0.88),
    ("1788469209", "held_drag",    3,  8, True,  0.80),
    ("1788469217", "push_window",  4,  8, False, 0.88),
    ("1788469226", "sit_on_edge",  3,  3, True,  0.72),
    ("1788469798", "nap",          2,  3, True,  0.62),
]

SUPERSAMPLE = 4
BOTTOM_PAD_FRAC = 0.02
WIDTH_LIMIT_FRAC = 0.96

def split_columns(img: Image.Image, n: int) -> list[Image.Image]:
    """Slice the strip into N equal-width columns (figures sit side by side)."""
    w, h = img.size
    step = w / n
    cols = []
    for i in range(n):
        x0 = int(round(i * step))
        x1 = int(round((i + 1) * step)) if i < n - 1 else w
        cols.append(img.crop((x0, 0, x1, h)))
    return cols

def alpha_bbox(img: Image.Image):
    return img.convert("RGBA").getchannel("A").getbbox()

def normalize_frame(fig: Image.Image, scale: float, cell: int) -> Image.Image:
    """Crop to content, scale by the shared per-strip scale, and paste centered with
    feet on the cell bottom into a transparent ``cell x cell`` canvas."""
    bbox = alpha_bbox(fig)
    if bbox is None:
        return Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
    cropped = fig.crop(bbox)
    sw = max(1, round(cropped.width * scale))
    sh = max(1, round(cropped.height * scale))
    scaled = cropped.resize((sw, sh), Image.Resampling.LANCZOS)

    canvas = Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
    x = (cell - sw) // 2
    y = cell - sh - round(BOTTOM_PAD_FRAC * cell)
    canvas.alpha_composite(scaled, (max(0, x), max(0, y)))
    return canvas

def process_strip(path: Path, n: int, frac: float, cell_hi: int, session) -> list[Image.Image]:
    from rembg import remove

    src = Image.open(path).convert("RGBA")
    cut = remove(src, session=session)
    cols = split_columns(cut, n)

    boxes = [alpha_bbox(c) for c in cols]
    heights = [(b[3] - b[1]) for b in boxes if b]
    widths = [(b[2] - b[0]) for b in boxes if b]
    if not heights:
        return [Image.new("RGBA", (cell_hi, cell_hi), (0, 0, 0, 0)) for _ in cols]

    ref_h = max(heights)
    ref_w = max(widths)

    scale = (frac * cell_hi) / ref_h
    max_w = WIDTH_LIMIT_FRAC * cell_hi
    if ref_w * scale > max_w:
        scale = max_w / ref_w
    return [normalize_frame(c, scale, cell_hi) for c in cols]

def pixelize_sheet(hi: Image.Image, factor: int, colors: int) -> Image.Image:
    """Downscale the assembled hi-res grid by an exact integer factor, quantize to a
    shared palette, and re-binarize alpha so edges stay crisp (no soft fringe)."""
    small = hi.resize((hi.width // factor, hi.height // factor), Image.Resampling.LANCZOS)
    if colors and colors > 0:
        alpha = small.getchannel("A")
        rgb = small.convert("RGB").quantize(colors=colors, method=Image.Quantize.MEDIANCUT)
        small = rgb.convert("RGBA")
        small.putalpha(alpha)
    a = small.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
    small.putalpha(a)
    return small

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=str(Path(__file__).resolve().parents[2] / "lily_png"),
                    help="folder holding the generated pose-strip PNGs")
    ap.add_argument("--name", default="lily")
    ap.add_argument("--cell", type=int, default=128, help="final pixel cell size (square)")
    ap.add_argument("--colors", type=int, default=48, help="palette size (0 = keep full colour)")
    ap.add_argument("--render-scale", type=int, default=2, help="on-screen integer upscale hint")
    args = ap.parse_args()

    src_dir = Path(args.src)
    cell = args.cell
    cell_hi = cell * SUPERSAMPLE
    ncols = max(e[2] for e in MANIFEST)
    nrows = len(MANIFEST)

    from rembg import new_session
    session = new_session("isnet-anime")

    grid_hi = Image.new("RGBA", (ncols * cell_hi, nrows * cell_hi), (0, 0, 0, 0))
    actions: dict[str, dict] = {}

    for row, (stem, action, frames, fps, loop, frac) in enumerate(MANIFEST):
        path = src_dir / f"{stem}.png"
        if not path.is_file():
            raise FileNotFoundError(f"missing source strip: {path}")
        cells = process_strip(path, frames, frac, cell_hi, session)
        for col, cimg in enumerate(cells):
            grid_hi.alpha_composite(cimg, (col * cell_hi, row * cell_hi))
        actions[action] = {"row": row, "frames": frames, "fps": fps, "loop": loop}
        print(f"row {row:2d}  {action:<12} {frames} frames  ({path.name})")

    sheet = pixelize_sheet(grid_hi, SUPERSAMPLE, args.colors)

    out_dir = Path(__file__).resolve().parents[2] / "assets" / "characters" / args.name
    out_dir.mkdir(parents=True, exist_ok=True)
    sheet.save(out_dir / "sprites.png")
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.Resampling.NEAREST) \
        .save(out_dir / "preview.png")

    frames_json = {
        "meta": {
            "image": "sprites.png",
            "frameWidth": cell,
            "frameHeight": cell,
            "style": "pixelized-image",
            "renderScale": args.render_scale,
            "note": "Packed from lily_png pose-strips via ai/tools/pack_lily.py. "
                    "Drawn talk/blink rows replace the procedural mouth/eye overlay.",
        },
        "actions": actions,
    }
    (out_dir / "frames.json").write_text(json.dumps(frames_json, indent=2), encoding="utf-8")

    print(f"\nwrote {out_dir / 'sprites.png'} ({sheet.width}x{sheet.height}, "
          f"{nrows} rows x {ncols} cols, cell {cell})")
    print(f"wrote {out_dir / 'preview.png'} (4x preview)")
    print(f"wrote {out_dir / 'frames.json'} ({len(actions)} actions)")

if __name__ == "__main__":
    main()
