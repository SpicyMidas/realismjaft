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
import shutil
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


# Hand-made textures (tools/art/hand/, same layout as textures/, with any .png.mcmeta beside them) always win:
# the generator does not draw over them, and main() copies them in.
HAND = os.path.join(os.path.dirname(os.path.abspath(__file__)), "art", "hand")


def save(img, rel):
    if os.path.exists(os.path.join(HAND, rel + ".png")):
        return
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


# The new Tuff Alloy look (tools/art/tuff_alloy.png, hand-made): cool sage-grey highlights, blue-grey mids, dark olive
# edges. Every gearshift accent is taken from it.
ALLOY = {
    'out': (43, 45, 39), 'dark': (66, 69, 60), 'dmid': (78, 81, 71), 'mid': (148, 148, 139), 'blue': (108, 112, 112),
    'blue_l': (115, 117, 115), 'light': (187, 189, 177), 'hi': (226, 228, 218), 'olive': (90, 90, 80),
}


def tuff_alloy():
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "art", "tuff_alloy.png")
    save(Image.open(art).convert("RGBA"), "item/tuff_alloy")


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


# Create's casing frame (andesite funnel frame / gearshift / gearbox share it), pixel for pixel, recoloured from
# andesite grey into the Tuff Alloy palette. Letters: the same shades Create uses, '.' = the open centre.
CREATE_FRAME = [
    "ABBBCBCBBBCCBBBD", "BEFGFHFGHEEFHHEA", "BF............IA", "BG............EA",
    "BF............HA", "CH............EJ", "BF............IA", "BG............EA",
    "CH............EA", "BF............IA", "CF............EJ", "BH............EA",
    "BH............EA", "BE............IA", "BIEEEEIEEIEEEIIA", "DAAKAAAAKAAAAAAL"]
FRAME_COLORS = {'A': (78, 81, 71), 'B': (93, 95, 86), 'C': (108, 112, 112), 'D': (66, 69, 60), 'E': (133, 134, 124),
                'F': (166, 168, 156), 'G': (187, 189, 177), 'H': (148, 148, 139), 'I': (115, 117, 115), 'J': (85, 87, 76),
                'K': (70, 72, 63), 'L': (43, 45, 39)}
# Create's own gearshift side and gearbox face, pixel for pixel (Create 6.0.10: gearshift_off.png, gearbox.png). Their
# andesite greys become the alloy (CREATE_GREY -> FRAME_COLORS), Create's red lamp columns (x 2-3, 12-13) become tuff
# and the gearbox ring dark tuff. The wood is Create's.
CREATE_GEARSHIFT = [
    "505351 60635f 60635f 828784 412d15 755631 795933 966f3f 755631 795933 795933 412d15 828784 60635f 60635f 404543",
    "60635f 828784 9aa49d a8b3ab 50371c 70522e 82613a 966f3f 82613a 795933 755631 50371c 8f9691 8f9691 828784 505351",
    "60635f 9aa49d 2c0300 2c0300 553a1f 70522e 7a5a34 966f3f 553a1f 82613a 755631 553a1f 2c0300 2c0300 707570 505351",
    "60635f a8b3ab 400101 2c0300 50371c 614b2e 70522e 886539 614b2e 553a1f 886539 50371c 400101 2c0300 828784 505351",
    "60635f 9aa49d 470102 400101 50371c 70522e 7a5a34 886539 5a4424 614b2e 553a1f 50371c 470102 400101 8f9691 505351",
    "686c68 8f9691 580101 4b0101 50371c 82613a 70522e 886539 5a4424 82613a 70522e 50371c 580101 4b0101 828784 585b58",
    "60635f 9aa49d 4b0101 580101 553a1f 785836 70522e 886539 553a1f 785836 70522e 553a1f 4b0101 580101 707570 505351",
    "60635f a8b3ab 580101 470102 553a1f 70522e 70522e 966f3f 553a1f 70522e 70522e 553a1f 580101 470102 828784 505351",
    "686c68 8f9691 580101 580101 553a1f 70522e 795933 966f3f 553a1f 70522e 795933 553a1f 580101 580101 828784 505351",
    "60635f 9aa49d 580101 400101 50371c 70522e 70522e 966f3f 5a4424 70522e 795933 553a1f 580101 400101 707570 505351",
    "686c68 9aa49d 4b0101 580101 50371c 70522e 614b2e 966f3f 5a4424 70522e 7a5a34 553a1f 4b0101 580101 828784 585b58",
    "60635f 8f9691 400101 470102 50371c 966f3f 886539 966f3f 614b2e 7a5a34 785832 553a1f 400101 470102 828784 505351",
    "60635f 8f9691 2c0200 400101 50371c 70522e 966f3f 886539 5a4424 7a5a34 70522e 553a1f 2c0200 400101 828784 505351",
    "60635f 828784 2c0300 2c0300 553a1f 82613a 70522e 966f3f 553a1f 82613a 70522e 553a1f 2c0300 2c0300 707570 505351",
    "60635f 707570 828784 828784 50371c 805f38 775832 70522e 553a1f 805f38 775832 50371c 828784 707570 707570 505351",
    "404543 505351 505351 686c68 412d15 805f38 70522e 70522e 5a4424 805f38 70522e 412d15 60635f 505351 505351 353b39",
]
CREATE_GEARBOX = [
    "505351 60635f 60635f 60635f 686c68 60635f 686c68 60635f 60635f 60635f 686c68 686c68 60635f 60635f 60635f 404543",
    "60635f 828784 9aa49d a8b3ab 9aa49d 8f9691 9aa49d a8b3ab 8f9691 828784 828784 9aa49d 8f9691 8f9691 828784 505351",
    "60635f 9aa49d 46321c 46321c 4e361c 4e361c 46321c 46321c 46321c 46321c 46321c 4e361c 46321c 4e361c 707570 505351",
    "60635f a8b3ab 46321c 654928 70512c 654928 644828 593e22 593e22 644828 6f512b 654928 6f512b 553b1f 828784 505351",
    "60635f 9aa49d 46321c 654928 70522e 333131 424141 505050 505050 454444 333131 70522e 6f512b 4e361c 8f9691 505351",
    "686c68 8f9691 4e361c 654928 333131 3a3838 1a1919 030303 030303 1a1919 3a3838 333131 6f512b 553b1f 828784 585b58",
    "60635f 9aa49d 46321c 5d4224 424141 1a1919 000000 000000 000000 000000 1a1919 454444 644828 4e361c 707570 505351",
    "60635f a8b3ab 46321c 593e22 505050 030303 000000 000000 000000 000000 030303 505050 593e22 4e361c 828784 505351",
    "686c68 8f9691 46321c 593e22 505050 030303 000000 000000 000000 000000 030303 505050 593e22 4e361c 828784 505351",
    "60635f 9aa49d 46321c 654928 3a3838 1a1919 000000 000000 000000 000000 1a1919 454444 644828 553b1f 707570 505351",
    "686c68 9aa49d 46321c 6b4d2c 333131 3a3838 1a1919 030303 030303 1a1919 3a3838 383737 6f512b 593e22 828784 585b58",
    "60635f 8f9691 4e361c 6b4d2c 70522e 333131 3d3c3c 4e4e4e 505050 424141 333131 70522e 6b4d2c 593e22 828784 505351",
    "60635f 8f9691 46321c 6c4e2d 6c4e2d 654928 654928 5d4224 5d4224 6f512b 654928 6b4d2c 644828 5a3f23 828784 505351",
    "60635f 828784 4e361c 5a3f22 5d4222 593e22 5a3f23 4e361c 4e361c 5d4221 593e22 593e22 543b1f 5a3f22 707570 505351",
    "60635f 707570 828784 828784 828784 828784 707570 828784 828784 707570 828784 828784 828784 707570 707570 505351",
    "404543 505351 505351 494c4a 505351 505351 505351 505351 494c4a 505351 505351 505351 505351 505351 505351 353b39",
]
CREATE_GREY = {'353b39': 'L', '404543': 'D', '494c4a': 'K', '505351': 'A', '585b58': 'J', '60635f': 'B', '686c68': 'C',
               '707570': 'I', '828784': 'E', '8f9691': 'H', '9aa49d': 'F', 'a8b3ab': 'G'}
RING_TUFF = {'1a1919': (26, 27, 24), '333131': (52, 54, 48), '383737': (56, 58, 52), '3a3838': (58, 60, 54),
             '3d3c3c': (62, 64, 57), '424141': (66, 69, 60), '454444': (70, 72, 63), '4e4e4e': (77, 80, 72),
             '505050': (80, 83, 74)}
WOOD_LIGHT = (0x96, 0x6f, 0x3f)
# The small redstone light in each tuff strip, 2x2: off (Create's unlit lamp reds) and lit.
REDSTONE_OFF = [[(0x58, 1, 1), (0x4b, 1, 1)], [(0x47, 1, 2), (0x58, 1, 1)]]
REDSTONE_ON = [[(0xff, 0x52, 0x3a), (0xe0, 0x10, 0x08)], [(0xcd, 0, 0), (0xa5, 0, 0)]]
SOCKET = (36, 38, 33)
# The sport's mark: a thin dark-red pinline in the board's middle seam (two shades, so it reads as paint in the grain).
SPORT_STRIPE = ((128, 30, 22), (104, 24, 18))


def hexc(h):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def tuff_art():
    return Image.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "art", "tuff.png")).convert("RGBA")


def create_pixels(rows, extra=None):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, h in enumerate(row.split()):
            c = FRAME_COLORS[CREATE_GREY[h]] if h in CREATE_GREY else (extra or {}).get(h, hexc(h))
            img.putpixel((x, y), rgba(c))
    return img


def create_casing(interior):
    """Create's frame around an interior image (16x16, only its centre is used)."""
    img = interior.copy()
    for y, row in enumerate(CREATE_FRAME):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), rgba(FRAME_COLORS[ch]))
    return img


def gearshift_side(sport, on):
    """Create's gearshift side: alloy frame, Create's wooden board down the middle, tuff where Create has its lamps, and
    a small redstone light in each tuff strip that is lit while powered. The sport has a thin dark-red pinline in the board's seam."""
    img = create_pixels(CREATE_GEARSHIFT)
    tuff = tuff_art()
    for y in range(2, 14):
        for x in (2, 3, 12, 13):
            r, g, b, _ = tuff.getpixel((x, y))
            k = 0.62 if y in (2, 13) else 1.0    # the strip sits a little deeper, like Create's lamp
            img.putpixel((x, y), (int(r * k), int(g * k), int(b * k), 255))
    lamp = REDSTONE_ON if on else REDSTONE_OFF
    for x0 in (2, 12):
        for dx in (0, 1):
            img.putpixel((x0 + dx, 6), rgba(SOCKET))
            img.putpixel((x0 + dx, 9), rgba(SOCKET))
            for dy in (0, 1):
                img.putpixel((x0 + dx, 7 + dy), rgba(lamp[dy][dx]))
    if sport:
        for y in range(16):
            img.putpixel((8, y), rgba(SPORT_STRIPE[(y // 3) % 2]))
    return img


def gate_plate(sport, on):
    """The top: the side turned so the board runs across it (it wraps around the shaft axis, as on Create's), widened
    to the whole inside of the frame, with the gate cut into it. Regular and sport differ only in the number of
    columns. Must match TuffGearshiftRenderer: rail at x 7-8, odd gears and R toward x 12, even toward x 4; columns at
    z 4/8/12 (sport 3/6/10/13), all two texels wide."""
    top = gearshift_side(sport, on).rotate(90)
    # widen the board over the tuff strips: two more board rows each side, Create's plank pixels shifted along the grain
    for x in range(2, 14):
        for z, src, k in ((2, 9, 0.72), (3, 10, 1.0), (12, 5, 1.0), (13, 6, 0.72)):
            r, g, b, _ = top.getpixel(((x + 5) % 16, src))
            top.putpixel((x, z), (int(r * k), int(g * k), int(b * k), 255))
    groove, groove_dark = (46, 31, 14), (24, 16, 7)
    cols = [3, 6, 10, 13] if sport else [4, 8, 12]
    cells = set()

    def cut(x0, x1, z0, z1):
        for x in range(x0, x1):
            for z in range(z0, z1):
                cells.add((x, z))

    cut(7, 9, cols[0] - 1, cols[-1] + 1)                           # neutral rail along z
    for i, c in enumerate(cols):
        last = i == len(cols) - 1
        cut(7 if last else 3, 13, c - 1, c + 1)                    # odd gears and R reach x = 12, even gears x = 4
    stripe = {rgba(c) for c in SPORT_STRIPE}
    for (x, z) in cells:   # cut into the wood: dark brown, deeper in the shadow of the upper/left edge
        shadow = (x - 1, z) not in cells or (x, z - 1) not in cells
        top.putpixel((x, z), rgba(groove_dark if shadow else groove))
    for (x, z) in cells:   # a light wooden lip on the lower/right edge of every groove
        for (lx, lz) in ((x + 1, z), (x, z + 1)):
            if (lx, lz) not in cells and 2 <= lz <= 13 and 2 <= lx <= 13 and top.getpixel((lx, lz)) not in stripe:
                top.putpixel((lx, lz), rgba(WOOD_LIGHT))
    return top


def gearshift_textures():
    for sport in (False, True):
        for on in (False, True):
            suffix = ("_sport" if sport else "") + ("_on" if on else "")
            save(gearshift_side(sport, on), "block/gearshift/casing_side" + suffix)
            save(gate_plate(sport, on), "block/gearshift/gate_plate" + suffix)

    # Shaft faces, as Create builds them: the frame alone on the face, and the gearbox panel (wood, dark ring, black
    # hole) one pixel further in, so the end of the shaft stands out of it.
    save(create_casing(Image.new("RGBA", (16, 16), T)), "block/gearshift/casing_frame")
    save(create_pixels(CREATE_GEARBOX, RING_TUFF), "block/gearshift/casing_gearbox")

    # The stick: a plain rod, a small collar and a knob, one texel per model pixel (UVs in tools/gen_models.py), with a
    # little shading in the block's own alloy palette: light from above, darker underneath.
    knob_base = (52, 54, 47)
    rod = Image.new("RGBA", (16, 16), rgba(ALLOY['mid']))
    for y, c in enumerate((ALLOY['mid'], ALLOY['hi'], ALLOY['light'], FRAME_COLORS['F'], ALLOY['mid'])):
        rod.putpixel((7, y), rgba(c))                      # rod: only rows 0-3 show above the plate
    for (x, y), c in {(10, 0): FRAME_COLORS['B'], (11, 0): FRAME_COLORS['A'],       # collar sides
                      (10, 2): FRAME_COLORS['C'], (11, 2): FRAME_COLORS['B'],       # collar top
                      (10, 3): FRAME_COLORS['B'], (11, 3): FRAME_COLORS['A']}.items():
        rod.putpixel((x, y), rgba(c))
    save(rod, "block/gearshift/rod")

    def knob_texture(sides, top, bottom):
        img = Image.new("RGBA", (16, 16), rgba(bottom))
        for y, row in enumerate(sides):
            for x, c in enumerate(row):
                img.putpixel((6 + x, y), rgba(c))
        for y, row in enumerate(top):
            for x, c in enumerate(row):
                img.putpixel((6 + x, 6 + y), rgba(c))
        return img

    a = ALLOY
    save(knob_texture(sides=[(a['dmid'], FRAME_COLORS['B'], a['dmid']),
                             (a['dark'], knob_base, knob_base),
                             (knob_base, a['out'], a['out'])],
                      top=[(a['dmid'], a['dmid'], a['dark']),
                           (a['dmid'], FRAME_COLORS['B'], a['dark']),
                           (a['dark'], a['dark'], knob_base)],
                      bottom=a['out']), "block/gearshift/knob")
    red = SPORT_STRIPE[0]          # just a small dot on top, the same dark red as the pinline
    save(knob_texture(sides=[(a['hi'], a['hi'], a['light']),
                             (a['light'], a['light'], a['mid']),
                             (a['light'], a['mid'], a['mid'])],
                      top=[(a['hi'], a['hi'], a['light']),
                           (a['hi'], red, a['light']),
                           (a['light'], a['light'], a['mid'])],
                      bottom=a['mid']), "block/gearshift/knob_sport")

    frame = noise_fill(16, 16, [ALLOY['out'], (36, 38, 33), ALLOY['dark']], 909)
    save(frame, "block/gearshift/window_frame")
    # Gear window glyphs, 32x32 so the digit stays crisp on a 4x4 px window.
    for key, ch in (("n", 'N'), ("r", 'R'), ("1", '1'), ("2", '2'), ("3", '3'), ("4", '4'), ("5", '5'), ("6", '6')):
        color = (255, 196, 64) if ch == 'N' else (255, 72, 52) if ch == 'R' else (96, 255, 120)
        g = Image.new("RGBA", (32, 32), rgba((12, 13, 11)))
        bevel(g, 0, 0, 32, 32, FRAME_COLORS['C'], (4, 4, 4))
        dim = mix(color, (12, 13, 11), 0.75)
        draw_glyph(g, FONT5x7, ch, 9, 5, dim, 3)
        draw_glyph(g, FONT5x7, ch, 8, 5, color, 3)
        save(g, "block/gearshift/gear_" + key)

    gearshift_gui()


def wood_board(w, h, seed, vertical=True):
    """Planks in Create's wood colours: 8-10 px boards with a dark edge, grain streaks and the odd butt joint."""
    rnd = random.Random(seed)
    body = [hexc(c) for c in ("70522e", "755631", "795933", "785836", "7a5a34", "775832")]
    light = [hexc(c) for c in ("82613a", "886539", "966f3f")]
    dark = [hexc(c) for c in ("5a4424", "614b2e")]
    edge = [hexc(c) for c in ("50371c", "553a1f")]
    along, across = (h, w) if vertical else (w, h)
    img = Image.new("RGBA", (w, h))

    def put(a, s, c):
        img.putpixel((s, a) if vertical else (a, s), rgba(c))

    s = 0
    while s < across:
        pw = min(rnd.choice((8, 8, 9, 10)), across - s)
        joint = rnd.randrange(8, along - 8) if along > 24 and rnd.random() < 0.6 else -1
        for i in range(pw):
            base = rnd.choice(body)
            streak = rnd.random()
            for a in range(along):
                if i == 0:
                    c = edge[(a // 3) % 2]
                elif a == joint:
                    c = hexc("412d15")
                else:
                    c = base
                    if streak < 0.25 and (a // 7 + i) % 3:
                        c = light[(a // 5 + i) % 3]
                    elif 0.25 <= streak < 0.4 and (a // 9) % 2 == 0:
                        c = dark[(a // 4) % 2]
                    if rnd.random() < 0.05:
                        c = rnd.choice(light + dark)
                put(a, s + i, c)
        s += pw
    return img


def gearshift_gui():
    img = Image.new("RGBA", (256, 256), T)
    # The plate is the block's top: Create's wood board between tuff strips, in the alloy frame.
    tuff = tuff_art()
    plate = Image.new("RGBA", (144, 100))
    for y in range(100):
        for x in range(144):
            plate.putpixel((x, y), tuff.getpixel((x % 16, y % 16)))
    plate.paste(wood_board(112, 96, 77), (16, 2))
    for y in range(2, 98):      # dark seams where the board meets the tuff
        for x in (15, 128):
            r, g, b, _ = plate.getpixel((x, y))
            plate.putpixel((x, y), (int(r * 0.55), int(g * 0.55), int(b * 0.55), 255))
    bevel(plate, 0, 0, 144, 100, FRAME_COLORS['G'], FRAME_COLORS['L'])
    bevel(plate, 1, 1, 143, 99, FRAME_COLORS['F'], FRAME_COLORS['A'])
    # one small redstone light in the middle of each tuff strip, as on the block (static here; the readout has the live one)
    for x0 in (7, 135):
        rect(plate, x0 - 1, 46, x0 + 3, 54, SOCKET)
        rect(plate, x0, 48, x0 + 2, 52, REDSTONE_OFF[0][0])
    for (bx, by) in ((5, 5), (135, 5), (5, 91), (135, 91)):
        rect(plate, bx, by, bx + 4, by + 4, ALLOY['dark'])
        rect(plate, bx, by, bx + 3, by + 3, ALLOY['light'])
        plate.putpixel((bx + 1, by + 1), rgba(ALLOY['hi']))
    img.paste(plate, (0, 0))

    def disc(cx, cy, r, color_fn):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
                c = color_fn(x, y, d)
                if c is not None and d <= r:
                    img.putpixel((x, y), rgba(c))

    # regular knob (0,104): dark tuff with a pale alloy ring
    disc(8, 112, 7.5, lambda x, y, d: ALLOY['light'] if d > 6.3 else mix(ALLOY['dmid'], ALLOY['out'],
                                                                         min(1, ((x - 5) ** 2 + (y - 108) ** 2) ** 0.5 / 10)))
    # sport knob (16,104): the same knob in pale alloy with a small dark-red dot
    disc(24, 112, 7.5, lambda x, y, d: ALLOY['hi'] if d > 6.3 else mix(ALLOY['light'], ALLOY['mid'],
                                                                      min(1, ((x - 21) ** 2 + (y - 108) ** 2) ** 0.5 / 12)))
    for (x, y) in ((23, 111), (24, 111), (23, 112), (24, 112)):     # a small dark-red dot instead of a band
        img.putpixel((x, y), rgba(SPORT_STRIPE[0] if y == 111 else SPORT_STRIPE[1]))
    # slot ring (32,104) 24x24: the dark collar the stick comes out of
    disc(44, 116, 11.5, lambda x, y, d: None if d < 7.5 else mix(ALLOY['dmid'], ALLOY['out'], (d - 7.5) / 4))
    # lamps (56,104) off and (68,104) on, 12x12
    disc(62, 110, 5.5, lambda x, y, d: (90, 24, 20) if d > 4.5 else (60, 14, 12))
    disc(74, 110, 5.5, lambda x, y, d: (120, 30, 20) if d > 4.5 else mix((255, 220, 200), (255, 50, 30), min(1, d / 4)))
    # tuff tile (80,104) for the window body, and the wooden title board (0,132) 228x11
    img.paste(tuff, (80, 104))
    img.paste(wood_board(228, 11, 31, vertical=False), (0, 132))
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
    drift = Image.new("RGBA", (18, 18), T)
    rect(drift, 5, 2, 13, 3, (150, 110, 70))
    rect(drift, 5, 15, 13, 16, (150, 110, 70))
    for y in range(3, 15):
        w = int(abs(y - 8.5) * 0.7) + 1
        for x in range(9 - w, 9 + w):
            drift.putpixel((x, y), rgba((220, 236, 255)))
    for (x, y) in ((8, 13), (9, 13), (7, 14), (8, 14), (9, 14), (10, 14), (8, 4), (9, 5), (8, 7), (9, 10)):
        drift.putpixel((x, y), rgba((236, 214, 160)))
    save(drift, "mob_effect/drift")
    # Lucid Sight: an open eye with a pale cyan iris and a cut gem for a pupil.
    eye = [
        "..................", "..................", "..................", "..................",
        "......oooooo......", "....oowwwwwwoo....", "...owwwiiiiwwwo...", "..owwwiiggiiiwwo..",
        ".owwwiigddgiiwwwo.", ".owwwiigddgiiwwwo.", "..owwwiiggiiiwwo..", "...owwwiiiiwwwo...",
        "....oowwwwwwoo....", "......oooooo......", "..................", "..................",
        "..................", ".................."]
    save(grid(eye, {'o': (40, 60, 92), 'w': (232, 244, 255), 'i': (120, 220, 255), 'g': (196, 244, 255), 'd': (40, 120, 170)}),
         "mob_effect/lucid_sight")
    # Out Cold: a closed eye under a little ring of dizzy stars.
    ko = [
        "..................", "....y.....y.......", "...yYy...yYy..y...", "....y.....y..yYy..",
        "..............y...", "..................", "..................", "....oooooooooo....",
        "...o..........o...", "..o.lllllllll..o..", "...o..........o...", "....oo......oo....",
        "......oooooo......", "..................", "..................", "..................",
        "..................", ".................."]
    save(grid(ko, {'y': (255, 230, 120), 'Y': (255, 250, 210), 'o': (60, 44, 96), 'l': (150, 120, 200)}), "mob_effect/out_cold")


def misc_textures():
    vig = Image.new("RGBA", (256, 256), T)
    for y in range(256):
        for x in range(256):
            dx, dy = (x - 127.5) / 128, (y - 127.5) / 128
            d = (dx * dx + dy * dy) ** 0.5
            a = max(0.0, min(1.0, (d - 0.45) / 0.75)) ** 1.6
            vig.putpixel((x, y), (8, 6, 16, int(a * 235)))
    save(vig, "misc/dream_vignette")


# ---------------------------------------------------------------- dreamland
LUCID = [(76, 80, 92), (92, 97, 110), (106, 112, 126), (122, 128, 142), (140, 146, 160)]
SOIL = [(52, 40, 40), (64, 50, 48), (76, 60, 56), (90, 72, 64)]
DWOOD = [(70, 50, 56), (90, 66, 70), (110, 82, 84), (128, 98, 96)]


def grey(img):
    out = Image.new("RGBA", img.size)
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = img.getpixel((x, y))
            v = int(0.3 * r + 0.59 * g + 0.11 * b)
            out.putpixel((x, y), (v, v, v, a))
    return out


def dreamland_textures():
    # Grass: greyscale top/overlay for the position-drifting tint, pre-coloured bottom for upside-down islands.
    top = ramp_fill(16, 16, [(150, 150, 150), (172, 172, 172), (192, 192, 192), (214, 214, 214), (236, 236, 236)], 1501, cell=2, jitter=0.35)
    save(top, "block/dream_grass_block_top")
    soil = ramp_fill(16, 16, SOIL, 1502, cell=3, jitter=0.3)
    speckle(soil, [(104, 88, 78), (40, 30, 32)], 6, 1503)
    save(soil, "block/dream_soil")
    side = soil.copy()
    overlay = Image.new("RGBA", (16, 16), T)
    rnd = random.Random(1504)
    for x in range(16):
        h = 3 + rnd.randint(0, 2)
        for y in range(h):
            v = 236 - y * 18 + rnd.randint(-10, 10)
            overlay.putpixel((x, y), (v, v, v, 255))
    save(side, "block/dream_grass_block_side")
    save(overlay, "block/dream_grass_block_side_overlay")
    bottom = ramp_fill(16, 16, [(96, 112, 100), (110, 126, 112), (124, 138, 122), (138, 150, 132)], 1505, cell=2, jitter=0.35)
    save(bottom, "block/dream_grass_block_bottom")

    stone = ramp_fill(16, 16, LUCID, 1510, cell=4, jitter=0.2)
    speckle(stone, [(150, 168, 176), (48, 50, 60)], 7, 1511)
    save(stone, "block/lucid_stone")
    cob = Image.new("RGBA", (16, 16))
    rnd = random.Random(1512)
    for y in range(16):
        for x in range(16):
            cob.putpixel((x, y), rgba(LUCID[1]))
    for _ in range(18):
        cx, cy, r = rnd.randint(0, 15), rnd.randint(0, 15), rnd.randint(2, 3)
        shade = LUCID[rnd.randint(2, 4)]
        for y in range(cy - r, cy + r + 1):
            for x in range(cx - r, cx + r + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    cob.putpixel((x % 16, y % 16), rgba(shade))
        cob.putpixel(((cx + r) % 16, (cy + r) % 16), rgba(LUCID[0]))
    save(cob, "block/cobbled_lucid_stone")
    bricks = ramp_fill(16, 16, LUCID[1:], 1513, cell=2, jitter=0.15)
    for y in (0, 4, 8, 12):
        for x in range(16):
            bricks.putpixel((x, y), rgba(LUCID[0]))
    for row, y0 in enumerate((0, 4, 8, 12)):
        off = 0 if row % 2 == 0 else 4
        for x in range(off, 16, 8):
            for y in range(y0, y0 + 4):
                bricks.putpixel((x, y), rgba(LUCID[0]))
    save(bricks, "block/lucid_stone_bricks")

    ore = stone.copy()
    rnd = random.Random(1514)
    for _ in range(4):
        cx, cy = rnd.randint(2, 13), rnd.randint(2, 13)
        for (dx, dy, c) in ((0, 0, (226, 252, 250)), (1, 0, (120, 214, 212)), (0, 1, (76, 168, 176)), (-1, 0, (168, 236, 232)), (0, -1, (54, 128, 140))):
            ore.putpixel((cx + dx, cy + dy), rgba(c))
    save(ore, "block/lucid_crystal_ore")

    bark = [(64, 64, 72), (86, 86, 96), (108, 108, 118), (128, 126, 134)]
    log = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = bark[[1, 2, 1, 0, 1, 2, 3, 2, 1, 0, 1, 2, 2, 1, 0, 1][x]]
            log.putpixel((x, y), rgba(v))
    speckle(log, [(150, 148, 154), (44, 42, 50)], 6, 1515)
    save(log, "block/dreamwood_log")
    logtop = Image.new("RGBA", (16, 16), rgba(bark[0]))
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d < 6.5:
                logtop.putpixel((x, y), rgba((170, 160, 146) if int(d) % 2 == 0 else (140, 130, 120)))
    save(logtop, "block/dreamwood_log_top")
    planks = Image.new("RGBA", (16, 16))
    rnd = random.Random(1516)
    for y in range(16):
        for x in range(16):
            band = y // 4
            planks.putpixel((x, y), rgba(DWOOD[1 + (band + (x // 8 if band % 2 else 0)) % 3]))
        if y % 4 == 3:
            for x in range(16):
                planks.putpixel((x, y), rgba(DWOOD[0]))
    speckle(planks, [DWOOD[3]], 6, 1517)
    save(planks, "block/dreamwood_planks")
    leaves = Image.new("RGBA", (16, 16), T)
    rnd = random.Random(1518)
    for y in range(16):
        for x in range(16):
            if rnd.random() < 0.88:
                v = rnd.choice([196, 212, 226, 240, 252])
                leaves.putpixel((x, y), (v, v, v, 255))
    save(leaves, "block/dreamwood_leaves")

    cluster = Image.new("RGBA", (16, 16), T)
    for (x0, h, w) in ((3, 10, 2), (7, 15, 3), (12, 8, 2)):
        for y in range(16 - h, 16):
            for x in range(x0 - w // 2, x0 + (w + 1) // 2 + 1):
                if 0 <= x < 16:
                    t = (y - (16 - h)) / h
                    cluster.putpixel((x, y), rgba(mix((224, 250, 248), (54, 128, 140), t)))
    save(cluster, "block/lucid_crystal_cluster")

    cloud = ramp_fill(16, 16, [(176, 180, 190), (190, 194, 202), (204, 206, 212), (216, 218, 222)], 1520, cell=4, jitter=0.2)
    for y in range(16):
        for x in range(16):
            r, g, b, _ = cloud.getpixel((x, y))
            cloud.putpixel((x, y), (r, g, b, 214))
    save(cloud, "block/pillow_cloud")

    sand = ramp_fill(16, 16, [(150, 132, 100), (166, 148, 114), (180, 162, 128), (192, 176, 142)], 1521, cell=2, jitter=0.4)
    speckle(sand, [(122, 104, 76), (214, 202, 172)], 8, 1522)
    save(sand, "block/hourglass_sand")

    # Mirror Water: an animated slice of a sky that is not quite this one (a waxing crescent that never is).
    frames = 16
    mirror = Image.new("RGBA", (16, 16 * frames))
    rnd = random.Random(1523)
    stars = [(rnd.randint(0, 15), rnd.randint(0, 15)) for _ in range(9)]
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                t = y / 15
                c = mix((30, 40, 62), (62, 78, 104), t)
                wave = math.sin((x + f) * 0.8) * 6 + math.sin((y * 1.3 - f) * 0.7) * 5
                c = tuple(max(0, min(255, int(v + wave))) for v in c)
                mirror.putpixel((x, f * 16 + y), rgba(c))
        for i, (sx, sy) in enumerate(stars):
            if (i + f) % 5 != 0:
                mirror.putpixel((sx, f * 16 + sy), rgba((236, 240, 255)))
        for y in range(16):
            for x in range(16):
                d1 = ((x - 10.5) ** 2 + (y - 4.5) ** 2) ** 0.5
                d2 = ((x - 11.8) ** 2 + (y - 3.8) ** 2) ** 0.5
                if d1 < 2.6 and d2 > 2.2:
                    mirror.putpixel((x, f * 16 + y), rgba((250, 244, 214)))
    save(mirror, "block/mirror_water")
    meta = os.path.join(TEX, "block/mirror_water.png.mcmeta")
    with open(meta, "w") as fh:
        fh.write('{"animation": {"frametime": 4, "interpolate": true}}\n')

    # Decor parts
    lantern = Image.new("RGBA", (16, 16), T)
    rect(lantern, 0, 0, 16, 16, (60, 50, 76))
    rect(lantern, 2, 2, 14, 14, (255, 214, 150))
    rect(lantern, 4, 4, 12, 12, (255, 240, 200))
    bevel(lantern, 0, 0, 16, 16, (110, 96, 132), (36, 30, 48))
    save(lantern, "block/floating_lantern")
    clock = Image.new("RGBA", (16, 16), rgba((240, 230, 200)))
    for i in range(12):
        a = i * math.pi / 6
        clock.putpixel((int(round(7.5 + math.sin(a) * 6.3)), int(round(7.5 - math.cos(a) * 6.3))), rgba((80, 60, 40)))
    bevel(clock, 0, 0, 16, 16, GOLD[3], GOLD[1])
    save(clock, "block/melted_clock")
    save(Image.new("RGBA", (16, 16), rgba((30, 26, 34))), "block/clock_hand")
    save(Image.new("RGBA", (16, 16), rgba(GOLD[2])), "block/gilded")
    eye = Image.new("RGBA", (16, 16), T)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 7.6:
                eye.putpixel((x, y), rgba((240, 236, 246) if d < 6.2 else (196, 116, 196)))
    save(eye, "block/eyebloom_eye")
    pupil = Image.new("RGBA", (16, 16), rgba((24, 18, 40)))
    rect(pupil, 3, 3, 6, 6, (120, 90, 200))
    save(pupil, "block/eyebloom_pupil")
    stem = Image.new("RGBA", (16, 16), T)
    for y in range(16):
        stem.putpixel((7, y), rgba(STEM['s']))
        stem.putpixel((8, y), rgba(STEM['S']))
    for (x, y) in ((5, 10), (6, 11), (9, 8), (10, 9), (4, 9)):
        stem.putpixel((x, y), rgba(STEM['e']))
    save(stem, "block/eyebloom_stem")
    glow = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            a = int(110 + 60 * math.sin((x + y) * 0.6))
            glow.putpixel((x, y), (255, 246, 236, a))
    save(glow, "block/lone_door_glow")
    glass = Image.new("RGBA", (16, 16), (220, 236, 255, 90))
    bevel(glass, 0, 0, 16, 16, (255, 255, 255, 200), (170, 190, 220, 200))
    save(glass, "block/sandglass_glass")

    # Items
    shard = [
        "................", "................", "........o.......", ".......oho......", ".......ohlo.....",
        "......ohllo.....", "......ohlbo.....", ".....ohllbdo....", ".....ohlbbdo....", "....ohllbddo....",
        "....ohlbbdo.....", ".....olbddo.....", ".....obddo......", "......oddo......", ".......oo.......", "................"]
    save(grid(shard, {'o': (70, 50, 130), 'd': (120, 90, 210), 'b': (160, 130, 236), 'l': (204, 186, 252), 'h': (250, 244, 255)}), "item/lucid_shard")
    dust = Image.new("RGBA", (16, 16), T)
    rnd = random.Random(1530)
    for _ in range(40):
        x, y = int(8 + rnd.gauss(0, 2.6)), int(10 + rnd.gauss(0, 1.8))
        if 0 <= x < 16 and 0 <= y < 16:
            dust.putpixel((x, y), rgba(rnd.choice([(230, 220, 255), (196, 176, 244), (170, 150, 230), (255, 250, 220)])))
    save(dust, "item/dream_dust")
    door = [
        "....oooooooo....", "....oddddddo....", "....odwwwwdo....", "....odwhhwdo....", "....odwhhwdo....",
        "....odwwwwdo....", "....odwhhwdo....", "....odwhhwdo....", "....odwwwwdo....", "....odwhhwdo....",
        "....odwhhwdo....", "....odwwwwdo....", "....odwhhwdo....", "....odwwwwdo....", "...oddddddddo...", "...oooooooooo..."]
    save(grid(door, {'o': (70, 40, 70), 'd': DWOOD[1], 'w': (250, 244, 236), 'h': (255, 255, 255)}), "item/lone_door")


def paper_glider_texture():
    """A folded paper glider seen from above, with a lilac fold stripe."""
    rows = [
        "................", "................", ".......w........", "......wWw.......",
        ".....wWfWw......", "....wWWfWWw.....", "...wWWWfWWWw....", "..wWWWWfWWWWw...",
        ".wWWWWWfWWWWWw..", "wWWWWWWfWWWWWWw.", ".ooooowfwooooo..", "......wfw.......",
        "......wfw.......", "......oso.......", ".......s........", "................"]
    save(grid(rows, {'w': (214, 208, 196), 'W': (240, 236, 226), 'f': (176, 150, 214), 'o': (150, 144, 136),
                     's': (124, 96, 66)}), "item/paper_glider")
    # The 3D glider's paper (models/item/paper_glider_3d.json, one texel per model pixel): warm white sheet with a
    # little grain, a lilac fold down the middle (columns 7-8) and faint creases where the wing strips meet.
    rnd = random.Random(4242)
    sheet = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = (240, 236, 226) if rnd.random() > 0.18 else (228, 223, 212)
            if x in (7, 8):
                c = (196, 178, 228) if x == 7 else (176, 150, 214)
            elif x in (2, 5, 10, 13):
                c = (214, 208, 196)
            sheet.putpixel((x, y), rgba(c))
    save(sheet, "item/paper_glider_sheet")
    save(Image.new("RGBA", (16, 16), T), "item/empty")


# ---------------------------------------------------------------- melatonia cake
# Lavender frosting, a golden sponge, lavender cream and a petal-jam line; the bloom and petals use the Melatonia
# plant's own colours (BLOOM). Shading is placed by hand-written rules rather than noise, in the manner of vanilla's
# cake and Farmer's Delight's pies: light from the upper left, darker tones of each colour at the edges.
CAKE = {
    'H': (248, 243, 254), 'F': (231, 219, 248), 'f': (209, 191, 238), 'd': (178, 156, 220), 'D': (146, 122, 196),
    'L': (228, 170, 100), 'S': (204, 140, 78), 's': (174, 112, 60), 'b': (138, 86, 46), 'B': (104, 62, 34),
    'C': (216, 200, 246), 'c': (190, 170, 234), 'J': (112, 88, 196), 'j': (84, 64, 156),
    'P': BLOOM['P'], 'p': BLOOM['q'], 'v': BLOOM['v'], 'y': BLOOM['y'], 'w': BLOOM['w'],
}


def _put(img, x, y, key):
    if 0 <= x < img.size[0] and 0 <= y < img.size[1]:
        img.putpixel((x, y), rgba(CAKE[key]))


def _bloom(img, cx, cy):
    """A tiny Melatonia bloom: four petals round a pale yellow heart, lit from the upper left."""
    for (dx, dy, k) in ((0, -1, 'p'), (-1, 0, 'p'), (1, 0, 'P'), (0, 1, 'P'), (-1, -1, 'p'), (1, 1, 'v'), (1, -1, 'P'), (-1, 1, 'P')):
        _put(img, cx + dx, cy + dy, k)
    _put(img, cx, cy, 'y')


def _petal(img, x, y):
    _put(img, x, y, 'P')
    _put(img, x - 1, y, 'p')


def melatonia_cake_textures():
    """The Melatonia Cake in vanilla's cake layout: top (the cake is 1..14), side and cut face (the cake in rows 8-15),
    bottom; plus the cake and slice icons."""
    # --- top: soft light from the upper left, a ring of piped dollops, a bloom in the middle, scattered petals
    top = Image.new("RGBA", (16, 16), rgba(CAKE['F']))
    for y in range(16):
        for x in range(16):
            light = ((x - 4.5) ** 2 + (y - 4.5) ** 2) ** 0.5
            k = 'H' if light < 3.2 else 'F' if light < 9.5 else 'f'
            if k == 'F' and light > 8.2 and (x + y) % 2 == 0:
                k = 'f'                                  # dithered falloff toward the lower right
            if k == 'H' and light > 2.4 and (x + y) % 2 == 1:
                k = 'F'
            _put(top, x, y, k)
    for i in range(1, 15):                               # rim
        for (x, y) in ((i, 1), (1, i)):
            _put(top, x, y, 'f')
        for (x, y) in ((i, 14), (14, i)):
            _put(top, x, y, 'd')
    for i in (2, 5, 8, 11):                              # piped dollops along the rim: lit top-left, shadow bottom-right
        for (x, y, horizontal) in ((i, 2, True), (2, i, False), (i, 12, True), (12, i, False)):
            far = x >= 12 or y >= 12
            dx, dy = (1, 0) if horizontal else (0, 1)
            _put(top, x, y, 'F' if far else 'H')
            _put(top, x + dx, y + dy, 'f' if far else 'F')
            _put(top, x + 1, y + 1, 'd')
            if horizontal:
                _put(top, x + 2, y + 1, 'd' if far else 'f')
            else:
                _put(top, x + 1, y + 2, 'd' if far else 'f')
    _bloom(top, 7, 7)
    for (x, y) in ((5, 4), (10, 5), (4, 9), (11, 10), (8, 11), (10, 3)):
        _petal(top, x, y)
    save(top, "block/melatonia_cake_top")

    # --- side: frosting band with drips over the sponge, a cream layer, darker baked base
    drips = [0, 1, 2, 1, 0, 0, 1, 2, 1, 0, 1, 0, 0, 1, 2, 1]
    def sponge(x, y):
        # crumb: a lighter fleck above a darker one, in a staggered pattern (not noise)
        if (x * 3 + y * 5) % 7 == 0:
            return 's'
        if (x * 3 + (y + 1) * 5) % 7 == 0:
            return 'L'
        return 'S'

    side = Image.new("RGBA", (16, 16), T)
    for x in range(16):
        n = drips[x]
        for y in range(8, 16):
            if y == 8:
                k = 'H' if x % 4 else 'F'
            elif y == 9:
                k = 'F'
            elif y < 10 + n:
                k = 'F' if y < 9 + n else 'f'            # drip body, its tip a shade darker
            elif y == 10 + n and n > 0 and y < 12:
                k = 'd'                                  # the shadow just under a drip
            elif y == 12:
                k = 'J' if x % 5 else 'j'
            elif y == 15:
                k = 'b' if x % 3 else 'B'
            elif y == 14:
                k = 's' if (x + y) % 2 else sponge(x, y)
            else:
                k = sponge(x, y)
            _put(side, x, y, k)
    save(side, "block/melatonia_cake_side")

    # --- cut face: frosting cut through, airy sponge, cream with a petal-jam line under it
    inner = Image.new("RGBA", (16, 16), T)
    for x in range(16):
        for y in range(8, 16):
            if y == 8:
                k = 'F'
            elif y == 9:
                k = 'f'
            elif y == 11:
                k = 'C' if x % 6 else 'c'
            elif y == 12:
                k = 'J' if x % 4 else 'j'
            elif y == 15:
                k = 's' if x % 2 else 'b'
            else:
                k = 'L' if (x * 5 + y * 3) % 6 == 0 else 's' if (x * 5 + (y - 1) * 3) % 6 == 0 else 'S'
            _put(inner, x, y, k)
    for x in (3, 9, 13):
        _put(inner, x, 12, 'P')                          # bits of petal in the jam
    save(inner, "block/melatonia_cake_inner")

    # --- bottom: the baked base
    bottom = Image.new("RGBA", (16, 16))
    rnd = random.Random(2211)
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            k = 's' if r < 0.3 else 'L' if r < 0.38 else 'S'
            if x in (1, 14) or y in (1, 14):
                k = 'b' if r < 0.7 else 's'
            _put(bottom, x, y, k)
    save(bottom, "block/melatonia_cake_bottom")

    save(_cake_icon(), "item/melatonia_cake")
    save(_slice_icon(), "item/melatonia_cake_slice")


def _cake_icon():
    """A round cake seen from the front and a little above (vanilla's icon pose): an oval frosted top, drips over a
    curved sponge side with the cream layer, every edge shaded with a darker tone of its own colour."""
    icon = Image.new("RGBA", (16, 16), T)
    cx, cy, rx, ry, height = 7.5, 5.0, 7.5, 3.6, 6
    region = {}
    for y in range(16):
        for x in range(16):
            u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            if u * u + v * v <= 1.0:
                region[(x, y)] = ('top', u, v)
            elif abs(u) <= 1.0:
                low = cy + ry * (1 - u * u) ** 0.5
                if low <= y + 0.5 < low + height:
                    region[(x, y)] = ('side', u, int(y + 0.5 - low))
    drips = {1: 1, 3: 2, 4: 1, 7: 1, 8: 2, 11: 1, 13: 2, 14: 1}
    for (x, y), (kind, u, w) in region.items():
        if kind == 'top':
            light = (u + 0.55) ** 2 + (w + 0.45) ** 2
            k = 'H' if light < 0.35 else 'F' if light < 1.25 else 'f'
            if u * u + w * w > 0.72:
                k = 'f' if (w < 0.2 and u < 0.3) else 'd'    # the rim, darker along the front edge
        else:
            d = w
            drip = drips.get(x, 0)
            if d == 0 or d <= drip:
                k = 'F' if d < drip or d == 0 else 'f'
                if u > 0.55:
                    k = 'f' if k == 'F' else 'd'
            elif d == 3:
                k = 'J' if u < 0.55 else 'j'
            elif d == height - 1:
                k = 'b' if u < 0.6 else 'B'
            else:
                k = 'L' if u < -0.6 else 'S' if u < 0.45 else 's'
        _put(icon, x, y, k)
    # edges: the outermost pixels take a darker tone of their own colour
    darker = {'H': 'f', 'F': 'f', 'f': 'd', 'd': 'D', 'C': 'c', 'c': 'c', 'L': 'S', 'S': 's', 's': 'b', 'b': 'B', 'B': 'B'}
    inv = {v: k for k, v in CAKE.items()}
    src = icon.copy()
    for (x, y) in region:
        if any((nx, ny) not in region for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))):
            key = inv.get(src.getpixel((x, y))[:3])
            if key in darker:
                _put(icon, x, y, darker[key])
    _bloom(icon, 7, 4)
    for (x, y) in ((4, 3), (11, 5), (4, 6), (10, 2)):
        _petal(icon, x, y)
    return icon


def _slice_icon():
    """A wedge, point to the left: frosted top with a bloom, the cut face showing sponge, cream and petal jam."""
    rows = [
        "................",
        "................",
        "................",
        "...........ff...",
        ".........ffHFd..",
        ".......ffHFpPd..",
        ".....ffHFFPyPd..",
        "...ffHFFFFFPvd..",
        ".ffFFFFpPFFFfd..",
        "fFFFFFFFFFFffdd.",
        "dFfLFfLSFfSfdfd.",
        "bLSSSSLSSSSSsfd.",
        "bCCCCCCCCCCCcdd.",
        "bJJJPJJJJPJJjjd.",
        "bsSSSsSSSsSSsbd.",
        ".bbbbbbbbbbbbBB.",
    ]
    return grid(rows, {k: v for k, v in CAKE.items()})


def fauna_textures():
    """Lucid Moth: wings up, wings down, and a soft glow cell (48x16). A snore Z particle."""
    import math as m
    img = Image.new("RGBA", (48, 16), T)
    body, wing, edge = (40, 36, 70), (150, 220, 255), (220, 248, 255)
    for frame, spread in ((0, 1.0), (1, 0.45)):
        ox = frame * 16
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 8.0
                # body: a thin vertical oval
                if abs(dx) < 1.2 and abs(dy) < 4.5:
                    img.putpixel((ox + x, y), rgba(body))
                    continue
                # wings: two rounded lobes each side, narrower when folded
                for sy, rw in ((-2.2, 6.2), (2.4, 4.6)):
                    wx = abs(dx) / (rw * spread + 0.6)
                    wy = (dy - sy) / 2.6
                    if wx * wx + wy * wy < 1 and abs(dx) >= 1.2:
                        c = edge if wx * wx + wy * wy > 0.6 else wing
                        img.putpixel((ox + x, y), rgba(c, 235))
        img.putpixel((ox + 6, 3), rgba(body))
        img.putpixel((ox + 9, 3), rgba(body))
    for y in range(16):
        for x in range(16):
            d = m.hypot(x - 7.5, y - 7.5) / 7.5
            if d < 1:
                v = (1 - d) ** 2
                img.putpixel((32 + x, y), (int(120 * v), int(200 * v), int(255 * v), 255))
    save(img, "entity/lucid_moth")
    z = [
        "........", ".zzzzz..", "....z...", "...z....", "..z.....", ".zzzzz..", "........", "........"]
    save(grid(z, {'z': (236, 232, 255)}), "particle/dream_z_0")


def paper_whale_texture():
    """A paper whale: pale folded paper, a darker fold line, a tiny eye. Faces right."""
    import math as m
    img = Image.new("RGBA", (64, 32), T)
    for x in range(64):
        t = x / 63.0
        # body: thick at the head (right), tapering to the tail flukes (left)
        half = 9 * m.sin(min(1, t * 1.3) * m.pi * 0.5) * (1 - max(0, t - 0.85) * 3)
        cy = 16 + m.sin(t * 3.0) * 1.5
        for y in range(32):
            if abs(y - cy) <= half:
                fold = abs(y - (cy + half * 0.15)) < 0.6
                facet = (int(x / 7) + (1 if y < cy else 0)) % 2
                c = (214, 210, 226) if facet else (236, 232, 244)
                if fold:
                    c = (170, 164, 190)
                img.putpixel((x, y), rgba(c, 230))
    for (x, y) in ((2, 10), (3, 11), (4, 12), (2, 21), (3, 20), (4, 19), (5, 13), (5, 18)):
        img.putpixel((x, y), rgba((222, 218, 234), 230))
    img.putpixel((52, 14), rgba((60, 50, 90)))
    save(img, "environment/paper_whale")


def dream_catcher_texture():
    """A willow hoop with a web, a lucid shard at its heart and two feathers."""
    rows = [
        "................", ".....hhhhhh.....", "....hw.ww.wh....", "...hw..w..wwh...",
        "...h.w.w.w..h...", "...hw.wLw.w.h...", "...h..LlL...h...", "...hw.wLw.w.h...",
        "...h.w.w.w..h...", "....hw.w.wwh....", ".....hhhhhh.....", ".....s....s.....",
        ".....s....s.....", "....fF....fF....", "....fF....fF....", ".....f.....f....",
    ]
    save(grid(rows, {'h': (124, 86, 52), 'w': (226, 222, 236), 'L': (120, 196, 236), 'l': (220, 246, 255),
                     's': (200, 196, 210), 'f': (236, 232, 244), 'F': (170, 150, 220)}), "item/dream_catcher")


def particle_textures():
    """Dream petals (three tints), paper pages and an upward raindrop streak."""
    petal = [
        "........", "...pp...", "..pPPp..", ".pPqqPp.", ".pPqqPp.", "..pPPp..", "...pp...", "........"]
    for i, a in enumerate(((128, 110, 214), (226, 150, 196), (170, 156, 240))):
        save(grid(petal, {'p': mix(a, (40, 30, 80), 0.25), 'P': a, 'q': mix(a, (255, 255, 255), 0.45)}), f"particle/dream_petal_{i}")
    page = [
        "........", ".wwwwww.", ".wllllw.", ".wwwwww.", ".wllllw.", ".wwwwww.", ".wllwww.", "........"]
    save(grid(page, {'w': (236, 230, 214), 'l': (150, 140, 170)}), "particle/dream_page_0")
    page2 = [r[::-1] for r in page]
    save(grid(page2, {'w': (222, 216, 236), 'l': (130, 120, 160)}), "particle/dream_page_1")
    drop = [
        "...ww...", "...wb...", "...bb...", "...bb...", "...b....", "...b....", "........", "........"]
    save(grid(drop, {'w': (230, 240, 255), 'b': (150, 180, 240)}), "particle/dream_raindrop_0")


def sky_textures():
    """Dreamland sky: a moon atlas (full, gibbous, half, crescent), a ringed planet and the Eye (4 frames)."""
    import math as m
    moon = Image.new("RGBA", (64, 16), T)
    craters = value_noise(16, 16, 4242, cell=3)
    for k in range(4):
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                if dx * dx + dy * dy > 7.2 * 7.2:
                    continue
                lit = True
                if k == 1:
                    lit = (dx + 11) ** 2 + dy * dy > 7.4 * 7.4
                elif k == 2:
                    lit = dx > 0
                elif k == 3:
                    lit = (dx + 3.2) ** 2 + dy * dy > 7.2 * 7.2
                c = mix((236, 230, 214), (186, 178, 196), craters[y][x] * 0.8)
                moon.putpixel((k * 16 + x, y), rgba(c) if lit else (60, 58, 110, 70))
    save(moon, "environment/dream_moon")

    planet = Image.new("RGBA", (64, 32), T)
    ring = lambda x, y: ((x - 31.5) / 29.0) ** 2 + ((y - 15.5) / 6.5) ** 2
    def ring_px(x, y):
        r = ring(x, y)
        if 0.72 < r < 1.0:
            return rgba(mix((238, 214, 170), (196, 170, 222), (r - 0.72) / 0.28), 200)
        return None
    for y in range(32):
        for x in range(64):
            if y < 16 and ring_px(x, y):
                planet.putpixel((x, y), ring_px(x, y))
    for y in range(32):
        for x in range(64):
            dx, dy = x - 31.5, y - 15.5
            if dx * dx + dy * dy <= 11.5 * 11.5:
                band = 0.5 + 0.5 * m.sin(dy * 0.9 + m.sin(dx * 0.3) * 0.8)
                shade = max(0.45, 1 - (dx + dy) / 30.0)
                c = mix((180, 140, 210), (240, 196, 176), band)
                planet.putpixel((x, y), rgba(tuple(int(v * shade) for v in c)))
    for y in range(16, 32):
        for x in range(64):
            if ring_px(x, y):
                planet.putpixel((x, y), ring_px(x, y))
    save(planet, "environment/dream_planet")

    eye = Image.new("RGBA", (64, 128), T)
    lid = (30, 22, 46)
    for k in range(4):
        oy = k * 32
        openness = [0.0, 0.25, 0.6, 1.0][k]
        for x in range(4, 60):
            t = (x - 32) / 28.0
            curve = (1 - t * t)
            top = 16 - curve * 11 * openness
            bot = 16 + curve * 7 * max(openness, 0.12)
            for y in range(32):
                if top <= y <= bot and openness > 0:
                    dx, dy = x - 32, y - 16
                    d = m.hypot(dx, dy * 1.1)
                    if d < 2.6:
                        c = (8, 4, 16, 255)
                    elif d < 7.5:
                        c = rgba(mix((120, 70, 190), (60, 30, 110), d / 7.5))
                    else:
                        c = rgba((206, 196, 230), 235)
                    eye.putpixel((x, oy + y), c)
            # lids and lashes
            for (yy, w) in ((top, 1), (bot, 1)):
                iy = int(round(yy))
                if 0 <= iy < 32:
                    eye.putpixel((x, oy + iy), rgba(lid))
            if x % 5 == 0 and curve > 0.2:
                for l in range(1, 4):
                    ly = int(round(bot + l)) if openness == 0 else int(round(top - l))
                    lx = x + (l if t > 0 else -l) // 2
                    if 0 <= ly < 32 and 0 <= lx < 64:
                        eye.putpixel((lx, oy + ly), rgba(lid))
        if openness == 1.0:
            eye.putpixel((34, oy + 13), rgba((255, 255, 255)))
            eye.putpixel((35, oy + 13), rgba((255, 255, 255)))
    save(eye, "environment/dream_eye")


def fd_textures():
    """Farmer's Delight dishes (only used when FD is installed)."""
    P = BLOOM['P']
    crust = {'o': (112, 66, 34), 'c': (204, 142, 74), 'C': (236, 184, 110)}
    stew = [
        "................", "................", "................", "................",
        "................", "...oooooooooo...", "..owwqmwwPqwwo..", "..omPwwqwmwPwo..",
        "..owwwPwmwqwwo..", "...bwwwwwwwwb...", "...obbbbbbbbo...", "....obbbbbbo....",
        ".....obbbbo.....", "......oooo......", "................", "................"]
    save(grid(stew, {'o': (72, 46, 26), 'b': (128, 84, 46), 'w': (84, 72, 162), 'q': (150, 140, 222),
                     'm': (156, 96, 64), 'P': P}), "item/lullaby_stew")
    dumplings = [
        "................", "................", "................", "................",
        "......oooo......", ".....oddddo.....", "....odpdpddo....", "....oddddddo....",
        ".oooooddddooooo.", "oddddoooooodddo.", "odpdpddo.odpdpdo", "oddddddo.oddddo.",
        ".oooooo...oooo..", "................", "................", "................"]
    save(grid(dumplings, {'o': (150, 128, 150), 'd': (238, 228, 238), 'p': (186, 150, 222)}), "item/dozing_dumplings")
    pie = [
        "................", "................", "................", "................",
        "................", "....oooooooo....", "..ooCcCcCcCcoo..", ".oCfPfcfPfcfPCo.",
        ".ocfcfPfcfcfPco.", ".oCPfcfcfPfcfCo.", "..oCcCcCcCcCco..", "..occcccccccco..",
        "...oooooooooo...", "................", "................", "................"]
    pal = dict(crust, f=(170, 140, 230), P=P)
    save(grid(pie, pal), "item/dreamy_pie")
    slice_ = [
        "................", "................", "................", "................",
        "................", "................", "........oo......", "......ooCo......",
        "....ooCffCo.....", "..ooCfPffCo.....", ".oCffffPfCo.....", ".occcccccco.....",
        "..oooooooo......", "................", "................", "................"]
    save(grid(slice_, pal), "item/dreamy_pie_slice")
    roll = [
        "................", "................", "................", "................",
        "................", "....kkkk...kkkk.", "...kwwwwk.kwwwwk", "...kwPPwk.kwPPwk",
        "...kwPPwk.kwPPwk", "...kwwwwk.kwwwwk", "....kkkk...kkkk.", "................",
        "................", "................", "................", "................"]
    save(grid(roll, {'k': (34, 52, 40), 'w': (240, 236, 226), 'P': P}), "item/night_bloom_roll")
    syrup = [
        "................", "................", "......cccc......", "......cCCc......",
        ".......gg.......", ".......gg.......", "......g..g......", ".....gLLLLg.....",
        "....gLlllllg....", "....gllllllg....", "....gllllldg....", "....gllldddg....",
        "....gdlldddg....", ".....gddddg.....", "......gggg......", "................"]
    img = grid(syrup, {'c': (140, 104, 64), 'C': (176, 140, 102), 'g': (190, 210, 232), 'L': (236, 186, 220),
                       'l': (204, 120, 186), 'd': (140, 70, 140)})
    img.putpixel((6, 8), rgba((255, 255, 255)))
    save(img, "item/melatonia_syrup")

    top = Image.new("RGBA", (16, 16), T)
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            lattice = (x + y) % 4 == 0 or (x - y) % 4 == 0
            c = crust['c'] if edge else (crust['C'] if lattice else (170, 140, 230))
            top.putpixel((x, y), rgba(c))
    for (x, y) in ((5, 4), (9, 6), (6, 9), (11, 10), (4, 11)):
        top.putpixel((x, y), rgba(P))
    save(top, "block/dreamy_pie_top")
    inner = Image.new("RGBA", (16, 16), T)
    for y in range(12, 16):
        for x in range(2, 14):
            c = crust['C'] if y == 12 else (crust['c'] if y == 15 else ((150, 116, 214) if (x + y) % 5 else P))
            inner.putpixel((x, y), rgba(c))
    save(inner, "block/dreamy_pie_inner")


def shade_textures():
    """Shade silhouette: near-black with a faint violet grain (so it is not a hole in the world), and a pale
    corner used only by the pinprick-eye boxes."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    n = value_noise(64, 64, 9101, cell=4)
    for y in range(64):
        for x in range(64):
            v = n[y][x]
            img.putpixel((x, y), rgba(mix((6, 4, 10), (20, 14, 30), v * 0.7)))
    rect(img, 56, 0, 64, 8, rgba((236, 232, 255)))
    save(img, "entity/shade")


GROUPS = [melatonia_cake_textures, tuff_alloy, gearshift_textures, latch_remote, melatonia_textures, misc_textures, dreamland_textures, shade_textures, fd_textures, sky_textures, particle_textures, dream_catcher_texture, paper_whale_texture, fauna_textures, paper_glider_texture]


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
    hand = 0
    for folder, _, files in os.walk(HAND):
        for f in files:
            src = os.path.join(folder, f)
            dst = os.path.join(TEX, os.path.relpath(src, HAND))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)
            hand += 1
    print(f"wrote {len(WRITTEN)} textures, copied {hand} hand-made files")
    if args.preview:
        contact_sheet(args.preview)


if __name__ == "__main__":
    sys.exit(main())
