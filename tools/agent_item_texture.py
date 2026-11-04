import sys
from PIL import Image

PAL = {
    '.': (0, 0, 0, 0),
    'o': (38, 40, 46, 255),     # outline (iron, darkest)
    'd': (88, 92, 100, 255),    # dark
    'b': (132, 137, 145, 255),  # base
    'l': (176, 181, 188, 255),  # light
    'h': (214, 218, 224, 255),  # highlight
    'k': (24, 24, 30, 255),     # lens ring
    'g': (28, 62, 128, 255),    # glass dark
    'G': (52, 112, 196, 255),   # glass
    'c': (120, 176, 236, 255),  # glass light
    'w': (236, 246, 255, 255),  # sparkle
}

GRID = [
    "................",
    "......oho.......",
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
