# Using Neroland Core

NeroMaps hard-depends on **Neroland Core** and on nothing else. Features of the other Nero mods reach the map only
through Core's APIs.

## Version pin and floor

`nerolandcore_version` in `gradle.properties` (currently **1.17.6**) is the Core version NeroMaps compiles
against. Every manifest derives its floor from it, so the loader refuses a Core older than the one NeroMaps was
built against:

- NeoForge / Forge: `versionRange = "[${nerolandcoreVersion},2.0)"`
- Fabric: `"nerolandcore": ">=${nerolandcore_version} <2.0.0"`

Pin a **tagged** Core release, never a working-tree version.

## Resolving Core

Core is consumed per cell as `za.co.neroland.nerolandcore:nerolandcore-<loader>-<mc>:<version>`, resolved
from:

1. `mavenLocal()` — run Core's `publishToMavenLocal` for local development, or
2. GitHub Packages (`NerolandCoreGitHubPackages` in `stonecutter.gradle`) — needs `gpr.user` / `gpr.key`
   Gradle properties, or `GITHUB_ACTOR` / `GITHUB_TOKEN` in the environment (CI has both).

## Core APIs NeroMaps uses

| Core API | Used for |
|----------|----------|
| `config.ConfigSchema` / `ConfigManager` / `ConfigValue` | `config/neromaps.properties` |
| `registry.RegistrationProvider` | Cross-loader registration (the creative tab so far) |
| `data.PlayerDataErasure` / `PlayerDataEraser` | The single composite eraser in `data/NeroMapsData` (no store yet) |
| `data.SavedDataRecovery` | Every `SavedData` read and the backup refresh after erasure, through `data/NeroMapsSavedData` |

NeroMaps adds nothing to Core's creative tab and nothing to Core's network channel.

## Erasure

`data/NeroMapsData` registers one eraser with `PlayerDataErasure` during common init. One erasure request
(`/neroland data eraseme`, or an operator's `/neroland data erase <uuid>`) walks every NeroMaps store and then
refreshes each store's recovery backup. See [PRIVACY.md](PRIVACY.md) for what each store holds (none yet).
