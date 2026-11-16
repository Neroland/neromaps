# NeroMaps — agent guide

> Distribution: uploads go to CurseForge and GitHub Releases only. Do not add references to any other mod-hosting platform.

NeroMaps is the minimap and world-map mod of the Neroland mod family, built on **Neroland Core**. The minimap is
the heart of the mod and is on by default; the world map, waypoints, sharing and the companion-app map follow.
It has to be performant, highly customisable, and show features from the other Nero mods.

**Status: 0.1.0-beta.1 working tree (release candidate, untagged; needs Core 1.19.0 released first).** The client map engine (minimap, world map, caches),
waypoints with the one player store, server policy and sync, the `neromaps:main` payloads, server commands, the
Waypoint Compass, server terrain capture with fog of war, consent-only sharing, and the consumer side of Core's map
layer API, the survival blocks and screens, compliance (erasure, `/neromaps export`, retention) and the `neromaps`
link module are in. The app module lives in the NeroLink app.

## Repository layout

| Path | What lives there |
|------|------------------|
| `common/src/main/java/za/co/neroland/neromaps/` | Loader-agnostic code (vanilla + Core APIs only, no loader imports) |
| `common/src/main/resources/` | Shared assets, lang, mixin config, `pack.mcmeta`, mod-list logo |
| `fabric/`, `forge/`, `neoforge/` | One branch per loader: entry points, `PlatformInfo` implementation, manifest templates |
| `tools/` | `ecj.prefs` (analyzer settings), `gen_logo.py` (family logo), `build_colour_table.py` (block/biome colour table), the Gradle MCP server |
| `art/` | Logo outputs |
| `wiki/` | Player-facing wiki pages, published to the public GitHub wiki |

Common packages so far:

| Package | What lives there |
|---------|------------------|
| (root) | `NeroMapsCommon`: `MOD_ID`, `LOGGER`, the numbered `init()` |
| `platform` | `PlatformInfo` (loader facts) and `Services` (resolved once during init) |
| `config` | `NeroMapsConfig` (Core `ConfigSchema`, file `config/neromaps.properties`: `telemetryEnabled`, every server policy key, server-only capture tuning, `nameDenyList`) |
| `policy` | `PolicySnapshot` (the policy as one value; wire flags; lock explanation keys) |
| `text` | `NameFilter` (the one filter and 32-character limit for waypoint names and, later, signpost labels) |
| `store` | The one player store: `MapPlayerState` (`neromaps:players`, schema-versioned codec), `PlayerRecord`, `Waypoint`, `DeathPoint`, `ExploredMap` (region bitsets), `Grant`, `ShareRequest`, `ShareScope`, `ConsentLedger` (offer / accept / decline / revoke rules), `PlayerStore` (recovery-guarded access, eraser) |
| `fog` | `Visibility` (own + granted explored maps, public map), `TileMask` (per-chunk mask, masked tile, mixed etag) |
| `network` | `MapNetwork` (payload registry, channel `neromaps:main`), `Payloads`, `Wire`, `RateLimiter`, `ClientSink` |
| `server` | `MapServer` (tick-driven sessions, exploration, deaths, live positions, hello), `ServerHandlers` (validated intents), `WorldId`; `server.tiles` (capture: `DirtyMarks`, `CaptureLoop`, `CaptureBudget`, `ChunkAccess`/`LevelChunkAccess` (`getChunkNow` only), `ServerChunkSnapshot`, `ServerTileStore`, `CaptureService`, `TileService`, `CapturePerf`); `server.layers` (`LayerService`, `LayerItems`: the Core map API consumer) |
| `command` | `MapCommands` (the server `/neromaps` root) |
| `item` | `WaypointCompassItem` |
| `telemetry` | `NeroMapsTelemetry` (opt-out Sentry, EU DSN), `SentryLogAppender`, `ForeignMixinFailure` |
| `registry` | `ModCreativeTab` (id `neromaps`, title `itemGroup.neromaps`, through Core's `RegistrationProvider`), `ModItems` |
| `menu` | `MenuOpener`: the only way any NeroMaps GUI is opened |
| `data` | `NeroMapsData` (the one composite eraser registered with Core's `PlayerDataErasure`), `NeroMapsSavedData` (every `SavedData` read through Core's `SavedDataRecovery`) |
| `compat` | `PlayerMessages` (chat/overlay message seam across Minecraft lines) |
| `tile` | `Tile` (256 x 256 columns, per-tile palettes), `TileCodec` (schema 1, one `TileCorruptException`), `TileLod`, `TileKey` |
| `scan` | `ColumnScanner` (surface, roofed, cave) over the `ColumnSource` seam, `ChunkColumns` |
| `colour` | `ColourTable` (JSON table + vanilla map-colour fallback), `MapRasterizer` (styles, hillshade, water, fog), `MapPalette`, `MapStyle`, `RasterOptions` |
| `engine` | Pure, tested client engine: `MapStore` (tiles, LOD, disk cache), `Clipmap`, `MinimapFrame`/`MinimapGeometry`/`QuadBatch`, `MinimapLayout`, `LongObjMap`, `Sprites`, `Projection` (HUD beams), `IconAtlas` (`map_icons.json`, generic fallback) |
| `cache` | `TileDiskCache` (per server/world/dimension, size cap, LRU, atomic writes) |
| `settings` | `MinimapSettings` (`config/neromaps-client.properties`, client preferences only) |
| `perf` | `PerfCounters`, `AllocationMeter` (behind `/neromaps perf`) |
| `client` | Minecraft-facing client: `ClientMap` (chunk polling, dirty marks, scan worker), `ClientState` (everything from the server, memory only), `ClientWaypoints` (key U, spyglass marking, temporary waypoints), `ChunkSnapshot`, `ColourTables` (reload listener), `MapKeys`, `Readouts`, `ClientCommands` (`/neromaps-client perf`); `client.hud` (`MinimapHud`, `WaypointHud`, `MinimapGuiState`, `PerfOverlay`), `client.screen` (`WorldMapScreen` with the layer and waypoint panels, `WaypointEditScreen`), `client.texture` (`MapTexture` holds every upload `//?`, `WorldMapTextures`) |
| `mixin` | `ClientLevelBlockMixin` (client block-change dirty marks), `ServerLevelBlockMixin` (server capture dirty marks), `GuiGraphicsAccessor` (1.21.6-1.21.11 only, in the non-required `neromaps-gui.mixins.json` overlay) |

**Commands.** The server owns `/neromaps` (`waypoint`, `share`, `unshare`, `privacy`, `policy`, `reload`, `perf`).
The client registers no `/neromaps` root: Fabric's client dispatcher would answer "unknown argument" for every
server subcommand instead of forwarding it. The client-only fallback is `/neromaps-client perf`.

## Build

Stonecutter produces 27 cells: three loaders x nine Minecraft lines (1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10,
1.21.11, 26.1.2, 26.2, 26.3). Source is written for 26.x (26.3 is the target); older lines use `//?` blocks,
`legacyRenames` in `stonecutter.gradle` or `common/src/main/resources-<mc>` overlays. Java 25 for 26.x, Java 21
bytecode for 1.21.x.

Never run a plain `build`: the root `:fabric`, `:forge` and `:neoforge` projects are vestigial containers. Build
the cells explicitly, 26.x first, then 1.21.11 down to 1.21.1:

```text
./gradlew :neoforge:26.3:build :fabric:26.3:build :forge:26.3:build \
          :neoforge:26.2:build :fabric:26.2:build :forge:26.2:build ...
./gradlew :fabric:26.3:ecjCheck
```

All 27 cells must build and `:fabric:26.3:ecjCheck` must pass at every gate. `ecjCheck` runs the Eclipse
analyzer with `tools/ecj.prefs` (the same settings VS Code uses) and fails only on errors. Any unit tests in
`common/src/test/java` run as part of every NeoForge cell build.

Neroland Core is resolved from `mavenLocal()` or GitHub Packages; see [USING-CORE.md](USING-CORE.md).

## Ground rules

- **Location is personal data and the most sensitive thing this mod touches.** A player's position is never
  written to disk: no trails, no position history, no last-seen location. Logs, telemetry, alerts and pushes
  carry no personal data: no UUIDs, names, coordinates, waypoint names or free text.
- **Nobody learns where another player is, has been, or has built without that player's consent or the server
  operator's explicit policy.** Fog of war is an access control, not a cosmetic. This applies to the minimap,
  the world map, layers, the link module and the companion app equally.
- **One player-keyed store needs an owner decision to become two.** Every player-keyed store registers with
  `data/NeroMapsData`, reads through `NeroMapsSavedData`, refreshes its backup after erasure, and lands with its
  erasure, export, retention and `PRIVACY.md` entry in the same change.
- **The server never loads or generates a chunk for the map**, and never keeps one loaded. The client never
  requests terrain from the server for the minimap. No chunk scan, tile encode or file I/O runs on the server
  thread or the render thread beyond the budgets the mod sets.
- **Server-authoritative everything that matters.** Waypoint storage, sharing, fog of war, layer scoping and
  the server policy resolve on the server. Never use nearest-player lookups for ownership, credit or
  recipients. Every client-to-server payload is validated against the sender and rate-limited.
- **Neroland Core is the only hard dependency.** No sibling mod in any manifest and no reflection into a
  sibling. Sibling features reach the map through Core's API only.
- **Maps never gate gameplay**, and nothing in another mod may become unreachable without NeroMaps.
- **English only for new text**, and every string lives in the lang file. No datagen: JSON is hand-authored or
  produced by Python tools in `tools/`.

## Conventions

- **Core floor is derived, never hardcoded.** `nerolandcore_version_range: "[${nerolandcoreVersion},2.0)"` in
  the NeoForge and Forge builds, `">=${nerolandcore_version} <2.0.0"` in `fabric.mod.json`. Pin
  `nerolandcore_version` to a tagged Core release.
- **Every `SavedData` goes through Core's `SavedDataRecovery`** (via `data/NeroMapsSavedData`). A corrupt
  `.dat` must never become a repeating crash. Use Core's guard, never a local copy.
- **Player data routes through Core's `PlayerDataErasure`** (POPIA/GDPR): minimise it, set retention limits,
  support export, erasure and opt-out.
- **Own network channel `neromaps:main`** when payloads arrive; never add to Core's network registry.
- **Platform seam.** Loader facts come from `Services.PLATFORM`, resolved during init and never lazily on a tick
  path.
- **Numbered init.** `NeroMapsCommon.init()` runs fixed, numbered steps; new work fills the reserved slot.
- **`MenuOpener` for every GUI.** Never call `Player#openMenu` directly.
- **Config through Core's `ConfigSchema`** (`config/neromaps.properties`).
- **Opt-out Sentry telemetry** with no personal data. The DSN is a compile-time constant in
  `NeroMapsTelemetry` (the NeroMaps EU project). Keep the placeholder guard so a fork that blanks the DSN
  stays silent.
- **`publish.yml` rules.** Direct-curl CurseForge upload with `max-parallel: 1` and the 4-attempt 5xx retry
  loop. The CurseForge project id is the `CURSEFORGE_PROJECT_ID` secret (project 1809964).
- **Own creative tab** (`registry/ModCreativeTab`, id `neromaps`, title `itemGroup.neromaps`). Never add items to
  Core's tab.
- **Never commit, tag or push.** Leave version control to the owner.
- **The wiki is public.** Everything under `wiki/` is published as is; keep private planning material out of it
  and out of the repository. `wiki.yml` runs a guard that fails on such references.
- **`CLAUDE.md` and `AGENTS.md` stay byte-identical.**

## Translation workflow

English is the source language for new and changed text. Community translations are intended to be contributed through Weblate; other-language translations are not required when implementing a feature. Preserve existing translations. This supersedes older instructions that require translating every change.
