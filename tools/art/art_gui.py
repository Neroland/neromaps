"""GUI art: minimap frames, masks, readouts, widgets, fog, beams, screens."""
import math

from artlib import *  # noqa: F401,F403
from palette import C, FONT

RECTS = {}   # sheet -> {name: [u, v, w, h] (+ border)}


def reg(sheet, name, x, y, w, h, border=None, note=None):
    e = {"uv": [x, y, w, h]}
    if border is not None:
        e["border"] = border
    if note:
        e["note"] = note
    RECTS.setdefault(sheet, {})[name] = e


def text57(im, s, x, y, c):
    for ch in s:
        for j, row in enumerate(FONT[ch]):
            for i, bit in enumerate(row):
                if bit == '1':
                    im.putpixel((x + i, y + j), col(c))
        x += 6


# ----------------------------------------------------------------------------------------------- minimap frames
def square_frames():
    im = new(32, 32)
    frame(im, 0, 0, 31, 31, 'G_DARK')
    frame(im, 1, 1, 30, 30, 'STEEL')
    rect(im, 1, 1, 30, 1, 'G_LIGHT')
    rect(im, 1, 1, 1, 30, 'G_LIGHT')
    frame(im, 2, 2, 29, 29, 'GLASS')
    for (cx, sx) in ((1, 1), (30, -1)):
        for (cy, sy) in ((1, 1), (30, -1)):
            for k in range(5):
                im.putpixel((cx + sx * k, cy), col('MAP_ACCENT'))
                im.putpixel((cx, cy + sy * k), col('MAP_ACCENT'))
            im.putpixel((cx, cy), col('MAP_ACCENT_BRIGHT'))
    save(im, "gui/minimap/frame_square_nero.png", "Square minimap bezel, nine-slice border 6 (visible frame 3 px)")
    im = new(16, 16)
    frame(im, 0, 0, 15, 15, 'G_DARK')
    frame(im, 1, 1, 14, 14, 'TRIM')
    save(im, "gui/minimap/frame_square_minimal.png", "Minimal square border, nine-slice border 2")
    im = new(16, 16)
    frame(im, 0, 0, 15, 15, 'WHITE')
    frame(im, 1, 1, 14, 14, 'WHITE')
    save(im, "gui/minimap/frame_square_contrast.png", "High-contrast square rule, nine-slice border 2")


def round_frames():
    for D in (64, 96, 128, 160):
        S = D + 6
        c = (S - 1) / 2.0
        R = D / 2.0 + 3
        nero, ticks, minimal, contrast = new(S, S), new(S, S), new(S, S), new(S, S)
        for y in range(S):
            for x in range(S):
                dx, dy = x - c, y - c
                r = math.hypot(dx, dy)
                band = R - r            # 0..1 outer, 1..2 steel, 2..3 glass, >3 map
                if band < 0 or band >= 3:
                    continue
                b = int(band)
                tl = (-dx - dy) / (r + 1e-9) > 0.9
                nero.putpixel((x, y), col(('G_DARK', 'G_LIGHT' if tl else 'STEEL', 'GLASS')[b]))
                if b >= 1:
                    minimal.putpixel((x, y), col(('G_DARK', 'TRIM')[b - 1]))
                    contrast.putpixel((x, y), col('WHITE'))
                ang = math.degrees(math.atan2(dx, -dy)) % 360       # 0 = north, clockwise
                k = round(ang / 45.0) % 8
                arc = abs(((ang - k * 45.0 + 180) % 360) - 180) * math.pi / 180.0 * r
                if k == 0 and arc <= 2.5:
                    ticks.putpixel((x, y), col('MAP_ACCENT_BRIGHT'))
                elif k in (2, 4, 6) and arc <= 1.5:
                    ticks.putpixel((x, y), col('MAP_ACCENT'))
                elif k % 2 == 1 and arc <= 0.75 and b <= 1:
                    ticks.putpixel((x, y), col('MAP_ACCENT'))
        mask = new(D, D)
        mc = (D - 1) / 2.0
        for y in range(D):
            for x in range(D):
                if math.hypot(x - mc, y - mc) <= D / 2.0:
                    mask.putpixel((x, y), (255, 255, 255, 255))
        save(nero, f"gui/minimap/frame_round_nero_{D}.png", f"Round bezel (static) for a {D} px map; draw at map origin -3,-3")
        save(ticks, f"gui/minimap/ticks_round_{D}.png", f"Tick ring for a {D} px map; rotates with the map about its centre")
        save(minimal, f"gui/minimap/frame_round_minimal_{D}.png", f"Minimal ring for a {D} px map")
        save(contrast, f"gui/minimap/frame_round_contrast_{D}.png", f"High-contrast ring for a {D} px map")
        save(mask, f"gui/minimap/mask_round_{D}.png", f"Alpha mask (white disc) for the {D} px round map")


def small_sprites():
    im = new(9, 9)
    rect(im, 0, 0, 8, 8, 'G_DARK')
    rect(im, 1, 1, 7, 7, 'PANEL')
    text57(im, 'N', 2, 1, 'MAP_ACCENT_BRIGHT')
    save(im, "gui/minimap/north_tab.png", "N tab: sits centred on the north side of the square frame; drawn upright at the rotated north point of the round frame")
    for ch in 'ESW':
        im = new(9, 9)
        rect(im, 0, 0, 8, 8, 'G_DARK')
        rect(im, 1, 1, 7, 7, 'PANEL')
        text57(im, ch, 2, 1, 'MAP_ACCENT')
        save(im, f"gui/minimap/cardinal_{ch.lower()}.png", f"Optional {ch} tab (setting: show all cardinals)")
    arrow = ascii_img("""
...k...
..kAk..
..kAk..
.kAwAk.
.kAwAk.
kAAwAAk
kAAwAAk
kAkkkAk
kk...kk""")
    save(arrow, "gui/minimap/player_arrow.png", "Minimap player arrow 7x9, pivot at 3,5; points up (north)")
    prism = ascii_img("""
.....k.....
....kAk....
..kkkAkkk..
.kaaaAaaak.
kammaAammak
kammwwwmmak
kammwwwmmak
kaammmmmaak
.kaaaaaaak.
..kkaaakk..
....kkk....""", key=dict(KEY, m='MAP_ACCENT_DIM'))
    save(prism, "gui/map_player_prism.png", "World-map 'you are here' hex prism 11x11, pivot 5,6; nub points north")
    glyphs = [
        ".......|...#...|...#...|.##.##.|...#...|...#...|.......",   # coordinates (crosshair)
        "...#...|..###..|.#.#.#.|...#...|...#...|...#...|.......",   # facing (arrow)
        "..###..|.#####.|.#####.|..###..|...#...|...#...|..###..",   # biome (tree)
        ".#####.|.#...#.|.#.#.#.|.#.#.#.|.#.#.#.|.#...#.|.#####.",   # dimension (portal)
        "..###..|.#.#.#.|#..#..#|#..##.#|#.....#|.#...#.|..###..",   # clock
        "#..#..#|.#...#.|..###..|#.###.#|..###..|.#...#.|#..#..#",   # light (sun)
        "...##..|..##...|.####..|...##..|..##...|.##....|.......",   # storm badge (lightning)
        ".......|.......|..###..|..###..|..###..|.......|.......",   # spare (dot)
    ]
    im = new(64, 8)
    names = ['coords', 'facing', 'biome', 'dimension', 'clock', 'light', 'storm', 'spare']
    for i, g in enumerate(glyphs):
        for y, row in enumerate(g.split('|')):
            for x, ch in enumerate(row):
                if ch == '#':
                    im.putpixel((i * 8 + x, y), (255, 255, 255, 255))
        reg('readout_glyphs', names[i], i * 8, 0, 7, 7)
    save(im, "gui/minimap/readout_glyphs.png", "Eight 7x7 white readout glyphs at x = i*8: coords, facing, biome, dimension, clock, light, storm, spare")
    im = new(8, 8)
    for y in range(8):
        for x in range(8):
            if (x + y) % 4 == 0:
                im.putpixel((x, y), (255, 255, 255, 255))
    save(im, "gui/map_area_hatch.png", "Tileable 4 px diagonal hatch (white, tinted) for hazard areas")


# ----------------------------------------------------------------------------------------------- fog
def star(x, z):
    h = (x * 374761393 + z * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return h % 96 == 0


def fog():
    edge = new(4, 4)
    for y in range(4):
        edge.putpixel((0, y), col('VOID_EDGE'))
        if y % 4 == 1:
            edge.putpixel((1, y), col('VOID'))
        if y % 2 == 0:
            edge.putpixel((2, y), col('VOID'))
        edge.putpixel((3, y), col('VOID'))
    save(edge, "gui/map_fog_edge.png", "Fog edge strip 4x4, tiles along the boundary: x0 rim (explored side), x1 25 % void, x2 50 % void, x3 void. Rotate for the other three sides")
    stars = new(96, 96, 'VOID')
    for y in range(96):
        for x in range(96):
            if star(x, y):
                stars.putpixel((x, y), col('VOID_STAR'))
    save(stars, "gui/map_fog_stars.png", "96x96 void with the hashed star speckle (reference / optional tile; the engine can hash instead)")
    # reference sample: explored terrain on the left, fog on the right, chunk-aligned stair boundary
    im = new(64, 64)
    import random
    rng = random.Random(53)
    for y in range(64):
        for x in range(64):
            h = math.sin(x / 9.0) * 3 + math.cos(y / 7.0) * 3 + math.sin((x + y) / 5.0) * 2
            base = C['NERO_ALLOY'] if h < -3 else (88, 140, 72) if h < 2 else (120, 124, 112)
            if h < -3:
                base = (44, 95, 168)
            d = round(h - (math.sin(x / 9.0) * 3 + math.cos((y - 1) / 7.0) * 3 + math.sin((x + y - 1) / 5.0) * 2
                           + math.sin((x - 1) / 9.0) * 3 + math.cos(y / 7.0) * 3 + math.sin((x + y - 1) / 5.0) * 2) / 2 * 1.0)
            mul = {-2: 0.78, -1: 0.89, 0: 1.0, 1: 1.08, 2: 1.16}[max(-2, min(2, d * 2))]
            im.putpixel((x, y), tuple(min(255, int(v * mul)) for v in base) + (255,))

    def explored(cx, cz):
        return cx < (2 if cz < 2 else 3 if cz < 3 else 1)
    for y in range(64):
        for x in range(64):
            if explored(x // 16, y // 16):
                # distance (in columns) to the nearest unexplored chunk, 4-neighbour
                dist = 99
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    for k in range(1, 4):
                        xx, yy = x + dx * k, y + dy * k
                        if 0 <= xx < 64 and 0 <= yy < 64 and not explored(xx // 16, yy // 16):
                            dist = min(dist, k)
                if dist == 3:
                    im.putpixel((x, y), col('VOID_EDGE'))
                elif dist == 2 and (x + y) % 4 == 1 and y % 2 == 1:
                    im.putpixel((x, y), col('VOID'))
                elif dist == 1 and (x + y) % 2 == 0:
                    im.putpixel((x, y), col('VOID'))
            else:
                im.putpixel((x, y), col('VOID_STAR' if star(x, y) else 'VOID'))
    save(im, "gui/map_fog_sample.png", "Reference render of fog speckle, dither and rim (visual test target; not loaded at runtime)")


# ----------------------------------------------------------------------------------------------- beams
def beams():
    for dashed in (False, True):
        im = new(16, 64)
        for y in range(64):
            if dashed and (y % 32) >= 16:
                continue
            band = y % 64 < 6
            soft = y % 64 in (6, 7, 62, 63)
            for x in range(16):
                d = abs(x - 7.5)
                if d <= 1:
                    px = (255, 255, 255, 255)
                elif d <= 3:
                    px = (224, 224, 224, 255)
                else:
                    a = 150 if band else 118 if soft else 89
                    if d > 6:
                        a = int(a * 0.6)
                    px = (255, 255, 255, a)
                im.putpixel((x, y), px)
        save(im, "misc/waypoint_beam_dashed.png" if dashed else "misc/waypoint_beam.png",
             "Greyscale beam strip 16x64 (= 0.5 block wide x 4 blocks), tinted, scroll v upward 1 block/s"
             + ("; dashed for death points" if dashed else ""))
    im = new(32, 32)
    c = 15.5
    for y in range(32):
        for x in range(32):
            dx, dy = abs(x - c), abs(y - c)
            hexd = max(dx, dx * 0.5 + dy * 0.866)
            if 12.5 <= hexd <= 14.5:
                im.putpixel((x, y), (255, 255, 255, 255))
            elif 14.5 < hexd <= 15.5 or 11.5 <= hexd < 12.5:
                im.putpixel((x, y), (255, 255, 255, 110))
            elif hexd < 11.5 and ((x + y) % 4 == 0) and hexd > 7:
                im.putpixel((x, y), (255, 255, 255, 60))
            elif hexd <= 2.5:
                im.putpixel((x, y), (255, 255, 255, 200))
    save(im, "misc/waypoint_ground_hex.png", "Ground hex ring 32x32 (2x2 blocks), white, tinted, drawn flat and full-bright")
    im = new(16, 16)
    for y in range(16):
        a = 70 if y % 4 == 0 else 18 if y % 4 == 1 else 0
        if a:
            rect(im, 0, y, 15, y, 'MAP_ACCENT_BRIGHT', a)
    save(im, "misc/map_table_scanline.png", "Hologram scanline overlay for the Map Table relief (scroll v slowly)")


# ----------------------------------------------------------------------------------------------- widgets
ICON9 = {
    'zoom_in': "....#....|....#....|....#....|....#....|#########|....#....|....#....|....#....|....#....",
    'zoom_out': ".........|.........|.........|.........|#########|.........|.........|.........|.........",
    'recentre': "....#....|....#....|..#####..|..#...#..|###.#.###|..#...#..|..#####..|....#....|....#....",
    'search': "..####...|.#....#..|#......#.|#......#.|#......#.|.#....#..|..####...|......##.|.......##",
    'add_waypoint': "..###....|.#####...|.##.##.#.|.#####.#.|..###.###|...#...#.|...#...#.|.........|.........",
    'share': "......##.|......##.|....##...|.##......|.##......|....##...|......##.|......##.|.........",
    'gear': "...###...|.#.###.#.|.#######.|###...###|###...###|###...###|.#######.|.#.###.#.|...###...",
    'layers': "....#....|..#####..|#########|..#####..|#...#...#|..#####..|#########|..#####..|....#....",
    'close': "#.......#|.#.....#.|..#...#..|...#.#...|....#....|...#.#...|..#...#..|.#.....#.|#.......#",
    'follow': "....#....|...###...|...###...|..#####..|..#####..|.###.###.|.##...##.|##.....##|.........",
    'list': "#.#######|.........|#.#######|.........|#.#######|.........|#.#######|.........|.........",
    'cave': "..#####..|.#######.|###...###|##.....##|##.....##|#.......#|#.......#|#.......#|.........",
    'eye_on': ".........|..#####..|.#.....#.|#..###..#|#..###..#|#..###..#|.#.....#.|..#####..|.........",
    'eye_off': "#........|.######..|.##....#.|#..#.#..#|#...#...#|#..#.#..#|.#....##.|..######.|........#",
    'label_on': ".........|#######..|#......#.|#.####..#|#......#.|#######..|.........|.........|.........",
    'label_off': ".........|#.#.#.#..|.......#.|#.......#|.......#.|#.#.#.#..|.........|.........|.........",
    'mini_on': ".#######.|#.......#|#..###..#|#..###..#|#..###..#|#.......#|.#######.|.........|.........",
    'mini_off': ".#.#.#.#.|.........|#.......#|.........|#.......#|.........|.#.#.#.#.|.........|.........",
    'world_on': "#########|#...#...#|#...#...#|#########|#...#...#|#...#...#|#########|.........|.........",
    'world_off': "#.#.#.#.#|.........|#.......#|.........|#.......#|.........|#.#.#.#.#|.........|.........",
    'padlock': "..#####..|.#.....#.|.#.....#.|#########|####.####|###...###|####.####|#########|.........",
    'warn': "....#....|...###...|...#.#...|..##.##..|..##.##..|.#######.|.###.###.|#########|.........",
}


def icon9(im, name, x, y, c):
    for j, row in enumerate(ICON9[name].split('|')):
        for i, ch in enumerate(row):
            if ch == '#':
                im.putpixel((x + i, y + j), col(c))


def widgets():
    im = new(256, 256)
    S = 'map_widgets'
    # panel hull 24x24, border 4
    rect(im, 0, 0, 23, 23, 'G_DARK')
    rect(im, 1, 1, 22, 22, 'PANEL_EDGE')
    rect(im, 2, 2, 21, 21, 'PANEL')
    for (cx, sx) in ((1, 1), (22, -1)):
        for (cy, sy) in ((1, 1), (22, -1)):
            for k in range(3):
                im.putpixel((cx + sx * k, cy), col('MAP_ACCENT'))
                im.putpixel((cx, cy + sy * k), col('MAP_ACCENT'))
    reg(S, 'panel', 0, 0, 24, 24, 4)
    # inset well 24x24, border 2
    rect(im, 24, 0, 47, 23, 'G_DARK')
    rect(im, 25, 1, 46, 22, 'GLASS')
    rect(im, 25, 22, 46, 22, 'PANEL_EDGE')
    rect(im, 46, 1, 46, 22, 'PANEL_EDGE')
    reg(S, 'well', 24, 0, 24, 24, 2)
    # header strip 24x12 border 2 (title bars, group headers)
    rect(im, 48, 0, 71, 11, 'G_DARK')
    rect(im, 49, 1, 70, 10, 'GRAPHITE')
    rect(im, 49, 1, 70, 1, 'GRAPHITE_4')
    rect(im, 49, 10, 70, 10, 'MAP_ACCENT_DIM')
    reg(S, 'header', 48, 0, 24, 12, 2)
    # plain translucent readout plate 8x8 (PANEL at 70 %), border 1
    rect(im, 72, 0, 79, 7, 'PANEL', 178)
    reg(S, 'readout_plate', 72, 0, 8, 8, 1, "PANEL at 70 % opacity")
    rect(im, 80, 0, 87, 7, 'BLACK')
    reg(S, 'label_plate_contrast', 80, 0, 8, 8, 1)
    rect(im, 88, 0, 95, 7, 'G_DARK', 204)
    reg(S, 'label_plate', 88, 0, 8, 8, 1, "marker label plate, 80 %")
    # tooltip 12x12 border 3
    rect(im, 96, 0, 107, 11, 'G_DARK')
    frame(im, 97, 1, 106, 10, 'MAP_ACCENT_DIM')
    rect(im, 98, 2, 105, 9, 'PANEL')
    reg(S, 'tooltip', 96, 0, 12, 12, 3)
    # buttons 20x20 border 3: idle, hover, pressed, locked, primary
    specs = [('button', 'GRAPHITE', 'GRAPHITE_4', 'G_DARK', 'TRIM'),
             ('button_hover', 'GRAPHITE_4', 'TRIM', 'G_DARK', 'MAP_ACCENT'),
             ('button_pressed', 'G_DARK', 'G_DARK', 'GRAPHITE', 'MAP_ACCENT_BRIGHT'),
             ('button_locked', 'PANEL', 'PANEL', 'PANEL', 'STEEL_DK'),
             ('button_primary', 'MAP_ACCENT_DIM', 'MAP_ACCENT', 'G_DARK', 'MAP_ACCENT_BRIGHT')]
    for i, (name, fill, hi, lo, edge) in enumerate(specs):
        x = i * 20
        rect(im, x, 24, x + 19, 43, 'G_DARK')
        frame(im, x + 1, 24 + 1, x + 18, 24 + 18, edge)
        rect(im, x + 2, 26, x + 17, 41, fill)
        rect(im, x + 2, 26, x + 17, 26, hi)
        rect(im, x + 2, 41, x + 17, 41, lo)
        reg(S, name, x, 24, 20, 20, 3)
    # tabs 24x16 border 3 (bottom open): idle, hover, selected
    for i, (name, fill, edge) in enumerate([('tab', 'GRAPHITE', 'TRIM'), ('tab_hover', 'GRAPHITE_4', 'MAP_ACCENT'),
                                            ('tab_selected', 'PANEL', 'MAP_ACCENT_BRIGHT')]):
        x = i * 24
        rect(im, x, 44, x + 23, 59, 'G_DARK')
        rect(im, x + 1, 45, x + 22, 59, fill)
        rect(im, x + 1, 45, x + 22, 45, edge)
        if name != 'tab_selected':
            rect(im, x, 59, x + 23, 59, 'G_DARK')
        reg(S, name, x, 44, 24, 16, 3)
    # toggles 20x10: off, on, locked off, locked on
    for i, (name, track, knob, kx) in enumerate([('toggle_off', 'GLASS', 'STEEL', 1), ('toggle_on', 'MAP_ACCENT_DIM', 'MAP_ACCENT_BRIGHT', 11),
                                                 ('toggle_locked_off', 'PANEL', 'STEEL_DK', 1), ('toggle_locked_on', 'PANEL', 'MAP_ACCENT_DIM', 11)]):
        x = i * 20
        rect(im, x, 60, x + 19, 69, 'G_DARK')
        rect(im, x + 1, 61, x + 18, 68, track)
        rect(im, x + kx, 61, x + kx + 7, 68, 'G_DARK')
        rect(im, x + kx + 1, 62, x + kx + 6, 67, knob)
        reg(S, name, x, 60, 20, 10)
    # checkboxes 10x10
    for i, (name, edge, tick) in enumerate([('check_off', 'TRIM', None), ('check_on', 'MAP_ACCENT', 'MAP_ACCENT_BRIGHT'),
                                            ('check_hover_off', 'MAP_ACCENT', None), ('check_hover_on', 'MAP_ACCENT_BRIGHT', 'WHITE'),
                                            ('check_locked_off', 'STEEL_DK', None), ('check_locked_on', 'STEEL_DK', 'TRIM')]):
        x = 80 + i * 10
        rect(im, x, 60, x + 9, 69, 'G_DARK')
        frame(im, x + 1, 61, x + 8, 68, edge)
        rect(im, x + 2, 62, x + 7, 67, 'GLASS')
        if tick:
            for (px, py) in ((2, 5), (3, 6), (4, 7), (5, 6), (6, 5), (7, 4), (3, 5), (4, 6), (5, 5), (6, 4), (7, 3)):
                im.putpixel((x + px, 60 + py), col(tick))
        reg(S, name, x, 60, 10, 10)
    # slider: track 16x6 border 2, fill 16x6 border 2, knobs 6x10
    rect(im, 0, 72, 15, 77, 'G_DARK')
    rect(im, 1, 73, 14, 76, 'GLASS')
    reg(S, 'slider_track', 0, 72, 16, 6, 2)
    rect(im, 16, 72, 31, 77, 'G_DARK')
    rect(im, 17, 73, 30, 76, 'MAP_ACCENT')
    rect(im, 17, 73, 30, 73, 'MAP_ACCENT_BRIGHT')
    reg(S, 'slider_fill', 16, 72, 16, 6, 2)
    for i, (name, fill) in enumerate([('slider_knob', 'STEEL'), ('slider_knob_hover', 'MAP_ACCENT_BRIGHT'), ('slider_knob_locked', 'STEEL_DK')]):
        x = 32 + i * 6
        rect(im, x, 70, x + 5, 79, 'G_DARK')
        rect(im, x + 1, 71, x + 4, 78, fill)
        rect(im, x + 1, 71, x + 1, 78, 'G_LIGHT' if name == 'slider_knob' else fill)
        reg(S, name, x, 70, 6, 10)
    # scrollbar: track 6x16 border 2, thumb 6x16 border 2, thumb hover
    rect(im, 56, 70, 61, 85, 'G_DARK')
    rect(im, 57, 71, 60, 84, 'GLASS')
    reg(S, 'scroll_track', 56, 70, 6, 16, 2)
    for i, (name, fill) in enumerate([('scroll_thumb', 'TRIM'), ('scroll_thumb_hover', 'MAP_ACCENT')]):
        x = 62 + i * 6
        rect(im, x, 70, x + 5, 85, 'G_DARK')
        rect(im, x + 1, 71, x + 4, 84, fill)
        reg(S, name, x, 70, 6, 16, 2)
    # dimension chips 16x14 border 3: idle, hover, selected
    for i, (name, fill, edge) in enumerate([('chip', 'GRAPHITE', 'TRIM'), ('chip_hover', 'GRAPHITE_4', 'MAP_ACCENT'),
                                            ('chip_selected', 'MAP_ACCENT_DIM', 'MAP_ACCENT_BRIGHT')]):
        x = 80 + i * 16
        rect(im, x + 1, 72, x + 14, 85, 'G_DARK')
        rect(im, x, 73, x + 15, 84, 'G_DARK')
        rect(im, x + 1, 73, x + 14, 84, edge)
        rect(im, x + 2, 74, x + 13, 83, fill)
        reg(S, name, x, 72, 16, 14, 3)
    # colour swatch frames 12x12 (centre 8x8 is filled by code): idle, selected
    for i, (name, edge) in enumerate([('swatch', 'TRIM'), ('swatch_selected', 'WHITE')]):
        x = 128 + i * 12
        frame(im, x, 72, x + 11, 83, 'G_DARK')
        frame(im, x + 1, 73, x + 10, 82, edge)
        reg(S, name, x, 72, 12, 12, None, "fill the inner 8x8 at +2,+2 with the colour")
    # 9x9 icons, white (tinted by code): row y=88, pitch 10
    for i, name in enumerate(ICON9):
        x, y = (i % 25) * 10, 88 + (i // 25) * 10
        icon9(im, name, x, y, 'WHITE')
        reg(S, 'icon_' + name, x, y, 9, 9, None, "white, tint at draw time")
    # preset cards 40x28 border 4: idle, hover, selected
    for i, (name, fill, edge) in enumerate([('card', 'PANEL', 'PANEL_EDGE'), ('card_hover', 'PANEL', 'MAP_ACCENT'),
                                            ('card_selected', 'PANEL', 'MAP_ACCENT_BRIGHT')]):
        x = i * 40
        rect(im, x, 100, x + 39, 127, 'G_DARK')
        frame(im, x + 1, 101, x + 38, 126, edge)
        rect(im, x + 2, 102, x + 37, 125, fill)
        if name == 'card_selected':
            rect(im, x + 2, 102, x + 37, 103, 'MAP_ACCENT')
        reg(S, name, x, 100, 40, 28, 4)
    # focus ring 12x12 border 2 (keyboard focus)
    frame(im, 120, 100, 131, 111, 'MAP_ACCENT_BRIGHT')
    reg(S, 'focus_ring', 120, 100, 12, 12, 2)
    # selection rectangle 12x12 border 2 (area picker)
    for k in range(12):
        if k % 4 < 2:
            for (px, py) in ((132 + k, 100), (132 + k, 111), (132, 100 + k), (143, 100 + k)):
                im.putpixel((px, py), col('WHITE'))
    reg(S, 'select_rect', 132, 100, 12, 12, 2, "dashed; tile the edges")
    save(im, "gui/map_widgets.png", "Shared GUI sheet (see widget table)")


# ----------------------------------------------------------------------------------------------- container screens
def slot(im, x, y, c='GLASS'):
    """18x18 slot with its top-left at x,y (item draws at x+1,y+1)."""
    rect(im, x, y, x + 17, y + 17, 'G_DARK')
    rect(im, x + 1, y + 1, x + 16, y + 16, c)
    rect(im, x + 1, y + 17, x + 17, y + 17, 'PANEL_EDGE')
    rect(im, x + 17, y + 1, x + 17, y + 17, 'PANEL_EDGE')


def hull(im, x0, y0, w, h):
    rect(im, x0, y0, x0 + w - 1, y0 + h - 1, 'G_DARK')
    rect(im, x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2, 'PANEL_EDGE')
    rect(im, x0 + 2, y0 + 2, x0 + w - 3, y0 + h - 3, 'PANEL')
    for (cx, sx) in ((x0 + 1, 1), (x0 + w - 2, -1)):
        for (cy, sy) in ((y0 + 1, 1), (y0 + h - 2, -1)):
            for k in range(5):
                im.putpixel((cx + sx * k, cy), col('MAP_ACCENT'))
                im.putpixel((cx, cy + sy * k), col('MAP_ACCENT'))


def desk_screen():
    im = new(256, 256)
    S = 'cartography_desk'
    hull(im, 0, 0, 176, 186)
    reg(S, 'hull', 0, 0, 176, 186)
    # title bar
    rect(im, 4, 4, 171, 15, 'GRAPHITE')
    rect(im, 4, 15, 171, 15, 'MAP_ACCENT_DIM')
    reg(S, 'title_bar', 4, 4, 168, 12, None, "title text at 8,6")
    # four mode tabs across the top of the work area (drawn from the sheet strip below)
    for i, name in enumerate(['copy', 'merge', 'label', 'print']):
        x = 7 + i * 27
        rect(im, x, 19, x + 25, 32, 'G_DARK')
        rect(im, x + 1, 20, x + 24, 32, 'GRAPHITE')
        rect(im, x + 1, 20, x + 24, 20, 'TRIM')
        reg(S, 'tab_' + name, x, 19, 26, 14, None, "idle state baked in; overlay tab_selected from the strip at 176,0")
    # input slots
    slot(im, 14, 40)
    reg(S, 'slot_atlas_a', 14, 40, 18, 18, None, "source atlas")
    slot(im, 14, 62)
    reg(S, 'slot_atlas_b', 14, 62, 18, 18, None, "second atlas (merge) / blank atlas (copy)")
    slot(im, 36, 62)
    reg(S, 'slot_paper', 36, 62, 18, 18, None, "paper (print) ")
    # ghost hints inside slots
    for (sx, sy) in ((14, 40), (14, 62)):
        frame(im, sx + 5, sy + 4, sx + 12, sy + 13, 'PANEL_EDGE')
    frame(im, 36 + 5, 62 + 5, 36 + 12, 62 + 12, 'PANEL_EDGE')
    # arrow
    for k in range(10):
        rect(im, 60 + k, 70, 60 + k, 71, 'TRIM')
    for k in range(4):
        rect(im, 70 + k, 67 + k, 70 + k, 74 - k, 'TRIM')
    reg(S, 'progress_arrow', 60, 67, 14, 8, None, "overlay progress_arrow_fill left to right")
    # preview well 66x66 (64x64 map inside)
    rect(im, 80, 36, 145, 101, 'G_DARK')
    rect(im, 81, 37, 144, 100, 'VOID')
    grid = tuple((v * 60 + w * 195) // 255 for v, w in zip(C['MAP_ACCENT_DIM'], C['VOID']))
    for g in range(81, 145, 8):
        rect(im, g, 37, g, 100, grid)
    for g in range(37, 101, 8):
        rect(im, 81, g, 144, g, grid)
    reg(S, 'preview', 81, 37, 64, 64, None, "map preview drawn by code")
    # output slot
    slot(im, 150, 60)
    frame(im, 149, 59, 168, 78, 'MAP_ACCENT_DIM')
    reg(S, 'slot_output', 150, 60, 18, 18)
    # label field
    rect(im, 14, 84, 73, 97, 'G_DARK')
    rect(im, 15, 85, 72, 96, 'GLASS')
    reg(S, 'label_field', 14, 84, 60, 14, None, "edit box; text at 17,87; label tab only")
    # player inventory
    for r in range(3):
        for cidx in range(9):
            slot(im, 7 + cidx * 18, 105 + r * 18)
    for cidx in range(9):
        slot(im, 7 + cidx * 18, 162)
    reg(S, 'inventory', 7, 105, 162, 54, None, "3x9, 18 px pitch")
    reg(S, 'hotbar', 7, 162, 162, 18, None, "1x9, 18 px pitch")
    # strip at x 176: selected tab, arrow fill, slot highlight, locked overlay
    rect(im, 176, 0, 201, 13, 'G_DARK')
    rect(im, 177, 1, 200, 13, 'PANEL')
    rect(im, 177, 1, 200, 1, 'MAP_ACCENT_BRIGHT')
    reg(S, 'tab_selected', 176, 0, 26, 14)
    for k in range(10):
        rect(im, 176 + k, 19, 176 + k, 20, 'MAP_ACCENT_BRIGHT')
    for k in range(4):
        rect(im, 186 + k, 16 + k, 186 + k, 23 - k, 'MAP_ACCENT_BRIGHT')
    reg(S, 'progress_arrow_fill', 176, 16, 14, 8)
    for i, name in enumerate(['copy', 'merge', 'label', 'print']):
        x = 176 + i * 12
        art = {'copy': "#######..|#.....#..|#.#######|#.#.....#|#.#.....#|###.....#|..#.....#|..#######|.........",
               'merge': "#...#....|.#..#....|..#.#....|...##....|#########|...##....|..#.#....|.#..#....|#...#....",
               'label': "#########|#.......#|#.#####.#|#.......#|#.###...#|#.......#|#########|.........|.........",
               'print': "..#####..|..#...#..|#########|#.......#|#.#####.#|###...###|..#...#..|..#####..|........."}[name]
        for j, row in enumerate(art.split('|')):
            for ii, ch in enumerate(row):
                if ch == '#':
                    im.putpixel((x + ii, 28 + j), col('WHITE'))
        reg(S, 'tab_icon_' + name, x, 28, 9, 9, None, "white, tint; centre in the tab")
    save(im, "gui/cartography_desk.png", "Cartography Desk screen, 176x186 hull at 0,0 plus overlay strip at x 176")


def atlas_screen():
    im = new(256, 256)
    S = 'atlas_frame'
    # open folio 232x168: steel cover, paper margin, dark map well 200x136
    rect(im, 0, 0, 231, 167, 'G_DARK')
    rect(im, 1, 1, 230, 166, 'STEEL')
    rect(im, 1, 1, 230, 1, 'G_LIGHT')
    rect(im, 1, 1, 1, 166, 'G_LIGHT')
    rect(im, 1, 166, 230, 166, 'STEEL_DK')
    rect(im, 230, 1, 230, 166, 'STEEL_DK')
    rect(im, 4, 4, 227, 163, 'G_DARK')
    rect(im, 5, 5, 226, 162, 'PAPER')
    rect(im, 5, 160, 226, 162, 'PAPER_DK')
    rect(im, 224, 5, 226, 162, 'PAPER_DK')
    rect(im, 15, 15, 216, 152, 'G_DARK')
    rect(im, 16, 16, 215, 151, 'VOID')
    # spine
    rect(im, 115, 1, 116, 166, 'STEEL_DK')
    rect(im, 115, 5, 116, 162, 'PAPER_DK')
    rect(im, 115, 16, 116, 151, 'VOID')
    # hex clasp at the right edge
    stamp(im, ascii_img("..kkk..|.kaaak.|kaAAAak|kaAwAak|kaAAAak|.kaaak.|..kkk..".split('|')), 225, 80)
    # corner ticks on the paper
    for (cx, sx) in ((8, 1), (223, -1)):
        for (cy, sy) in ((8, 1), (159, -1)):
            for k in range(4):
                im.putpixel((cx + sx * k, cy), col('PAPER_DK'))
                im.putpixel((cx, cy + sy * k), col('PAPER_DK'))
    reg(S, 'folio', 0, 0, 232, 168)
    reg(S, 'map_well', 16, 16, 200, 136, None, "map drawn by code; spine line at x 115-116 is drawn over the map at 25 % if desired")
    reg(S, 'footer', 16, 153, 200, 8, None, "page / scale text in G_DARK on paper, y 153")
    # page buttons 12x12 at y 172: prev, next, each idle/hover; page pip 5x5 off/on
    for i, (name, c) in enumerate([('page_prev', 'PAPER_DK'), ('page_prev_hover', 'MAP_ACCENT'),
                                   ('page_next', 'PAPER_DK'), ('page_next_hover', 'MAP_ACCENT')]):
        x = i * 12
        rect(im, x, 172, x + 11, 183, 'G_DARK')
        rect(im, x + 1, 173, x + 10, 182, 'PAPER')
        for k in range(4):
            xx = x + (7 - k if 'prev' in name else 4 + k)
            rect(im, xx, 174 + k, xx, 181 - k, c)
        reg(S, name, x, 172, 12, 12)
    for i, (name, c) in enumerate([('pip_off', 'PAPER_DK'), ('pip_on', 'MAP_ACCENT')]):
        x = 48 + i * 6
        rect(im, x, 172, x + 4, 176, 'G_DARK')
        rect(im, x + 1, 173, x + 3, 175, c)
        reg(S, name, x, 172, 5, 5)
    save(im, "gui/atlas_frame.png", "Atlas view: 232x168 open folio at 0,0 with a 200x136 map well")


def run():
    square_frames()
    round_frames()
    small_sprites()
    fog()
    beams()
    widgets()
    desk_screen()
    atlas_screen()
    return RECTS
