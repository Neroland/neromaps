# Minimap

The minimap is on by default. It is drawn from the chunks your game has already loaded, so it works on any
server, including one that does not run NeroMaps. It never asks the server for terrain.

## Where it sits

By default the minimap sits in the top-right corner, just below the status-effect icons. It moves down a row
for each row of effect icons, so vanilla's icons stay visible. You can also anchor it to any other corner, or
give it a free position. It always keeps a small margin from the edge of the screen.

## What you can change

| Setting | Options | Default |
|---|---|---|
| Shape | square, round | square |
| Size | 48 to 192 GUI pixels | 96 |
| Zoom | 0.25 to 8 pixels per block (`=` and `-` keys) | 1 |
| Opacity | 20 % to 100 % | 100 % |
| Border | Nero (steel bezel), minimal, none | Nero |
| Rotation | north-locked, or rotating with your view | north-locked |
| Style | Day / Night, Biome, Height-shaded, Flat, High contrast | Day / Night |
| Grid and chunk borders | on / off | off |
| Cave mode | on / off, if the server allows it | off |
| Readouts | coordinates, facing, biome, dimension, time, light, each on or off | coordinates and biome |

Change all of these in the settings screen, with a live preview: see [Customising](Customising), which also lists
the presets and every key in the settings file. The settings are stored on your computer in
`config/neromaps-client.properties`. They are display preferences only and are never sent anywhere.

## Styles

- **Day / Night**: true block colours with biome tint, hillshade and stepped water depth. At night the map takes
  on a cool tint. The Nether and other roofed dimensions never darken.
- **Biome**: a flat colour for each biome family, with borders between biomes.
- **Height-shaded**: colour shows height, in 4-block bands, with a contour line every 16 blocks.
- **Flat**: block colours only, with no shading.
- **High contrast**: four grey bands for land, solid water with a white shoreline, and black for unexplored ground.

Ground you have not explored yet is shown as dark space with a faint star speckle, and a dithered edge where it meets
explored ground.

## What else is on it

- **You**, as an arrow in the centre.
- **Your waypoints and death points** (switch them off with `waypointsOnMinimap` and `deathPointsShown`). Shared
  waypoints show too while the share lasts.
- **Players who share their live position with you**, while you are both online.
- **Map layers** that are on for the minimap. Each layer has its own minimap switch in the world map's Layers panel;
  some start off on the minimap (for example nearby drone ports and security devices). See [Layers](Layers).
- Markers use colour-blind-safe colours and each kind has its own shape and symbol, so nothing relies on colour alone.

There is **no entity radar** in 0.1.0: mobs, items and other players are never drawn unless a player shares their
position with you.

## Cave mode

Cave mode shows a slice at your height instead of the surface. Cave slices are kept in memory only. They are
never written to disk. Servers running NeroMaps can switch cave mode off with the `caveMode` policy. On a
server without NeroMaps it is allowed.

## Roofed dimensions

In the Nether and other dimensions with a ceiling, the map looks through the roof and shows the first open
floor beneath it.

## Keys

| Action | Default key |
|---|---|
| Toggle minimap | Y |
| Zoom in / out | = / - |
| Open world map | M |
| New waypoint here (or at the spyglass target) | U |
| Toggle waypoint beams, cycle preset, open settings | unbound |

All keys can be changed under Controls, in the NeroMaps category.
