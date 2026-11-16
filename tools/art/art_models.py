"""Block and item parent models, plus a small isometric point renderer used for the contact sheets."""
import math
import os

import numpy as np
from PIL import Image

from artlib import *  # noqa: F401,F403
from palette import C

NS = "neromaps:"
BLOCK_MODELS = {}


def face(tex, uv=None, cull=None, tint=None, rot=None):
    f = {"texture": "#" + tex}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    if tint is not None:
        f["tintindex"] = tint
    if rot:
        f["rotation"] = rot
    return f


def box(frm, to, faces, rotation=None, glow=False):
    e = {"from": frm, "to": to}
    if rotation:
        e["rotation"] = rotation
    if glow:
        e["shade"] = False
        e["light_emission"] = 15
    e["faces"] = faces
    return e


def all_faces(tex, uv_side=None, uv_cap=None):
    f = {d: face(tex, uv_side) for d in ("north", "south", "east", "west")}
    f["up"] = face(tex, uv_cap)
    f["down"] = face(tex, uv_cap)
    return f


def model(textures, elements, cutout=True):
    m = {"parent": "minecraft:block/block"}
    if cutout:
        m["render_type"] = "minecraft:cutout"
    m["textures"] = {k: NS + "block/" + v for k, v in textures.items()}
    m["elements"] = elements
    return m


def desk():
    slant = {"angle": -22.5, "axis": "x", "origin": [8, 9, 0]}
    els = [
        box([0, 0, 0], [16, 9, 16], {
            "north": face("front", [0, 7, 16, 16], "north"), "south": face("back", [0, 7, 16, 16], "south"),
            "east": face("side", [0, 7, 16, 16], "east"), "west": face("side", [0, 7, 16, 16], "west"),
            "up": face("bottom"), "down": face("bottom", None, "down")}),
        box([1, 9, 6], [15, 11, 16], {"south": face("back", [1, 5, 15, 7]), "east": face("side", [0, 8, 10, 10]),
                                      "west": face("side", [6, 8, 16, 10]), "up": face("bottom")}),
        box([1, 11, 11], [15, 13.5, 16], {"south": face("back", [1, 2.5, 15, 5]), "east": face("side", [0, 8, 5, 10.5]),
                                         "west": face("side", [11, 8, 16, 10.5]), "up": face("bottom")}),
        box([0, 9, 0], [16, 10.5, 13], {
            "up": face("top", [0, 0, 16, 16]), "down": face("bottom"),
            "north": face("side", [0, 0, 16, 1.5]), "south": face("side", [0, 0, 16, 1.5]),
            "east": face("side", [0, 0, 13, 1.5]), "west": face("side", [0, 0, 13, 1.5])}, slant),
        box([0, 10.52, 0], [16, 10.52, 13], {"up": face("top_glow", [0, 0, 16, 16])}, slant, glow=True),
        box([2, 13.5, 13], [14, 15.5, 15.5], {
            "north": face("paper", [0, 0, 12, 2]), "south": face("paper", [0, 4, 12, 6]),
            "up": face("paper", [0, 0, 12, 2.5]), "east": face("paper", [0, 8, 2.5, 10]),
            "west": face("paper", [0, 8, 2.5, 10])}),
        box([1, 13.5, 13.5], [2, 15, 15], all_faces("side", [0, 0, 1.5, 1.5], [0, 0, 1, 1.5])),
        box([14, 13.5, 13.5], [15, 15, 15], all_faces("side", [0, 0, 1.5, 1.5], [0, 0, 1, 1.5])),
        box([12.5, 10.5, 2], [13.5, 11, 9], all_faces("side", [0, 0, 7, 0.5], [0, 0, 1, 7]), slant),
    ]
    BLOCK_MODELS["cartography_desk"] = model(
        {"particle": "cartography_desk_side", "top": "cartography_desk_top", "top_glow": "cartography_desk_top_glow",
         "front": "cartography_desk_front", "side": "cartography_desk_side", "back": "cartography_desk_back",
         "bottom": "cartography_desk_bottom", "paper": "cartography_desk_paper"}, els)


def wall_map():
    for mask in range(16):
        for lit in (False, True):
            if lit and (mask & 2 or mask & 4):
                continue        # the status pixel lives on the piece that owns the lower-right corner
            els = [box([0, 0, 15], [16, 16, 16], {
                "north": face("front", [0, 0, 16, 16]), "south": face("back", [0, 0, 16, 16], "south"),
                "up": face("back", [0, 0, 16, 1]), "down": face("back", [0, 15, 16, 16]),
                "east": face("back", [0, 0, 1, 16]), "west": face("back", [15, 0, 16, 16])})]
            tex = {"particle": "wall_map_back", "front": f"wall_map_frame_{mask}", "back": "wall_map_back"}
            if lit:
                tex["glow"] = "wall_map_status_glow"
                els.append(box([0, 0, 14.98], [16, 16, 14.98], {"north": face("glow", [0, 0, 16, 16])}, glow=True))
            name = "wall_map" + ("" if mask == 0 else f"_{mask}") + ("_lit" if lit else "")
            BLOCK_MODELS[name] = model(tex, els)


def signpost():
    BLOCK_MODELS["signpost_post"] = model(
        {"particle": "signpost_post", "post": "signpost_post"},
        [box([7, 0, 7], [9, 16, 9], {**{d: face("post", [7, 0, 9, 16]) for d in ("north", "south", "east", "west")},
                                     "up": face("post", [7, 0, 9, 2], "up"), "down": face("post", [7, 14, 9, 16], "down")})],
        cutout=False)
    rows = [(11, 15), (6, 10), (1, 5)]
    for n in (1, 2, 3):
        for diag in (False, True):
            rot = {"angle": 45, "axis": "y", "origin": [8, 8, 8]} if diag else None
            els = []
            for (y0, y1) in rows[:n]:
                els.append(box([1, y0, 6], [15, y1, 7], {
                    "north": face("plate", [0, 0, 14, 4]), "south": face("plate", [0, 8, 14, 12]),
                    "up": face("plate", [0, 5, 14, 6]), "down": face("plate", [0, 6, 14, 7]),
                    "east": face("plate", [0, 5, 1, 7]), "west": face("plate", [0, 5, 1, 7])}, rot))
                els.append(box([1, y0, 5.98], [15, y1, 5.98], {"north": face("glow", [0, 0, 14, 4], tint=0)}, rot, glow=True))
            BLOCK_MODELS[f"signpost_plate_{n}" + ("_diag" if diag else "")] = model(
                {"particle": "signpost_plate", "plate": "signpost_plate", "glow": "signpost_plate_glow"}, els)


def map_table():
    els = [
        box([2, 0, 2], [14, 2, 14], {**{d: face("side") for d in ("north", "south", "east", "west")},
                                     "up": face("bottom"), "down": face("bottom", None, "down")}),
        box([5, 2, 5], [11, 8, 11], {d: face("side") for d in ("north", "south", "east", "west")}),
        box([0, 8, 0], [16, 11, 16], {**{d: face("side") for d in ("north", "south", "east", "west")},
                                      "up": face("top", [0, 0, 16, 16]), "down": face("bottom")}),
        box([0, 11.02, 0], [16, 11.02, 16], {"up": face("top_glow", [0, 0, 16, 16])}, glow=True),
    ]
    BLOCK_MODELS["map_table"] = model(
        {"particle": "map_table_side", "top": "map_table_top", "top_glow": "map_table_top_glow", "side": "map_table_side",
         "bottom": "map_table_bottom"}, els)


def trail_marker():
    tex = {"particle": "trail_marker", "body": "trail_marker", "cap": "trail_marker_cap_glow"}
    side = {d: face("body", [0, 0, 2, 6]) for d in ("north", "south", "east", "west")}
    els = [
        box([7, 0, 7], [9, 6, 9], {**side, "down": face("body", [0, 4, 2, 6], "down")}),
        box([6.5, 5, 6.5], [9.5, 6, 9.5], all_faces("body", [4, 0, 7, 1], [4, 2, 7, 5])),
        box([7, 6, 7], [9, 8, 9], {**{d: face("cap", [8, 2, 10, 4], tint=0) for d in ("north", "south", "east", "west")},
                                   "up": face("cap", [8, 0, 10, 2], tint=0)}, glow=True),
    ]
    BLOCK_MODELS["trail_marker"] = model(tex, els)
    lside = {d: face("body", [0, 0, 2, 6], rot=90) for d in ("up", "down", "east", "west")}
    els = [
        box([7, 7, 10], [9, 9, 16], {**lside, "south": face("body", [0, 4, 2, 6], "south")}),
        box([6.5, 6.5, 10], [9.5, 9.5, 11], {**{d: face("body", [4, 0, 7, 1]) for d in ("up", "down", "east", "west")},
                                             "north": face("body", [4, 2, 7, 5]), "south": face("body", [4, 2, 7, 5])}),
        box([7, 7, 8], [9, 9, 10], {**{d: face("cap", [8, 2, 10, 4], tint=0) for d in ("up", "down", "east", "west")},
                                    "north": face("cap", [8, 0, 10, 2], tint=0)}, glow=True),
    ]
    BLOCK_MODELS["trail_marker_wall"] = model(tex, els)


def item_models():
    def gen(name, *layers):
        save_json({"parent": "minecraft:item/generated",
                   "textures": {f"layer{i}": NS + "item/" + t for i, t in enumerate(layers)}},
                  MODELS, f"item/{name}.json", "Flat item model")
    gen("atlas_blank", "atlas_blank")
    gen("atlas", "atlas", "atlas_clasp_glow")
    gen("atlas_upgrade_pages", "atlas_upgrade_pages")
    gen("atlas_upgrade_binding", "atlas_upgrade_binding")
    gen("survey_kit", "survey_kit", "survey_kit_lens_glow")
    gen("signpost", "signpost")
    gen("trail_marker", "trail_marker")
    gen("wall_map", "wall_map")
    for f in range(16):
        gen(f"waypoint_compass_{f:02d}", f"waypoint_compass_{f:02d}")
        gen(f"waypoint_compass_locked_{f:02d}", f"waypoint_compass_{f:02d}", "waypoint_compass_locked_glow")
    gen("waypoint_compass", "waypoint_compass_00")
    for b in ("cartography_desk", "map_table"):
        save_json({"parent": NS + "block/" + b}, MODELS, f"item/{b}.json", "Block item model (inherits the block model)")


def run():
    desk()
    wall_map()
    signpost()
    map_table()
    trail_marker()
    for name, m in BLOCK_MODELS.items():
        save_json(m, MODELS, f"block/{name}.json", "Block model")
    item_models()


# ---------------------------------------------------------------------------------------------- iso renderer
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def default_uv(d, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {"north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1],
            "west": [z1, 16 - y2, z2, 16 - y1], "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
            "up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1]}[d]


def render_model(m, scale=10, tint=None, size=None):
    """Isometric render seen from the north-west-above (the model's north face is its front)."""
    tint = tint or C['M_ORANGE']
    texs = {}
    for k, v in m["textures"].items():
        texs[k] = np.asarray(Image.open(os.path.join(TEX, v.split(":")[1] + ".png")).convert("RGBA"), dtype=np.float32)
    W = size or int(scale * 16 * 1.9)
    H = int(scale * 16 * 2.1)
    rgb = np.zeros((H, W, 4), dtype=np.float32)
    zb = np.full((H, W), -1e9, dtype=np.float32)
    step = 0.35 / scale
    for e in m["elements"]:
        f, t = e["from"], e["to"]
        for d, fc in e["faces"].items():
            uv = fc.get("uv") or default_uv(d, f, t)
            tex = texs[fc["texture"][1:]]
            # s along u, tt along v
            if d in ("north", "south"):
                a, b = (f[0], t[0]), (f[1], t[1])
            elif d in ("east", "west"):
                a, b = (f[2], t[2]), (f[1], t[1])
            else:
                a, b = (f[0], t[0]), (f[2], t[2])
            na = max(2, int((a[1] - a[0]) / step) + 2)
            nb = max(2, int((b[1] - b[0]) / step) + 2)
            s, tt = np.meshgrid(np.linspace(0, 1, na), np.linspace(0, 1, nb))
            if d == "north":
                x, y, z = t[0] - s * (t[0] - f[0]), t[1] - tt * (t[1] - f[1]), np.full_like(s, f[2])
            elif d == "south":
                x, y, z = f[0] + s * (t[0] - f[0]), t[1] - tt * (t[1] - f[1]), np.full_like(s, t[2])
            elif d == "west":
                x, y, z = np.full_like(s, f[0]), t[1] - tt * (t[1] - f[1]), f[2] + s * (t[2] - f[2])
            elif d == "east":
                x, y, z = np.full_like(s, t[0]), t[1] - tt * (t[1] - f[1]), t[2] - s * (t[2] - f[2])
            elif d == "up":
                x, y, z = f[0] + s * (t[0] - f[0]), np.full_like(s, t[1]), f[2] + tt * (t[2] - f[2])
            else:
                x, y, z = f[0] + s * (t[0] - f[0]), np.full_like(s, f[1]), t[2] - tt * (t[2] - f[2])
            r = e.get("rotation")
            if r:
                ang = math.radians(r["angle"])
                ox, oy, oz = r["origin"]
                ca, sa = math.cos(ang), math.sin(ang)
                if r["axis"] == "x":
                    y, z = oy + (y - oy) * ca - (z - oz) * sa, oz + (y - oy) * sa + (z - oz) * ca
                elif r["axis"] == "y":
                    x, z = ox + (x - ox) * ca + (z - oz) * sa, oz - (x - ox) * sa + (z - oz) * ca
                else:
                    x, y = ox + (x - ox) * ca - (y - oy) * sa, oy + (x - ox) * sa + (y - oy) * ca
            th, tw = tex.shape[0], tex.shape[1]
            uu, vv = s, tt
            if fc.get("rotation") == 90:
                uu, vv = tt, 1 - s
            tu = np.clip(((uv[0] + uu * (uv[2] - uv[0])) / 16.0 * tw).astype(int), 0, tw - 1)
            tv = np.clip(((uv[1] + vv * (uv[3] - uv[1])) / 16.0 * th).astype(int), 0, th - 1)
            colr = tex[tv, tu].copy()
            if "tintindex" in fc:
                colr[..., :3] *= np.array(tint, dtype=np.float32) / 255.0
            if not e.get("light_emission"):
                colr[..., :3] *= SHADE[d]
            X, Z = -(x - 8), -(z - 8)
            sx = ((X - Z) * 0.866 * scale + W / 2).astype(int)
            sy = ((X + Z) * 0.5 * scale - (y - 8) * scale + H / 2).astype(int)
            depth = (X + Z + y + (0.02 if e.get("light_emission") else 0)).astype(np.float32)
            ok = (colr[..., 3] > 8) & (sx >= 0) & (sx < W) & (sy >= 0) & (sy < H)
            sx, sy, depth, colr = sx[ok], sy[ok], depth[ok], colr[ok]
            order = np.argsort(depth)
            sx, sy, depth, colr = sx[order], sy[order], depth[order], colr[order]
            win = depth >= zb[sy, sx]
            sx, sy, depth, colr = sx[win], sy[win], depth[win], colr[win]
            zb[sy, sx] = depth
            rgb[sy, sx] = np.concatenate([colr[..., :3], np.full((len(sx), 1), 255.0, dtype=np.float32)], axis=1)
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGBA")
