# Changelog

All notable changes to NeroMaps are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Compliance (Stage 8):
  - `/neromaps export`: everything NeroMaps holds about you (your record, shares others gave you, offers you made)
    is sent to your own game and saved as JSON under `neromaps/exports/`. Nothing is written on the server; once a
    minute.
  - Erasure now also resyncs the erased player if online (empty waypoints, shares and positions), ends in-flight tile
    answers and layer state for everyone who shared with them, forgets their link and rate state, and tells the app
    (a new record id), all in the same tick. Core's `ErasureConformance` passes; retention runs through the same eraser
    from Core's sweep.
  - Each player record carries a random record id (no identity) that changes when the record is erased.
  - Tests: Core's erasure conformance, a two-player export, a simulated world folder scanned for a session position
    and an erased player's identity before, during and after a session, a source log sweep and a runtime INFO+ log
    capture, and telemetry checks.
- NeroLink link module `neromaps`, schema 1 (Stage 9): sections `dimensions`, `tiles`, `palette`, `waypoints`,
  `layers`, `position`, `shares`; actions `waypoint_add`, `waypoint_edit`, `waypoint_delete`, `share_offer`,
  `share_accept`, `share_decline`, `share_revoke` (20 a minute, offline-safe, no teleport or world change); events
  `waypoints`, `shares`, `deaths` and coarse `tiles`, with push copy (no names or coordinates) only for share requests
  and death points.
  - Tiles: a cursor/want-mask protocol over 8 x 8-tile blocks, fog-checked on every read (a hidden tile answers
    exactly like a missing one), partly visible tiles masked, pages capped at 256 KiB, 8 MiB per player per minute.
    The server thread only assembles pre-encoded base64 strings that the capture worker prepares.
  - Fog masks are now built region by region (55 times faster than per chunk), so the link can serve LOD 0-4.

- Client map engine:
  - Tile format and codec (schema 1) with round-trip, fuzz and size tests. A corrupt or unknown-schema tile is
    dropped and recaptured without a log line per tile.
  - Off-thread chunk scans from immutable snapshots, triggered by chunk loads and coalesced block changes, through
    a bounded queue.
  - Region textures updated a 16 x 16 patch at a time, with at most one upload batch per frame.
  - A disk cache for each server, world and dimension, with a size cap and least-recently-used eviction. It holds
    terrain only.
- Block and biome colour tables built by `tools/build_colour_table.py` from vanilla and Nero block textures. They
  ship in `assets/` and `data/` and can be overridden by resource packs. Blocks missing from the table fall back to
  the vanilla map colour.
- Minimap, on by default:
  - Square or round, in any corner or at a free position inside a safe-area margin. The default is top-right,
    below the status effects.
  - Size, zoom, opacity, three border styles, and north-locked or rotating.
  - Day / Night, Biome, Height-shaded, Flat and High-contrast styles, plus grid and chunk-border overlays.
  - Readouts for coordinates, facing, biome, dimension, time and light, each toggled separately.
- World map screen (M): pan, zoom with level-of-detail tiles, dimension switcher, follow-me and distance measure.
- Cave mode, a client-only slice that is never written to disk, gated by the `caveMode` server policy (default
  allowed). Roofed dimensions get a scan that skips the ceiling.
- Key mappings: M (world map), Y (toggle minimap), = / - (zoom), U (new waypoint, placeholder), plus three
  unbound actions.
- `/neromaps perf`: an overlay showing frame cost, queue depth, texture memory and NeroMaps allocations. Also
  `/neromaps perf reset` and `/neromaps perf dump`. On servers without NeroMaps the client-only form is
  `/neromaps-client perf` (a client root named `neromaps` would block the server's subcommands on Fabric).
- Subtitle, block and item names for the art set.
- Waypoints, stored on the server in the one player store (`neromaps:players`, schema 1, read through Neroland
  Core's corrupt-file recovery): create, edit and delete, sets per dimension, a per-player limit (default 256), the
  last three death points (players can turn them off), temporary waypoints kept in client memory only, beams and
  distance labels drawn on the HUD, and a waypoint editor (key U, or right-click the world map).
- One name filter and 32-character limit for waypoint names (and, later, signpost labels), with a server deny list
  (`nameDenyList`).
- Spyglass marking: press U while looking through a spyglass to drop a waypoint where you look.
- Waypoint Compass: locks onto your waypoints and death points in the current dimension, using vanilla's lodestone
  needle. Crafted from a compass, an empty map and redstone.
- Server policy in `config/neromaps.properties` (cave mode, radar categories, players on radar, waypoint teleport,
  death waypoints, waypoint limit, public map, explore radius, server capture, debug overlays), sent to clients in
  a hello payload at login, on dimension change and after `/neromaps reload`, with lock explanations.
- NeroMaps' own payload channel `neromaps:main`: every serverbound payload is validated against the sender and
  rate-limited per player; codec round-trip and hostile-input tests.
- Server commands: `/neromaps waypoint add|list|remove`, `share`, `unshare`, `privacy`, `policy` and `reload`
  (operators), and `perf` (the client overlay, plus server counters for operators).
- Server terrain capture: dirty marks from block changes and newly explored chunks; snapshots of already-loaded
  chunks only, within `captureBudgetMicros` per tick (a quarter while the server is behind, none below
  `captureMinTps`) and only while something needs server tiles; one worker scans, encodes, rebuilds the LOD 1-4
  pyramid and writes tiles atomically under `<world>/data/neromaps/tiles/` with a disk cap, eviction and
  corrupt-tile recovery.
- Fog of war: each player's explored chunks (one bit per chunk) gate every server terrain read; a public-map
  policy with a notice on join.
- Consent-only sharing of a waypoint, an explored map or a live position with a named player: offer, accept,
  decline and revoke by a server-issued request id. Live positions are relayed at most once a second, memory only,
  never while the sharer sneaks, is invisible or spectates; revocation ends everything at once. Shared terrain is
  masked to the sharer's exploration and kept in client memory only.
- Map layers from other mods through Neroland Core's map API: bounded per-player queries (on a schedule and on
  change hints), delta sync, a Layers panel on the world map with world-map and minimap toggles per layer, and icons
  resolved from `assets/neromaps/map/map_icons.json` with a generic fallback.

- Survival cartography:
  - Atlas (blank and filled): records your own explored map of the current dimension, nearest regions first; using a
    filled atlas merges its coverage into the reader's map; sneak-use re-records. Page Packs (+16 regions each) and
    a Binding Ring (allows up to eight packs) fit with the atlas in the other hand. An atlas holds coverage only,
    never an identity.
  - Cartography Desk: copy, merge and label atlases, print a Wall Map of an atlas's area (paper), or make a Signpost
    (a sign). The desk holds nothing; its slots return to you when you close it.
  - Wall Map: panels join into arrays up to 8 x 8 with connected frames; a printed panel brings its area (32, 64 or
    128 blocks per panel). Each viewer sees only their own explored part of the area.
  - Map Table: a flat map of its surroundings in its glass top (32, 64 or 128 blocks across; use to change).
  - Signpost: a public waypoint with a label, symbol and colour; anyone within 128 blocks sees it on their map
    (layer `neromaps:signposts`). Sneak-use adds a plate (up to three).
  - Trail Marker: a stake that shows as a dot on the map of anyone within 64 blocks (layer
    `neromaps:trail_markers`); use to change its colour.
  - Recipes with recipe-book unlocks, loot tables, `minecraft:mineable/pickaxe` and `neroland:highlight/*` tags, and
    creative tab entries (the tab icon is now the atlas).
  - A NeroQuests chapter, "Charting the World" (seven quests), shipped as inert data.
- Settings screen: tabs for the minimap, style, readouts, waypoints, presets and accessibility, a live
  preview that is the real minimap, a tooltip on every control, and locked controls that say which server policy
  locked them. Open it with its key (unbound by default) or from the world map.
- Presets: Compact, Explorer (the default look), Builder, Cinematic and Off, plus your own (saved in
  `config/neromaps-presets.json`); the cycle-preset key switches them in play; export and import a preset as JSON
  text through the clipboard (only display settings; every value is checked and clamped).
- The art set's minimap frames: the square bezel as a nine-slice, the round bezel and its turning tick ring for 64, 96,
  128 and 160, minimal and high-contrast variants and the north tab. A missing texture falls back to the drawn
  frame.
- Fog edge: a two-step ordered dither and a rim on the explored side of the fog boundary (white edge in high
  contrast).
- World map: a search field in the waypoint list, a Go button that centres on a waypoint, click a waypoint marker to
  edit it, and a Settings button.
- Accessibility: two colour-blind-safe marker palettes (Okabe-Ito, the default, and Tol bright), named colours in
  every colour picker, a keyline and solid-plate option, reduce motion, and a night-darkening switch. The waypoint
  colour picker uses the seven marker colours of the art direction (the dark blue is gone; old waypoints keep it).
- NeroEvents war zones now carry a side badge (a glyph, not only a colour) on the world map.

### Cut

- **Survey Kit** (first in the cut order): not registered in 0.1.0. Its art stays in the resources, unused.
- Map Table holographic relief: the table ships the flat map in its glass top, the written fallback of the art
  direction.
- The wall-map area picker screen: the printed area is the atlas's explored extent, centred, at the span chosen on
  the desk's Print tab.
- The atlas reading view (the folio screen): an atlas is used, merged and copied, not browsed.
- The Radar and Sharing tabs of the settings screen: entity radar is not built in 0.1.0, and sharing lives in the
  world map's waypoint panel and the `/neromaps share` commands. Layers are toggled in the world map's Layers panel.

### Seams and deferrals

- Waypoint beams are drawn on the HUD from a projection of the player's view, not as textured beams in the world.
- `waypointTeleport` is a policy key only; no teleport command ships yet.
- Link module: the waypoint `minimap` flag is not stored (always `true`); `position` shares need both players online;
  the tile index and the palette fail with a bridge error for a few seconds after a server start while they warm
  up; shares carry no `since`; a tile larger than one page is never offered (real terrain never is).
- Offers can be made to online players only. Offline counterparts are shown as `#` plus eight hex digits.
- The 0.1.0 shared-map read path serves LOD 0-2 tiles; the per-viewer masked-tile cache is not built (masks are
  rebuilt per request).
- Server tile eviction is least-recently-read across all LODs (not LOD-0 first).
- Trail markers change colour by use, not with dye; their in-world cap glow is not tinted per colour yet (the map
  dot is).
- Wall maps, map tables and the desk preview read server tiles of level of detail 0-2 only.
- Map textures use `DynamicTexture` on every line. Their CPU copy counts against the texture budget, as decision
  005 allows.
- Biome colours come from the biome nearest the surface of each 4 x 4 column.

## [0.0.1-alpha.1]

### Added

- Project scaffold: one entry point per loader (Fabric, NeoForge, Forge) across 27 builds (Minecraft 1.21.1,
  1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3).
- Hard dependency on Neroland Core 1.17.6 (accepts any Core from 1.17.6 below 2.0).
- The NeroMaps creative tab (empty for now).
- `config/neromaps.properties` with the `telemetryEnabled` opt-out.
- Anonymous, opt-out crash reporting, inert in this build (no reporting project is configured yet).
- Player-data erasure registration with Neroland Core (no player data is stored yet).
