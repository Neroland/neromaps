#!/usr/bin/env python3
"""
Generate the NEROMAPS mod logo (square), in the shared Neroland family style: a deep-space
starfield with two nebula glows and the family faceted hexagonal core-prism with a glowing centre
node + specular sparkle.

NeroMaps is the minimap / world-map mod, so the family core-prism is the player marker at the
centre of a round periwinkle (#7c8cff) minimap: a bezel with N/E/S/W ticks, terrain bands and
contour lines inside, a dotted route to a waypoint pin, all standing on the family perspective
grid. The wordmark and tagline are drawn in a built-in 5x7 pixel font (no system font needed, so
the output is identical on every OS). Renders supersampled, then downsamples.

Outputs:
  art/logo/neromaps_logo.png       (1024x1024 master)
  art/logo/neromaps_logo_400.png   (CurseForge-ready)
  common/src/main/resources/neromaps_logo.png  (256x256 in-game mods-list icon)

Usage: python tools/gen_logo.py   (needs Pillow and numpy)
"""
SEED = 53
MOD = 'neromaps'
NAME = 'NEROMAPS'
TAG = 'EXPLORE · MARK · SHARE'
ACCENT = (124, 140, 255)         # family colour #7c8cff (periwinkle)
ACCENT_BRIGHT = (178, 190, 255)
BRIGHT = (236, 240, 255)
NEBULA = (36, 40, 120)
STAR_TINT = (206, 214, 255)
TAG_COL = (196, 204, 255)
GOLD = (255, 214, 90)
PIN = (255, 92, 92)

import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "art", "logo")
ICON = os.path.join(ROOT, "common", "src", "main", "resources")

FINAL = 1024
SS = 2
R = FINAL * SS
rng = random.Random(SEED)

NERO_ALLOY = (38, 166, 154)     # family teal
STARSTEEL  = (140, 178, 208)    # family steel-blue
PLASMA     = (96, 212, 232)     # family cyan
FACET_COLS = [STARSTEEL, NERO_ALLOY, ACCENT, STARSTEEL, NERO_ALLOY, ACCENT]

# ---- 5x7 pixel font ------------------------------------------------------------------------------
GLYPHS = {
    'A': ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    'B': ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    'C': ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    'D': ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    'E': ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    'H': ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    'I': ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    'K': ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    'L': ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    'M': ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    'N': ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'P': ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    'R': ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'X': ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    'U': ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    'Y': ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    '·': ["00000", "00000", "00000", "00100", "00000", "00000", "00000"],
    ' ': ["00000"] * 7,
}



def text_cells(text, gap=1):
    """(col, row) of every lit pixel, and the text's width in pixel columns."""
    cells, x = [], 0
    for ch in text:
        g = GLYPHS[ch]
        for r, row in enumerate(g):
            for c, bit in enumerate(row):
                if bit == '1':
                    cells.append((x + c, r))
        x += len(g[0]) + gap
    return cells, x - gap


def sprite_cells(rows):
    return [(c, r) for r, row in enumerate(rows) for c, bit in enumerate(row) if bit == '1']


# ---- background ----------------------------------------------------------------------------------
def background():
    top = np.array([6, 11, 17], float)
    bot = np.array([13, 18, 28], float)
    yy = np.linspace(0, 1, R)[:, None, None]
    img = top[None, None, :] * (1 - yy) + bot[None, None, :] * yy
    img = np.repeat(img, R, axis=1)
    Y, X = np.mgrid[0:R, 0:R].astype(float)

    def glow(cx, cy, rad, color, strength):
        d = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
        f = np.clip(1 - d / rad, 0, 1) ** 2 * strength
        for c in range(3):
            img[:, :, c] += color[c] * f

    glow(R * 0.22, R * 0.26, R * 0.55, (20, 80, 84), 0.42)   # family teal nebula
    glow(R * 0.80, R * 0.40, R * 0.55, NEBULA, 0.50)         # mod accent nebula
    glow(R * 0.5, R * 0.36, R * 0.40, (24, 30, 80), 0.30)    # glow behind the minimap

    d = np.sqrt((X - R / 2) ** 2 + (Y - R / 2) ** 2) / (R * 0.72)
    vig = np.clip(1 - (d ** 2) * 0.85, 0.25, 1)
    img *= vig[:, :, None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def add_stars(base):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(380):
        x, y = rng.randint(0, R), rng.randint(0, int(R * 0.66))
        s = rng.choice([1, 1, 1, 2, 2, 3]) * SS
        b = rng.randint(120, 255)
        tint = rng.choice([(b, b, b), (b, 255, 255), STAR_TINT, (200, 200, 255)])
        d.rectangle([x, y, x + s, y + s], fill=tint + (rng.randint(120, 255),))  # square: pixel stars
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(2 * SS)))
    base.alpha_composite(layer)
    return base


def soft(draw_fn, blur):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    return layer.filter(ImageFilter.GaussianBlur(blur))


def neon(base, draw_fn, blur=10):
    """A shape drawn three times: wide glow, tight glow, crisp core."""
    base.alpha_composite(soft(draw_fn, blur * SS))
    base.alpha_composite(soft(draw_fn, max(1, blur * SS // 4)))
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    base.alpha_composite(layer)


def grid_floor(base, horizon):
    """A retro perspective grid from the horizon to the bottom edge, fading out with distance."""
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    vx = R / 2
    for i in range(-14, 15):
        x_bottom = vx + i * R * 0.11
        d.line([(vx + i * R * 0.012, horizon), (x_bottom, R)], fill=ACCENT + (120,), width=SS * 2)
    for k in range(1, 14):
        t = (k / 13) ** 2.1
        y = horizon + (R - horizon) * t
        d.line([(0, y), (R, y)], fill=ACCENT + (int(40 + 110 * t),), width=SS * 2)
    # fade toward the horizon
    mask = np.zeros((R, R), np.uint8)
    ys = np.arange(R)
    fade = np.clip((ys - horizon) / (R - horizon), 0, 1) ** 0.8
    mask[:] = (fade * 255).astype(np.uint8)[:, None]
    a = np.array(layer.split()[3]).astype(float) * (mask / 255.0)
    layer.putalpha(Image.fromarray(a.astype(np.uint8)))
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(4 * SS)))
    base.alpha_composite(layer)
    # horizon glow line
    neon(base, lambda dr: dr.line([(0, horizon), (R, horizon)], fill=ACCENT_BRIGHT + (200,), width=SS * 2), 12)


# ---- the family core-prism ----------------------------------------------------------------------
def draw_core_prism(base, cx, cy, rad):
    base.alpha_composite(soft(
        lambda dr: dr.ellipse([cx - rad * 1.3, cy - rad * 1.3, cx + rad * 1.3, cy + rad * 1.3],
                              fill=ACCENT + (110,)), 20 * SS))
    hexpts = [(cx + math.cos(math.radians(60 * i - 90)) * rad,
               cy + math.sin(math.radians(60 * i - 90)) * rad) for i in range(6)]
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(6):
        shade = 0.58 + 0.42 * (i / 5.0)
        col = tuple(int(c * shade) for c in FACET_COLS[i])
        d.polygon([(cx, cy), hexpts[i], hexpts[(i + 1) % 6]], fill=col + (255,))
    ir = rad * 0.36
    d.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill=ACCENT_BRIGHT + (255,))
    d.ellipse([cx - ir * 0.5, cy - ir * 0.5, cx + ir * 0.5, cy + ir * 0.5], fill=BRIGHT + (255,))
    for i in range(6):
        d.line([hexpts[i], hexpts[(i + 1) % 6]], fill=(230, 240, 250, 235), width=max(1, SS * 2))
        d.line([(cx, cy), hexpts[i]], fill=(220, 230, 240, 150), width=max(1, SS))
    base.alpha_composite(layer)
    sx, sy = cx - rad * 0.16, cy - rad * 0.46
    base.alpha_composite(soft(lambda dr: dr.ellipse([sx - 7 * SS, sy - 7 * SS, sx + 7 * SS, sy + 7 * SS],
                                                    fill=(255, 255, 255, 255)), 4 * SS))
    dd = ImageDraw.Draw(base)
    L = 13 * SS
    dd.line([sx - L, sy, sx + L, sy], fill=(255, 255, 255, 230), width=SS * 2)
    dd.line([sx, sy - L, sx, sy + L], fill=(255, 255, 255, 230), width=SS * 2)


def pixels(d, cells, ox, oy, px, fill, inset=0.12):
    g = px * inset
    for c, r in cells:
        x, y = ox + c * px, oy + r * px
        d.rectangle([x + g, y + g, x + px - g, y + px - g], fill=fill)


# ---- the minimap ---------------------------------------------------------------------------------
def minimap(base):
    cx, cy = R / 2, R * 0.335
    rad = R * 0.255
    # outer glow + bezel
    base.alpha_composite(soft(lambda dr: dr.ellipse([cx - rad * 1.12, cy - rad * 1.12, cx + rad * 1.12,
                                                     cy + rad * 1.12], fill=ACCENT + (150,)), 40 * SS))
    d = ImageDraw.Draw(base)
    d.ellipse([cx - rad * 1.08, cy - rad * 1.08, cx + rad * 1.08, cy + rad * 1.08], fill=(22, 24, 40, 255))

    # map face: terrain bands clipped to the disc
    face = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    fd = ImageDraw.Draw(face)
    fd.rectangle([cx - rad, cy - rad, cx + rad, cy + rad], fill=(18, 52, 74, 255))         # sea
    land = [(cx - rad * 0.95, cy - rad * 0.10), (cx - rad * 0.55, cy - rad * 0.62), (cx - rad * 0.05, cy - rad * 0.80),
            (cx + rad * 0.45, cy - rad * 0.58), (cx + rad * 0.92, cy - rad * 0.18), (cx + rad * 0.80, cy + rad * 0.40),
            (cx + rad * 0.30, cy + rad * 0.70), (cx - rad * 0.30, cy + rad * 0.62), (cx - rad * 0.80, cy + rad * 0.35)]
    fd.polygon(land, fill=(40, 96, 72, 255))                                                # lowland
    for k, col in enumerate([(62, 124, 84), (92, 140, 90), (128, 150, 104), (168, 170, 138)]):
        s = 0.78 - k * 0.17
        pts = [(cx + (x - cx) * s + rad * 0.06 * k, cy + (y - cy) * s - rad * 0.03 * k) for x, y in land]
        fd.polygon(pts, fill=col + (255,), outline=(20, 40, 30, 150))
    # contour lines
    for k in range(1, 6):
        s = 0.95 - k * 0.16
        pts = [(cx + (x - cx) * s + rad * 0.05 * k, cy + (y - cy) * s - rad * 0.025 * k) for x, y in land]
        fd.line(pts + [pts[0]], fill=(220, 230, 255, 70), width=SS * 2)
    # chunk grid
    step = rad / 4
    for i in range(-4, 5):
        fd.line([(cx + i * step, cy - rad), (cx + i * step, cy + rad)], fill=(255, 255, 255, 22), width=SS)
        fd.line([(cx - rad, cy + i * step), (cx + rad, cy + i * step)], fill=(255, 255, 255, 22), width=SS)
    mask = Image.new("L", (R, R), 0)
    ImageDraw.Draw(mask).ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=255)
    face.putalpha(Image.fromarray(np.minimum(np.array(face.split()[3]), np.array(mask)).astype(np.uint8)))
    base.alpha_composite(face)
    # inner vignette so the edge reads as glass
    vg = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    ImageDraw.Draw(vg).ellipse([cx - rad, cy - rad, cx + rad, cy + rad], outline=(0, 0, 0, 160), width=int(rad * 0.10))
    base.alpha_composite(vg.filter(ImageFilter.GaussianBlur(10 * SS)))

    # dotted route from the centre to the waypoint pin
    px_, py_ = cx + rad * 0.52, cy - rad * 0.46
    route = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    rd = ImageDraw.Draw(route)
    for i in range(1, 12):
        t = i / 12
        x = cx + (px_ - cx) * t + math.sin(t * math.pi) * rad * 0.10
        y = cy + (py_ - cy) * t + math.sin(t * math.pi) * rad * 0.12
        rd.ellipse([x - SS * 5, y - SS * 5, x + SS * 5, y + SS * 5], fill=GOLD + (255,))
    base.alpha_composite(route.filter(ImageFilter.GaussianBlur(4 * SS)))
    base.alpha_composite(route)
    # waypoint pin + beam
    neon(base, lambda dr: dr.line([(px_, py_ - rad * 0.52), (px_, py_)], fill=PIN + (120,), width=SS * 6), 10)
    pr = rad * 0.09
    neon(base, lambda dr: dr.polygon([(px_ - pr, py_ - pr * 1.9), (px_ + pr, py_ - pr * 1.9), (px_, py_)],
                                     fill=PIN + (255,)), 8)
    neon(base, lambda dr: dr.ellipse([px_ - pr, py_ - pr * 2.9, px_ + pr, py_ - pr * 0.9], fill=PIN + (255,)), 8)
    ImageDraw.Draw(base).ellipse([px_ - pr * 0.4, py_ - pr * 2.3, px_ + pr * 0.4, py_ - pr * 1.5],
                                 fill=(255, 235, 235, 255))

    # the family core-prism is the player marker at the centre
    draw_core_prism(base, cx, cy, int(rad * 0.26))

    # bezel ring with compass ticks and an N marker
    neon(base, lambda dr: dr.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], outline=ACCENT + (255,), width=SS * 6), 14)
    tk = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    td = ImageDraw.Draw(tk)
    for i in range(72):
        a = math.radians(i * 5 - 90)
        long_ = i % 18 == 0
        mid = i % 6 == 0
        r0 = rad * (1.015 if long_ else 1.03)
        r1 = rad * (1.075 if long_ else (1.06 if mid else 1.05))
        td.line([(cx + math.cos(a) * r0, cy + math.sin(a) * r0), (cx + math.cos(a) * r1, cy + math.sin(a) * r1)],
                fill=(BRIGHT if long_ else ACCENT_BRIGHT) + (255 if long_ else 160,), width=SS * (4 if long_ else 2))
    base.alpha_composite(tk)
    # N marker: a small pixel N above the ring on a lit plate
    ncells, ncols = text_cells("N")
    npx = R * 0.0085
    nx, ny = cx - ncols * npx / 2, cy - rad * 1.20 - 7 * npx / 2
    plate = [nx - npx * 1.4, ny - npx * 1.2, nx + ncols * npx + npx * 1.4, ny + 7 * npx + npx * 1.2]
    neon(base, lambda dr: dr.rounded_rectangle(plate, radius=SS * 8, fill=(30, 34, 70, 255),
                                               outline=ACCENT + (255,), width=SS * 3), 10)
    nl = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    pixels(ImageDraw.Draw(nl), ncells, nx, ny, npx, BRIGHT + (255,), inset=0.06)
    base.alpha_composite(nl)
    # floor glow under the map
    base.alpha_composite(soft(lambda dr: dr.ellipse([cx - rad * 0.9, R * 0.615, cx + rad * 0.9, R * 0.655],
                                                     fill=ACCENT + (150,)), 14 * SS))


# ---- pixel wordmark -----------------------------------------------------------------------------
def pixel_text(base, text, cy, max_w, px_max, face_top, face_bot, glow, extrude=0.0, gap=1):
    cells, cols = text_cells(text, gap)
    px = min(px_max, max_w / cols)
    w = cols * px
    ox, oy = (R - w) / 2, cy - 7 * px / 2
    # glow
    gl = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    pixels(ImageDraw.Draw(gl), cells, ox, oy, px, glow + (255,), inset=0.0)
    base.alpha_composite(gl.filter(ImageFilter.GaussianBlur(px * 1.1)))
    # extrusion (retro 3-D drop)
    if extrude > 0:
        ex = Image.new("RGBA", (R, R), (0, 0, 0, 0))
        ed = ImageDraw.Draw(ex)
        steps = int(px * extrude)
        for k in range(steps, 0, -SS):
            shade = 0.25 + 0.35 * (1 - k / steps)
            pixels(ed, cells, ox + k * 0.55, oy + k, px, tuple(int(c * shade) for c in ACCENT) + (255,), 0.0)
        base.alpha_composite(ex)
    # face: vertical gradient, each pixel a bevelled block
    face = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    fd = ImageDraw.Draw(face)
    for c, r in cells:
        t = r / 6.0
        col = tuple(int(face_top[i] * (1 - t) + face_bot[i] * t) for i in range(3))
        x, y = ox + c * px, oy + r * px
        g = px * 0.06
        fd.rectangle([x + g, y + g, x + px - g, y + px - g], fill=col + (255,))
        hi = tuple(min(255, int(v + (255 - v) * 0.55)) for v in col)
        fd.rectangle([x + g, y + g, x + px - g, y + g + px * 0.18], fill=hi + (255,))
        lo = tuple(int(v * 0.72) for v in col)
        fd.rectangle([x + g, y + px - g - px * 0.14, x + px - g, y + px - g], fill=lo + (255,))
    base.alpha_composite(face)
    return oy + 7 * px


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(ICON, exist_ok=True)
    img = background()
    img = add_stars(img)
    horizon = R * 0.60
    grid_floor(img, horizon)
    minimap(img)
    # dark band behind the wordmark so it reads over the grid
    img.alpha_composite(soft(lambda dr: dr.rectangle([0, R * 0.665, R, R * 0.935], fill=(6, 8, 14, 205)), 30 * SS))
    pixel_text(img, NAME, R * 0.755, R * 0.90, R * 0.016, BRIGHT, ACCENT, ACCENT, extrude=0.55)
    pixel_text(img, TAG, R * 0.885, R * 0.62, R * 0.0072, TAG_COL, TAG_COL, ACCENT)

    final = img.convert("RGB").resize((FINAL, FINAL), Image.LANCZOS)
    p1 = os.path.join(OUT, MOD + "_logo.png")
    p2 = os.path.join(OUT, MOD + "_logo_400.png")
    p3 = os.path.join(ICON, MOD + "_logo.png")
    final.save(p1)
    final.resize((400, 400), Image.LANCZOS).save(p2)
    final.resize((256, 256), Image.LANCZOS).save(p3)
    for p in (p1, p2, p3):
        print("wrote", os.path.relpath(p, ROOT))




def generate_gif(source=None, output=None):
    source = source or os.path.join(OUT, "neromaps_logo.png")
    output = output or os.path.join(OUT, "neromaps_logo.gif")
    with Image.open(source) as image:
        logo = image.convert("RGB")
    logo.thumbnail((400, 400), Image.Resampling.LANCZOS)
    w, h = logo.size
    color = ACCENT
    frames = []
    for index in range(14):
        t = index / 14
        phase = 2 * math.pi * t
        overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        draw = ImageDraw.Draw(overlay)
        # a radar sweep turning around the minimap centre
        cx, cy, r = w * 0.5, h * 0.335, w * 0.245
        draw.line((cx, cy, cx + math.cos(phase - math.pi / 2) * r, cy + math.sin(phase - math.pi / 2) * r),
                  fill=color + (150,), width=max(1, w // 160))
        overlay = overlay.filter(ImageFilter.MaxFilter(3))
        image = logo.convert("RGBA")
        image.alpha_composite(overlay.filter(ImageFilter.GaussianBlur(max(1, w * 0.008))))
        image.alpha_composite(overlay)
        frames.append(image.convert("RGB"))
    frames[0].save(output, save_all=True, append_images=frames[1:], duration=200, loop=0, optimize=True)
    return output


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Generate the logo or animate its existing master PNG.")
    parser.add_argument("--gif", action="store_true", help="generate the animated logo")
    args = parser.parse_args()
    if args.gif:
        print("wrote", os.path.relpath(generate_gif(), ROOT))
    else:
        main()
