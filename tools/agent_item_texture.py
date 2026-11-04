import sys
from PIL import Image

PAL = {
    '.': (0, 0, 0, 0),
    'o': (92, 94, 102, 255),    # outline (light-grey metal, darkest)
    'd': (146, 148, 156, 255),  # dark
    'b': (196, 198, 204, 255),  # base
    'l': (222, 224, 228, 255),  # light
    'h': (242, 243, 245, 255),  # highlight
    'r': (196, 92, 52, 255),    # rust antenna tip
    'k': (24, 24, 30, 255),     # lens ring
    'g': (28, 62, 128, 255),    # glass dark
    'G': (52, 112, 196, 255),   # glass
    'c': (120, 176, 236, 255),  # glass light
    'w': (236, 246, 255, 255),  # sparkle
}

GRID = [
    "................",
    "......oro.......",
    ".......o........",
    "..oooooooooooo..",
    ".ohhhllllllllbo.",
    ".ohlllkkkkllbdo.",
    ".ohllkcwGGkbbdo.",
    ".olbkcwGGGgkbdo.",
    ".olbkcGGGGgkbdo.",
    ".olbkGGGGggkbdo.",
    ".obbkgGGgggkddo.",
    ".obbbkggggkbddo.",
    ".obbbbkkkkbbddo.",
    ".oddddddddddddo.",
    "..oooooooooooo..",
    "................",
]

out, preview = sys.argv[1], sys.argv[2]
size = len(GRID)
assert all(len(r) == size for r in GRID), [len(r) for r in GRID]
img = Image.new("RGBA", (size, size))
for y, row in enumerate(GRID):
    for x, ch in enumerate(row):
        img.putpixel((x, y), PAL[ch])
img.save(out)
img.resize((size * 16, size * 16), Image.NEAREST).save(preview)
