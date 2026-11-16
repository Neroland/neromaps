"""Block and item textures (16x16, family rules: flat steps, top-left light, emissive parts as _glow layers)."""
import math
import random

from artlib import *  # noqa: F401,F403
from palette import C


def graphite(seed, w=16, h=16):
    rng = random.Random(seed)
    im = new(w, h, 'GRAPHITE')
    for _ in range(w * h // 9):
        im.putpixel((rng.randrange(w), rng.randrange(h)), col('GRAPHITE_4' if rng.random() < 0.6 else 'G_DARK'))
    return im


def steel_band(im, y0, y1):
    rect(im, 0, y0, im.width - 1, y1, 'STEEL')
    rect(im, 0, y0, im.width - 1, y0, 'G_LIGHT')
    rect(im, 0, y1, im.width - 1, y1, 'STEEL_DK')


def desk():
    # side: slab rows 0-5 are the slanted top's edge region (model maps the body from row 7 down)
    side = graphite(11)
    bevel(side, 0, 0, 15, 15, 'GRAPHITE_4', 'G_DARK')
    steel_band(side, 0, 1)
    steel_band(side, 6, 7)
    rect(side, 2, 10, 13, 10, 'G_DARK')
    rect(side, 2, 11, 13, 11, 'GRAPHITE_4')
    for x in (3, 6, 9, 12):
        rect(side, x, 13, x, 14, 'G_DARK')
    save(side, "block/cartography_desk_side.png", "Desk side (rows 0-1 slab edge, 6-7 body trim)")
    front = graphite(12)
    bevel(front, 0, 0, 15, 15, 'GRAPHITE_4', 'G_DARK')
    steel_band(front, 0, 1)
    steel_band(front, 6, 7)
    frame(front, 2, 9, 13, 14, 'G_DARK')
    rect(front, 3, 10, 12, 13, 'GRAPHITE_4')
    rect(front, 6, 11, 9, 11, 'STEEL')
    rect(front, 6, 12, 9, 12, 'STEEL_DK')
    front.putpixel((12, 10), col('MAP_ACCENT'))
    save(front, "block/cartography_desk_front.png", "Desk front: drawer with pull and status pixel")
    back = graphite(13)
    bevel(back, 0, 0, 15, 15, 'GRAPHITE_4', 'G_DARK')
    steel_band(back, 0, 1)
    steel_band(back, 6, 7)
    for y in (10, 12, 14):
        rect(back, 4, y, 11, y, 'G_DARK')
    save(back, "block/cartography_desk_back.png", "Desk back: vents")
    bottom = graphite(14)
    frame(bottom, 0, 0, 15, 15, 'G_DARK')
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        bottom.putpixel((x, y), col('TRIM'))
    save(bottom, "block/cartography_desk_bottom.png", "Desk underside")
    top = new(16, 16, 'STEEL')
    rect(top, 0, 0, 15, 0, 'G_LIGHT')
    rect(top, 0, 0, 0, 15, 'G_LIGHT')
    rect(top, 0, 15, 15, 15, 'STEEL_DK')
    rect(top, 15, 0, 15, 15, 'STEEL_DK')
    rect(top, 1, 1, 14, 14, 'G_DARK')
    rect(top, 2, 2, 13, 13, 'GLASS')
    for g in (5, 9):
        rect(top, g, 2, g, 13, 'MAP_ACCENT_DIM')
        rect(top, 2, g + 0, 13, g + 0, 'MAP_ACCENT_DIM')
    save(top, "block/cartography_desk_top.png", "Desk plotting surface: steel frame, dark glass, dim grid")
    glow = new(16, 16)
    for g in (5, 9):
        rect(glow, g, 2, g, 13, 'MAP_ACCENT')
        rect(glow, 2, g, 13, g, 'MAP_ACCENT')
    for (x, y) in ((3, 4), (4, 3), (6, 3), (7, 4), (8, 6), (7, 7), (6, 8), (4, 8), (3, 7), (3, 6),
                   (10, 10), (11, 11), (12, 11), (11, 7), (12, 6)):
        glow.putpixel((x, y), col('MAP_ACCENT_BRIGHT'))
    glow.putpixel((5, 6), col('M_ORANGE'))
    save(glow, "block/cartography_desk_top_glow.png", "Desk plotting grid and contour sketch (emissive overlay)")
    roll = new(16, 16, 'PAPER')
    for y in range(16):
        rect(roll, 0, y, 15, y, 'PAPER' if y % 4 != 3 else 'PAPER_DK')
    rect(roll, 0, 0, 15, 0, 'WHITE')
    for x in (0, 15):
        rect(roll, x, 0, x, 15, 'PAPER_DK')
    save(roll, "block/cartography_desk_paper.png", "Paper roll and stylus arm material (rows 0-7 paper, used by the roll element)")


def wall_map():
    for mask in range(16):      # bit 1 = connected north (up), 2 = east (right), 4 = south (down), 8 = west (left)
        im = new(16, 16, 'GLASS')
        for g in (3, 7, 11, 15):
            rect(im, g, 0, g, 15, (16, 22, 38))
            rect(im, 0, g, 15, g, (16, 22, 38))
        n, e, s, w = mask & 1, mask & 2, mask & 4, mask & 8
        if not n:
            rect(im, 0, 0, 15, 0, 'G_DARK')
            rect(im, 0, 1, 15, 1, 'G_LIGHT')
        if not s:
            rect(im, 0, 15, 15, 15, 'G_DARK')
            rect(im, 0, 14, 15, 14, 'STEEL_DK')
        if not w:
            rect(im, 0, 0, 0, 15, 'G_DARK')
            rect(im, 1, 0 if n else 1, 1, 15 if s else 14, 'G_LIGHT')
        if not e:
            rect(im, 15, 0, 15, 15, 'G_DARK')
            rect(im, 14, 0 if n else 1, 14, 15 if s else 14, 'STEEL_DK')
        if not n:
            rect(im, 0 if w else 1, 1, 15 if e else 14, 1, 'G_LIGHT')
            rect(im, 0, 0, 15, 0, 'G_DARK')
        if not s:
            rect(im, 0 if w else 1, 14, 15 if e else 14, 14, 'STEEL_DK')
            rect(im, 0, 15, 15, 15, 'G_DARK')
        if not w:
            rect(im, 0, 0, 0, 15, 'G_DARK')
        if not e:
            rect(im, 15, 0, 15, 15, 'G_DARK')
        if not n and not w:
            im.putpixel((1, 1), col('MAP_ACCENT'))
        if not n and not e:
            im.putpixel((14, 1), col('MAP_ACCENT'))
        if not s and not w:
            im.putpixel((1, 14), col('MAP_ACCENT'))
        if not s and not e:
            im.putpixel((14, 14), col('MAP_ACCENT_DIM'))
        save(im, f"block/wall_map_frame_{mask}.png",
             f"Wall-map face, connection mask {mask} (1 up, 2 right, 4 down, 8 left as seen from the front)")
    back = graphite(21)
    frame(back, 0, 0, 15, 15, 'G_DARK')
    rect(back, 7, 7, 8, 8, 'TRIM')
    save(back, "block/wall_map_back.png", "Wall-map back plate and edges")
    glow = new(16, 16)
    glow.putpixel((14, 14), col('MAP_ACCENT_BRIGHT'))
    glow.putpixel((13, 14), col('MAP_ACCENT'))
    save(glow, "block/wall_map_status_glow.png", "Wall-map status pixel (emissive), lower-right corner")


def signpost():
    post = graphite(31)
    for x in range(16):
        c = 'GRAPHITE_4' if x % 2 == 0 else 'GRAPHITE'
        rect(post, x, 0, x, 15, c)
    for y in (0, 1):
        rect(post, 0, y, 15, y, 'STEEL' if y else 'G_LIGHT')
    rect(post, 0, 14, 15, 14, 'STEEL_DK')
    rect(post, 0, 15, 15, 15, 'G_DARK')
    save(post, "block/signpost_post.png", "Signpost post (vertical graphite with a steel cap and foot)")
    plate = new(16, 16)
    # rows 0-3: plate face 14 wide. icon tile x1-2, glass strip x4-11, chevron x12-13
    rect(plate, 0, 0, 13, 3, 'STEEL')
    rect(plate, 0, 0, 13, 0, 'G_LIGHT')
    rect(plate, 0, 3, 13, 3, 'STEEL_DK')
    rect(plate, 1, 1, 2, 2, 'G_DARK')
    rect(plate, 4, 1, 11, 2, 'GLASS')
    plate.putpixel((12, 1), col('MAP_ACCENT'))
    plate.putpixel((13, 1), col('MAP_ACCENT'))
    plate.putpixel((13, 2), col('MAP_ACCENT'))
    plate.putpixel((12, 2), col('STEEL_DK'))
    # rows 5-6: edge material
    rect(plate, 0, 5, 15, 5, 'STEEL')
    rect(plate, 0, 6, 15, 6, 'STEEL_DK')
    # rows 8-11: plate back
    rect(plate, 0, 8, 13, 11, 'STEEL_DK')
    rect(plate, 0, 8, 13, 8, 'STEEL')
    rect(plate, 0, 11, 13, 11, 'G_DARK')
    save(plate, "block/signpost_plate.png", "Signpost plate: face rows 0-3 (icon tile 1-2, glass label strip 4-11, chevron 12-13), edge rows 5-6, back rows 8-11")
    glow = new(16, 16)
    rect(glow, 1, 1, 2, 2, 'WHITE')
    save(glow, "block/signpost_plate_glow.png", "Signpost icon tile (emissive, tintindex 0 = chosen icon colour)")


def map_table():
    top = new(16, 16, 'STEEL')
    rect(top, 0, 0, 15, 0, 'G_LIGHT')
    rect(top, 0, 0, 0, 15, 'G_LIGHT')
    rect(top, 0, 15, 15, 15, 'STEEL_DK')
    rect(top, 15, 0, 15, 15, 'STEEL_DK')
    rect(top, 1, 1, 14, 14, 'G_DARK')
    rect(top, 2, 2, 13, 13, 'GLASS')
    glow = new(16, 16)
    for y in range(3, 13, 2):
        for x in range(3 + ((y // 2) % 2), 13, 2):
            top.putpixel((x, y), col('MAP_ACCENT_DIM'))
            glow.putpixel((x, y), col('MAP_ACCENT' if (x + y) % 3 else 'MAP_ACCENT_BRIGHT'))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        top.putpixel((x, y), col('MAP_ACCENT'))
    save(top, "block/map_table_top.png", "Map Table top: steel frame, glass, hex emitter grid (dim)")
    save(glow, "block/map_table_top_glow.png", "Map Table emitter grid (emissive overlay)")
    side = graphite(41)
    # rows 5-7 = top slab edge (y 8-11); rows 8-13 column; rows 14-15 plinth
    rect(side, 0, 0, 15, 4, 'GRAPHITE')
    steel_band(side, 5, 7)
    side.putpixel((2, 6), col('MAP_ACCENT'))
    side.putpixel((13, 6), col('MAP_ACCENT'))
    rect(side, 0, 8, 15, 8, 'G_DARK')
    for x in (6, 9):
        rect(side, x, 9, x, 13, 'GRAPHITE_4')
    rect(side, 7, 10, 8, 12, 'MAP_ACCENT_DIM')
    rect(side, 0, 14, 15, 14, 'TRIM')
    rect(side, 0, 15, 15, 15, 'G_DARK')
    save(side, "block/map_table_side.png", "Map Table side (rows 5-7 slab edge, 8-13 column, 14-15 plinth)")
    bottom = graphite(42)
    frame(bottom, 0, 0, 15, 15, 'G_DARK')
    frame(bottom, 5, 5, 10, 10, 'TRIM')
    save(bottom, "block/map_table_bottom.png", "Map Table underside")


def trail_marker():
    im = new(16, 16)
    # spike: cols 0-1, rows 0-5 ; collar cols 4-6 row 0-0 ; cap base cols 8-9 rows 0-1
    rect(im, 0, 0, 1, 5, 'GRAPHITE')
    rect(im, 0, 0, 0, 5, 'GRAPHITE_4')
    rect(im, 0, 5, 1, 5, 'G_DARK')
    rect(im, 4, 0, 6, 0, 'STEEL')
    im.putpixel((4, 0), col('G_LIGHT'))
    im.putpixel((6, 0), col('STEEL_DK'))
    rect(im, 4, 2, 6, 4, 'STEEL')
    rect(im, 4, 2, 6, 2, 'G_LIGHT')
    rect(im, 8, 0, 9, 1, 'TRIM')
    save(im, "block/trail_marker.png", "Trail Marker: spike (0-1,0-5), collar side (4-6,0), collar top (4-6,2-4)")
    glow = new(16, 16)
    rect(glow, 8, 0, 9, 1, 'WHITE')
    rect(glow, 8, 2, 9, 3, (224, 224, 224))
    save(glow, "block/trail_marker_cap_glow.png", "Trail Marker cap, greyscale, emissive, tintindex 0 (cap top 8-9,0-1; cap side 8-9,2-3)")


ITEMS = {
    'atlas_blank': """
................
...kkkkkkkkkk...
..kllllllllsdk..
..klsssssssdpk..
..klsssssssdpk..
..klsskkkssdpk..
..klskmmmksdpk..
..klskmkmksdpk..
..klskmmmksdpk..
..klsskkkssdpk..
..klsssssssdpk..
..klsssssssdpk..
..ksddddddddpk..
..kkpppppppppk..
...kkkkkkkkkk...
................""",
    'atlas': """
................
...kkkkkkkkkk...
..kllllllllsdk..
..klsasssssdpk..
..klssaasssdpk..
..klsskkkasdpk..
..klskaaaksdpk..
..klskaAaksdpk..
..klakaaaksdpk..
..klsakkkssdpk..
..klssasssadpk..
..klsssaaasdpk..
..ksddddddddpk..
..kkpppppppppk..
...kkkkkkkkkk...
................""",
    'atlas_clasp_glow': """
................
................
................
................
................
................
......AAA.......
......AwA.......
......AAA.......
................
................
................
................
................
................
................""",
    'atlas_upgrade_pages': """
................
................
..kkkkkkkkkk....
..kppppppppkk...
..kpPPPPPPpkpk..
..kppppppppkpk..
..klllllllskpk..
..kssssasssksk..
..kddddddddkdk..
..kppppppppkpk..
..kpPPPPPPpkpk..
..kppppppppkpk..
..kkkkkkkkkkpk..
...kPPPPPPPPPk..
....kkkkkkkkkk..
................""",
    'atlas_upgrade_binding': """
................
................
.....kkkkkk.....
....kllllllk....
...klsskksssk...
..klskk..kksdk..
..klsk....ksdk..
..kaak....ksdk..
..kaak....ksdk..
..klsk....ksdk..
..klskk..kksdk..
...kssskkdddk...
....kddddddk....
.....kkkkkk.....
................
................""",
    'survey_kit': """
................
....kkkkkkkk....
...kllllllllk...
...klsssssddk...
...klskkkksdk...
...klskcckdsk...
...klskcckdsk...
...klskkkksdk...
...ksddddddkk...
....kkkttkkk....
......kttk......
.....ktkktk.....
....ktk.kktk....
...ktk.kt.ktk...
..kkk..kk..kkk..
................""",
    'survey_kit_lens_glow': """
................
................
................
................
................
.......cc.......
.......cw.......
................
................
................
................
................
................
................
................
................""",
    'signpost': """
................
......kkk.......
......klk.......
.kkkkkkgkkkkk...
.kllllllllllkk..
.kswxxxxxxsaak..
.ksdddddddddkk..
.kkkkkkgkkkkk...
...kkkkgkkkkkkk.
..kkllllllllllk.
..kaasxxxxxxwsk.
..kkdddddddddsk.
...kkkkgkkkkkkk.
......kgk.......
......kgk.......
......kkk.......""",
    'wall_map': """
................
.kkkkkkkkkkkkkk.
.kllllllllllldk.
.klxxxxxxxxxxdk.
.klxxnnxxxxxxdk.
.klxnnnnxxmxxdk.
.klxxnnnxmmmxdk.
.klxxxnxxxmxxdk.
.klxxxxxxxxxxdk.
.klxmmxxxoxxxdk.
.klxxmmxxxxxxdk.
.klxxxxxxxxxAdk.
.klddddddddddk..
.kkkkkkkkkkkkk..
................
................""",
    'trail_marker': """
................
................
.........kkkk...
........kwwwwk..
........kwwwwk..
........klllsk..
.......kksssdk..
......kgGkkkk...
.....kgGk.......
....kgGk........
...kgGk.........
..kgGk..........
..kgk...........
..kk............
................
................""",
}


def items():
    purposes = {'atlas_blank': "Blank atlas (dark clasp)", 'atlas': "Filled atlas (contour scrawl, lit clasp base)",
                'atlas_clasp_glow': "Atlas clasp, emissive layer1", 'atlas_upgrade_pages': "Atlas upgrade: page pack",
                'atlas_upgrade_binding': "Atlas upgrade: binding ring", 'survey_kit': "Survey Kit (folded tripod, sensor head)",
                'survey_kit_lens_glow': "Survey Kit lens, emissive layer1", 'signpost': "Signpost inventory icon",
                'trail_marker': "Trail Marker inventory icon (cap baked white; no tint layer)", 'wall_map': "Wall Map inventory icon"}
    for name, art in ITEMS.items():
        save(ascii_img(art, 16), f"item/{name}.png", purposes[name])
    # waypoint compass: 16 needle frames, frame 0 = needle up, clockwise 22.5 degrees per frame
    body = """
................
......kkkk......
......kssk......
....kkkkkkkk....
...kllsssssdk...
..klskkkkkksdk..
..kskxxxxxxkdk..
..kskxxxxxxkdk..
..kskxxxxxxkdk..
..kskxxxxxxkdk..
..kskxxxxxxkdk..
..kskxxxxxxkdk..
..ksdkkkkkkddk..
...kddddddddk...
....kkkkkkkk....
................"""
    base = ascii_img(body, 16)
    # round the glass corners
    for (x, y) in ((5, 6), (10, 6), (5, 11), (10, 11)):
        base.putpixel((x, y), col('G_DARK'))
    cx, cy = 7.5, 8.5
    for f in range(16):
        im = base.copy()
        ang = math.radians(f * 22.5)
        dx, dy = math.sin(ang), -math.cos(ang)
        x0, y0 = int(math.floor(cx - dx * 1.6 + 0.5)), int(math.floor(cy - dy * 1.6 + 0.5))
        x1, y1 = int(math.floor(cx + dx * 2.6 + 0.5)), int(math.floor(cy + dy * 2.6 + 0.5))
        n = max(abs(x1 - x0), abs(y1 - y0))
        mx, my = int(math.floor(cx - dx * 0.4 + 0.5)), int(math.floor(cy - dy * 0.4 + 0.5))
        for k in range(n + 1):
            x, y = int(round(x0 + (x1 - x0) * k / n)), int(round(y0 + (y1 - y0) * k / n))
            tail = (x - cx) * dx + (y - cy) * dy < -0.3
            im.putpixel((x, y), col('TRIM' if tail else 'MAP_ACCENT'))
        im.putpixel((x1, y1), col('WHITE'))
        save(im, f"item/waypoint_compass_{f:02d}.png", f"Waypoint Compass needle frame {f} ({f * 22.5:g} deg clockwise from up)")
    glow = new(16, 16)
    glow.putpixel((7, 8), col('MAP_ACCENT_BRIGHT'))
    glow.putpixel((8, 8), col('MAP_ACCENT_BRIGHT'))
    glow.putpixel((7, 9), col('MAP_ACCENT_BRIGHT'))
    glow.putpixel((8, 9), col('WHITE'))
    save(glow, "item/waypoint_compass_locked_glow.png", "Waypoint Compass locked-on centre (emissive layer1)")


def run():
    desk()
    wall_map()
    signpost()
    map_table()
    trail_marker()
    items()
