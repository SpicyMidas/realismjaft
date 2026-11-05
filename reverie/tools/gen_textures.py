#!/usr/bin/env python3
"""Procedural pixel-art textures for Reverie.

Run from the project root:  python3 tools/gen_textures.py [--preview DIR]
Writes PNGs into src/main/resources/assets/reverie/textures/. Everything is seeded, so
re-running produces identical files. Items are drawn as character grids (one char per pixel),
blocks are filled from small palettes with seeded noise so they tile.
"""
import argparse
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "reverie", "textures")
WRITTEN = []

T = (0, 0, 0, 0)


def rgba(c, a=255):
    if len(c) == 4:
        return c
    return (c[0], c[1], c[2], a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def save(img, rel):
    path = os.path.join(TEX, rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    WRITTEN.append((rel, img))


def grid(rows, pal, size=None):
    h = len(rows)
    w = len(rows[0])
    assert all(len(r) == w for r in rows), "ragged grid: " + repr([len(r) for r in rows])
    img = Image.new("RGBA", (w, h), T)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.' or ch == ' ':
                continue
            img.putpixel((x, y), rgba(pal[ch]))
    return img


def noise_fill(w, h, shades, seed, weights=None):
    """Random pick from shades with weights, tileable by construction (no neighbourhood)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    weights = weights or [1] * len(shades)
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), rgba(rnd.choices(shades, weights)[0]))
    return img


def value_noise(w, h, seed, cell=4):
    """Smooth tileable value noise in [0,1]."""
    rnd = random.Random(seed)
    gw, gh = max(1, w // cell), max(1, h // cell)
    g = [[rnd.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx) % gw, int(fy) % gh
            x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0][x0] * (1 - tx) + g[y0][x1] * tx
            b = g[y1][x0] * (1 - tx) + g[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def ramp_fill(w, h, shades, seed, cell=4, jitter=0.18):
    """Noise mapped onto a palette ramp: blotchy, stone-like, tileable."""
    n = value_noise(w, h, seed, cell)
    rnd = random.Random(seed * 7 + 1)
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            v = n[y][x] + (rnd.random() - 0.5) * jitter
            i = max(0, min(len(shades) - 1, int(v * len(shades))))
            img.putpixel((x, y), rgba(shades[i]))
    return img


def speckle(img, colors, count, seed, avoid_edge=0):
    rnd = random.Random(seed)
    w, h = img.size
    for _ in range(count):
        x = rnd.randint(avoid_edge, w - 1 - avoid_edge)
        y = rnd.randint(avoid_edge, h - 1 - avoid_edge)
        img.putpixel((x, y), rgba(rnd.choice(colors)))
    return img


def outline(img, color):
    """Add a 1px outline around opaque pixels (items)."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] > 0:
                    img.putpixel((x, y), rgba(color))
                    break
    return img


# ---------------------------------------------------------------- palettes
TUFF = [(58, 60, 54), (77, 80, 72), (95, 99, 88), (110, 114, 101), (128, 132, 118), (150, 154, 138)]
TUFF_GREEN = [(52, 62, 52), (68, 82, 66), (86, 101, 82), (104, 119, 98), (124, 139, 115), (150, 163, 138)]
GOLD = [(122, 76, 18), (186, 128, 30), (232, 182, 54), (252, 226, 112)]
IRON = [(110, 110, 110), (170, 170, 170), (216, 216, 216), (240, 240, 240)]


def tuff_alloy():
    pal = {
        'o': (38, 44, 38), 'd': TUFF_GREEN[1], 'b': TUFF_GREEN[2], 'l': TUFF_GREEN[3], 'h': TUFF_GREEN[4],
        's': TUFF_GREEN[5], 'g': GOLD[2], 'G': GOLD[3], 'i': IRON[2], 'I': IRON[3], 'f': (172, 176, 160),
    }
    rows = [
        "................",
        "................",
        "................",
        "................",
        "......oooooooo..",
        ".....ohshhlhlbo.",
        "....ohhlGhlhlbdo",
        "...ohlhlhlfhbdo.",
        "..oshhlhIhlbddo.",
        ".obbbbbbbbbbdo..",
        "obgbdbbibbbddo..",
        "obbbbdbbbGbdo...",
        "odbibbbdbbddo...",
        ".ooooooooooo....",
        "................",
        "................",
    ]
    save(grid(rows, pal), "item/tuff_alloy")


# ---------------------------------------------------------------- tiny bitmap fonts
FONT5x7 = {
    'N': ["X...X", "XX..X", "X.X.X", "X.X.X", "X..XX", "X...X", "X...X"],
    'R': ["XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"],
    'S': [".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."],
    '1': ["..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."],
    '2': [".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"],
    '3': ["XXXX.", "....X", "....X", ".XXX.", "....X", "....X", "XXXX."],
    '4': ["...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."],
    '5': ["XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."],
    '6': ["..XX.", ".X...", "X....", "XXXX.", "X...X", "X...X", ".XXX."],
}
FONT3x5 = {
    'N': ["X.X", "XXX", "XXX", "XXX", "X.X"],
    'S': [".XX", "X..", ".X.", "..X", "XX."],
    'R': ["XX.", "X.X", "XX.", "X.X", "X.X"],
    '1': [".X.", "XX.", ".X.", ".X.", "XXX"],
    '2': ["XX.", "..X", ".X.", "X..", "XXX"],
    '3': ["XX.", "..X", "XX.", "..X", "XX."],
    '4': ["X.X", "X.X", "XXX", "..X", "..X"],
    '5': ["XXX", "X..", "XX.", "..X", "XX."],
    '6': [".XX", "X..", "XXX", "X.X", "XXX"],
}


def draw_glyph(img, font, ch, ox, oy, color, scale=1, shadow=None):
    rows = font[ch]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c != 'X':
                continue
            for sy in range(scale):
                for sx in range(scale):
                    px, py = ox + x * scale + sx, oy + y * scale + sy
                    if shadow is not None and 0 <= px + scale < img.size[0] and 0 <= py + scale < img.size[1]:
                        if img.getpixel((px + scale, py + scale))[:3] != color[:3]:
                            img.putpixel((px + scale, py + scale), rgba(shadow))
                    if 0 <= px < img.size[0] and 0 <= py < img.size[1]:
                        img.putpixel((px, py), rgba(color))


def rect(img, x0, y0, x1, y1, color):
    """Fill [x0,x1) x [y0,y1)."""
    for y in range(max(0, y0), min(img.size[1], y1)):
        for x in range(max(0, x0), min(img.size[0], x1)):
            img.putpixel((x, y), rgba(color))


def bevel(img, x0, y0, x1, y1, light, dark):
    for x in range(x0, x1):
        img.putpixel((x, y0), rgba(light))
        img.putpixel((x, y1 - 1), rgba(dark))
    for y in range(y0, y1):
        img.putpixel((x0, y), rgba(light))
        img.putpixel((x1 - 1, y), rgba(dark))


# ---------------------------------------------------------------- gearshift
BRASS = [(104, 70, 30), (150, 104, 44), (200, 152, 72), (236, 196, 116), (252, 230, 170)]
STEEL = [(58, 62, 68), (88, 94, 100), (122, 128, 134), (160, 166, 172), (204, 208, 214)]
LEATHER = [(30, 23, 20), (42, 32, 27), (56, 43, 36), (70, 55, 46)]
STITCH = (150, 128, 100)
RED = [(96, 14, 16), (150, 26, 28), (200, 44, 40), (236, 92, 80)]
CASING_GREEN = [(60, 70, 60), (74, 86, 72), (88, 101, 84), (100, 114, 95), (114, 128, 107)]
FLECK = (168, 174, 156)


def casing_base(seed):
    img = ramp_fill(16, 16, CASING_GREEN, seed, cell=4, jitter=0.25)
    speckle(img, [FLECK, (150, 156, 140)], 9, seed + 3)
    return img


def gearshift_textures():
    # Side panels: the visible region is 2..14; a recessed panel line frames it.
    for sport in (False, True):
        img = casing_base(101)
        line = (44, 52, 44)
        for i in range(3, 13):
            img.putpixel((i, 3), rgba(line))
            img.putpixel((i, 12), rgba(line))
            img.putpixel((3, i), rgba(line))
            img.putpixel((12, i), rgba(line))
        for i in range(4, 12):  # lit lip under the groove (light from upper left)
            img.putpixel((i, 4), rgba(CASING_GREEN[4]))
            img.putpixel((4, i), rgba(CASING_GREEN[4]))
        if sport:
            for x in range(2, 14):
                img.putpixel((x, 6), rgba(RED[2]))
                img.putpixel((x, 7), rgba(RED[1]))
            for x in range(2, 14, 3):
                img.putpixel((x, 6), rgba(RED[3]))
        save(img, "block/gearshift/casing_side" + ("_sport" if sport else ""))

    top = casing_base(202)
    for x in range(16):
        top.putpixel((x, 2), rgba(CASING_GREEN[4]))
    save(top, "block/gearshift/casing_top")

    vent = casing_base(303)
    for z in (5, 8, 11):
        for x in range(4, 12):
            vent.putpixel((x, z), rgba((18, 22, 18)))
            vent.putpixel((x, z + 1), rgba(CASING_GREEN[4]))
        vent.putpixel((3, z), rgba((40, 46, 40)))
        vent.putpixel((12, z), rgba((40, 46, 40)))
    save(vent, "block/gearshift/casing_vent")

    flange = ramp_fill(16, 16, [(48, 56, 48), (58, 67, 57), (68, 78, 66), (78, 89, 75)], 404, cell=4)
    speckle(flange, [FLECK], 5, 405)
    bevel(flange, 0, 0, 16, 16, (98, 110, 92), (34, 40, 34))
    bevel(flange, 1, 1, 15, 15, (84, 96, 80), (44, 52, 44))
    save(flange, "block/gearshift/flange")

    gold = noise_fill(16, 16, [GOLD[1], GOLD[2], GOLD[2], GOLD[3]], 505, [1, 3, 3, 1])
    save(gold, "block/gearshift/gold_inlay")
    for name, pal in (("brass", BRASS[0:4]), ("brass_polished", BRASS[1:5])):
        b = ramp_fill(16, 16, pal, 606 if name == "brass" else 607, cell=2, jitter=0.1)
        for x in range(0, 16, 5):
            b.putpixel((x, x % 16), rgba(pal[-1]))
        save(b, "block/gearshift/" + name)

    bolt = Image.new("RGBA", (16, 16), rgba(STEEL[2]))
    for y in range(16):
        for x in range(16):
            v = (x + y) / 30.0
            bolt.putpixel((x, y), rgba(mix(STEEL[4], STEEL[1], v)))
    save(bolt, "block/gearshift/bolt")

    leather = ramp_fill(16, 16, LEATHER, 707, cell=2, jitter=0.3)
    for i in range(0, 16, 2):  # stitch rows
        leather.putpixel((i, 0), rgba(STITCH))
        leather.putpixel((i + 1, 15), rgba(STITCH))
        leather.putpixel((0, i), rgba(STITCH))
        leather.putpixel((15, i + 1), rgba(STITCH))
    save(leather, "block/gearshift/leather")

    rod = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            rod.putpixel((x, y), rgba(STEEL[[1, 3, 4, 3, 2, 1, 1, 2][x % 8]]))
    save(rod, "block/gearshift/rod")

    knob = ramp_fill(16, 16, [(14, 14, 16), (24, 24, 28), (34, 34, 40), (48, 48, 56)], 808, cell=4, jitter=0.2)
    for x in range(16):
        knob.putpixel((x, 0), rgba((70, 70, 80)))
    # tiny gold shift pattern on the knob top (visible on the up face)
    for y in range(5, 11):
        knob.putpixel((8, y), rgba(GOLD[2]))
    for x in range(5, 12):
        knob.putpixel((x, 8), rgba(GOLD[2]))
    for x in (5, 11):
        for y in range(5, 11):
            knob.putpixel((x, y), rgba(GOLD[2]))
    save(knob, "block/gearshift/knob")

    ball = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = ((x - 5) ** 2 + (y - 5) ** 2) ** 0.5 / 14
            ball.putpixel((x, y), rgba(mix(STEEL[4], STEEL[1], min(1, v))))
    for x in range(16):
        ball.putpixel((x, 7), rgba(RED[2]))
        ball.putpixel((x, 8), rgba(RED[1]))
    draw_glyph(ball, FONT3x5, 'S', 6, 2, (240, 240, 240))
    save(ball, "block/gearshift/knob_sport")

    frame = noise_fill(16, 16, [(24, 28, 24), (30, 34, 30), (36, 40, 36)], 909)
    save(frame, "block/gearshift/window_frame")
    dot_on = Image.new("RGBA", (16, 16), rgba((255, 60, 40)))
    rect(dot_on, 4, 4, 12, 12, (255, 150, 120))
    save(dot_on, "block/gearshift/dot_on")
    save(Image.new("RGBA", (16, 16), rgba((70, 18, 16))), "block/gearshift/dot_off")

    # Gear window glyphs, 32x32 so the digit stays crisp on a 4x4 px window.
    for key, ch in (("n", 'N'), ("r", 'R'), ("1", '1'), ("2", '2'), ("3", '3'), ("4", '4'), ("5", '5'), ("6", '6')):
        color = (255, 196, 64) if ch == 'N' else (255, 72, 52) if ch == 'R' else (96, 255, 120)
        g = Image.new("RGBA", (32, 32), rgba((10, 12, 10)))
        bevel(g, 0, 0, 32, 32, (40, 44, 40), (4, 4, 4))
        dim = mix(color, (10, 12, 10), 0.75)
        draw_glyph(g, FONT5x7, ch, 9, 5, dim, 3)   # soft halo
        draw_glyph(g, FONT5x7, ch, 8, 5, color, 3)
        save(g, "block/gearshift/gear_" + key)

    gate_plate(sport=False)
    gate_plate(sport=True)
    gearshift_gui()


def brushed(w, h, seed, base=(150, 156, 150)):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        row = rnd.randint(-10, 10)
        for x in range(w):
            v = row + rnd.randint(-6, 6)
            img.putpixel((x, y), rgba(tuple(max(0, min(255, c + v)) for c in base)))
    return img


def gate_plate(sport):
    # 64x64; the plate face uses u in [0, cols_px*k] and v in [0, 64]. Rail runs along v (the shaft axis).
    img = brushed(64, 64, 1111 + sport, (140, 146, 138))
    k = 64 / (12 if sport else 10)          # texels per model pixel
    uw = int(round(6 * k))                    # used width in texels
    columns = 4 if sport else 3
    rest = (columns - 1) / 2
    col_v = [int(round((6 if sport else 5) * k + (c - rest) * 3 * k)) for c in range(columns)]
    rail_u = uw // 2
    groove, lip = (26, 30, 28), (196, 200, 192)
    gw = 2
    top_u, bot_u = int(round(1.1 * k)), int(round(4.9 * k))

    def vgroove(u, v0, v1):
        rect(img, u - gw, v0, u + gw, v1, groove)
        for v in range(v0, v1):
            if u + gw < 64:
                img.putpixel((u + gw, v), rgba(lip))

    def ugroove(v, u0, u1):
        rect(img, u0, v - gw, u1, v + gw, groove)
        for u in range(u0, u1):
            if v + gw < 64:
                img.putpixel((u, v + gw), rgba(lip))

    vgroove(rail_u, col_v[0] - gw, col_v[-1] + gw)
    for c in range(columns):
        has_top = True
        has_bot = c < columns - 1
        u0 = top_u if has_top else rail_u
        u1 = bot_u if has_bot else rail_u
        ugroove(col_v[c], u0, u1 + 1)
        labels = []
        if c == columns - 1:
            labels.append(('R', top_u))
        else:
            labels.append((str(c * 2 + 1), top_u))
            labels.append((str(c * 2 + 2), bot_u))
        for ch, u in labels:
            lv = col_v[c] + 4 if col_v[c] < 40 else col_v[c] - 14
            draw_glyph(img, FONT3x5, ch, u - 3, lv, (20, 24, 22), 2)
    draw_glyph(img, FONT3x5, 'N', rail_u + 4, int(round((6 if sport else 5) * k)) - 5, (90, 70, 20), 2)
    # bezel around the used area
    bevel(img, 0, 0, uw, 64, (210, 214, 206), (70, 74, 70))
    save(img, "block/gearshift/gate_plate" + ("_sport" if sport else ""))


def gearshift_gui():
    img = Image.new("RGBA", (256, 256), T)
    plate = brushed(144, 100, 2222, (132, 140, 130))
    bevel(plate, 0, 0, 144, 100, (190, 196, 186), (60, 64, 60))
    for (bx, by) in ((4, 4), (136, 4), (4, 92), (136, 92)):
        rect(plate, bx, by, bx + 4, by + 4, STEEL[1])
        rect(plate, bx, by, bx + 3, by + 3, STEEL[3])
        plate.putpixel((bx + 1, by + 1), rgba(STEEL[4]))
    img.paste(plate, (0, 0))

    def disc(cx, cy, r, color_fn):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
                c = color_fn(x, y, d)
                if c is not None and d <= r:
                    img.putpixel((x, y), rgba(c))

    # regular knob (0,104): black with gold collar and a gold shift pattern on top
    disc(8, 112, 7.5, lambda x, y, d: GOLD[1] if d > 6.5 else mix((70, 70, 80), (10, 10, 12), min(1, ((x - 5) ** 2 + (y - 108) ** 2) ** 0.5 / 10)))
    for y in range(109, 116):
        img.putpixel((8, y), rgba(GOLD[2]))
    for x in range(5, 12):
        img.putpixel((x, 112), rgba(GOLD[2]))
    for y in range(109, 116):
        img.putpixel((5, y), rgba(GOLD[2]))
        img.putpixel((11, y), rgba(GOLD[2]))
    # sport knob (16,104): metal ball, red band, S
    disc(24, 112, 7.5, lambda x, y, d: mix(STEEL[4], STEEL[0], min(1, ((x - 21) ** 2 + (y - 108) ** 2) ** 0.5 / 12)))
    for x in range(17, 32):
        for y in (111, 112):
            if ((x + 0.5 - 24) ** 2 + (y + 0.5 - 112) ** 2) ** 0.5 <= 7.5:
                img.putpixel((x, y), rgba(RED[2] if y == 111 else RED[1]))
    draw_glyph(img, FONT3x5, 'S', 23, 105, (245, 245, 245))
    # leather boot ring (32,104) 24x24
    disc(44, 116, 11.5, lambda x, y, d: None if d < 7.5 else (STITCH if abs(d - 9.5) < 0.5 and (x + y) % 3 == 0 else mix(LEATHER[3], LEATHER[0], (d - 7.5) / 4)))
    # lamps (56,104) off and (68,104) on, 12x12
    disc(62, 110, 5.5, lambda x, y, d: (90, 24, 20) if d > 4.5 else (60, 14, 12))
    disc(74, 110, 5.5, lambda x, y, d: (120, 30, 20) if d > 4.5 else mix((255, 220, 200), (255, 50, 30), min(1, d / 4)))
    save(img, "gui/gearshift")


def latch_remote():
    pal = {
        'o': (24, 26, 30), 'd': (44, 48, 54), 'b': (66, 72, 80), 'l': (92, 100, 110), 'h': (130, 138, 150),
        'g': CASING_GREEN[2], 'G': CASING_GREEN[4], 'a': STEEL[2], 'A': STEEL[4], 'k': (30, 32, 36),
        'y': GOLD[2], 'r': (90, 20, 18), 'R': (255, 70, 50), 'W': (255, 200, 180), 'c': (54, 58, 64),
    }
    base = [
        "..........oA....",
        "..........oa....",
        ".........oa.....",
        ".........oa.....",
        "....oooooaoo....",
        "...ohhhlllllo...",
        "...ohlrrlllbo...",
        "...olrLrlbbbo...",
        "...olbrrbbbbo...",
        "...oggggggggo...",
        "...olkcklkckdo..",
        "...olbbbbbbbdo..",
        "...olkcklkckdo..",
        "...olbbybbbbdo..",
        "....odddddddo...",
        ".....ooooooo....",
    ]
    base = [r[:16].ljust(16, '.') for r in base]
    off = [r.replace('L', 'r') for r in base]
    on = [r.replace('r', 'R').replace('L', 'W') for r in base]
    save(grid(off, pal), "item/latch_remote")
    save(grid(on, pal), "item/latch_remote_active")
    latch_remote_gui()


def mc_slot(img, x, y, ghost=False):
    rect(img, x, y, x + 18, y + 18, (139, 139, 139) if not ghost else (120, 128, 140))
    for i in range(18):
        img.putpixel((x + i, y), rgba((55, 55, 55)))
        img.putpixel((x, y + i), rgba((55, 55, 55)))
        img.putpixel((x + i, y + 17), rgba((255, 255, 255)))
        img.putpixel((x + 17, y + i), rgba((255, 255, 255)))


def latch_remote_gui():
    img = Image.new("RGBA", (256, 256), T)
    W, H = 176, 166
    rect(img, 0, 0, W, H, (198, 198, 198))
    for i in range(W):
        img.putpixel((i, 0), rgba((0, 0, 0)))
        img.putpixel((i, H - 1), rgba((0, 0, 0)))
        img.putpixel((i, 1), rgba((255, 255, 255)))
        img.putpixel((i, H - 2), rgba((85, 85, 85)))
    for j in range(H):
        img.putpixel((0, j), rgba((0, 0, 0)))
        img.putpixel((W - 1, j), rgba((0, 0, 0)))
        img.putpixel((1, j), rgba((255, 255, 255)))
        img.putpixel((W - 2, j), rgba((85, 85, 85)))
    # ghost slots at (62,30) and (98,30) (item position; slot frame is 1px larger)
    for gx in (61, 97):
        mc_slot(img, gx, 29, ghost=True)
    # signal waves between them
    for k, r in enumerate((3, 6, 9)):
        for a in range(-40, 41, 8):
            ang = math.radians(a)
            px = int(round(88 + r * math.sin(ang)))
            py = int(round(46 - r * math.cos(ang)))
            img.putpixel((px, py), rgba((80, 80, 80) if k else (200, 40, 30)))
    rect(img, 87, 44, 90, 50, (60, 60, 60))
    for row in range(3):
        for col in range(9):
            mc_slot(img, 7 + col * 18, 83 + row * 18)
    for col in range(9):
        mc_slot(img, 7 + col * 18, 141)
    save(img, "gui/latch_remote")


# ---------------------------------------------------------------- melatonia
STEM = {'s': (46, 92, 52), 'S': (70, 128, 70), 'k': (30, 64, 40), 'e': (94, 156, 88)}
BLOOM = {'p': (88, 70, 168), 'P': (128, 110, 214), 'q': (170, 156, 240), 'w': (226, 220, 255), 'y': (250, 236, 160),
         'v': (60, 46, 120), 'b': (104, 132, 220)}


def plant(rows, extra=None):
    pal = dict(STEM)
    pal.update(BLOOM)
    if extra:
        pal.update(extra)
    return grid(rows, pal)


def melatonia_textures():
    stage0 = [
        "................", "................", "................", "................",
        "................", "................", "................", "................",
        "................", "................", "................", "......e.........",
        ".....eSe..e.....", "......S..eS.....", "......s..s......", "......s..s......"]
    stage1 = [
        "................", "................", "................", "................",
        "................", "................", "........e.......", "...e...eS.......",
        "...Se...S..e....", "....S..sk.eS....", "....s.Ss..S.....", "..e.ss.s..s.....",
        "..SSs..s.Ss..e..", "...ks..s.s..eS..", "....s..s.s...s..", "....s..s.s...s.."]
    stage2 = [
        "................", "................", "................", "........v.......",
        "...v...vpv......", "..vpv...S...v...", "...S....s..vpv..", "...s...eS...S...",
        "..eS..eSs...s...", "...s...ks..eS...", "...sS..s..Ss....", "...s...s...s.e..",
        "..Ss..Ss...sSe..", "...k..s.e..s....", "...s..s.S..s....", "...s..s.s..s...."]
    stage3 = [
        "................", "..q.....P.......", ".qPq...PwP...q..", "qPyPq.PwywP.qPq.",
        ".qPq...PwP..PyP.", "..pS....S...qPq.", "...s...pS....S..", "..eS....s...eS..",
        ".eSs..eSs....s..", "...s...ks..eSs..", "...sS..s....s...", "...s...s...Ss.e.",
        "..Ss..Ss....sSe.", "...k..s.e...s...", "...s..s.S...s...", "...s..s.s...s..."]
    for i, rows in enumerate((stage0, stage1, stage2, stage3)):
        save(plant(rows), f"block/melatonia_stage{i}")
    wild = [
        "................", ".......q........", "......qPq.......", ".....qPwPq...q..",
        "..q...PyP...qPq.", ".qPq...S...qPwPq", "qPwPq..s....PyP.", ".PyP...s.....S..",
        "..S..e.s.....s..", "..s.eS.s..e..s..", "..sSs..s.eS.Ss..", "...s...sSs...s..",
        ".e.s...s.s..Ss..", ".SSs..Ss..s.s...", "..ks...s..sks...", "...s...s...s...."]
    save(plant(wild), "block/wild_melatonia")

    seeds = [
        "................", "................", "................", "................",
        "................", "......o.........", ".....oxo...o....", "......o...oxo...",
        "...o.......o....", "..oxo...o.......", "...o...oxo..o...", ".......wo..oxo..",
        "............o...", "................", "................", "................"]
    save(grid(seeds, {'o': (52, 44, 88), 'x': (110, 96, 170), 'w': (230, 226, 255)}), "item/melatonia_seeds")
    petals = [
        "................", "................", "................", "................",
        "......vv........", ".....vPPv..vv...", "....vPqqPvvPPv..", "....vPqwqPPqqPv.",
        ".....vPqPvPqwqv.", "..vv..vvv.vPqPv.", ".vPPv..vv..vvv..", "vPqqPvvPPv......",
        "vPqwqPPqqPv.....", ".vPqPvvPqPv.....", "..vvv..vvv......", "................"]
    save(grid(petals, BLOOM), "item/melatonia_petals")

    def bottle(liquid, light, dark, sparkle=None, cork=(140, 104, 64)):
        rows = [
            "................", "................", "......cccc......", "......cCCc......",
            ".......gg.......", ".......gg.......", "......g..g......", ".....gLLLLg.....",
            "....gLlllllg....", "....gllllllg....", "....gllllldg....", "....glllldddg...",
            "....gdlldddg....", ".....gddddg.....", "......gggg......", "................"]
        rows = [r[:16] for r in rows]
        rows[11] = "....gllldddg...."
        img = grid(rows, {'c': cork, 'C': mix(cork, (255, 255, 255), 0.25), 'g': (190, 210, 232), 'L': light,
                          'l': liquid, 'd': dark})
        img.putpixel((6, 8), rgba((255, 255, 255)))
        img.putpixel((6, 9), rgba(mix(liquid, (255, 255, 255), 0.6)))
        if sparkle:
            for (x, y) in ((9, 9), (7, 11), (10, 12)):
                img.putpixel((x, y), rgba(sparkle))
        return img

    save(bottle((128, 104, 210), (180, 166, 246), (86, 64, 160)), "item/melatonia_extract")
    save(bottle((84, 58, 178), (140, 120, 240), (46, 30, 116), sparkle=(255, 246, 190)), "item/concentrated_melatonia_extract")
    tea = bottle((196, 168, 214), (226, 208, 238), (150, 120, 176))
    for (x, y) in ((7, 3), (8, 2)):
        tea.putpixel((x, y), rgba(STEM['e']))
    save(tea, "item/drowsy_tea")

    cookie = [
        "................", "................", "................", ".....oooooo.....",
        "....obbbbbbo....", "...obbpbbbbbo...", "..obbbbbdbpbbo..", "..obdbbbbbbbbo..",
        "..obbbpbbbdbbo..", "..obbbbbbbbbbo..", "..obpbbdbbbpbo..", "...obbbbbbbbo...",
        "....obbbdbbo....", ".....oooooo.....", "................", "................"]
    save(grid(cookie, {'o': (120, 70, 36), 'b': (214, 150, 86), 'd': (96, 56, 32), 'p': BLOOM['P']}), "item/melatonia_cookie")
    stew = [
        "................", "................", "................", "................",
        "................", "...oooooooooo...", "..owwqsswqwwwo..", "..osPwwqwswPwo..",
        "..owwwPwwwqwwo..", "...bwwwwwwwwb...", "...obbbbbbbbo...", "....obbbbbbo....",
        ".....obbbbo.....", "......oooo......", "................", "................"]
    save(grid(stew, {'o': (72, 46, 26), 'b': (128, 84, 46), 'w': (170, 140, 196), 'q': (220, 206, 236),
                     's': (226, 160, 74), 'P': BLOOM['P']}), "item/sleepy_stew")

    # Effect icons (18x18)
    swirl = Image.new("RGBA", (18, 18), T)
    for i in range(240):
        a = i / 240 * 4.6 * math.pi
        r = 1.2 + a * 0.52
        x, y = 9 + r * math.cos(a), 9 + r * math.sin(a)
        if 0 <= x < 18 and 0 <= y < 18:
            swirl.putpixel((int(x), int(y)), rgba(mix((240, 230, 255), (120, 80, 210), min(1, a / 14))))
    save(swirl, "mob_effect/dizziness")
    zzz = Image.new("RGBA", (18, 18), T)
    cloud = [
        "...........XXXXX..", "..............X...", ".............X....", "............X.....",
        "...........XXXXX..", "......XXX.........", "........X.........", ".......X..........",
        "......XXX.........", "..................", "...wwww..wwww.....", "..wccccwwccccw....",
        ".wccccccccccccw...", "wccccccccccccccw..", "wccccccccccccccw..", ".wccccccccccccw...",
        "..wwwwwwwwwwww....", ".................."]
    zzz = grid(cloud, {'X': (230, 236, 255), 'w': (120, 132, 200), 'c': (176, 188, 240)})
    save(zzz, "mob_effect/sleepyness")


def misc_textures():
    vig = Image.new("RGBA", (256, 256), T)
    for y in range(256):
        for x in range(256):
            dx, dy = (x - 127.5) / 128, (y - 127.5) / 128
            d = (dx * dx + dy * dy) ** 0.5
            a = max(0.0, min(1.0, (d - 0.45) / 0.75)) ** 1.6
            vig.putpixel((x, y), (8, 6, 16, int(a * 235)))
    save(vig, "misc/dream_vignette")


GROUPS = [tuff_alloy, gearshift_textures, latch_remote, melatonia_textures, misc_textures]


def contact_sheet(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    scale = 8
    cols = 8
    cell = 16 * scale + 8
    items = [(rel, img) for rel, img in WRITTEN]
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (40, 40, 48, 255))
    for i, (rel, img) in enumerate(items):
        w, h = img.size
        s = max(1, (16 * scale) // max(w, h))
        if h > w * 2:  # animated strip: first frame
            img = img.crop((0, 0, w, w))
            h = w
        big = img.resize((w * s, h * s), Image.NEAREST)
        bg = Image.new("RGBA", big.size, (90, 90, 100, 255))
        for y in range(0, big.size[1], 8):
            for x in range(0, big.size[0], 8):
                if (x // 8 + y // 8) % 2 == 0:
                    for yy in range(y, min(y + 8, big.size[1])):
                        for xx in range(x, min(x + 8, big.size[0])):
                            bg.putpixel((xx, yy), (110, 110, 120, 255))
        bg.alpha_composite(big)
        sheet.paste(bg, ((i % cols) * cell + 4, (i // cols) * cell + 4))
    sheet.save(os.path.join(out_dir, "textures_sheet.png"))
    with open(os.path.join(out_dir, "textures_sheet.txt"), "w") as f:
        for i, (rel, _) in enumerate(items):
            f.write(f"{i}: {rel}\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="write a contact sheet of every texture into this directory")
    args = ap.parse_args()
    for g in GROUPS:
        g()
    print(f"wrote {len(WRITTEN)} textures")
    if args.preview:
        contact_sheet(args.preview)


if __name__ == "__main__":
    sys.exit(main())
