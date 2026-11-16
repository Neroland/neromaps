#!/usr/bin/env python3
"""
render_contact_sheets.py -- review sheets for the NeroMaps art. Run after the three generators.

    python3 tools/art/render_contact_sheets.py OUT_DIR

Writes styles.png, minimap.png, icons.png, icons_cvd.png, blocks_items.png, gui.png into OUT_DIR.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import artlib  # noqa: E402
import art_models  # noqa: E402
import gen_map_icons as icons  # noqa: E402
from art_gui import star  # noqa: E402
from palette import C, HEX, HYPSO, MARKER_COLOURS, rgb  # noqa: E402

T = artlib.TEX
BG = (40, 45, 52, 255)


def tex(rel):
    return Image.open(os.path.join(T, rel)).convert("RGBA")


def up(im, s):
    return im.resize((im.width * s, im.height * s), Image.NEAREST)


def tint(im, colour):
    a = np.asarray(im, dtype=np.float32).copy()
    a[..., :3] *= np.array(colour, dtype=np.float32) / 255.0
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def label(sheet, x, y, text, c=(232, 236, 244, 255)):
    ImageDraw.Draw(sheet).text((x, y), text, fill=c)


# ------------------------------------------------------------------------------------------------ terrain sample
def terrain(n=96, seed=53):
    rng = np.random.default_rng(seed)
    h = np.zeros((n, n))
    for octv, amp in ((4, 46.0), (8, 20.0), (16, 8.0), (32, 3.0)):
        g = rng.random((octv + 2, octv + 2))
        xs = np.linspace(0, octv, n, endpoint=False)
        xi, xf = xs.astype(int), xs - xs.astype(int)
        xf = xf * xf * (3 - 2 * xf)
        a = g[np.ix_(xi, xi)] * np.outer(1 - xf, 1 - xf) + g[np.ix_(xi + 1, xi)] * np.outer(xf, 1 - xf) \
            + g[np.ix_(xi, xi + 1)] * np.outer(1 - xf, xf) + g[np.ix_(xi + 1, xi + 1)] * np.outer(xf, xf)
        h += (a - 0.5) * amp
    yy, xx = np.mgrid[0:n, 0:n]
    h += 70 + (xx - n / 2) * 0.35 + 60 * np.exp(-(((xx - 70) / 14.0) ** 2 + ((yy - 30) / 16.0) ** 2))
    h = np.round(h).astype(int)
    return h


SEA = 63


def base_colours(h):
    n = h.shape[0]
    out = np.zeros((n, n, 3))
    grass, sand, stone, snow, forest = (0x79, 0xC0, 0x5A), (0xDB, 0xD3, 0xA0), (0x7D, 0x7D, 0x7D), (0xF4, 0xF8, 0xFA), (0x4E, 0x8B, 0x3A)
    for y in range(n):
        for x in range(n):
            v = h[y, x]
            c = sand if v <= SEA + 1 else grass if v < 96 else stone if v < 140 else snow
            if c is grass and ((x * 7 + y * 13) % 11 < 3 or 80 < v < 92):
                c = forest
            out[y, x] = c
    return out


def hillshade(h, strength=1.0):
    north = np.roll(h, 1, axis=0)
    west = np.roll(h, 1, axis=1)
    d = h - (north + west) / 2.0
    d = np.clip(np.round(d), -2, 2).astype(int)
    lut = np.array([0.78, 0.89, 1.00, 1.08, 1.16])[d + 2]
    return 1 + (lut - 1) * strength


def water(img, h, colour=(0x3F, 0x76, 0xE4), bed=None, steps=True):
    depth = SEA - h
    wet = depth > 0
    mul = np.where(depth <= 2, 1.0, np.where(depth <= 6, 0.86, np.where(depth <= 15, 0.72, 0.58))) if steps else np.ones_like(depth, float)
    w = np.array(colour)[None, None, :] * mul[..., None]
    if bed is not None and steps:
        shallow = (depth > 0) & (depth <= 2)
        w[shallow] = w[shallow] * 0.65 + bed[shallow] * 0.35
    if steps:
        land = ~wet
        shore = wet & (np.roll(land, 1, 0) | np.roll(land, -1, 0) | np.roll(land, 1, 1) | np.roll(land, -1, 1))
        w[shore] = w[shore] + (255 - w[shore]) * 0.12
    img = img.copy()
    img[wet] = w[wet]
    return img


def style(h, name):
    base = base_colours(h)
    land = np.maximum(h, SEA)
    if name in ("Day", "Night"):
        img = water(base * hillshade(land)[..., None], h, bed=base)
        if name == "Night":
            img = img * (1 - 0.45) + img * (np.array(rgb('#6F7FC8')) / 255.0) * 0.45
    elif name == "Flat":
        img = water(base, h, steps=False)
    elif name == "Biome":
        fam = np.where(h <= SEA, 0, np.where(h <= SEA + 1, 1, np.where(h < 80, 2, np.where(h < 96, 3, np.where(h < 140, 4, 5)))))
        cols = np.array([rgb(x) for x in ("#2C5FA8", "#D9CF9A", "#8DB360", "#3F7A3A", "#8A8F96", "#E8F0F4")], float)
        img = cols[fam] * hillshade(land, 0.5)[..., None]
        edge = (fam != np.roll(fam, 1, 0)) | (fam != np.roll(fam, 1, 1))
        img[edge] *= 0.80
    elif name == "Height-shaded":
        stops = [(a, np.array(rgb(b), float)) for a, b in HYPSO]
        q = (land // 4) * 4
        img = np.zeros(h.shape + (3,))
        for (a0, c0), (a1, c1) in zip(stops, stops[1:]):
            m = (q >= a0) & (q <= a1)
            f = ((q - a0) / float(a1 - a0))[..., None]
            img[m] = (c0 + (c1 - c0) * f)[m]
        img *= hillshade(land)[..., None]
        contour = (land // 16) != (np.roll(land, 1, 0) // 16)
        contour |= (land // 16) != (np.roll(land, 1, 1) // 16)
        img[contour] *= 0.80
        img = water(img, h, colour=rgb('#2C5FA8'))
    else:   # High contrast
        bands = np.array([rgb(x) for x in ("#3A3A3A", "#6B6B6B", "#9C9C9C", "#D0D0D0")], float)
        img = bands[np.clip((land - SEA) // 24, 0, 3)]
        wet = h < SEA
        img[wet] = rgb('#0B2A7A')
        land_m = ~wet
        shore = wet & (np.roll(land_m, 1, 0) | np.roll(land_m, -1, 0) | np.roll(land_m, 1, 1) | np.roll(land_m, -1, 1))
        img[shore] = (255, 255, 255)
    return np.clip(img, 0, 255)


def apply_fog(img, contrast=False):
    n = img.shape[0]
    img = img.copy()

    def explored(cx, cz):
        return not (cx >= 4 and cz >= 3) and not (cx == 5 and cz == 2)
    void, vstar, vedge = (C['VOID'], C['VOID_STAR'], C['VOID_EDGE']) if not contrast else ((0, 0, 0), (0, 0, 0), (255, 255, 255))
    for y in range(n):
        for x in range(n):
            if not explored(x // 16, y // 16):
                img[y, x] = vstar if star(x, y) else void
                continue
            dist = 99
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for k in (1, 2, 3):
                    xx, yy = x + dx * k, y + dy * k
                    if 0 <= xx < n and 0 <= yy < n and not explored(xx // 16, yy // 16):
                        dist = min(dist, k)
            if contrast:
                if dist == 1:
                    img[y, x] = vedge
            elif dist == 3:
                img[y, x] = vedge
            elif dist == 2 and (x % 2 == 0) and (y % 2 == 0):
                img[y, x] = void
            elif dist == 1 and (x + y) % 2 == 0:
                img[y, x] = void
    return img


def to_img(a):
    return Image.fromarray(np.dstack([a.astype(np.uint8), np.full(a.shape[:2], 255, np.uint8)]), "RGBA")


def sheet_styles(out):
    h = terrain()
    names = ["Day", "Night", "Biome", "Height-shaded", "Flat", "High contrast"]
    s = Image.new("RGBA", (3 * 400 + 20, 2 * 420 + 20), BG)
    for i, nm in enumerate(names):
        im = up(to_img(apply_fog(style(h, nm), nm == "High contrast")), 4)
        x, y = 20 + (i % 3) * 400, 30 + (i // 3) * 420
        s.alpha_composite(im, (x, y))
        label(s, x, y - 14, f"{nm}  (96x96 columns at 4x, fog edge lower right)")
    s.save(os.path.join(out, "styles.png"))


# ------------------------------------------------------------------------------------------------ minimap
MAN = icons.manifest()["icons"]
ATLAS = None


def icon(iid, micro=False, colour=None):
    e = MAN[iid]
    u, v, w, hh = e["micro_uv"] if micro else e["uv"]
    im = ATLAS.crop((u, v, u + w, v + hh))
    return tint(im, colour or rgb(e["colour"]))


def nine(im, w, h, b):
    out = Image.new("RGBA", (w, h))
    sw, sh = im.size
    xs = [(0, b, 0, b), (b, sw - b, b, w - b), (sw - b, sw, w - b, w)]
    ys = [(0, b, 0, b), (b, sh - b, b, h - b), (sh - b, sh, h - b, h)]
    for (sx0, sx1, dx0, dx1) in xs:
        for (sy0, sy1, dy0, dy1) in ys:
            if dx1 > dx0 and dy1 > dy0:
                out.paste(im.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST), (dx0, dy0))
    return out


MARKS = [("neromaps:waypoint", 0.25, 0.30), ("neromaps:death", 0.70, 0.22), ("nerospace:station", 0.62, 0.62),
         ("neroruins:ruin", 0.30, 0.72), ("neroevents:event", 0.80, 0.50), ("nerospace:launch_pad", 0.45, 0.18),
         ("nerocompanion:companion", 0.42, 0.58), ("neromaps:signpost", 0.16, 0.52), ("neromaps:radar_hostile", 0.58, 0.40)]


def minimap(D, shape, border, hmap):
    pad = 14
    W = D + 2 * pad
    im = Image.new("RGBA", (W, W + 24), (0, 0, 0, 0))
    src = hmap.crop((0, 0, D, D)) if D <= hmap.width else hmap.resize((D, D), Image.NEAREST)
    body = Image.new("RGBA", (D, D))
    body.paste(src, (0, 0))
    for iid, fx, fy in MARKS:
        m = icon(iid, micro=True)
        body.alpha_composite(m, (int(fx * D) - 3, int(fy * D) - 3))
    arrow = tex("gui/minimap/player_arrow.png")
    body.alpha_composite(arrow, (D // 2 - 3, D // 2 - 5))
    if shape == "round":
        near = min((64, 96, 128, 160), key=lambda k: abs(k - D))
        mask = tex(f"gui/minimap/mask_round_{near}.png")
        body.putalpha(mask.split()[3])
        im.alpha_composite(body, (pad, pad))
        if border != "none":
            im.alpha_composite(tex(f"gui/minimap/frame_round_{border}_{near}.png"), (pad - 3, pad - 3))
            if border == "nero":
                im.alpha_composite(tex(f"gui/minimap/ticks_round_{near}.png"), (pad - 3, pad - 3))
                im.alpha_composite(tex("gui/minimap/north_tab.png"), (pad + D // 2 - 4, pad - 7))
    else:
        im.alpha_composite(body, (pad, pad))
        if border != "none":
            fr, b, grow = {"nero": ("frame_square_nero", 6, 3), "minimal": ("frame_square_minimal", 2, 2),
                           "contrast": ("frame_square_contrast", 2, 2)}[border]
            im.alpha_composite(nine(tex(f"gui/minimap/{fr}.png"), D + 2 * grow, D + 2 * grow, b), (pad - grow, pad - grow))
            if border == "nero":
                im.alpha_composite(tex("gui/minimap/north_tab.png"), (pad + D // 2 - 4, pad - 7))
    # readout plate
    plate = Image.new("RGBA", (D, 10), C['PANEL'] + (178,))
    im.alpha_composite(plate, (pad, pad + D + 5))
    g = tex("gui/minimap/readout_glyphs.png")
    im.alpha_composite(g.crop((0, 0, 7, 7)), (pad + 2, pad + D + 6))
    ImageDraw.Draw(im).text((pad + 12, pad + D + 4), "-128 71 304", fill=(232, 236, 244, 255))
    return im


def sheet_minimap(out):
    h = terrain(160, 54)
    day = to_img(apply_fog(style(h, "Day"))) if False else to_img(style(h, "Day"))
    hc = to_img(style(h, "High contrast"))
    s = Image.new("RGBA", (2440, 3 * 440 + 30), BG)
    row = 0
    for border in ("nero", "minimal", "contrast"):
        x = 10
        for shape in ("square", "round"):
            for D in (64, 96, 128, 160):
                m = up(minimap(D, shape, border, hc if border == "contrast" else day), 2)
                s.alpha_composite(m, (x, 30 + row * 440))
                label(s, x + 4, 14 + row * 440, f"{shape} {border} {D}")
                x += m.width + 6
        row += 1
    s.save(os.path.join(out, "minimap.png"))


# ------------------------------------------------------------------------------------------------ icons
def sheet_icons(out):
    s = Image.new("RGBA", (1340, 1560), BG)
    label(s, 10, 6, "map_icons.png at 1x (left) and 4x, white master")
    s.alpha_composite(ATLAS, (10, 24))
    mid = Image.new("RGBA", (1024, 1024), (86, 96, 108, 255))
    mid.alpha_composite(up(ATLAS, 4))
    s.alpha_composite(mid, (290, 24))
    # tinted on backgrounds
    bgs = [("day", (0x79, 0xC0, 0x5A)), ("night", (0x4B, 0x70, 0x5C)), ("water", (0x3F, 0x76, 0xE4)), ("fog", C['VOID']),
           ("high contrast", (0x6B, 0x6B, 0x6B))]
    ids = [k for k in MAN if not k.startswith("neromaps:halo") and not k.startswith("neromaps:glyph/")]
    y = 1064
    for name, bg in bgs:
        strip = Image.new("RGBA", (len(ids) * 20 + 8, 34), bg + (255,))
        for i, iid in enumerate(ids):
            strip.alpha_composite(icon(iid), (4 + i * 20, 2))
            strip.alpha_composite(icon(iid, micro=True), (8 + i * 20, 22))
        s.alpha_composite(up(strip, 1), (10, y))
        label(s, 10 + strip.width + 8, y + 10, name)
        y += 40
    # 3x versions of two strips for close review
    for name, bg in (bgs[0], bgs[3]):
        half = (len(ids) + 1) // 2
        for part in range(2):
            sub = ids[part * half:(part + 1) * half]
            strip = Image.new("RGBA", (len(sub) * 20 + 8, 34), bg + (255,))
            for i, iid in enumerate(sub):
                strip.alpha_composite(icon(iid), (4 + i * 20, 2))
                strip.alpha_composite(icon(iid, micro=True), (8 + i * 20, 22))
            s.alpha_composite(up(strip, 3), (10, y))
            y += 106
    # the seven marker colours on the waypoint circle
    for i, cname in enumerate(MARKER_COLOURS):
        s.alpha_composite(up(icon("neromaps:glyph/home", colour=C[cname]), 3), (1000 + (i % 4) * 54, 1070 + (i // 4) * 54))
    s.save(os.path.join(out, "icons.png"))


CVD = {
    "protanopia": [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
    "deuteranopia": [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
    "tritanopia": [[1.255528, -0.076749, -0.178779], [-0.078411, 0.930809, 0.147602], [0.004733, 0.691367, 0.303900]],
    "greyscale": [[0.2126, 0.7152, 0.0722]] * 3,
}


def simulate(im, m):
    a = np.asarray(im.convert("RGB"), dtype=np.float32) / 255.0
    lin = np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)
    o = np.clip(lin @ np.array(m).T, 0, 1)
    srgb = np.where(o <= 0.0031308, o * 12.92, 1.055 * o ** (1 / 2.4) - 0.055)
    return Image.fromarray((srgb * 255).astype(np.uint8), "RGB").convert("RGBA")


def sheet_cvd(out):
    carriers = ["neromaps:waypoint", "neromaps:death", "neromaps:signpost", "nerospace:station", "neroruins:ruin",
                "neroevents:event", "nerospace:launch_pad", "nerocreatures:my_creature", "nerocompanion:companion_lost",
                "nerospace:terraform_region", "neromaps:player_self", "neromaps:generic"]
    h = terrain(96, 55)
    bgimg = to_img(style(h, "Day")).crop((0, 0, 96, 40)).resize((len(carriers) * 22 + 8, 40), Image.NEAREST)
    strip = bgimg.copy()
    for i, iid in enumerate(carriers):
        strip.alpha_composite(icon(iid), (6 + i * 22, 4))
        strip.alpha_composite(icon(iid, micro=True), (10 + i * 22, 26))
    sw = Image.new("RGBA", (7 * 22 + 8, 40), (60, 66, 74, 255))
    for i, cname in enumerate(MARKER_COLOURS):
        sw.alpha_composite(icon("neromaps:waypoint", colour=C[cname]), (6 + i * 22, 4))
        sw.alpha_composite(icon("neromaps:waypoint", micro=True, colour=C[cname]), (10 + i * 22, 26))
    row = Image.new("RGBA", (strip.width + sw.width + 10, 40), BG)
    row.alpha_composite(strip, (0, 0))
    row.alpha_composite(sw, (strip.width + 10, 0))
    s = Image.new("RGBA", (row.width * 3 + 20, 5 * 150 + 10), BG)
    for i, (name, m) in enumerate([("normal vision", None)] + list(CVD.items())):
        r = row if m is None else simulate(row, m)
        s.alpha_composite(up(r, 3), (10, 24 + i * 150))
        label(s, 10, 8 + i * 150, name + "  (one of each carrier at 16 px and 8 px on Day terrain; right: the seven marker colours)")
    s.save(os.path.join(out, "icons_cvd.png"))


# ------------------------------------------------------------------------------------------------ blocks / items
def sheet_blocks(out):
    s = Image.new("RGBA", (1720, 1500), BG)
    names = ['cartography_desk', 'map_table', 'signpost_plate_1', 'signpost_plate_3', 'signpost_plate_2_diag', 'trail_marker',
             'trail_marker_wall', 'wall_map', 'wall_map_lit', 'wall_map_10']
    for i, nme in enumerate(names):
        m = art_models.BLOCK_MODELS[nme]
        if nme.startswith('signpost_plate'):
            m = dict(m)
            m['textures'] = dict(m['textures'], post='neromaps:block/signpost_post')
            m['elements'] = m['elements'] + art_models.BLOCK_MODELS['signpost_post']['elements']
        tintc = C[MARKER_COLOURS[i % 7]]
        r = art_models.render_model(m, 10, tint=tintc)
        x, y = 10 + (i % 5) * 320, 24 + (i // 5) * 350
        s.alpha_composite(r, (x, y))
        label(s, x, y - 14, nme)
    y0 = 740
    label(s, 10, y0 - 16, "item textures (6x)")
    files = sorted(os.listdir(os.path.join(T, "item")))
    for i, f in enumerate(files):
        s.alpha_composite(up(tex("item/" + f), 6), (10 + (i % 16) * 104, y0 + (i // 16) * 104))
    y1 = y0 + ((len(files) + 15) // 16) * 104 + 30
    label(s, 10, y1 - 16, "block textures (6x)")
    files = sorted(os.listdir(os.path.join(T, "block")))
    for i, f in enumerate(files):
        s.alpha_composite(up(tex("block/" + f), 6), (10 + (i % 16) * 104, y1 + (i // 16) * 104))
    s.save(os.path.join(out, "blocks_items.png"))


def sheet_gui(out):
    s = Image.new("RGBA", (1640, 1360), (70, 120, 80, 255))
    s.alpha_composite(up(tex("gui/map_widgets.png").crop((0, 0, 256, 130)), 4), (10, 10))
    s.alpha_composite(up(tex("gui/cartography_desk.png").crop((0, 0, 230, 186)), 3), (10, 550))
    s.alpha_composite(up(tex("gui/atlas_frame.png").crop((0, 0, 232, 186)), 3), (720, 550))
    x = 1050
    for f in ("gui/minimap/north_tab.png", "gui/minimap/player_arrow.png", "gui/map_player_prism.png", "gui/map_fog_edge.png",
              "gui/map_area_hatch.png"):
        im = up(tex(f), 8)
        s.alpha_composite(im, (x, 10))
        x += im.width + 10
    s.alpha_composite(up(tex("gui/minimap/readout_glyphs.png"), 6), (1050, 100))
    s.alpha_composite(up(tex("gui/map_fog_sample.png"), 4), (1050, 170))
    s.alpha_composite(up(tex("misc/waypoint_beam.png"), 3), (1330, 170))
    s.alpha_composite(up(tex("misc/waypoint_beam_dashed.png"), 3), (1390, 170))
    s.alpha_composite(up(tex("misc/waypoint_ground_hex.png"), 4), (1450, 170))
    s.alpha_composite(up(tex("misc/map_table_scanline.png"), 4), (1450, 310))
    s.save(os.path.join(out, "gui.png"))


def main():
    global ATLAS
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(artlib.OUT, "contact-sheets")
    os.makedirs(out, exist_ok=True)
    ATLAS = tex("gui/map_icons.png")
    art_models.desk(), art_models.wall_map(), art_models.signpost(), art_models.map_table(), art_models.trail_marker()
    sheet_styles(out)
    sheet_minimap(out)
    sheet_icons(out)
    sheet_cvd(out)
    sheet_blocks(out)
    sheet_gui(out)
    print("wrote 6 sheets to", out)


if __name__ == "__main__":
    main()
