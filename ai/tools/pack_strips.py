from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

DEFAULT_SRC = str(Path(__file__).resolve().parents[2] / "lily_png")

MANIFEST = [
    ("1788472831", "idle",         4,  4, True,  0.90),
    ("1788472840", "blink",        3, 10, False, 0.90),
    ("1788472852", "talk",         5, 12, True,  0.90),
    ("1788472861", "walk",         7, 10, True,  0.90),
    ("1788472900", "wave",         6, 12, False, 0.90),
    ("1788472919", "sit",          3,  3, True,  0.74),
    ("1788472930", "sleep",        4,  3, True,  0.90),
    ("1788472965", "happy",        4, 10, False, 0.90),
    ("1788472985", "sad",          3, 10, False, 0.90),
    ("1788473005", "angry",        3, 10, False, 0.90),
    ("1788473015", "surprised",    3, 12, False, 0.90),
    ("1788473056", "grab_cursor",  4, 12, False, 0.90),
    ("1788473066", "held_drag",    3,  8, True,  0.82),
    ("1788473077", "push_window",  4,  8, False, 0.90),
    ("1788473086", "sit_on_edge",  3,  3, True,  0.74),
]

OUTFITS = {
    "1788472701": "gamer",
    "1788472711": "hacker",
    "1788472721": "sleepy",
    "1788472731": "hoodie",
    "1788472762": "casual",
    "1788472772": "sporty",
    "1788472781": "cozy",
}

BOTTOM_PAD_FRAC = 0.02
WIDTH_LIMIT_FRAC = 0.96
MIN_FRAGMENT_AREA = 120

KEEP_X_MARGIN_FRAC = 0.06

ALPHA_FG = 46
NOD_DIP_FRAC = 0.03

OUTFIT_MOUTH = {"x": 0.5, "y": 0.22, "w": 0.035, "h": 0.03}

BASE_HAND = {"x": 0.68, "y": 0.42}

BASE_MOUTH = {"x": 0.49, "y": 0.305, "w": 0.022, "h": 0.018}
BASE_EYES = [
    {"x": 0.455, "y": 0.250, "w": 0.030, "h": 0.020},
    {"x": 0.525, "y": 0.250, "w": 0.030, "h": 0.020},
]

def split_positions_by_gap(alpha: np.ndarray, n: int) -> list[tuple[int, int]]:
    """Return N (x0, x1) column ranges cut at the emptiest columns between figures."""
    w = alpha.shape[1]
    if n <= 1:
        return [(0, w)]
    coverage = (alpha > ALPHA_FG).sum(axis=0).astype(float)
    cuts, prev = [], 0
    for i in range(1, n):
        ideal = round(i * w / n)
        window = int(w / n * 0.35)
        lo = max(prev + 1, ideal - window)
        hi = min(w - 1, ideal + window)
        seg = coverage[lo:hi]
        x = lo + int(np.argmin(seg)) if seg.size else ideal
        cuts.append(x)
        prev = x
    xs = [0, *cuts, w]
    return [(xs[k], xs[k + 1]) for k in range(n)]

def clean_column(fig: Image.Image) -> Image.Image:
    """Keep the main figure plus its own (non-edge) accessories; erase neighbour bleed.

    Also strips sub-threshold alpha first: rembg leaves faint speckle across a textured
    background, which would otherwise inflate the alpha bbox (a full-canvas bbox scales the
    figure to a tiny sliver) and render as haze.
    """
    arr = np.array(fig.convert("RGBA"))
    arr[arr[:, :, 3] <= ALPHA_FG, 3] = 0
    fg = arr[:, :, 3] > ALPHA_FG
    if not fg.any():
        return Image.fromarray(arr, "RGBA")
    labels, num = ndimage.label(fg)
    if num <= 1:
        return Image.fromarray(arr, "RGBA")
    w = fg.shape[1]
    sizes = ndimage.sum(np.ones_like(labels), labels, index=range(1, num + 1))
    main = 1 + int(np.argmax(sizes))
    main_xs = np.where(labels == main)[1]
    margin = KEEP_X_MARGIN_FRAC * w
    main_lo, main_hi = main_xs.min() - margin, main_xs.max() + margin
    keep = np.zeros_like(fg)
    for lbl in range(1, num + 1):
        if lbl == main:
            keep |= labels == lbl
            continue
        xs = np.where(labels == lbl)[1]
        touches_side = xs.min() == 0 or xs.max() == w - 1

        overlaps_main = xs.max() >= main_lo and xs.min() <= main_hi
        if not touches_side and xs.size >= MIN_FRAGMENT_AREA and overlaps_main:
            keep |= labels == lbl
    arr[~keep, 3] = 0
    return Image.fromarray(arr, "RGBA")

def alpha_bbox(img: Image.Image):

    a = img.convert("RGBA").getchannel("A").point(lambda v: 255 if v > ALPHA_FG else 0)
    return a.getbbox()

def normalize(fig: Image.Image, scale: float, cw: int, ch: int, dy: int = 0) -> Image.Image:
    bbox = alpha_bbox(fig)
    canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    if bbox is None:
        return canvas
    cropped = fig.crop(bbox)
    sw = max(1, round(cropped.width * scale))
    sh = max(1, round(cropped.height * scale))
    scaled = cropped.resize((sw, sh), Image.Resampling.LANCZOS)
    x = (cw - sw) // 2
    y = ch - sh - round(BOTTOM_PAD_FRAC * ch) + dy
    canvas.alpha_composite(scaled, (max(0, x), max(0, y)))
    return canvas

def process_strip(path: Path, n: int, frac: float, cw: int, ch: int, session) -> list[Image.Image]:
    from rembg import remove

    cut = remove(Image.open(path).convert("RGBA"), session=session)
    alpha = np.array(cut.getchannel("A"))
    ranges = split_positions_by_gap(alpha, n)
    figs = [clean_column(cut.crop((x0, 0, x1, cut.height))) for (x0, x1) in ranges]

    boxes = [alpha_bbox(f) for f in figs]
    heights = [b[3] - b[1] for b in boxes if b]
    widths = [b[2] - b[0] for b in boxes if b]
    if not heights:
        return [Image.new("RGBA", (cw, ch), (0, 0, 0, 0)) for _ in figs]
    scale = (frac * ch) / max(heights)
    if max(widths) * scale > WIDTH_LIMIT_FRAC * cw:
        scale = (WIDTH_LIMIT_FRAC * cw) / max(widths)
    return [normalize(f, scale, cw, ch) for f in figs]

def cutout_single(path: Path, frac: float, cw: int, ch: int, session) -> Image.Image:
    """rembg-cutout a single pose and normalize it into one centered cw x ch cell."""
    from rembg import remove

    cut = clean_column(remove(Image.open(path).convert("RGBA"), session=session))
    bbox = alpha_bbox(cut)
    if bbox is None:
        return Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    bw, bh = bbox[2] - bbox[0], bbox[3] - bbox[1]
    scale = (frac * ch) / bh
    if bw * scale > WIDTH_LIMIT_FRAC * cw:
        scale = (WIDTH_LIMIT_FRAC * cw) / bw
    return normalize(cut, scale, cw, ch)

def _shift_down(cell: Image.Image, dy: int) -> Image.Image:
    """Return a copy of a normalized cell nudged down by dy px (for the synthetic nod)."""
    out = Image.new("RGBA", cell.size, (0, 0, 0, 0))
    out.alpha_composite(cell, (0, max(0, dy)))
    return out

def write_frames_json(out_dir: Path, cw: int, ch: int, actions: dict, note: str,
                      mouth: dict | None = None, hand: dict | None = None,
                      eyes: list | None = None) -> None:
    meta = {
        "image": "sprites.png",
        "frameWidth": cw,
        "frameHeight": ch,
        "style": "portrait-image",
        "renderScale": 1,
        "note": note,
    }
    if mouth is not None:
        meta["mouth"] = mouth
    if hand is not None:
        meta["hand"] = hand
    if eyes:
        meta["eyes"] = eyes
    payload = {"meta": meta, "actions": actions}
    (out_dir / "frames.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")

def pack_outfit(name: str, file_name: str, src_dir: Path, char_dir: Path,
                cw: int, ch: int) -> None:
    """Pack one alternate-outfit pose into outfits/<name>/ as a single-idle portrait pack."""
    from rembg import new_session

    path = src_dir / file_name
    if not path.is_file():
        raise FileNotFoundError(f"missing outfit source: {path}")
    session = new_session("isnet-anime")
    cell = cutout_single(path, 0.90, cw, ch, session)

    out_dir = char_dir / "outfits" / name
    out_dir.mkdir(parents=True, exist_ok=True)
    cell.save(out_dir / "sprites.png")
    cell.save(out_dir / "preview.png")
    actions = {"idle": {"row": 0, "frames": 1, "fps": 1, "loop": True}}
    write_frames_json(
        out_dir, cw, ch, actions,
        note=f"Outfit '{name}' from {file_name} via ai/tools/pack_strips.py --outfit. "
             "Single idle frame; procedural mouth/blink. Tune 'mouth' with mouth_preview.py.",
        mouth=OUTFIT_MOUTH,
    )
    print(f"wrote {out_dir / 'sprites.png'} ({cw}x{ch})  outfit '{name}'  <- {file_name}")

def pack_base_single(file_name: str, src_dir: Path, char_dir: Path, cw: int, ch: int) -> None:
    """Pack ONE clean pose as the single-image base character (Goose-style: one consistent
    drawing animated procedurally in-app). Fixes the "different photos per frame" jitter of
    the multi-strip pack and makes her easy to size down."""
    from rembg import new_session

    path = src_dir / file_name
    if not path.is_file():
        raise FileNotFoundError(f"missing base source: {path}")
    session = new_session("isnet-anime")
    cell = cutout_single(path, 0.90, cw, ch, session)

    char_dir.mkdir(parents=True, exist_ok=True)
    cell.save(char_dir / "sprites.png")
    cell.save(char_dir / "preview.png")
    actions = {"idle": {"row": 0, "frames": 1, "fps": 1, "loop": True}}
    write_frames_json(
        char_dir, cw, ch, actions,
        note=f"Single clean base image from {file_name} via --base-single. One consistent "
             "drawing; all motion (walk-bob, lean, talk, blink, cursor-drag, emotion FX) is "
             "procedural in-app.",
        mouth=BASE_MOUTH, hand=BASE_HAND, eyes=BASE_EYES,
    )
    print(f"wrote {char_dir / 'sprites.png'} ({cw}x{ch})  single-image base  <- {file_name}")

def pack_base(src_dir: Path, char_dir: Path, cw: int, ch: int) -> None:
    """Pack all drawn strips + synthetic nod/nap into the base sprites.png grid."""
    from rembg import new_session
    session = new_session("isnet-anime")

    cells_by_action: dict[str, list[Image.Image]] = {}
    meta_by_action: dict[str, tuple[int, int, bool]] = {}
    for stem, action, frames, fps, loop, frac in MANIFEST:
        path = src_dir / f"{stem}.png"
        if not path.is_file():
            raise FileNotFoundError(f"missing source strip: {path}")
        cells_by_action[action] = process_strip(path, frames, frac, cw, ch, session)
        meta_by_action[action] = (frames, fps, loop)
        print(f"  {action:<12} {frames} frames  ({path.name})")

    idle0 = cells_by_action["idle"][0]
    dip = round(NOD_DIP_FRAC * ch)
    cells_by_action["nod"] = [idle0, _shift_down(idle0, dip), idle0]
    meta_by_action["nod"] = (3, 8, False)
    print(f"  {'nod':<12} 3 frames  (synth from idle)")

    sleep_cells = cells_by_action["sleep"]
    nap = [sleep_cells[0], sleep_cells[len(sleep_cells) // 2]]
    cells_by_action["nap"] = nap
    meta_by_action["nap"] = (2, 2, True)
    print(f"  {'nap':<12} 2 frames  (synth from sleep)")

    order = [a for (_, a, *_rest) in MANIFEST] + ["nod", "nap"]
    ncols = max(len(cells_by_action[a]) for a in order)
    nrows = len(order)

    grid = Image.new("RGBA", (ncols * cw, nrows * ch), (0, 0, 0, 0))
    actions: dict[str, dict] = {}
    for row, action in enumerate(order):
        for col, cell in enumerate(cells_by_action[action]):
            grid.alpha_composite(cell, (col * cw, row * ch))
        frames, fps, loop = meta_by_action[action]
        actions[action] = {"row": row, "frames": frames, "fps": fps, "loop": loop}

    char_dir.mkdir(parents=True, exist_ok=True)
    grid.save(char_dir / "sprites.png")
    grid.save(char_dir / "preview.png")
    write_frames_json(
        char_dir, cw, ch, actions,
        note="Smooth hi-res pack via ai/tools/pack_strips.py (gap-split + connected-"
             "component clean-up) from the redrawn lily_png set. nod/nap are synthetic. "
             "Drawn talk/blink rows replace the procedural mouth/eye overlay.",
        hand=BASE_HAND,
    )
    print(f"\nwrote {char_dir / 'sprites.png'} ({grid.width}x{grid.height}, "
          f"{nrows} rows x {ncols} cols, cell {cw}x{ch})")
    print(f"wrote {char_dir / 'frames.json'} ({len(actions)} actions)")

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=DEFAULT_SRC)
    ap.add_argument("--name", default="lily")
    ap.add_argument("--cell-w", type=int, default=300)
    ap.add_argument("--cell-h", type=int, default=380)
    ap.add_argument("--outfit", help="pack a single alternate outfit into outfits/<name>")
    ap.add_argument("--file", help="source PNG (stem or filename) for --outfit; "
                                   "defaults to the OUTFITS mapping for that name")
    ap.add_argument("--base-single", dest="base_single",
                    help="pack ONE clean pose (stem or filename) as the single-image base")
    args = ap.parse_args()

    src_dir = Path(args.src)
    cw, ch = args.cell_w, args.cell_h
    char_dir = Path(__file__).resolve().parents[2] / "assets" / "characters" / args.name

    if args.base_single:
        file_name = args.base_single
        if not file_name.lower().endswith(".png"):
            file_name = f"{file_name}.png"
        pack_base_single(file_name, src_dir, char_dir, cw, ch)
        return

    if args.outfit:
        file_name = args.file
        if file_name is None:
            stem = next((s for s, n in OUTFITS.items() if n == args.outfit), None)
            if stem is None:
                raise SystemExit(f"unknown outfit '{args.outfit}'; pass --file <png>")
            file_name = f"{stem}.png"
        elif not file_name.lower().endswith(".png"):
            file_name = f"{file_name}.png"
        pack_outfit(args.outfit, file_name, src_dir, char_dir, cw, ch)
        return

    pack_base(src_dir, char_dir, cw, ch)

if __name__ == "__main__":
    main()
