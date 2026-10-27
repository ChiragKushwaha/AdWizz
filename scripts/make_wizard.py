#!/usr/bin/env python3
"""
Generates AdWizz's pixel-art wizard from one definition:

  - app/src/main/java/rocks/chirag/adshield/ui/WizardArt.kt  (in-app sprites: idle + casting)
  - app/src/main/res/drawable/ic_launcher_foreground.xml     (adaptive icon, Android 8+)
  - app/src/main/res/drawable/ic_launcher_monochrome.xml     (themed icon, Android 13+)
  - app/src/main/res/mipmap-*/ic_launcher.png                (icons for Android 6-7)
  - app/src/main/res/drawable/ic_shield.xml                  (notification / tile icon)

The wizard is an original character: pointy starry hat, white beard, blue robe, riding a broom.
Run from the repo root:  python3 scripts/make_wizard.py   (needs Pillow for the PNGs)
"""
import os

W, H = 56, 50
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

PALETTE = {
    "O": "#1B1720",  # outline
    "H": "#5B3F99",  # hat
    "h": "#41297A",  # hat shade
    "S": "#F4C95D",  # gold (band, star, wand tip)
    "F": "#F6D3B8",  # skin
    "f": "#E8B99A",  # skin shade
    "E": "#1B1720",  # eye
    "w": "#FFFFFF",  # eye glint
    "P": "#F2A0A0",  # rosy cheek
    "N": "#5B3A1E",  # hair
    "n": "#3F2714",  # hair shade
    "C": "#2E2B3D",  # cloak
    "c": "#211F2D",  # cloak shade
    "L": "#7B6FD0",  # cloak lining
    "K": "#7A4A26",  # broom stick
    "k": "#55331A",  # stick shade
    "Y": "#6B4226",  # bristles (dark twigs, like the design)
    "y": "#4E2F1B",  # bristle shade
    "M": "#B5432F",  # broom band
    "W": "#6B4220",  # wand
    "D": "#2A2333",  # boot
}


def blank():
    return [["." for _ in range(W)] for _ in range(H)]


def rect(g, x0, y0, x1, y1, c):
    """Fills x0..x1, y0..y1 inclusive."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < W and 0 <= y < H:
                g[y][x] = c


def px(g, x, y, c):
    rect(g, x, y, x, y, c)


def ellipse(g, cx, cy, rx, ry, c, top=None, bottom=None):
    for y in range(int(cy - ry), int(cy + ry) + 1):
        if (top is not None and y < top) or (bottom is not None and y > bottom):
            continue
        for x in range(int(cx - rx), int(cx + rx) + 1):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                px(g, x, y, c)


def line(g, x0, y0, x1, y1, c, thick=1):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n); y = round(y0 + (y1 - y0) * i / n)
        rect(g, x, y, x + thick - 1, y + thick - 1, c)


def wizard(casting):
    """
    Young chibi wizard in the design's pixel style: oversized head (~2/3 of the height), messy
    hair, big eyes, tiny dark cloak, flying on a broom with the bristles behind (left).
    Deliberately original: pointed hat, no round glasses, no scar.
    """
    g = blank()
    # Broom: twig bristles on the left, red band, stick rising gently to the right.
    for y in range(38, 47):
        half = 4.5 - abs(y - 42) * 0.55
        ellipse(g, 9, 42, 9, 4.6, "Y")
    for x in range(1, 17, 3):
        line(g, x, 39 + (x % 2), x + 4, 41 + (x % 3), "y")
    line(g, 0, 42, 16, 42, "y")
    rect(g, 17, 40, 18, 44, "M")
    line(g, 19, 42, 55, 36, "K", thick=2)
    line(g, 19, 44, 55, 38, "k")
    # Cloak: small body tucked behind the head, lavender lining, boots over the broom.
    ellipse(g, 29, 38, 11, 7, "C")
    ellipse(g, 33, 39, 6, 5, "c")
    ellipse(g, 41, 36, 6, 4, "c")           # cloak tail flowing back
    rect(g, 24, 33, 27, 36, "L")             # collar lining
    rect(g, 33, 42, 36, 45, "D")
    rect(g, 38, 41, 41, 44, "D")
    # Head: big round face with slight shading on the right.
    ellipse(g, 29, 26, 15, 11.5, "F")
    ellipse(g, 39, 28, 5, 7, "f")
    # Hair: messy mop. Jagged bangs of uneven length, side locks over the ears, darker strands
    # on the shadow side (right), like the design's chunky pixel hair.
    ellipse(g, 29, 17, 17, 8, "N")
    bangs = [3, 5, 6, 4, 2, 4, 7, 5, 3, 1, 3, 6, 8, 6, 3, 2, 4, 6, 5, 3, 2, 4, 7, 5, 2, 3, 5, 4, 2, 1]
    for i, depth in enumerate(bangs):
        x = 15 + i
        rect(g, x, 18, x, 18 + depth, "N")
    rect(g, 12, 18, 15, 30, "N"); rect(g, 11, 22, 12, 27, "N")
    rect(g, 43, 18, 46, 29, "N"); rect(g, 46, 21, 47, 26, "N")
    for x, top, bot in [(17, 21, 23), (22, 22, 24), (30, 22, 25), (36, 21, 24), (41, 20, 23)]:
        rect(g, x, top, x, bot, "n")
    ellipse(g, 41, 16, 6, 5, "n", top=14)
    rect(g, 44, 19, 46, 29, "n")
    # Eyebrows, big eyes with glints, rosy cheeks, small smile.
    rect(g, 21, 24, 25, 24, "n"); rect(g, 33, 24, 37, 24, "n")
    for ex in (22, 34):
        ellipse(g, ex + 1.5, 28.5, 2.4, 3.2, "E")
        rect(g, ex, 26, ex + 1, 27, "w")
        px(g, ex + 3, 30, "w")
    ellipse(g, 19, 32, 2, 1.2, "P"); ellipse(g, 40, 32, 2, 1.2, "P")
    line(g, 28, 34, 31, 34, "f"); px(g, 27, 33, "f"); px(g, 32, 33, "f")
    # Pointed hat with gold band and star, tip flopping back to the right.
    ellipse(g, 29, 12, 18, 2.6, "h")
    rect(g, 17, 10, 41, 11, "S")
    for i, y in enumerate(range(9, 0, -1)):
        x0 = 18 + i * 1.3 + (i * i) * 0.12
        x1 = 40 - i * 0.6 + (i * i) * 0.25
        rect(g, int(x0), y, int(x1), y, "H")
    rect(g, 44, 0, 50, 1, "H"); px(g, 51, 2, "S")
    line(g, 34, 2, 37, 9, "h", thick=2)
    rect(g, 24, 5, 25, 7, "S"); rect(g, 23, 6, 26, 6, "S")
    if casting:
        # Arm thrown forward, wand pointing up-left with a glowing star tip.
        line(g, 19, 37, 12, 31, "C", thick=3)
        rect(g, 10, 30, 12, 32, "F")
        line(g, 10, 29, 4, 21, "W", thick=2)
        rect(g, 1, 17, 4, 20, "S"); px(g, 0, 18, "S"); px(g, 5, 18, "S"); px(g, 2, 16, "S"); px(g, 2, 21, "S")
    else:
        # Hand resting on the broom, wand tucked along the stick.
        line(g, 21, 38, 21, 41, "C", thick=3)
        rect(g, 21, 41, 23, 42, "F")
        line(g, 6, 43, 18, 43, "W")
    return outline(g)


def outline(g):
    """1-pixel dark outline around every filled pixel, for the crisp sprite look."""
    out = [row[:] for row in g]
    for y in range(H):
        for x in range(W):
            if g[y][x] != ".":
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < H and g[ny][nx] not in ".O":
                    out[y][x] = "O"
                    break
    return out


def rows(g):
    return ["".join(r) for r in g]


def write_kotlin(idle, cast):
    """WizardArt.kt (palette + constants) and one file per frame, each well under 120 lines."""
    ui = os.path.join(ROOT, "app/src/main/java/rocks/chirag/adshield/ui")
    pal = ",\n".join(f"        '{k}' to Color(0xFF{v[1:].upper()})" for k, v in PALETTE.items())
    with open(os.path.join(ui, "WizardArt.kt"), "w") as f:
        f.write(f"""package rocks.chirag.adshield.ui

import androidx.compose.ui.graphics.Color

// GENERATED by scripts/make_wizard.py — edit the script, not this file.

/** AdWizz's pixel wizard on a broom ({W}x{H} cells). '.' is transparent. */
object WizardArt {{
    const val WIDTH = {W}
    const val HEIGHT = {H}

    /** Wand tip cell in the casting frame, where the spell sparkles start. */
    const val WAND_X = 2
    const val WAND_Y = 18

    val palette: Map<Char, Color> = mapOf(
{pal},
    )

    val IDLE: Array<String> get() = WizardIdleFrame.ROWS
    val CASTING: Array<String> get() = WizardCastingFrame.ROWS
}}
""")
    for obj, doc, g in (("WizardIdleFrame", "riding, wand tucked along the broom", idle),
                        ("WizardCastingFrame", "wand raised, casting the spell", cast)):
        body = ",\n".join(f'        "{r}"' for r in rows(g))
        with open(os.path.join(ui, obj + ".kt"), "w") as f:
            f.write(f"""package rocks.chirag.adshield.ui

// GENERATED by scripts/make_wizard.py — edit the script, not this file.

/** Wizard frame: {doc}. */
internal object {obj} {{
    val ROWS = arrayOf(
{body},
    )
}}
""")


def merged_rects(g, keep):
    """Greedy merge: horizontal runs of one colour, then stack identical runs vertically.
    Cuts the vector icon's path length by ~10x versus one square per pixel."""
    runs = {}  # (x0, x1, color) -> list of rows
    for y in range(H):
        x = 0
        while x < W:
            c = keep(g[y][x])
            if c is None:
                x += 1
                continue
            x1 = x
            while x1 + 1 < W and keep(g[y][x1 + 1]) == c:
                x1 += 1
            runs.setdefault((x, x1, c), []).append(y)
            x = x1 + 1
    rects = []
    for (x0, x1, c), ys in runs.items():
        start = prev = ys[0]
        for y in ys[1:] + [None]:
            if y is not None and y == prev + 1:
                prev = y
                continue
            rects.append((x0, start, x1 - x0 + 1, prev - start + 1, c))
            if y is not None:
                start = prev = y
    return rects


def vector_paths(g, scale, ox, oy, mono=False):
    keep = (lambda ch: None if ch in ".O" else "#FFFFFF") if mono else (lambda ch: None if ch == "." else PALETTE[ch])
    by_color = {}
    for x, y, w, h, c in merged_rects(g, keep):
        by_color.setdefault(c, []).append(
            f"M{ox + x * scale:.1f},{oy + y * scale:.1f}h{w * scale:.1f}v{h * scale:.1f}h{-w * scale:.1f}z"
        )
    return "\n".join(
        f'    <path android:fillColor="{c}" android:pathData="{"".join(p)}" />' for c, p in by_color.items()
    )


def write_icon_xml(g):
    # Adaptive icons are 108dp with the safe zone in the middle 66dp.
    scale = 1.15
    ox, oy = (108 - W * scale) / 2, (108 - H * scale) / 2
    for name, mono in (("ic_launcher_foreground", False), ("ic_launcher_monochrome", True)):
        xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED by scripts/make_wizard.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{vector_paths(g, scale, ox, oy, mono)}
</vector>
"""
        with open(os.path.join(RES, "drawable", name + ".xml"), "w") as f:
            f.write(xml)

    # Notification / tile icon: white wizard-hat silhouette (status bar icons must be one colour).
    hat = """<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED by scripts/make_wizard.py: pixel wizard hat -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M14,2h4v2h-2v2h-2zM12,6h4v4h-4zM10,10h6v4h-6zM8,14h10v4h-8zM3,18h18v3h-18z" />
</vector>
"""
    with open(os.path.join(RES, "drawable", "ic_shield.xml"), "w") as f:
        f.write(hat)


def write_pngs(g):
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        print("Pillow not installed: skipping PNG icons")
        return
    bg = (0xDD, 0xE6, 0xEE)
    for folder, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        for name, round_ in (("ic_launcher", False),):
            cell = size * (0.72 if round_ else 0.85) / W
            img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            d = ImageDraw.Draw(img)
            if round_:
                d.ellipse([0, 0, size - 1, size - 1], fill=bg)
            else:
                d.rounded_rectangle([0, 0, size - 1, size - 1], radius=size // 6, fill=bg)
            # Draw the sprite at 1 px per cell, then scale: crisp when big, smooth (no gaps) when tiny.
            sprite = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            for y in range(H):
                for x in range(W):
                    if g[y][x] != ".":
                        sprite.putpixel((x, y), tuple(int(PALETTE[g[y][x]][i:i + 2], 16) for i in (1, 3, 5)) + (255,))
            sw, sh = round(W * cell), round(H * cell)
            sprite = sprite.resize((sw, sh), Image.NEAREST if cell >= 2 else Image.LANCZOS)
            img.alpha_composite(sprite, ((size - sw) // 2, (size - sh) // 2))
            # Lossless WebP: same pixels, smaller than PNG (Android 4.3+ reads it; minSdk is 23).
            for old in ("ic_launcher.png", "ic_launcher_round.png", "ic_launcher_round.webp"):
                path = os.path.join(RES, f"mipmap-{folder}", old)
                if os.path.exists(path): os.remove(path)
            img.save(os.path.join(RES, f"mipmap-{folder}", name + ".webp"), lossless=True, quality=100, method=6)
    # No separate round icon: Android 8+ launchers mask the adaptive icon to any shape themselves,
    # and older ones use ic_launcher. One less set of bitmaps in the APK.
    stale = os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_round.xml")
    if os.path.exists(stale): os.remove(stale)


if __name__ == "__main__":
    idle, cast = wizard(False), wizard(True)
    write_kotlin(idle, cast)
    write_icon_xml(cast)
    write_pngs(cast)
    print("\n".join(rows(cast)))
