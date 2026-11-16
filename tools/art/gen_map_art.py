#!/usr/bin/env python3
"""
gen_map_art.py -- every NeroMaps texture and parent model except the icon atlas (gen_map_icons.py) and sounds.

    python3 tools/art/gen_map_art.py           # write textures/** and models/** and tools/art/out/art_index.json
    python3 tools/art/gen_map_art.py --check   # fail if any generated file is missing

Fixed seeds; Pillow + numpy only; no hand-edited binaries, no borrowed textures.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import artlib  # noqa: E402
import art_blocks  # noqa: E402
import art_gui  # noqa: E402
import art_models  # noqa: E402
from palette import HEX  # noqa: E402

BIOME_STYLES = {
    "comment": "Biome style: 14 families -> flat colour. Match a biome by id first, then by tag, else 'exotic'.",
    "families": {
        "ocean": "#2C5FA8", "river": "#3A74C4", "beach": "#D9CF9A", "plains": "#8DB360", "forest": "#3F7A3A",
        "taiga": "#2F6B5A", "jungle": "#4FA02C", "swamp": "#5C6B3A", "savanna": "#B8A552", "desert": "#E2C877",
        "badlands": "#C46A34", "snowy": "#E8F0F4", "mountain": "#8A8F96", "exotic": "#7A5C8A"},
    "border_multiplier": 0.80, "hillshade_strength": 0.5,
    "tags": [["#minecraft:is_ocean", "ocean"], ["#minecraft:is_deep_ocean", "ocean"], ["#minecraft:is_river", "river"],
             ["#minecraft:is_beach", "beach"], ["#minecraft:is_jungle", "jungle"], ["#minecraft:is_taiga", "taiga"],
             ["#minecraft:is_savanna", "savanna"], ["#minecraft:is_badlands", "badlands"], ["#minecraft:is_forest", "forest"],
             ["#minecraft:is_mountain", "mountain"], ["#minecraft:is_hill", "mountain"], ["#minecraft:is_nether", "exotic"],
             ["#minecraft:is_end", "exotic"]],
    "ids": {"minecraft:plains": "plains", "minecraft:sunflower_plains": "plains", "minecraft:meadow": "plains",
            "minecraft:swamp": "swamp", "minecraft:mangrove_swamp": "swamp", "minecraft:desert": "desert",
            "minecraft:snowy_plains": "snowy", "minecraft:ice_spikes": "snowy", "minecraft:snowy_taiga": "snowy",
            "minecraft:snowy_beach": "snowy", "minecraft:snowy_slopes": "snowy", "minecraft:frozen_peaks": "snowy",
            "minecraft:grove": "snowy", "minecraft:frozen_river": "snowy", "minecraft:frozen_ocean": "snowy",
            "minecraft:stony_shore": "mountain", "minecraft:mushroom_fields": "exotic", "minecraft:cherry_grove": "forest",
            "minecraft:pale_garden": "forest", "minecraft:dripstone_caves": "mountain", "minecraft:lush_caves": "jungle",
            "minecraft:deep_dark": "exotic"}}


def main():
    rects = art_gui.run()
    art_blocks.run()
    art_models.run()
    os.makedirs(artlib.OUT, exist_ok=True)
    index = {"files": [{"path": "assets/neromaps/" + p, "size": list(s) if s else None, "purpose": why}
                       for p, s, why in artlib.WRITTEN], "sheets": rects, "palette": HEX}
    if "--check" in sys.argv:
        missing = [f["path"] for f in index["files"]
                   if not os.path.exists(os.path.join(artlib.ASSETS, f["path"][len("assets/neromaps/"):]))]
        print(len(index["files"]), "files |", "missing: " + ", ".join(missing) if missing else "all present")
        sys.exit(1 if missing else 0)
    with open(os.path.join(artlib.OUT, "art_index.json"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(index, indent=1) + "\n")
    with open(os.path.join(artlib.OUT, "biome_styles.json"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(BIOME_STYLES, indent=2) + "\n")
    print(f"wrote {len(index['files'])} files under {artlib.ASSETS}")


if __name__ == "__main__":
    main()
