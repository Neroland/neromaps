# Config

## Server: `config/neromaps.properties`

The map policy keys (`caveMode`, `radar*`, `waypointTeleport`, `deathWaypoints`, `waypointLimit`, `publicMap`,
`exploreRadiusChunks`, `serverCapture`, `debugOverlays`) are listed on [Server Policy](Server-Policy). They are
synced to clients. The rest stay on the server:

| Key | Default | What it does |
|-----|---------|--------------|
| `telemetryEnabled` | `true` | Anonymous NeroMaps-only crash reports (per install, never set by a server) |
| `nameDenyList` | empty | Comma-separated words rejected (case-insensitive) in waypoint and signpost names |
| `captureBudgetMicros` | `1000` (50-20000) | Server-thread time per tick for terrain snapshots, in microseconds (a quarter while the server is behind) |
| `captureMinTps` | `15` (1-20) | Capture pauses below this many ticks per second |
| `captureDemandMinutes` | `30` (1-1440) | How long capture keeps running after the last request for server tiles |
| `serverTileCacheMb` | `32` (4-1024) | Memory for server tiles |
| `serverTileDiskMb` | `2048` (16-65536) | Disk cap for server tiles (least recently read go first) |
| `maxExploredRegions` | `4096` (16-65536) | Explored 32 x 32-chunk regions recorded per player per dimension (128 bytes each) |

Server tiles live in `<world>/data/neromaps/tiles/`. They are terrain only and can be deleted at any time.

Edit the file, then run `/neromaps reload` (operators) to apply the policy and send it to everyone. Tuning advice is on
[Performance](Performance).

## NeroLink bridge

NeroMaps adds nothing to the bridge's config. From the NeroLink release after 1.2.2, the bridge's `bulkSnapshotRoutes`
lists `neromaps/tiles` by default, so
map tiles draw from their own per-device budget (`bulkRateLimitPerMinute`, default 30) and cannot starve other
modules. On an older bridge, tile requests share the ordinary per-device budget. NeroMaps also caps tile payload at 8 MiB a minute per player and pages at 256 KiB, whatever the bridge allows.
Turn single actions off with the bridge's `actionsDisabled` (for example `neromaps/share_offer`).

## Client: `config/neromaps-client.properties`

Display settings, presets and layer toggles are client preferences, never sent to the server. Every key is described
on [Customising](Customising); the waypoint display keys are:

| Key | Default | What it does |
|-----|---------|--------------|
| `beams` | `true` | Waypoint beams |
| `distanceLabels` | `true` | Waypoint names and distances in the world |
| `beamRange` | `1024` | How far away beams and labels show (blocks) |
| `waypointsOnMinimap` | `true` | Waypoints on the minimap |
| `deathPointsShown` | `true` | Death points on the maps |
| `layersOffWorld`, `layersOnWorld`, `layersOffMinimap`, `layersOnMinimap` | empty | Per-layer overrides from the world map's Layers panel |

Presets you save are in `config/neromaps-presets.json`. Exports from `/neromaps export` are saved under
`neromaps/exports/` in your game folder, and the map cache under `neromaps/cache/`.
