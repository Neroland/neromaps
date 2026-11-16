#!/usr/bin/env python3
"""Writes NeroMaps' hand-shaped survival-layer JSON (no datagen): blockstates, 1.21.4+ item model definitions,
loot tables, recipes, recipe-unlock advancements and tags for the five blocks and the atlas items.

Models and textures are Fable's (assets/neromaps/models, textures) and are never written here.
Run from the repository root:  python3 tools/gen_survival_data.py  (add --check to fail on drift).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources"
A = ROOT / "assets/neromaps"
D = ROOT / "data"
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}

out = {}


def put(path, obj):
    out[path] = json.dumps(obj, indent=2) + "\n"


def blockstates():
    v = {}
    for f, y in FACING_Y.items():
        v[f"facing={f}"] = {"model": "neromaps:block/cartography_desk", "y": y} if y else {"model": "neromaps:block/cartography_desk"}
    put(A / "blockstates/cartography_desk.json", {"variants": v})

    v = {}
    for f, y in FACING_Y.items():
        for mask in range(16):
            for lit in (False, True):
                base = "wall_map" if mask == 0 else f"wall_map_{mask}"
                # the status pixel sits on the lower-right piece only (no right or down neighbour)
                name = base + ("_lit" if lit and not (mask & 2) and not (mask & 4) else "")
                m = {"model": f"neromaps:block/{name}"}
                if y:
                    m["y"] = y
                v[f"facing={f},lit={'true' if lit else 'false'},mask={mask}"] = m
    put(A / "blockstates/wall_map.json", {"variants": v})

    parts = [{"apply": {"model": "neromaps:block/signpost_post"}}]
    for plates in (1, 2, 3):
        for r in range(8):
            diag = r % 2 == 1
            y = 45 * (r - (1 if diag else 0))
            m = {"model": f"neromaps:block/signpost_plate_{plates}{'_diag' if diag else ''}"}
            if y:
                m["y"] = y
            parts.append({"when": {"plates": str(plates), "rotation": str(r)}, "apply": m})
    put(A / "blockstates/signpost.json", {"multipart": parts})

    put(A / "blockstates/map_table.json", {"variants": {"": {"model": "neromaps:block/map_table"}}})

    v = {}
    for f, y in FACING_Y.items():
        for wall in (False, True):
            for c in range(7):
                m = {"model": "neromaps:block/trail_marker_wall" if wall else "neromaps:block/trail_marker"}
                if wall and y:
                    m["y"] = y
                v[f"colour={c},facing={f},wall={'true' if wall else 'false'}"] = m
    put(A / "blockstates/trail_marker.json", {"variants": v})


ITEM_MODELS = ["cartography_desk", "wall_map", "signpost", "map_table", "trail_marker", "atlas_blank", "atlas",
               "atlas_upgrade_pages", "atlas_upgrade_binding"]


def item_definitions():
    for i in ITEM_MODELS:
        put(A / f"items/{i}.json", {"model": {"type": "minecraft:model", "model": f"neromaps:item/{i}"}})


BLOCKS = ["cartography_desk", "wall_map", "signpost", "map_table", "trail_marker"]


def loot():
    for b in BLOCKS:
        entry = {"type": "minecraft:item", "name": f"neromaps:{b}"}
        if b == "signpost":
            entry["functions"] = [
                {"function": "minecraft:set_count", "count": n, "add": False, "conditions": [
                    {"condition": "minecraft:block_state_property", "block": "neromaps:signpost",
                     "properties": {"plates": str(n)}}]} for n in (2, 3)]
        put(D / f"neromaps/loot_table/blocks/{b}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [entry],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"neromaps:blocks/{b}"})


def shaped(pattern, key, result, count=1, category="misc"):
    return {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": key,
            "result": {"id": f"neromaps:{result}", "count": count}}


RECIPES = {
    "cartography_desk": (shaped(["PGP", "ICI", "III"], {"P": "minecraft:paper", "G": "minecraft:glass_pane",
                                                         "I": "minecraft:iron_ingot", "C": "minecraft:cartography_table"},
                                "cartography_desk"), "minecraft:cartography_table"),
    "wall_map": (shaped(["NGN", "GPG", "NGN"], {"N": "minecraft:iron_nugget", "G": "minecraft:glass_pane",
                                                 "P": "minecraft:paper"}, "wall_map", 4), "minecraft:paper"),
    "signpost": (shaped(["NSN", " R ", " R "], {"N": "minecraft:iron_nugget", "S": "#minecraft:signs",
                                                 "R": "minecraft:stick"}, "signpost"), "minecraft:iron_nugget"),
    "map_table": (shaped(["GMG", "III", " I "], {"G": "minecraft:glass_pane", "M": "minecraft:map",
                                                  "I": "minecraft:iron_ingot"}, "map_table"), "minecraft:map"),
    "trail_marker": (shaped(["G", "N", "R"], {"G": "minecraft:glowstone_dust", "N": "minecraft:iron_nugget",
                                              "R": "minecraft:stick"}, "trail_marker", 4), "minecraft:glowstone_dust"),
    "atlas_blank": (shaped(["NPN", "PBP", "NPN"], {"N": "minecraft:iron_nugget", "P": "minecraft:paper",
                                                    "B": "minecraft:book"}, "atlas_blank", 1, "equipment"),
                    "minecraft:book"),
    "atlas_upgrade_pages": ({"type": "minecraft:crafting_shapeless", "category": "misc",
                             "ingredients": ["minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:paper",
                                             "minecraft:iron_nugget"],
                             "result": {"id": "neromaps:atlas_upgrade_pages", "count": 1}}, "neromaps:atlas_blank"),
    "atlas_upgrade_binding": (shaped(["NIN", "I I", "NIN"], {"N": "minecraft:iron_nugget", "I": "minecraft:iron_ingot"},
                                     "atlas_upgrade_binding"), "neromaps:atlas_blank"),
}


def recipes():
    for name, (recipe, unlock) in RECIPES.items():
        put(D / f"neromaps/recipe/{name}.json", recipe)
    unlocks = {name: unlock for name, (_, unlock) in RECIPES.items()}
    unlocks["waypoint_compass"] = "minecraft:compass"
    for name, item in unlocks.items():
        put(D / f"neromaps/advancement/recipes/misc/{name}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}},
                "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": f"neromaps:{name}"}}},
            "requirements": [["has_item", "has_the_recipe"]],
            "rewards": {"recipes": [f"neromaps:{name}"]}})


def tag(path, ids):
    put(path, {"replace": False, "values": [{"id": i, "required": False} for i in ids]})


def tags():
    tag(D / "minecraft/tags/block/mineable/pickaxe.json", [f"neromaps:{b}" for b in BLOCKS])
    tag(D / "neroland/tags/item/highlight/machines.json", ["neromaps:cartography_desk", "neromaps:map_table"])
    tag(D / "neroland/tags/item/highlight/tools.json",
        ["neromaps:waypoint_compass", "neromaps:atlas_blank", "neromaps:atlas"])
    tag(D / "neroland/tags/item/highlight/upgrades.json",
        ["neromaps:atlas_upgrade_pages", "neromaps:atlas_upgrade_binding"])


def main():
    blockstates()
    item_definitions()
    loot()
    recipes()
    tags()
    check = "--check" in sys.argv
    drift = []
    for path, text in out.items():
        if check:
            if not path.is_file() or path.read_text(encoding="utf-8") != text:
                drift.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8", newline="\n")
    if drift:
        print("out of date:\n  " + "\n  ".join(drift))
        sys.exit(1)
    print(f"{len(out)} files {'checked' if check else 'written'}")


if __name__ == "__main__":
    main()
