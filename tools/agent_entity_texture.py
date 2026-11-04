# Paints textures/entity/agent.png (64x64) for AgentModel's boxes (standard Minecraft box UV layout).
import random, sys
from PIL import Image

O, D, B, L, H = (38, 40, 46), (88, 92, 100), (132, 137, 145), (176, 181, 188), (214, 218, 224)
K, G0, G1, G2, W = (24, 24, 30), (28, 62, 128), (52, 112, 196), (120, 176, 236), (236, 246, 255)
LED = (90, 220, 120)
rnd = random.Random(7)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

def metal(x0, y0, w, h, base):
    shades = {B: [B, B, B, L, D], L: [L, L, L, H, B], D: [D, D, D, B, O]}[base]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            img.putpixel((x, y), shades[rnd.randrange(len(shades))] + (255,))

def frame(x0, y0, w, h, c):
    for x in range(x0, x0 + w):
        img.putpixel((x, y0), c + (255,)); img.putpixel((x, y0 + h - 1), c + (255,))
    for y in range(y0, y0 + h):
        img.putpixel((x0, y), c + (255,)); img.putpixel((x0 + w - 1, y), c + (255,))

def box(u, v, w, h, d, front=None, back=None):
    metal(u + d, v, w, d, L)              # top (lit)
    metal(u + d + w, v, w, d, D)          # bottom
    metal(u, v + d, d, h, B)              # side
    metal(u + d, v + d, w, h, B)          # front
    metal(u + d + w, v + d, d, h, D)      # other side
    metal(u + 2 * d + w, v + d, w, h, D)  # back
    for (x, y, ww, hh) in [(u + d, v + d, w, h), (u, v + d, d, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]:
        if ww > 2 and hh > 2:  # a frame would cover a 2-px face entirely
            frame(x, y, ww, hh, O)
    if front: front(u + d, v + d, w, h)
    if back: back(u + 2 * d + w, v + d, w, h)

def head_front(x, y, w, h):
    # dark mount plate behind the lens (lens covers cols 2..7, rows 1..7 of this 10x9 face)
    for yy in range(y + 1, y + 8):
        for xx in range(x + 1, x + 9):
            img.putpixel((xx, yy), D + (255,))

def lens_front(x, y, w, h):
    rows = ["okkkko", "kc211k", "kw111k", "k1110k", "k1000k", "okkkko"]
    cmap = {'k': K, 'o': O, 'c': G2, 'w': W, '2': G2, '1': G1, '0': G0}
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            img.putpixel((x + i, y + j), cmap[ch] + (255,))

def body_front(x, y, w, h):
    frame(x + 2, y + 2, 4, 4, O)
    for yy in range(y + 3, y + 5):
        for xx in range(x + 3, x + 5):
            img.putpixel((xx, yy), D + (255,))
    img.putpixel((x + 4, y + 3), LED + (255,))

def vents(x, y, w, h):
    for yy in range(y + 2, y + h - 1, 2):
        for xx in range(x + 2, x + w - 2):
            img.putpixel((xx, yy), O + (255,))

box(0, 0, 10, 9, 8, front=head_front, back=vents)    # head
box(36, 0, 6, 6, 2, front=lens_front)                 # lens barrel
box(0, 18, 8, 9, 5, front=body_front, back=vents)    # body
box(28, 18, 2, 7, 2)                                  # right arm
box(36, 18, 2, 7, 2)                                  # left arm
img.save(sys.argv[1])
img.resize((64 * 8, 64 * 8), Image.NEAREST).save(sys.argv[2])
