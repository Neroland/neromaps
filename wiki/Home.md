# NeroMaps

NeroMaps is the minimap and world map of the Neroland mod family.

**Current version: 0.0.1-alpha.1.** This is an early scaffold: the mod loads, adds its (empty) creative tab and
its config file, and does nothing else yet.

## Requirements

- Minecraft 26.3 (older lines down to 1.21.1 are also built)
- Fabric (with Fabric API), NeoForge or Forge
- Neroland Core 1.17.6 or newer

## Configuration

`config/neromaps.properties`:

| Key | Default | What it does |
|-----|---------|--------------|
| `telemetryEnabled` | `true` | Anonymous NeroMaps-only crash reports. Set `false` to opt out (takes effect on restart). |

## Privacy

NeroMaps never writes a player's position to disk, and never shows anyone where another player is, has been or
has built without that player's consent or the server's stated policy. Version 0.0.1-alpha.1 stores no player
data at all.
