"""NeroMaps art palette. Single source for every art tool; mirrors the art-direction palette byte for byte."""


def rgb(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def rgba(h, a=255):
    return rgb(h) + (a,)


HEX = {
    # family metals and chrome
    'G_DARK': '#1A1D22', 'GRAPHITE': '#2C3137', 'GRAPHITE_4': '#3A4047', 'TRIM': '#5B6670',
    'STEEL': '#7A8492', 'STEEL_DK': '#424A56', 'G_LIGHT': '#8E98A4', 'GLASS': '#0B1119',
    'NERO_ALLOY': '#26A69A', 'PLASMA': '#60D4E8', 'PANEL': '#141C26', 'PANEL_EDGE': '#2A3A4D',
    'TEXT': '#E8ECF4', 'TEXT_2': '#97A6BC',
    # NeroMaps line
    'MAP_ACCENT': '#7C8CFF', 'MAP_ACCENT_BRIGHT': '#B2BEFF', 'MAP_ACCENT_DIM': '#3A4270',
    'VOID': '#070B12', 'VOID_STAR': '#1E2A3A', 'VOID_EDGE': '#2A3350',
    'PAPER': '#D8D2BE', 'PAPER_DK': '#A39C86',
    # marker palette (colour-blind safe)
    'M_ORANGE': '#E69F00', 'M_SKY': '#56B4E9', 'M_GREEN': '#009E73', 'M_YELLOW': '#F0E442',
    'M_VERMILLION': '#D55E00', 'M_PURPLE': '#CC79A7', 'M_WHITE': '#F2F5F8',
    'WHITE': '#FFFFFF', 'BLACK': '#000000', 'LAVA': '#E8651A',
}
C = {k: rgb(v) for k, v in HEX.items()}
MARKER_COLOURS = ['M_ORANGE', 'M_SKY', 'M_GREEN', 'M_YELLOW', 'M_VERMILLION', 'M_PURPLE', 'M_WHITE']

HILLSHADE = {-2: 0.78, -1: 0.89, 0: 1.00, 1: 1.08, 2: 1.16}
WATER_STEPS = [(2, 1.00), (6, 0.86), (15, 0.72), (10 ** 9, 0.58)]
HYPSO = [(-32, '#101A33'), (0, '#1B3A5C'), (63, '#1F6F78'), (87, '#3F9E78'), (127, '#9CC76B'),
         (175, '#E3D77A'), (239, '#F1EBCB'), (319, '#FFFFFF')]  # sea level 63; stops are sea level + offset
NIGHT_TINT = '#6F7FC8'

# 5x7 pixel font (subset) shared by the art tools
FONT = {
    'N': ["10001", "11001", "10101", "10101", "10011", "10001", "10001"],
    'E': ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'W': ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
}
