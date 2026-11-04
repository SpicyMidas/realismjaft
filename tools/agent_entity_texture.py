# Paints textures/entity/agent.png (64x32) for AgentModel's boxes (standard Minecraft box UV layout).
# Palette follows the owner's reference: light grey/white metal, dark face plate with a camera lens, rust accents.
import random, sys
from PIL import Image

O = (92, 94, 102)      # outline / seams
D = (146, 148, 156)    # shadow side
B = (196, 198, 204)    # base
L = (222, 224, 228)    # lit
H = (242, 243, 245)    # highlight
F = (44, 44, 52)       # face plate
J = (110, 112, 120)    # joints, hands, soles
K, G0, G1, G2, W = (22, 22, 28), (20, 40, 72), (42, 86, 150), (112, 164, 226), (236, 246, 255)
R, R2 = (196, 92, 52), (140, 62, 36)   # rust accent
LED = (120, 230, 140)
rnd = random.Random(11)
img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

def px(x, y, c):
    img.putpixel((x, y), c + (255,))

def metal(x0, y0, w, h, base):
    shades = {H: [H, H, L], L: [L, L, L, H, B], B: [B, B, B, L, D], D: [D, D, D, B, J]}.get(base, [base])
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(x, y, shades[rnd.randrange(len(shades))])

def solid(x0, y0, w, h, c):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(x, y, c)

def faces(u, v, w, h, d):
    """Returns face rects: top, bottom, right(-x side), front(-z), left(+x side), back."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "side1": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "side2": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

def box(u, v, w, h, d, top=L, sides=B, front=B, back=D, bottom=D):
    f = faces(u, v, w, h, d)
    metal(*f["top"], top); metal(*f["bottom"], bottom)
    metal(*f["side1"], sides); metal(*f["side2"], D if sides == B else sides)
    metal(*f["front"], front); metal(*f["back"], back)
    return f

# head 8x6x7 at (0,0): light shell, dark face plate on the front
f = box(0, 0, 8, 6, 7, top=H, front=L)
x, y, w, h = f["front"]
solid(x + 1, y + 1, w - 2, h - 2, F)          # face plate (the lens sits on it)
for i in range(w):
    px(x + i, y + h - 1, D)                    # chin seam
# lens 4x4x2 at (30,0): dark barrel, glass front with highlight
f = box(30, 0, 4, 4, 2, top=J, sides=J, front=J, back=J, bottom=J)
x, y, _, _ = f["front"]
for j, row in enumerate(["okko", "kw1k", "k10k", "okko"]):
    for i, ch in enumerate(row):
        px(x + i, y + j, {"o": J, "k": K, "w": W, "1": G1, "0": G0}[ch])
# antenna rod 1x3x1 at (30,6) and rust tip 2x1x2 at (34,6)
f = box(30, 6, 1, 3, 1, top=J, sides=J, front=J, back=J, bottom=J)
f = box(34, 6, 2, 1, 2, top=R, sides=R2, front=R, back=R2, bottom=R2)
# body 6x5x4 at (0,13): chest panel with a status light, vents on the back
f = box(0, 13, 6, 5, 4, top=L, front=B)
x, y, w, h = f["front"]
solid(x + 1, y + 1, 4, 3, D)
solid(x + 2, y + 2, 2, 1, F)
px(x + 3, y + 2, LED)
bx, by, bw, bh = f["back"]
for yy in range(by + 1, by + bh - 1, 2):
    for xx in range(bx + 1, bx + bw - 1):
        px(xx, yy, J)
# legs 2x4x2 at (0,22) and (8,22): soles and knee band darker
for u in (0, 8):
    f = box(u, 22, 2, 4, 2, top=B, sides=B, front=B, back=D, bottom=J)
    for k in ("side1", "front", "side2", "back"):
        x, y, w, h = f[k]
        solid(x, y + h - 1, w, 1, J)
# arms 2x6x2 at (16,22) and (24,22): darker hands with a rust knuckle
for u in (16, 24):
    f = box(u, 22, 2, 6, 2, top=L, sides=B, front=B, back=D, bottom=J)
    for k in ("side1", "front", "side2", "back"):
        x, y, w, h = f[k]
        solid(x, y + h - 2, w, 2, J)
    x, y, w, h = f["front"]
    px(x, y + h - 2, R)
img.save(sys.argv[1])
img.resize((64 * 10, 32 * 10), Image.NEAREST).save(sys.argv[2])
