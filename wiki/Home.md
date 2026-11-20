# NeroMaps

NeroMaps is the minimap and world map of the Neroland mod family: a fast minimap that is on from the first minute, a
world map of everywhere you have been, waypoints you share only with the people you choose, survival cartography
blocks, map layers from the other Nero mods, and a 2D and 3D map in the NeroLink companion app.

**Current version: 0.1.0-beta.3.**

## Requirements

- Minecraft 26.3 (also built for 26.2, 26.1.2, 1.21.11, 1.21.10, 1.21.8, 1.21.5, 1.21.4 and 1.21.1)
- NeoForge, Forge or Fabric (Fabric also needs Fabric API)
- **Neroland Core 1.19.0 or newer** (below 2.0). Core is the only required mod.
- Optional: the NeroLink bridge mod for the companion app, and any other Nero mod for its map layers

## Pages

**Playing**

- [Getting Started](Getting-Started)
- [Minimap](Minimap)
- [World Map](World-Map)
- [Customising](Customising): every setting and preset
- [Waypoints](Waypoints)
- [Sharing and Privacy](Sharing-and-Privacy)
- [Atlases and the Desk](Atlases-and-the-Desk)
- [Wall Maps and Signposts](Wall-Maps-and-Signposts)
- [Layers](Layers): what each Nero mod puts on the map, and who sees it
- [The 3D Map](The-3D-Map): the companion app's terrain view, and what it is not

**Running a server**

- [Server Policy](Server-Policy)
- [Config](Config)
- [Commands](Commands)
- [Performance](Performance): the budgets and how to tune them
- [Link Module](Link-Module): what the companion app can read and do

**Making a mod**

- [Adding a Layer](Adding-a-Layer): put your mod's things on the map through Neroland Core

## Privacy in one paragraph

Where you are is personal data. NeroMaps **never writes a player's position to disk**: no trails, no position
history, no last-seen location. It never shows anyone where another player is, has been or has built without that
player's consent or the server's stated policy (a public map, which players are told about when they join). What a
server stores about you (waypoints, death points, which chunks you explored, your shares and privacy toggles) can be
exported with `/neromaps export` and erased with Neroland Core's `/neroland data eraseme`. The full text is the mod's
`PRIVACY.md`.

## Crash reports

NeroMaps sends anonymous, NeroMaps-only crash reports. They are on by default and carry no IP, name, UUID, position,
world data or waypoint name. Set `telemetryEnabled=false` in `config/neromaps.properties` to opt out (takes effect on
restart).
