# Server Policy

Operators set the map policy in `config/neromaps.properties`. It is sent to every player when they join and after
`/neromaps reload`. A player cannot turn on what the policy forbids; their settings show why a control is locked.
The server also enforces every rule on its own side.

| Key | Default | What it does |
|-----|---------|--------------|
| `caveMode` | `true` | Allow the client-only cave-mode slice |
| `radarHostile`, `radarPassive`, `radarTamed`, `radarVehicles`, `radarItems` | `true` | Entity radar categories allowed. **Reserved**: there is no entity radar in 0.1.0 |
| `radarPlayers` | `false` | Show other players on the radar (never sneaking or invisible players). **Reserved**, as above |
| `waypointTeleport` | `false` | Allow operators to teleport to their own waypoints. **Reserved**: no teleport command ships in 0.1.0 |
| `deathWaypoints` | `true` | Record death points |
| `waypointLimit` | `256` | Waypoints per player (1-4096) |
| `publicMap` | `false` | Everyone sees all captured terrain, without fog of war. Players are told on join |
| `exploreRadiusChunks` | `8` | Chunks around a player that count as explored (1-32, capped by the view distance) |
| `serverCapture` | `true` | Capture terrain on the server for shared maps, merged atlases, wall maps, map tables and the companion app |
| `debugOverlays` | `false` | Allow the minimap's grid and chunk-border overlays |

`/neromaps policy` (operators) shows the live values. Edit the file, then run `/neromaps reload`.

## Fog of war

Each player has their own explored area: the chunks within `exploreRadiusChunks` of where they have been. The
server never shows terrain outside it, except terrain another player shared with them, or everything when
`publicMap` is on. Turning `publicMap` off again restores fog of war, because exploration is still recorded.

## Server capture

Server tiles are copied only from chunks that are **already loaded**; NeroMaps never loads, generates or keeps a
chunk loaded for the map. Capture runs only while something needs server tiles (a request in the last
`captureDemandMinutes`, or a loaded wall map or map table). Otherwise the server only notes which chunks
changed. See [Config](Config) for the tuning keys.

With `serverCapture` off, shared maps, merged atlases, wall maps and map tables show only what each player's own game
mapped, and the companion app says the server keeps no maps for it. The minimap and world map keep working from each
player's own client.

## The default policy

Out of the box: minimap on (a client setting), fog of war on, public map off, server capture on, players on radar off
(and no radar in 0.1.0), death waypoints on, waypoint limit 256, cave mode allowed, debug overlays off. Push
notifications in the companion app are off until each player turns them on in the app.

## Data the server keeps

- The `neromaps:players` saved data in the world's `data/` folder (and its backup): one record per player who used NeroMaps (waypoints, death
  points, explored chunks, shares, pending requests, privacy toggles), read through Neroland Core's corrupt-file
  recovery. It never holds a position or a position history. It is erased per player by Core's `/neroland data
  eraseme` and `/neroland data erase <uuid>`, and by Core's inactive-player retention.
- `<world>/data/neromaps/tiles/`: terrain tiles only, capped by `serverTileDiskMb`. Safe to delete at any time.
