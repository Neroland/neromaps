#!/usr/bin/env python3
"""
gen_map_icons.py -- draws the NeroMaps marker / layer icon atlas and its manifest.

    python3 tools/art/gen_map_icons.py           # write textures/gui/map_icons.png + tools/art/out/map_icons.json
    python3 tools/art/gen_map_icons.py --check   # fail on a missing micro, a duplicate slot or a stale atlas

Atlas: 256x256. y 0-191 = 16x16 cells (16 columns x 12 rows, slots 0-191, art inside 14x14 at +1,+1).
y 192-255 = 8x8 micro cells (32 columns x 8 rows; micro slot i belongs to 16 px slot i, art 7x7 at +0,+0).
Every icon is a white carrier with a dark outline and a dark glyph, tinted at draw time. "Inverted" carriers
(ring, dashed ring, area) carry a dark core and a white glyph instead. Needs Pillow.
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from palette import C, FONT  # noqa: E402
from icon_glyphs import G  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "neromaps", "textures", "gui")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
DARK = C['G_DARK'] + (255,)
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)


# ------------------------------------------------------------------------------------------- 16 px carriers
def m_circle(x, y):
    return (x - 6.5) ** 2 + (y - 6.5) ** 2 <= 7.05 ** 2


def m_tomb(x, y):
    if not 1 <= x <= 12:
        return False
    return (x - 6.5) ** 2 + (y - 6.0) ** 2 <= 6.3 ** 2 if y < 6 else True


def m_pennant(x, y):
    if 1 <= x <= 3:
        return True
    if 3 <= x <= 13 and 0 <= y <= 8:
        return not (x >= 10 and abs(y - 4) <= (x - 10))
    return False


def m_square(x, y):
    return 1 <= x <= 12 and 1 <= y <= 12


def m_diamond(x, y):
    return abs(x - 6.5) + abs(y - 6.5) <= 7.0


def m_tri_up(x, y):
    hw = (y + 2) // 2
    return 7 - hw <= x <= 6 + hw


def m_tri_down(x, y):
    return m_tri_up(x, 13 - y)


def m_full(x, y):
    return True


def m_arrow(x, y):
    hw = (y + 2) // 2
    if not 7 - hw <= x <= 6 + hw:
        return False
    return not (y >= 11 and abs(x - 6.5) < (y - 10) * 2)


def m_chevron(x, y):
    if not 2 <= y <= 10:
        return False
    d = abs(x - 6.5)
    top = 2 + d
    return top <= y <= top + 3.6


MASKS = {'circle': m_circle, 'tombstone': m_tomb, 'pennant': m_pennant, 'square': m_square, 'diamond': m_diamond,
         'tri_up': m_tri_up, 'tri_down': m_tri_down, 'ring': m_circle, 'ring_dashed': m_circle, 'area': m_full,
         'arrow': m_arrow, 'chevron': m_chevron}
# glyph centre in 14x14 space
CENTRE = {'circle': (7, 7), 'tombstone': (7, 7.5), 'pennant': (7.5, 4.5), 'square': (7, 7), 'diamond': (7, 7),
          'tri_up': (7, 9.5), 'tri_down': (7, 4.5), 'ring': (7, 7), 'ring_dashed': (7, 7), 'area': (7, 7),
          'arrow': (7, 8), 'chevron': (7, 7)}
INVERTED = {'ring', 'ring_dashed', 'area'}


def grid_from_mask(fn):
    return [[bool(fn(x, y)) for x in range(14)] for y in range(14)]


def is_edge(grid, x, y):
    n = len(grid)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        xx, yy = x + dx, y + dy
        if not (0 <= xx < len(grid[0]) and 0 <= yy < n) or not grid[yy][xx]:
            return True
    return False


def carrier16(kind):
    """14x14 RGBA: outline dark, body white; inverted carriers get a dark core."""
    grid = grid_from_mask(MASKS[kind])
    im = Image.new("RGBA", (14, 14), CLEAR)
    for y in range(14):
        for x in range(14):
            if not grid[y][x]:
                continue
            px = DARK if is_edge(grid, x, y) else WHITE
            if kind in ('ring', 'ring_dashed') and px == WHITE:
                r = math.hypot(x - 6.5, y - 6.5)
                if r <= 4.3:
                    px = DARK
                elif kind == 'ring_dashed':
                    a = (math.degrees(math.atan2(y - 6.5, x - 6.5)) + 360 + 22.5) % 90
                    if a < 45 - 12 or a > 45 + 12:
                        pass
                    else:
                        px = DARK
            if kind == 'area' and px == WHITE:
                ring = min(x, y, 13 - x, 13 - y)
                if ring >= 2:
                    px = DARK
                else:  # ring == 1 : dashed line, 3 on / 2 off
                    t = x if y in (1, 12) else y
                    px = WHITE if (t - 1) % 5 < 3 else DARK
            if kind == 'pennant' and px == WHITE and x == 3 and y <= 8:
                px = DARK  # mast line through the flag
            im.putpixel((x, y), px)
    return im, grid


def stamp(im, art, cx, cy, colour):
    h, w = len(art), max(len(r) for r in art)
    x0, y0 = int(math.floor(cx - w / 2 + 0.5)), int(math.floor(cy - h / 2 + 0.5))
    for j, row in enumerate(art):
        for i, ch in enumerate(row):
            if ch == '#' and 0 <= x0 + i < im.width and 0 <= y0 + j < im.height:
                im.putpixel((x0 + i, y0 + j), colour)


def icon16(kind, glyph):
    im, _ = carrier16(kind)
    if glyph and glyph != 'none':
        cx, cy = CENTRE[kind]
        stamp(im, G[glyph][0], cx, cy, WHITE if kind in INVERTED else DARK)
    cell = Image.new("RGBA", (16, 16), CLEAR)
    cell.paste(im, (1, 1))
    return cell


def halo16(kind):
    """1 px ring just outside the carrier silhouette, white (tinted MAP_ACCENT_BRIGHT at draw time)."""
    grid = grid_from_mask(MASKS[kind])
    cell = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            gx, gy = x - 1, y - 1
            inside = 0 <= gx < 14 and 0 <= gy < 14 and grid[gy][gx]
            if inside:
                continue
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    xx, yy = gx + dx, gy + dy
                    if 0 <= xx < 14 and 0 <= yy < 14 and grid[yy][xx]:
                        cell.putpixel((x, y), WHITE)
    return cell


# ------------------------------------------------------------------------------------------- 8 px micro carriers
# '#' dark, 'o' white, 'k' dark core (inverted), '.' clear. 7x7 art in the 8x8 cell at +0,+0.
MICRO = {
    'circle': ("..###..|.#ooo#.|#ooooo#|#ooooo#|#ooooo#|.#ooo#.|..###..", (2, 2)),
    'tombstone': ("..###..|.#ooo#.|#ooooo#|#ooooo#|#ooooo#|#ooooo#|#######", (2, 2)),
    'pennant': ("######.|#ooooo#|#oooo#.|#ooooo#|#o####.|#o#....|###....", (2, 1)),
    'square': ("#######|#ooooo#|#ooooo#|#ooooo#|#ooooo#|#ooooo#|#######", (2, 2)),
    'diamond': ("...#...|..#o#..|.#ooo#.|#ooooo#|.#ooo#.|..#o#..|...#...", (2, 2)),
    'tri_up': ("...#...|..#o#..|..#o#..|.#ooo#.|.#ooo#.|#ooooo#|#######", (2, 3)),
    'tri_down': ("#######|#ooooo#|.#ooo#.|.#ooo#.|..#o#..|..#o#..|...#...", (2, 1)),
    'ring': ("..###..|.#ooo#.|#okkko#|#okkko#|#okkko#|.#ooo#.|..###..", (2, 2)),
    'ring_dashed': ("..###..|.#oko#.|#okkko#|#kkkkk#|#okkko#|.#oko#.|..###..", (2, 2)),
    'area': ("#######|#o#o#o#|##kkk##|#okkko#|##kkk##|#o#o#o#|#######", (2, 2)),
    'arrow': ("...#...|..#o#..|..#o#..|.#ooo#.|.#ooo#.|#oo#oo#|###.###", None),
    'chevron': (".......|...#...|..#o#..|.#o#o#.|#o#.#o#|.#...#.|.......", None),
}
RAW8 = {  # radar blips and other micro-only art
    'radar_hostile': "...#...|..#o#..|..#o#..|.#ooo#.|.#ooo#.|#ooooo#|#######",
    'radar_passive': ".......|..###..|.#ooo#.|.#ooo#.|.#ooo#.|..###..|.......",
    'radar_tamed': "..###..|.#ooo#.|#o###o#|#o#.#o#|#o###o#|.#ooo#.|..###..",
    'radar_vehicle': ".......|######.|#oooo#.|#oooo#.|#oooo#.|#oooo#.|######.",
    'radar_item': "..###..|..#o#..|###o###|#ooooo#|###o###|..#o#..|..###..",
    'radar_player': "...#...|..#o#..|..#o#..|.#o#o#.|.#o#o#.|#o###o#|###.###",
    'trail_marker': ".......|..###..|.#ooo#.|.#ooo#.|.#ooo#.|..###..|.......",
    'badge_hostile': "#####..|#o#o#..|##o##..|#o#o#..|#####..|.......|.......",
    'badge_neutral': "#####..|#####..|#ooo#..|#####..|#####..|.......|.......",
    'badge_friendly': "#####..|##o##..|#ooo#..|##o##..|#####..|.......|.......",
    'north': ".#####.|.#o#o#.|.#oo##.|.#ooo#.|.##oo#.|.#o#o#.|.#####.",
}


def paint(art, w=8, h=8):
    im = Image.new("RGBA", (w, h), CLEAR)
    for y, row in enumerate(art.split('|')):
        for x, ch in enumerate(row):
            if ch in '#k':
                im.putpixel((x, y), DARK)
            elif ch == 'o':
                im.putpixel((x, y), WHITE)
    return im


def icon8(kind, glyph):
    art, anchor = MICRO[kind]
    im = paint(art)
    if glyph and glyph != 'none' and anchor:
        rows = G[glyph][1]
        col = WHITE if kind in INVERTED else DARK
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                x, y = anchor[0] + i, anchor[1] + j
                if ch != '#':
                    continue
                cur = im.getpixel((x, y))
                # only draw where the carrier has body (white) or core (dark, for inverted)
                if (col == DARK and cur == WHITE) or (col == WHITE and cur == DARK and art.split('|')[y][x] == 'k'):
                    im.putpixel((x, y), col)
    return im


def halo8(kind):
    """Micro halo = the carrier's own outline pixels in white; drawn over the icon it brightens the outline."""
    art = MICRO[kind][0]
    im = Image.new("RGBA", (8, 8), CLEAR)
    rows = art.split('|')
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '#':
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if not (0 <= xx < 7 and 0 <= yy < 7) or rows[yy][xx] == '.':
                    im.putpixel((x, y), WHITE)
                    break
    return im


def scale2(im8):
    """Legend version of a micro-only shape: the 7x7 art doubled and centred in the 16 px cell."""
    big = im8.crop((0, 0, 7, 7)).resize((14, 14), Image.NEAREST)
    cell = Image.new("RGBA", (16, 16), CLEAR)
    cell.paste(big, (1, 1))
    return cell


def north16():
    cell = Image.new("RGBA", (16, 16), CLEAR)
    for j, row in enumerate(FONT['N']):
        for i, ch in enumerate(row):
            if ch == '1':
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        if cell.getpixel((5 + i + dx, 4 + j + dy)) != WHITE:
                            cell.putpixel((5 + i + dx, 4 + j + dy), DARK)
                cell.putpixel((5 + i, 4 + j), WHITE)
    return cell


def badge16(kind):
    """7x7 badge plate at the cell origin: dark plate, white 3x3 glyph doubled is too big, so 5x5 strokes."""
    arts = {'badge_hostile': "#######|#o###o#|##o#o##|###o###|##o#o##|#o###o#|#######",
            'badge_neutral': "#######|#######|#######|#ooooo#|#######|#######|#######",
            'badge_friendly': "#######|###o###|###o###|#ooooo#|###o###|###o###|#######"}
    cell = Image.new("RGBA", (16, 16), CLEAR)
    cell.paste(paint(arts[kind], 7, 7), (0, 0))
    return cell


# ------------------------------------------------------------------------------------------- the icon table
# (id, carrier, glyph, default colour, cut-by-review flag, special)
ICONS = {}


def add(slot, iid, carrier, glyph, colour, cut=False, special=None, tint=True):
    assert slot not in [v['slot'] for v in ICONS.values()], f"duplicate slot {slot}"
    assert iid not in ICONS, iid
    ICONS[iid] = dict(slot=slot, carrier=carrier, glyph=glyph, colour=colour, cut=cut, special=special, tint=tint)


# core 0-31
add(0, 'neromaps:generic', 'ring', 'dot2', 'M_WHITE')
add(1, 'neromaps:waypoint', 'circle', 'dot', 'M_ORANGE')
add(2, 'neromaps:waypoint_temp', 'ring_dashed', 'dot2', 'M_ORANGE')
add(3, 'neromaps:death', 'tombstone', 'skull', 'M_WHITE')
add(4, 'neromaps:signpost', 'pennant', 'none', 'M_WHITE')
add(5, 'neromaps:trail_marker', 'raw', None, 'M_WHITE', special='trail_marker')
add(6, 'neromaps:player_self', 'arrow', 'none', 'MAP_ACCENT_BRIGHT')
add(7, 'neromaps:player_shared', 'ring', 'arrow_s', 'MAP_ACCENT_BRIGHT')
add(8, 'neromaps:radar_hostile', 'raw', None, 'M_VERMILLION', special='radar_hostile')
add(9, 'neromaps:radar_passive', 'raw', None, 'M_WHITE', special='radar_passive')
add(10, 'neromaps:radar_tamed', 'raw', None, 'M_PURPLE', special='radar_tamed')
add(11, 'neromaps:radar_vehicle', 'raw', None, 'M_GREEN', special='radar_vehicle')
add(12, 'neromaps:radar_item', 'raw', None, 'M_YELLOW', special='radar_item')
add(13, 'neromaps:radar_player', 'raw', None, 'MAP_ACCENT_BRIGHT', special='radar_player')
add(14, 'neromaps:area', 'area', 'none', 'M_SKY')
add(15, 'neromaps:badge_hostile', 'badge', None, 'M_VERMILLION', special='badge_hostile')
add(16, 'neromaps:badge_neutral', 'badge', None, 'M_WHITE', special='badge_neutral')
add(17, 'neromaps:badge_friendly', 'badge', None, 'M_GREEN', special='badge_friendly')
add(18, 'neromaps:star_chart', 'square', 'sparkle', 'M_SKY')
add(19, 'neromaps:north', 'raw', None, 'MAP_ACCENT_BRIGHT', special='north')
add(20, 'neromaps:offscreen_chevron', 'chevron', 'none', 'M_WHITE')
for n, k in enumerate(['circle', 'tombstone', 'pennant', 'square', 'diamond', 'tri_up', 'tri_down', 'ring', 'arrow']):
    add(21 + n, f'neromaps:halo_{k}', k, None, 'MAP_ACCENT_BRIGHT', special='halo')
# slots 30-31 spare

# waypoint glyphs 32-47 (48-63 spare)
WAYPOINT_GLYPHS = ['dot', 'home', 'bed', 'pickaxe', 'sword', 'chest', 'portal', 'anchor', 'wheat', 'tower', 'cave',
                   'star', 'heart', 'flag', 'question', 'exclamation']
for n, k in enumerate(WAYPOINT_GLYPHS):
    add(32 + n, f'neromaps:glyph/{k}', 'circle', k, 'M_ORANGE')

# sibling layers 64-167, eight slots per mod
SIB = [
    (64, 'nerospace:station', 'square', 'orbit', 'M_SKY', False),
    (65, 'nerospace:launch_pad', 'tri_down', 'pad', 'M_GREEN', False),
    (66, 'nerospace:rocket', 'tri_down', 'rocket', 'M_GREEN', False),
    (67, 'nerospace:terraform_region', 'area', 'leaf', 'M_GREEN', False),
    (68, 'nerospace:meteor_site', 'tri_up', 'meteor', 'M_VERMILLION', True),
    (72, 'nerotech:pollution', 'area', 'cloud', 'M_VERMILLION', False),
    (80, 'neropower:reactor', 'square', 'atom', 'M_SKY', False),
    (81, 'neropower:beam_link', 'square', 'bolt', 'M_SKY', False),
    (82, 'neropower:scorch_zone', 'tri_up', 'flame', 'M_VERMILLION', False),
    (88, 'nerologistics:drone_port', 'tri_down', 'rotor', 'M_GREEN', False),
    (89, 'nerologistics:train_station', 'tri_down', 'rails', 'M_GREEN', False),
    (90, 'nerologistics:drive_bay', 'square', 'bars', 'M_SKY', True),
    (91, 'nerologistics:rocket_flight', 'tri_down', 'arrow_up', 'M_GREEN', True),
    (96, 'nerocolonies:colony_border', 'area', 'beacon', 'M_SKY', False),
    (97, 'nerocolonies:colony_building', 'square', 'home', 'M_SKY', False),
    (98, 'nerocolonies:colony_storage', 'square', 'crate', 'M_SKY', False),
    (99, 'nerocolonies:outpost', 'square', 'tent', 'M_SKY', False),
    (104, 'nerofactions:outpost', 'diamond', 'banner', 'M_YELLOW', False),
    (112, 'neroeconomy:my_shop', 'square', 'coin', 'M_ORANGE', False),
    (120, 'nerosecurity:installation', 'square', 'shield', 'M_SKY', False),
    (121, 'nerosecurity:field', 'area', 'shield', 'M_SKY', False),
    (128, 'neroevents:event', 'tri_up', 'burst', 'M_VERMILLION', False),
    (129, 'neroevents:storm', 'tri_up', 'lightning', 'M_VERMILLION', False),
    (130, 'neroevents:war_zone', 'tri_up', 'blades', 'M_VERMILLION', False),
    (136, 'neroruins:ruin', 'diamond', 'pillar', 'M_YELLOW', False),
    (137, 'neroruins:vault_claimed', 'diamond', 'lock_open', 'M_YELLOW', False),
    (138, 'neroruins:expedition_target', 'diamond', 'crosshair', 'M_YELLOW', False),
    (144, 'nerocreatures:my_creature', 'ring', 'paw', 'M_PURPLE', False),
    (145, 'nerocreatures:my_altar', 'diamond', 'bowl', 'M_YELLOW', True),
    (152, 'nerocompanion:companion', 'ring', 'visor', 'M_PURPLE', False),
    (153, 'nerocompanion:companion_lost', 'ring_dashed', 'question_s', 'M_PURPLE', False),
    (154, 'nerocompanion:home_bay', 'square', 'arch', 'M_SKY', False),
    (160, 'neroagriculture:farm', 'square', 'sprout', 'M_SKY', True),
]
for s, iid, car, gl, col, cut in SIB:
    add(s, iid, car, gl, col, cut)


def draw(iid):
    e = ICONS[iid]
    sp = e['special']
    if sp == 'halo':
        return halo16(e['carrier']), halo8(e['carrier'])
    if sp == 'north':
        return north16(), paint(RAW8['north'])
    if sp and sp.startswith('badge'):
        return badge16(sp), paint(RAW8[sp])
    if sp:
        m = paint(RAW8[sp])
        return scale2(m), m
    return icon16(e['carrier'], e['glyph']), icon8(e['carrier'], e['glyph'])


def rect16(slot):
    return [(slot % 16) * 16, (slot // 16) * 16, 16, 16]


def rect8(slot):
    return [(slot % 32) * 8, 192 + (slot // 32) * 8, 8, 8]


def build():
    atlas = Image.new("RGBA", (256, 256), CLEAR)
    for iid, e in ICONS.items():
        big, small = draw(iid)
        assert small.getbbox() is not None, f"missing micro for {iid}"
        assert big.getbbox() is not None, f"missing icon for {iid}"
        r, m = rect16(e['slot']), rect8(e['slot'])
        atlas.paste(big, (r[0], r[1]))
        atlas.paste(small, (m[0], m[1]))
    return atlas


def manifest():
    icons = {}
    for iid, e in sorted(ICONS.items(), key=lambda kv: kv[1]['slot']):
        from palette import HEX
        icons[iid] = {"slot": e['slot'], "carrier": e['carrier'], "glyph": e['glyph'],
                      "colour": HEX[e['colour']], "colour_name": e['colour'],
                      "uv": rect16(e['slot']), "micro_uv": rect8(e['slot']),
                      "inverted": e['carrier'] in INVERTED, "shipped_layer": not e['cut']}
    return {"texture": "neromaps:textures/gui/map_icons.png", "size": [256, 256], "cell": 16, "micro_cell": 8,
            "columns": 16, "micro_columns": 32, "micro_y": 192, "art_inset": 1, "micro_art": 7,
            "fallback": "neromaps:generic", "waypoint_glyphs": [f"neromaps:glyph/{k}" for k in WAYPOINT_GLYPHS],
            "icons": icons}


def main():
    atlas = build()
    mtxt = json.dumps(manifest(), indent=2) + "\n"
    png = os.path.join(TEX, "map_icons.png")
    mpath = os.path.join(OUT, "map_icons.json")
    if "--check" in sys.argv:
        ok = os.path.exists(png) and list(Image.open(png).convert("RGBA").getdata()) == list(atlas.getdata())
        ok2 = os.path.exists(mpath) and open(mpath, encoding="utf-8").read() == mtxt
        print("atlas", "matches" if ok else "DIFFERS", "| manifest", "matches" if ok2 else "DIFFERS",
              "|", len(ICONS), "icons")
        sys.exit(0 if ok and ok2 else 1)
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    atlas.save(png)
    with open(mpath, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(mtxt)
    print(f"wrote {png} ({len(ICONS)} icons) and {mpath}")


if __name__ == "__main__":
    main()
