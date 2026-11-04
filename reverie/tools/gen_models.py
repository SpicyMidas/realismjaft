#!/usr/bin/env python3
"""Multi-element block/item models (and their multipart blockstates) that are too detailed for Java datagen.

Run from the project root:  python3 tools/gen_models.py
Writes into src/main/resources/assets/reverie/. Geometry is in model pixels (0..16) in a canonical
orientation: shaft along Z, gearstick on top (+Y), gear windows on +-X, vent underneath. The blockstate
rotates it for axis=x (y=90) and axis=y (x=90). The stick pivot and gate spacing must match
TuffGearshiftRenderer (PIVOT_*, COLUMN_SPACING, ROW_OFFSET).
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "reverie")
FACES = ("north", "south", "east", "west", "up", "down")


def write(rel, obj):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def auto_uv(face, f, t):
    """Vanilla's default face UV, shifted back into 0..16 when the element sticks out of the block
    (otherwise the face samples neighbouring atlas sprites)."""
    if face == "down":
        uv = [f[0], 16 - t[2], t[0], 16 - f[2]]
    elif face == "up":
        uv = [f[0], f[2], t[0], t[2]]
    elif face == "north":
        uv = [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    elif face == "south":
        uv = [f[0], 16 - t[1], t[0], 16 - f[1]]
    elif face == "west":
        uv = [f[2], 16 - t[1], t[2], 16 - f[1]]
    else:
        uv = [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]
    for lo, hi in ((0, 2), (1, 3)):
        size = uv[hi] - uv[lo]
        if size > 16:
            uv[lo], uv[hi] = 0, 16
        elif uv[lo] < 0:
            uv[lo], uv[hi] = 0, size
        elif uv[hi] > 16:
            uv[lo], uv[hi] = 16 - size, 16
    return [round(v, 4) for v in uv]


def box(frm, to, tex, faces=FACES, uv=None, rotation=None, emissive=False, shade=True, face_rot=None, name=None):
    el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {}}
    if name:
        el["name"] = name
    for f in faces:
        t = tex[f] if isinstance(tex, dict) else tex
        if t is None:
            continue
        face = {"texture": "#" + t}
        if uv and f in uv:
            face["uv"] = uv[f]
        elif min(frm) < 0 or max(to) > 16:
            face["uv"] = auto_uv(f, frm, to)
        if face_rot and f in face_rot:
            face["rotation"] = face_rot[f]
        el["faces"][f] = face
    if rotation:
        el["rotation"] = rotation
    if not shade:
        el["shade"] = False
    if emissive:
        el["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return el


def model(textures, elements, parent="block/block", render_type="minecraft:cutout", display=None):
    m = {"parent": parent, "render_type": render_type, "textures": textures, "elements": elements}
    if display:
        m["display"] = display
    return m


T = "reverie:block/gearshift/"


def casing_elements(sport):
    e = []
    # End flanges (16x16, 1.5 deep) with a 6x6 shaft hole, bolts at the corners and a brass collar.
    for z0, z1, bz0, bz1, cz0, cz1 in ((0.5, 2, 0, 1, 0, 1.5), (14, 15.5, 15, 16, 14.5, 16)):
        e.append(box([0, 0, z0], [16, 5, z1], "flange", name="flange"))
        e.append(box([0, 11, z0], [16, 16, z1], "flange", name="flange"))
        e.append(box([0, 5, z0], [5, 11, z1], "flange", name="flange"))
        e.append(box([11, 5, z0], [16, 11, z1], "flange", name="flange"))
        for bx, by in ((1, 1), (13, 1), (1, 13), (13, 13)):
            e.append(box([bx, by, bz0], [bx + 2, by + 2, bz1], "bolt", name="bolt"))
        e.append(box([5, 5, cz0], [11, 6, cz1], "collar", name="collar"))
        e.append(box([5, 10, cz0], [11, 11, cz1], "collar", name="collar"))
        e.append(box([5, 6, cz0], [6, 10, cz1], "collar", name="collar"))
        e.append(box([10, 6, cz0], [11, 10, cz1], "collar", name="collar"))
    # Body core between the flanges, stepped so the long edges can carry 45 degree gold chamfers.
    e.append(box([2, 1, 2], [14, 14, 14], {"north": None, "south": None, "east": "side", "west": "side",
                                           "up": "top", "down": "vent"}, name="core"))
    e.append(box([1, 2, 2], [15, 14, 14], {"north": None, "south": None, "east": "side", "west": "side",
                                           "up": "top", "down": "vent"}, name="core_wide"))
    r = 0.7071
    for cx, cy, angle, up in ((1.5, 1.5, -45, False), (14.5, 1.5, 45, False), (1.5, 14.5, 45, True), (14.5, 14.5, -45, True)):
        y0, y1 = (cy - 0.6, cy) if up else (cy, cy + 0.6)
        e.append(box([cx - r, y0, 2], [cx + r, y1, 14], "gold", name="gold_chamfer",
                     rotation={"origin": [cx, cy, 8], "axis": "z", "angle": angle}))
    # Top rim around the recessed gate plate.
    if sport:
        e.append(box([2, 14, 2], [5, 15, 14], "top", name="rim"))
        e.append(box([11, 14, 2], [14, 15, 14], "top", name="rim"))
        plate = ([5, 14, 2], [11, 14.05, 14], [0, 0, 8, 16])
    else:
        e.append(box([2, 14, 2], [5, 15, 14], "top", name="rim"))
        e.append(box([11, 14, 2], [14, 15, 14], "top", name="rim"))
        e.append(box([5, 14, 2], [11, 15, 3], "top", name="rim"))
        e.append(box([5, 14, 13], [11, 15, 14], "top", name="rim"))
        plate = ([5, 14, 3], [11, 14.05, 13], [0, 0, 9.6, 16])
    e.append(box(plate[0], plate[1], {"up": "plate", "north": None, "south": None, "east": None, "west": None, "down": None},
                 uv={"up": plate[2]}, name="gate_plate"))
    # Static leather boot base (the upper boot and the stick are animated by the renderer).
    e.append(box([5, 14, 5], [11, 15, 11], "leather", name="boot_base"))
    # Gear window frames on both stick-less sides (+X / -X); the glyph is a separate multipart model.
    for x0, x1 in ((15, 15.5), (0.5, 1)):
        e.append(box([x0, 5, 5], [x1, 6, 11], "collar", name="window_frame"))
        e.append(box([x0, 10, 5], [x1, 11, 11], "collar", name="window_frame"))
        e.append(box([x0, 6, 5], [x1, 10, 6], "collar", name="window_frame"))
        e.append(box([x0, 6, 10], [x1, 10, 11], "collar", name="window_frame"))
    return e


def casing_textures(sport):
    return {
        "particle": T + ("casing_side_sport" if sport else "casing_side"),
        "side": T + ("casing_side_sport" if sport else "casing_side"),
        "top": T + "casing_top",
        "vent": T + "casing_vent",
        "flange": T + "flange",
        "bolt": T + "bolt",
        "collar": T + ("brass_polished" if sport else "brass"),
        "gold": T + "gold_inlay",
        "plate": T + ("gate_plate_sport" if sport else "gate_plate"),
        "leather": T + "leather",
        "frame": T + "window_frame",
    }


def window_elements(vertical):
    # East glyph faces +X, west glyph faces -X; for axis=y the model is rotated x=90 so the face is turned back.
    east_rot = {"east": 270} if vertical else None
    west_rot = {"west": 90} if vertical else None
    return [
        box([15.05, 6, 6], [15.1, 10, 10], {"east": "glyph"}, faces=("east",), uv={"east": [0, 0, 16, 16]},
            emissive=True, shade=False, face_rot=east_rot, name="glyph"),
        box([0.9, 6, 6], [0.95, 10, 10], {"west": "glyph"}, faces=("west",), uv={"west": [0, 0, 16, 16]},
            emissive=True, shade=False, face_rot=west_rot, name="glyph"),
    ]


def dot_elements(on):
    return [
        box([15, 11.5, 10.5], [15.5, 12.5, 11.5], "dot", emissive=on, shade=not on, name="powered_dot"),
        box([0.5, 11.5, 4.5], [1, 12.5, 5.5], "dot", emissive=on, shade=not on, name="powered_dot"),
    ]


def boot_upper_elements():
    return [
        box([5.5, 15, 5.5], [10.5, 16, 10.5], "leather", name="boot_mid"),
        box([6, 16, 6], [10, 17, 10], "leather", name="boot_top"),
    ]


def rod(y0, y1):
    rot = {"origin": [8, (y0 + y1) / 2, 8], "axis": "y", "angle": 45}
    return [
        box([7.25, y0, 7.25], [8.75, y1, 8.75], "rod", faces=("north", "south", "east", "west"), name="rod"),
        box([7.25, y0, 7.25], [8.75, y1, 8.75], "rod", faces=("north", "south", "east", "west"), rotation=rot, name="rod"),
    ]


def stick_elements(sport):
    if not sport:
        e = rod(15.5, 21)
        e.append(box([6.75, 20.5, 6.75], [9.25, 21, 9.25], "collar", name="knob_collar"))
        e.append(box([6.5, 21, 6.5], [9.5, 24, 9.5], {"north": "knob", "south": "knob", "east": "knob", "west": "knob",
                                                       "up": "knob", "down": "knob"},
                     uv={"up": [0, 0, 16, 16], "north": [0, 0, 16, 10], "south": [0, 0, 16, 10],
                         "east": [0, 0, 16, 10], "west": [0, 0, 16, 10]}, name="knob"))
        return e
    e = rod(15.5, 22.5)
    e.append(box([6.75, 22, 6.75], [9.25, 22.5, 9.25], "collar", name="knob_collar"))
    full = {f: [0, 0, 16, 16] for f in FACES}
    e.append(box([6.25, 22.75, 6.75], [9.75, 25.75, 9.25], "knob", uv=full, name="ball"))
    e.append(box([6.75, 22.75, 6.25], [9.25, 25.75, 9.75], "knob", uv=full, name="ball"))
    e.append(box([6.75, 22.5, 6.75], [9.25, 26, 9.25], "knob", uv=full, name="ball"))
    return e


def stick_textures(sport):
    return {
        "particle": T + "rod",
        "rod": T + "rod",
        "collar": T + ("brass_polished" if sport else "gold_inlay"),
        "knob": T + ("knob_sport" if sport else "knob"),
        "leather": T + "leather",
    }


ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.35, 0.35, 0.35]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.35, 0.35, 0.35]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.36, 0.36, 0.36]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.36, 0.36, 0.36]},
}


def shaft_elements():
    return [box([6, 6, 0], [10, 10, 16], {"north": "axis_top", "south": "axis_top", "east": "axis", "west": "axis",
                                          "up": "axis", "down": "axis"},
                uv={"east": [6, 0, 10, 16], "west": [6, 0, 10, 16], "up": [6, 0, 10, 16], "down": [6, 0, 10, 16]},
                face_rot={"east": 90, "west": 90, "up": 0, "down": 0}, name="shaft")]


def gearshift():
    gears = {0: "n", 1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6", 7: "r"}
    for g, key in gears.items():
        for vertical in (False, True):
            write(f"models/block/gearshift/window_{key}{'_vertical' if vertical else ''}.json",
                  model({"glyph": T + "gear_" + key, "particle": T + "window_frame"}, window_elements(vertical)))
    for on in (False, True):
        write(f"models/block/gearshift/dot_{'on' if on else 'off'}.json",
              model({"dot": T + ("dot_on" if on else "dot_off"), "particle": T + "window_frame"}, dot_elements(on)))
    write("models/block/tuff_gearshift/boot_upper.json",
          model({"leather": T + "leather", "particle": T + "leather"}, boot_upper_elements()))

    for sport in (False, True):
        name = "sport_tuff_gearshift" if sport else "tuff_gearshift"
        write(f"models/block/{name}/casing.json", model(casing_textures(sport), casing_elements(sport)))
        write(f"models/block/{name}/stick.json", model(stick_textures(sport), stick_elements(sport)))

        tex = dict(casing_textures(sport))
        tex.update({"rod": T + "rod", "knob": T + ("knob_sport" if sport else "knob"),
                    "glyph": T + "gear_n", "dot": T + "dot_off",
                    "axis": "create:block/axis", "axis_top": "create:block/axis_top"})
        stick = stick_elements(sport)
        for el in stick:
            if el.get("name") == "knob_collar" and not sport:
                for f in el["faces"].values():
                    f["texture"] = "#gold"
        item_elements = casing_elements(sport) + window_elements(False) + dot_elements(False) \
            + boot_upper_elements() + stick + shaft_elements()
        write(f"models/item/{name}.json", model(tex, item_elements, display=ITEM_DISPLAY))

        parts = []
        rots = {"z": {}, "x": {"y": 90}, "y": {"x": 90}}
        for axis, rot in rots.items():
            parts.append({"when": {"axis": axis}, "apply": dict({"model": f"reverie:block/{name}/casing"}, **rot)})
            for g, key in gears.items():
                suffix = "_vertical" if axis == "y" else ""
                parts.append({"when": {"axis": axis, "gear": str(g)},
                              "apply": dict({"model": f"reverie:block/gearshift/window_{key}{suffix}"}, **rot)})
            for on in (False, True):
                parts.append({"when": {"axis": axis, "powered": str(on).lower()},
                              "apply": dict({"model": f"reverie:block/gearshift/dot_{'on' if on else 'off'}"}, **rot)})
        write(f"blockstates/{name}.json", {"multipart": parts})


if __name__ == "__main__":
    gearshift()
    print("models written")
