from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

CELL = 64
COLS = 6
ROWS = 5

T = (0, 0, 0, 0)
OUTLINE = (44, 32, 50, 255)
SKIN = (250, 214, 178, 255)
SKIN_SH = (222, 176, 140, 255)
SKIN_HI = (255, 233, 206, 255)
HAIR = (150, 92, 56, 255)
HAIR_SH = (110, 64, 38, 255)
HAIR_HI = (190, 132, 90, 255)
SHIRT = (92, 160, 222, 255)
SHIRT_SH = (60, 120, 186, 255)
SHIRT_HI = (144, 198, 240, 255)
PANTS = (72, 76, 108, 255)
PANTS_SH = (48, 52, 80, 255)
SHOE = (66, 50, 44, 255)
EYE_W = (246, 246, 250, 255)
EYE = (58, 48, 70, 255)
BLUSH = (246, 162, 152, 255)
MOUTH = (156, 66, 74, 255)
MOUTH_IN = (96, 42, 58, 255)
ZZZ = (206, 214, 232, 255)

def draw_lily(d: ImageDraw.ImageDraw, ox: int, oy: int, *,
              bob=0, leg=0, arm_swing=0, arm_up=0, mouth=0,
              eyes_closed=False, blush=False, zzz=0) -> None:
    """Draw one chibi frame. `mouth` 0..3 = closed..wide. `arm_up` 0..3 raises
    the left arm for waving. `leg` shifts feet for a walk cycle."""
    def rect(x0, y0, x1, y1, c):
        d.rectangle([ox + x0, oy + y0 + bob, ox + x1, oy + y1 + bob], fill=c)

    def px(x, y, c):
        d.point((ox + x, oy + y + bob), fill=c)

    rect(25 + leg, 46, 30 + leg, 55, PANTS)
    rect(25 + leg, 46, 25 + leg, 55, PANTS_SH)
    rect(34 - leg, 46, 39 - leg, 55, PANTS)
    rect(34 - leg, 46, 34 - leg, 55, PANTS_SH)
    rect(24 + leg, 55, 31 + leg, 58, SHOE)
    rect(33 - leg, 55, 40 - leg, 58, SHOE)

    rect(22, 33, 42, 48, SHIRT)
    rect(22, 33, 24, 48, SHIRT_SH)
    rect(40, 33, 42, 48, SHIRT_SH)
    rect(25, 34, 33, 35, SHIRT_HI)

    rect(29, 33, 35, 34, SHIRT_SH)

    ry = 35 + arm_swing
    rect(18, ry, 22, ry + 10, SKIN)
    rect(18, ry, 19, ry + 10, SKIN_SH)
    rect(18, ry + 10, 22, ry + 12, SKIN_HI)

    if arm_up > 0:

        rect(42, 30 - arm_up, 46, 38, SKIN)
        hx = 45 + (1 if arm_up % 2 else -1)
        rect(hx, 24 - arm_up, hx + 4, 28 - arm_up, SKIN_HI)
    else:
        ly = 35 - arm_swing
        rect(42, ly, 46, ly + 10, SKIN)
        rect(45, ly, 46, ly + 10, SKIN_SH)
        rect(42, ly + 10, 46, ly + 12, SKIN_HI)

    rect(21, 8, 43, 30, SKIN)
    rect(21, 8, 23, 30, SKIN_SH)
    rect(40, 8, 43, 30, SKIN_SH)
    rect(24, 9, 34, 10, SKIN_HI)
    if blush:
        rect(23, 22, 26, 24, BLUSH)
        rect(38, 22, 41, 24, BLUSH)

    rect(19, 4, 45, 12, HAIR)
    rect(19, 4, 45, 5, HAIR_HI)
    rect(19, 6, 21, 26, HAIR)
    rect(43, 6, 45, 26, HAIR)
    rect(19, 20, 21, 26, HAIR_SH)
    rect(43, 20, 45, 26, HAIR_SH)
    rect(22, 5, 42, 8, HAIR)
    rect(24, 7, 30, 8, HAIR_HI)

    px(20, 27, HAIR_SH)
    px(44, 27, HAIR_SH)

    ey = 18
    if eyes_closed:
        rect(25, ey + 2, 29, ey + 2, EYE)
        rect(35, ey + 2, 39, ey + 2, EYE)
    else:
        rect(25, ey, 28, ey + 4, EYE_W)
        rect(35, ey, 38, ey + 4, EYE_W)
        rect(26, ey + 1, 27, ey + 3, EYE)
        rect(36, ey + 1, 37, ey + 3, EYE)
        px(26, ey + 1, EYE_W)

    mx0, mx1, my = 30, 34, 26
    if mouth <= 0:
        rect(mx0, my, mx1, my, MOUTH)
    elif mouth == 1:
        rect(mx0, my, mx1, my + 1, MOUTH)
    elif mouth == 2:
        rect(mx0, my, mx1, my + 2, MOUTH)
        rect(mx0 + 1, my + 1, mx1 - 1, my + 1, MOUTH_IN)
    else:
        rect(mx0 - 1, my, mx1 + 1, my + 3, MOUTH)
        rect(mx0, my + 1, mx1, my + 2, MOUTH_IN)

    if zzz:
        base = 6 - zzz
        px(47, 10 - base, ZZZ)
        rect(48, 6 - base, 50, 6 - base, ZZZ)
        rect(49, 7 - base, 50, 8 - base, ZZZ)
        rect(48, 9 - base, 50, 9 - base, ZZZ)

def outline_pass(sheet: Image.Image) -> None:
    """Add a 1px dark outline around every opaque cluster for pixel-art pop."""
    px = sheet.load()
    w, h = sheet.size
    src = sheet.copy().load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] != 0:
                continue

            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] != 0:
                    px[x, y] = OUTLINE
                    break

def main() -> None:
    sheet = Image.new("RGBA", (CELL * COLS, CELL * ROWS), T)
    d = ImageDraw.Draw(sheet)

    for i in range(4):
        draw_lily(d, i * CELL, 0, bob=(0 if i in (0, 3) else 1),
                  eyes_closed=(i == 3), blush=True)

    walk = [(-3, 3), (-1, 1), (2, -2), (3, -3), (1, -1), (-2, 2)]
    for i, (leg, sw) in enumerate(walk):
        draw_lily(d, i * CELL, 1 * CELL, leg=leg, arm_swing=sw, bob=(i % 2))

    wave_up = [1, 2, 3, 3, 3, 2]
    for i, up in enumerate(wave_up):
        draw_lily(d, i * CELL, 2 * CELL, arm_up=up, blush=True,
                  mouth=1 if i > 1 else 0)

    talk_mouth = [0, 1, 2, 3, 2, 1]
    for i, m in enumerate(talk_mouth):
        draw_lily(d, i * CELL, 3 * CELL, mouth=m, bob=(i % 2))

    for i in range(4):
        draw_lily(d, i * CELL, 4 * CELL, eyes_closed=True, mouth=0,
                  bob=1, zzz=i + 1)

    outline_pass(sheet)

    out = Path(__file__).resolve().parents[2] / "assets" / "characters" / "lily" / "sprites.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(f"wrote {out} ({sheet.width}x{sheet.height})")

if __name__ == "__main__":
    main()
