"""Shared helpers for the NeroMaps art tools (Pillow only)."""
import json
import os

from PIL import Image

from palette import C

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "neromaps")
TEX = os.path.join(ASSETS, "textures")
MODELS = os.path.join(ASSETS, "models")
OUT = os.path.join(HERE, "out")
CLEAR = (0, 0, 0, 0)
WRITTEN = []          # (relative path under assets/neromaps, (w, h) or None, purpose)

KEY = {'.': CLEAR, 'k': 'G_DARK', 'g': 'GRAPHITE', 'G': 'GRAPHITE_4', 't': 'TRIM', 's': 'STEEL', 'd': 'STEEL_DK',
       'l': 'G_LIGHT', 'a': 'MAP_ACCENT', 'A': 'MAP_ACCENT_BRIGHT', 'm': 'MAP_ACCENT_DIM', 'p': 'PAPER',
       'P': 'PAPER_DK', 'c': 'PLASMA', 'n': 'NERO_ALLOY', 'w': 'WHITE', 'x': 'GLASS', 'e': 'PANEL', 'E': 'PANEL_EDGE',
       'T': 'TEXT', 'u': 'TEXT_2', 'v': 'VOID', 'o': 'M_ORANGE', 'r': 'M_VERMILLION'}


def col(name, a=255):
    if isinstance(name, tuple):
        return name if len(name) == 4 else name + (a,)
    return C[name] + (a,)


def new(w, h, fill=CLEAR):
    return Image.new("RGBA", (w, h), col(fill) if fill != CLEAR else CLEAR)


def rect(im, x0, y0, x1, y1, c, a=255):
    """Inclusive rectangle fill."""
    c = col(c, a)
    for y in range(max(0, y0), min(im.height, y1 + 1)):
        for x in range(max(0, x0), min(im.width, x1 + 1)):
            im.putpixel((x, y), c)


def frame(im, x0, y0, x1, y1, c, a=255):
    rect(im, x0, y0, x1, y0, c, a)
    rect(im, x0, y1, x1, y1, c, a)
    rect(im, x0, y0, x0, y1, c, a)
    rect(im, x1, y0, x1, y1, c, a)


def bevel(im, x0, y0, x1, y1, light, dark):
    rect(im, x0, y0, x1, y0, light)
    rect(im, x0, y0, x0, y1, light)
    rect(im, x0, y1, x1, y1, dark)
    rect(im, x1, y0, x1, y1, dark)


def ascii_img(rows, w=None, key=None, a=255):
    key = key or KEY
    rows = [r for r in rows.strip('\n').split('\n')] if isinstance(rows, str) else rows
    w = w or len(rows[0])
    for r in rows:
        assert len(r) == w, f"row width {len(r)} != {w}: {r!r}"
    im = new(w, len(rows))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            v = key[ch]
            if v != CLEAR:
                im.putpixel((x, y), col(v, a))
    return im


def stamp(dst, src, x, y):
    dst.alpha_composite(src, (x, y))


def save(im, rel, purpose):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    WRITTEN.append(("textures/" + rel, im.size, purpose))
    return path


def save_json(obj, base, rel, purpose):
    path = os.path.join(base, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(obj, indent=2) + "\n")
    if base == MODELS:
        WRITTEN.append(("models/" + rel, None, purpose))
    return path
