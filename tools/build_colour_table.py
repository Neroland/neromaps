#!/usr/bin/env python3
"""Builds the NeroMaps block and biome colour table (decision 003).

Reads block textures from a vanilla client jar and from each Nero mod's own namespace, averages the top face of
every block (pixels with alpha >= 128, in linear RGB, back to sRGB), records the biome tint class, and writes one
deterministic JSON file per namespace:

    assets/neromaps/map_colours/<ns>.json   (client, reloadable by resource pack)
    data/neromaps/map_colours/<ns>.json     (server copy, for the link palette in a later stage)

Vanilla textures are read, never copied: only averaged colours are written. Pillow is a dev-time dependency of
this tool only. Blocks missing from the table fall back to their vanilla map colour in game.

Usage (from the repo root):
    python3 tools/build_colour_table.py --source <client.jar> --source ../nerospace/common/src/main/resources \
        [--source ...] --namespaces minecraft,nerospace,... --out common/src/main/resources

A --source is a jar/zip or a directory that contains assets/ (and, for vanilla, data/).
"""
import argparse
import io
import json
import os
import sys
import zipfile

from PIL import Image

GRASS_TINTED = {"grass_block", "short_grass", "grass", "tall_grass", "fern", "large_fern", "sugar_cane",
                "potted_fern", "pink_petals", "bush"}
FIXED_FOLIAGE = {"spruce_leaves": 0x619961, "birch_leaves": 0x80A755}
UNTINTED_LEAVES = {"azalea_leaves", "flowering_azalea_leaves", "cherry_leaves", "pale_oak_leaves"}


class Source:
    def __init__(self, path):
        self.path = path
        self.zip = zipfile.ZipFile(path) if os.path.isfile(path) else None
        # one index per source, so lookups never touch the file system on a miss
        if self.zip is not None:
            self.names = {n for n in self.zip.namelist() if not n.endswith("/")}
        else:
            self.names = set()
            for top in ("assets", "data"):
                base = os.path.join(path, top)
                for root, _, files in os.walk(base):
                    for f in files:
                        self.names.add(os.path.relpath(os.path.join(root, f), path).replace(os.sep, "/"))

    def read(self, rel):
        if rel not in self.names:
            return None
        if self.zip is not None:
            return self.zip.read(rel)
        with open(os.path.join(self.path, rel), "rb") as f:
            return f.read()

    def list(self, prefix):
        return [n for n in self.names if n.startswith(prefix)]


class Assets:
    def __init__(self, sources):
        self.sources = sources

    def read(self, rel):
        # later sources win (like resource packs)
        for s in reversed(self.sources):
            b = s.read(rel)
            if b is not None:
                return b
        return None

    def json(self, rel):
        b = self.read(rel)
        if b is None:
            return None
        try:
            return json.loads(b.decode("utf-8"))
        except ValueError:
            return None

    def list(self, prefix):
        names = set()
        for s in self.sources:
            names.update(s.list(prefix))
        return sorted(names)


def split_id(ref, default_ns="minecraft"):
    ref = ref.lstrip("#")
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = default_ns, ref
    return ns, path


def first_model(state):
    if "variants" in state:
        variants = state["variants"]
        key = "" if "" in variants else sorted(variants.keys())[0]
        v = variants[key]
        if isinstance(v, list):
            v = v[0]
        return v.get("model")
    if "multipart" in state:
        for part in state["multipart"]:
            a = part.get("apply")
            if isinstance(a, list):
                a = a[0]
            if a and "model" in a:
                return a["model"]
    return None


def resolve_model(assets, ref):
    """Returns (textures dict, elements list, tinted) merged along the parent chain."""
    textures = {}
    elements = None
    seen = 0
    while ref and seen < 16:
        seen += 1
        ns, path = split_id(ref)
        m = assets.json("assets/%s/models/%s.json" % (ns, path))
        if m is None:
            break
        tex = m.get("textures", {})
        for k, v in (tex.items() if isinstance(tex, dict) else []):
            textures.setdefault(k, v)
        if elements is None and "elements" in m:
            elements = m["elements"]
        ref = m.get("parent")
    tinted = False
    for e in elements or []:
        for face in e.get("faces", {}).values():
            if "tintindex" in face:
                tinted = True
    return textures, elements or [], tinted


def deref(textures, ref):
    for _ in range(16):
        if isinstance(ref, dict):
            ref = ref.get("sprite")
        if not isinstance(ref, str):
            return None
        if not ref.startswith("#"):
            return ref
        ref = textures.get(ref[1:])
    return None


def top_texture(textures, elements):
    best = None
    best_y = -1e9
    for e in elements:
        up = e.get("faces", {}).get("up")
        if up and "texture" in up:
            y = e.get("to", [0, 0, 0])[1]
            if y > best_y:
                best_y = y
                best = up["texture"]
    t = deref(textures, best) if best is not None else None
    if t:
        return t
    for k in ("top", "up", "end", "all", "texture", "cross", "plant", "side", "particle"):
        if k in textures:
            t = deref(textures, "#" + k)
            if t:
                return t
    return None


def srgb_to_linear(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


LIN = [srgb_to_linear(i) for i in range(256)]


def linear_to_srgb(c):
    c = 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return max(0, min(255, int(round(c * 255))))


def average(assets, tex_ref):
    ns, path = split_id(tex_ref)
    b = assets.read("assets/%s/textures/%s.png" % (ns, path))
    if b is None:
        return None
    try:
        im = Image.open(io.BytesIO(b)).convert("RGBA")
    except Exception:
        return None
    w, h = im.size
    if h > w:  # animated strip: first frame
        im = im.crop((0, 0, w, w))
    r = g = bl = 0.0
    n = 0
    data = im.tobytes()
    for i in range(0, len(data), 4):
        if data[i + 3] >= 128:
            r += LIN[data[i]]
            g += LIN[data[i + 1]]
            bl += LIN[data[i + 2]]
            n += 1
    if n == 0:
        return None
    return (linear_to_srgb(r / n) << 16) | (linear_to_srgb(g / n) << 8) | linear_to_srgb(bl / n)


def multiply(a, b):
    return ((((a >> 16) & 255) * ((b >> 16) & 255) // 255) << 16) | ((((a >> 8) & 255) * ((b >> 8) & 255) // 255) << 8) \
        | ((a & 255) * (b & 255) // 255)


def block_entry(assets, ns, name):
    state = assets.json("assets/%s/blockstates/%s.json" % (ns, name))
    if state is None:
        return None
    if ns == "minecraft" and name in ("water", "bubble_column"):
        rgb = average(assets, "minecraft:block/water_still")
        return {"rgb": "%06x" % (rgb if rgb is not None else 0xB0B0B0), "tint": "water"}
    if ns == "minecraft" and name == "lava":
        rgb = average(assets, "minecraft:block/lava_still")
        return {"rgb": "%06x" % (rgb if rgb is not None else 0xD45A12), "tint": "none"}
    model = first_model(state)
    if model is None:
        return None
    textures, elements, tinted = resolve_model(assets, model)
    tex = top_texture(textures, elements)
    if tex is None:
        return None
    rgb = average(assets, tex)
    if rgb is None:
        return None
    tint = "none"
    if tinted and ns == "minecraft":
        if name in FIXED_FOLIAGE:
            rgb = multiply(rgb, FIXED_FOLIAGE[name])
        elif name.endswith("leaves") and name not in UNTINTED_LEAVES or name in ("vine",):
            tint = "foliage"
        elif name in GRASS_TINTED or name.startswith("potted_"):
            tint = "grass"
        elif "lily_pad" in name:
            rgb = multiply(rgb, 0x208030)
        elif "stem" in name or "redstone" in name:
            pass
        else:
            tint = "grass"
    return {"rgb": "%06x" % rgb, "tint": tint}


def colour_value(v):
    if isinstance(v, str):
        return int(v.lstrip("#"), 16) & 0xFFFFFF
    if isinstance(v, int):
        return v & 0xFFFFFF
    return None


def find_key(obj, key):
    if isinstance(obj, dict):
        if key in obj:
            return obj[key]
        for v in obj.values():
            r = find_key(v, key)
            if r is not None:
                return r
    elif isinstance(obj, list):
        for v in obj:
            r = find_key(v, key)
            if r is not None:
                return r
    return None


def colormap(assets, name):
    b = assets.read("assets/minecraft/textures/colormap/%s.png" % name)
    return Image.open(io.BytesIO(b)).convert("RGB") if b else None


def sample(cmap, temp, down, fallback):
    if cmap is None:
        return fallback
    t = max(0.0, min(1.0, temp))
    d = max(0.0, min(1.0, down)) * t
    x = int((1.0 - t) * 255)
    y = int((1.0 - d) * 255)
    r, g, b = cmap.getpixel((x, y))
    return (r << 16) | (g << 8) | b


def biome_entries(assets, ns):
    grass = colormap(assets, "grass")
    foliage = colormap(assets, "foliage")
    out = {}
    for rel in assets.list("data/%s/worldgen/biome/" % ns):
        if not rel.endswith(".json"):
            continue
        b = assets.json(rel)
        if b is None:
            continue
        name = rel[len("data/%s/worldgen/biome/" % ns):-5]
        temp = float(b.get("temperature", 0.5))
        down = float(b.get("downfall", 0.5))
        g = colour_value(find_key(b, "grass_color"))
        f = colour_value(find_key(b, "foliage_color"))
        w = colour_value(find_key(b, "water_color"))
        mod = find_key(b, "grass_color_modifier")
        if g is None:
            g = sample(grass, temp, down, 0x7FB238)
            if mod == "swamp":
                g = 0x6A7039
            elif mod == "dark_forest":
                g = ((g & 0xFEFEFE) + 0x28340A) >> 1
        if f is None:
            f = sample(foliage, temp, down, 0x59AE30)
        out["%s:%s" % (ns, name)] = {"grass": "%06x" % g, "foliage": "%06x" % f,
                                     "water": "%06x" % (w if w is not None else 0x3F76E4)}
    return out


def build(assets, ns):
    blocks = {}
    for rel in assets.list("assets/%s/blockstates/" % ns):
        if not rel.endswith(".json"):
            continue
        name = rel[len("assets/%s/blockstates/" % ns):-5]
        e = block_entry(assets, ns, name)
        if e is not None:
            blocks["%s:%s" % (ns, name)] = e
    doc = {"version": 1, "blocks": dict(sorted(blocks.items()))}
    biomes = biome_entries(assets, ns)
    if biomes:
        doc["biomes"] = dict(sorted(biomes.items()))
    return doc


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", action="append", required=True, help="jar/zip or resources directory")
    ap.add_argument("--namespaces", required=True, help="comma-separated namespaces to emit")
    ap.add_argument("--out", required=True, help="resources root to write assets/ and data/ under")
    args = ap.parse_args(argv)
    assets = Assets([Source(s) for s in args.source])
    for ns in [n.strip() for n in args.namespaces.split(",") if n.strip()]:
        doc = build(assets, ns)
        if not doc["blocks"] and "biomes" not in doc:
            print("skip %s: no blocks" % ns)
            continue
        text = json.dumps(doc, indent=1, sort_keys=False) + "\n"
        for side in ("assets", "data"):
            d = os.path.join(args.out, side, "neromaps", "map_colours")
            os.makedirs(d, exist_ok=True)
            with open(os.path.join(d, ns + ".json"), "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
        print("%s: %d blocks, %d biomes" % (ns, len(doc["blocks"]), len(doc.get("biomes", {}))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
