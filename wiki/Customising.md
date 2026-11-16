# Customising

Open **NeroMaps settings** with its key (unbound by default; set it in Controls) or the **Settings** button on the
world map. Tabs: Minimap, Style, Readouts, Waypoints, Presets and Accessibility. The preview on the right is the real
minimap with your changes applied as you make them; everything is saved when you close the screen, and **Reset**
puts back the values the screen opened with. Every control has a tooltip. A control the server has turned off stays
visible, cannot be changed, is marked "(server)", and its tooltip says which setting the server chose.

Map layers are switched on and off in the world map's **Layers** panel, one switch for the world map and one for the
minimap per layer (see [World Map](World-Map)). Sharing is in the world map's Waypoints panel and the `/neromaps
share` commands (see [Sharing and Privacy](Sharing-and-Privacy)). There is no radar tab: entity radar is not part of
0.1.0.

## Presets

| Preset | Look |
|---|---|
| Compact | Round 64, turns with you, no readouts |
| Explorer (default) | Square 96, north up, coordinates and biome |
| Builder | Square 128, flat colours, grid and chunk borders, coordinates, facing and light |
| Cinematic | Round 96, 70 % opacity, thin border, no readouts or beams |
| Off | No minimap; the world map still works |

Save your own (up to 16) with **Save as**; rename or delete them on the Presets tab. Bind **Cycle preset** in
Controls to switch presets while playing. **Export** copies the current display settings to the clipboard as JSON
text; **Import** applies JSON text from the clipboard. Only the display settings below are read (never the
performance budgets), unknown keys are refused, and every value is clamped to its range. Presets are kept in
`config/neromaps-presets.json`.

## Accessibility

High contrast (map style and frames), two colour-blind-safe marker palettes (Okabe-Ito and Tol bright), a white
keyline around markers, solid label plates, reduce motion and night darkening on or off. Markers never rely on
colour alone: each category has its own shape and symbol, and colour pickers name every colour.

## The settings file

Settings live in `config/neromaps-client.properties`:

| Key | Values | Default | Meaning |
|---|---|---|---|
| `enabled` | true / false | true | Show the minimap |
| `shape` | `SQUARE`, `ROUND` | `SQUARE` | Minimap shape |
| `anchor` | `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT`, `FREE` | `TOP_RIGHT` | Where the minimap sits |
| `freeX`, `freeY` | 0.0 to 1.0 | 0.5 | Position when `anchor=FREE`, as a fraction of the screen |
| `margin` | 0 to 64 | 4 | Safe-area margin in GUI pixels |
| `size` | 48 to 192 | 96 | Minimap size in GUI pixels |
| `zoom` | 0.25 to 8 | 1 | GUI pixels per block |
| `opacity` | 0.2 to 1.0 | 1.0 | Map opacity |
| `border` | `NERO`, `MINIMAL`, `NONE` | `NERO` | Frame style |
| `rotate` | true / false | false | Rotate with your view |
| `style` | `DAY_NIGHT`, `BIOME`, `HEIGHT`, `FLAT`, `HIGH_CONTRAST` | `DAY_NIGHT` | Colour style |
| `highContrast` | true / false | false | Force the high-contrast style everywhere |
| `grid`, `chunkBorders` | true / false | false | Overlay lines |
| `caveMode` | true / false | false | Cave slice (if the server allows it) |
| `showCoords`, `showFacing`, `showBiome`, `showDimension`, `showTime`, `showLight` | true / false | coords and biome on | Readouts under the map |
| `uploadBudget` | 1 to 16 | 8 | Map patches uploaded per frame (see [Performance](Performance)) |
| `textureBudgetMb` | 16 to 1024 | 64 | Texture memory cap |
| `cacheMb` | 0 to 16384 | 256 | Disk cache cap. 0 turns the cache off |
| `memoryTiles` | 16 to 512 | 48 | Map tiles kept in memory |
| `nightDarkening` | true / false | true | Day / Night style darkens at night |
| `markerPalette` | `OKABE_ITO`, `TOL_BRIGHT` | `OKABE_ITO` | Marker colours (both colour-blind safe) |
| `reduceMotion`, `keyline`, `solidPlates` | true / false | false | Accessibility options |
| `beams` | true / false | true | Waypoint beams on screen |
| `distanceLabels` | true / false | true | Waypoint names and distances on screen |
| `beamRange` | 16 to 8192 | 1024 | How far away beams and labels show |
| `waypointsOnMinimap` | true / false | true | Waypoints on the minimap |
| `deathPointsShown` | true / false | true | Death points on the maps |
| `layersOffWorld`, `layersOnWorld`, `layersOffMinimap`, `layersOnMinimap` | layer ids | empty | Your per-layer choices from the world map's Layers panel (overrides of each layer's default) |
| `preset` | a preset name | `explorer` | The preset last applied |

Resource packs can recolour single blocks. Add `assets/<namespace>/map_colours/<anything>.json` in the same format
as NeroMaps' own tables. Later packs win for each block. Biome-style colours come from
`assets/neromaps/map/biome_styles.json`.
