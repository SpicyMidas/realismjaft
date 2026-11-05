#!/usr/bin/env python3
"""Multi-element block/item models (and their multipart blockstates) that are too detailed for Java datagen.

Run from the project root:  python3 tools/gen_models.py
Writes into src/main/resources/assets/reverie/. Geometry is in model pixels (0..16) in a canonical
orientation: shaft along Z, gearstick on top (+Y), gear windows on +-X. The blockstate
rotates it for axis=x (y=90) and axis=y (x=90). The stick pivot and gate spacing must match
TuffGearshiftRenderer (PIVOT_*, columnAtTop, ROW_OFFSET).
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
    """Create's gearshift, element for element: a full cube whose shaft faces carry only the frame (open in the middle),
    and 1 px inside each shaft face the gearbox panel. The panel is an inverted box (from/to swapped along z), as in
    Create's block.json, so its four rim faces turn inward and show as the walls of the recess. The shaft half reaches
    the surface, so its end stands 1 px out of the black hole."""
    rim = {"up": [2, 15, 14, 16], "down": [2, 0, 14, 1], "east": [15, 2, 16, 14], "west": [0, 2, 1, 14]}
    walls = ("up", "down", "east", "west")
    return [
        box([0, 0, 0], [16, 16, 16], {"north": "frame", "south": "frame", "east": "side", "west": "side",
                                      "up": "plate", "down": "side"}, face_rot={"down": 90}, name="casing"),
        box([1.95, 1.95, 1], [14.05, 14.05, 0], "gearbox", faces=("north",) + walls,
            uv=dict(rim, north=[2, 2, 14, 14]), name="gearbox_north"),
        box([1.95, 1.95, 16], [14.05, 14.05, 15], "gearbox", faces=("south",) + walls,
            uv=dict(rim, south=[2, 2, 14, 14]), name="gearbox_south"),
    ]


def casing_textures(sport, powered=False):
    suffix = ("_sport" if sport else "") + ("_on" if powered else "")
    side = T + "casing_side" + suffix
    return {"particle": side, "side": side, "frame": T + "casing_frame", "gearbox": T + "casing_gearbox",
            "plate": T + "gate_plate" + suffix}


def window_elements(vertical):
    # The gear window in the middle of each side (+x and -x); axis=y models are rotated x=90, so the face is turned back.
    east_rot = {"east": 270} if vertical else None
    west_rot = {"west": 90} if vertical else None
    return [
        box([16.01, 6, 6], [16.02, 10, 10], {"east": "glyph"}, faces=("east",), uv={"east": [0, 0, 16, 16]},
            emissive=True, shade=False, face_rot=east_rot, name="glyph"),
        box([-0.02, 6, 6], [-0.01, 10, 10], {"west": "glyph"}, faces=("west",), uv={"west": [0, 0, 16, 16]},
            emissive=True, shade=False, face_rot=west_rot, name="glyph"),
    ]


def stick_elements(sport):
    """Small and plain, the same shape for both: a thin rod from the pivot (4 px up, inside the block), a collar and a
    3 px knob. Textures map one texel per pixel. Must match TuffGearshiftRenderer (knob centre at 21)."""
    side = ("north", "south", "east", "west")
    return [
        box([7.5, 4, 7.5], [8.5, 19.5, 8.5], "rod", faces=side, name="rod"),
        box([7, 19, 7], [9, 19.5, 9], "rod", name="collar"),
        box([6.5, 19.5, 6.5], [9.5, 22.5, 9.5], "knob", name="knob"),
    ]


def stick_textures(sport):
    return {"particle": T + "rod", "rod": T + "rod", "knob": T + ("knob_sport" if sport else "knob")}


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

    for sport in (False, True):
        name = "sport_tuff_gearshift" if sport else "tuff_gearshift"
        write(f"models/block/{name}/casing.json", model(casing_textures(sport), casing_elements(sport)))
        write(f"models/block/{name}/casing_powered.json", model(casing_textures(sport, True), casing_elements(sport)))
        write(f"models/block/{name}/stick.json", model(stick_textures(sport), stick_elements(sport)))

        tex = dict(casing_textures(sport))
        tex.update(stick_textures(sport))
        tex.update({"particle": T + ("casing_side_sport" if sport else "casing_side"), "glyph": T + "gear_n",
                    "axis": "create:block/axis", "axis_top": "create:block/axis_top"})
        item_elements = casing_elements(sport) + window_elements(False) + stick_elements(sport) + shaft_elements()
        write(f"models/item/{name}.json", model(tex, item_elements, display=ITEM_DISPLAY))

        parts = []
        rots = {"z": {}, "x": {"y": 90}, "y": {"x": 90}}
        for axis, rot in rots.items():
            for powered in (False, True):
                casing = f"reverie:block/{name}/casing" + ("_powered" if powered else "")
                parts.append({"when": {"axis": axis, "powered": str(powered).lower()}, "apply": dict({"model": casing}, **rot)})
            for g, key in gears.items():
                suffix = "_vertical" if axis == "y" else ""
                parts.append({"when": {"axis": axis, "gear": str(g)},
                              "apply": dict({"model": f"reverie:block/gearshift/window_{key}{suffix}"}, **rot)})
        write(f"blockstates/{name}.json", {"multipart": parts})


D = "reverie:block/"


def dream_decor():
    # Floating lantern (drawn bobbing by a renderer; the block model is also the item model).
    lantern = [
        box([5, 2, 5], [11, 9, 11], "lamp", name="lantern"),
        box([6, 9, 6], [10, 10, 10], "frame", name="cap"),
        box([4.5, 1.5, 4.5], [11.5, 2, 11.5], "frame", name="base"),
        box([7, 10, 7], [9, 12, 9], "frame", name="ring"),
    ]
    write("models/block/floating_lantern.json", model({"lamp": D + "floating_lantern", "frame": D + "dreamwood_log", "particle": D + "floating_lantern"},
                                                      [dict(e, **{"neoforge_data": {"block_light": 15, "sky_light": 15}}) if e.get("name") == "lantern" else e for e in lantern]))
    write("blockstates/floating_lantern.json", {"variants": {"": {"model": "reverie:block/floating_lantern"}}})

    # Melted clock: a disc draped over an edge (flat part on top, bending down the front), hands by renderer.
    clock = [
        box([1, 0, 1], [15, 1, 12], "face", uv={"up": [1, 1, 15, 12]}, name="flat"),
        box([2, -2, 12], [14, 1, 13], "face", uv={"north": [2, 12, 14, 15], "south": [2, 12, 14, 15]},
            rotation={"origin": [8, 0.5, 12], "axis": "x", "angle": 45}, name="bend"),
        box([3, -6, 13.5], [13, -1, 14.5], "face", uv={"north": [3, 11, 13, 16], "south": [3, 11, 13, 16]}, name="drip"),
        box([6, -9, 13.6], [9, -6, 14.4], "face", name="drop"),
        box([7.5, 1, 7.5], [8.5, 1.5, 8.5], "gold", name="pin"),
    ]
    write("models/block/melted_clock.json", model({"face": D + "melted_clock", "gold": D + "gilded", "particle": D + "melted_clock"}, clock))
    write("models/block/melted_clock_hands.json", model({"hand": D + "clock_hand", "particle": D + "clock_hand"}, [
        box([7.6, 1.05, 3.5], [8.4, 1.25, 8.2], "hand", name="minute"),
        box([7.6, 1.1, 5.5], [8.4, 1.3, 8.2], "hand", name="hour"),
    ]))
    write("blockstates/melted_clock.json", {"variants": {f"facing={f}": {"model": "reverie:block/melted_clock", "y": y}
                                                        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})

    # Eyebloom: stem cross + an eye disc facing outward on both sides; the pupil is a renderer model.
    eye = [
        box([7.5, 0, 4], [8.5, 9, 12], "stem", faces=("east", "west"), uv={"east": [4, 0, 12, 9], "west": [4, 0, 12, 9]}, name="stem"),
        box([4, 0, 7.5], [12, 9, 8.5], "stem", faces=("north", "south"), uv={"north": [4, 0, 12, 9], "south": [4, 0, 12, 9]}, name="stem"),
        box([4.5, 8, 4.5], [11.5, 9.2, 11.5], "eye", uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}, name="eye_white"),
    ]
    write("models/block/eyebloom.json", model({"stem": D + "eyebloom_stem", "eye": D + "eyebloom_eye", "particle": D + "eyebloom_eye"}, eye))
    write("models/block/eyebloom_pupil.json", model({"pupil": D + "eyebloom_pupil", "particle": D + "eyebloom_pupil"},
                                                    [box([6.6, 9.2, 6.6], [9.4, 9.6, 9.4], "pupil", uv={f: [0, 0, 16, 16] for f in FACES}, name="pupil")]))
    write("blockstates/eyebloom.json", {"variants": {"": {"model": "reverie:block/eyebloom"}}})

    # Sleep bell on a little yoke.
    bell = [
        box([4, 13, 7], [12, 14, 9], "wood", name="yoke"),
        box([4, 0, 7], [5, 13, 9], "wood", name="post"),
        box([11, 0, 7], [12, 13, 9], "wood", name="post"),
        box([6, 6, 6], [10, 12, 10], "gold", name="bell"),
        box([5.5, 5, 5.5], [10.5, 6, 10.5], "gold", name="lip"),
        box([7.5, 4, 7.5], [8.5, 5, 8.5], "gold", name="clapper"),
    ]
    write("models/block/sleep_bell.json", model({"wood": D + "dreamwood_log", "gold": D + "gilded", "particle": D + "gilded"}, bell))
    write("blockstates/sleep_bell.json", {"variants": {"": {"model": "reverie:block/sleep_bell"}}})

    # Sandglass core: a small hourglass (frame, two glass bulbs, sand).
    hg = [
        box([3, 0, 3], [13, 1, 13], "wood", name="base"),
        box([3, 15, 3], [13, 16, 13], "wood", name="top"),
        box([3, 1, 3], [4, 15, 4], "wood", name="rod"),
        box([12, 1, 12], [13, 15, 13], "wood", name="rod"),
        box([4.5, 1, 4.5], [11.5, 7, 11.5], "glass", name="bulb"),
        box([6.5, 7, 6.5], [9.5, 9, 9.5], "glass", name="waist"),
        box([4.5, 9, 4.5], [11.5, 15, 11.5], "glass", name="bulb"),
        box([5, 1, 5], [11, 3, 11], "sand", name="sand_low"),
        box([5.5, 11, 5.5], [10.5, 14.5, 10.5], "sand", name="sand_high"),
    ]
    write("models/block/sandglass_core.json", model({"wood": D + "dreamwood_planks", "glass": D + "sandglass_glass", "sand": D + "hourglass_sand",
                                                     "particle": D + "sandglass_glass"}, hg, render_type="minecraft:translucent"))
    write("blockstates/sandglass_core.json", {"variants": {"": {"model": "reverie:block/sandglass_core"}}})

    # Lone Door: a freestanding frame (model faces north), a soft glow inside the doorway.
    def door(upper):
        e = [box([0, 0, 6], [2, 16, 10], "wood", name="jamb"), box([14, 0, 6], [16, 16, 10], "wood", name="jamb"),
             dict(box([2, 0, 7.9], [14, 16, 8.1], "glow", faces=("north", "south"), uv={"north": [2, 0, 14, 16], "south": [2, 0, 14, 16]}, name="glow"),
                  **{"neoforge_data": {"block_light": 15, "sky_light": 15}, "shade": False})]
        if upper:
            e.append(box([0, 14, 6], [16, 16, 10], "wood", name="lintel"))
            e.append(box([-1, 16, 5.5], [17, 17, 10.5], "wood", name="cap"))
        else:
            e.append(box([-0.5, 0, 5.5], [2.5, 1, 10.5], "wood", name="foot"))
            e.append(box([13.5, 0, 5.5], [16.5, 1, 10.5], "wood", name="foot"))
        return e
    for upper in (False, True):
        write(f"models/block/lone_door_{'upper' if upper else 'lower'}.json",
              model({"wood": D + "dreamwood_log", "glow": D + "lone_door_glow", "particle": D + "dreamwood_planks"}, door(upper),
                    render_type="minecraft:translucent"))
    write("blockstates/lone_door.json", {"variants": {f"facing={f},half={h}": {"model": f"reverie:block/lone_door_{h}", "y": y}
                                                     for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
                                                     for h in ("lower", "upper")}})
    write("models/item/lone_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "reverie:item/lone_door"}})


if __name__ == "__main__":
    dream_decor()
    gearshift()
    print("models written")
