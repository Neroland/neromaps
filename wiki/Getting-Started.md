# Getting Started

## Install

1. Install **Neroland Core 1.19.0 or newer** and **NeroMaps** for the same Minecraft version and loader (on Fabric,
   Fabric API too).
2. On a server, install both on the server **and** on every client. NeroMaps also works client-only: on a server
   without it you still get the minimap, the world map, cave mode and temporary waypoints, drawn from the chunks your
   game has loaded.
3. Start the game. The minimap is already on, in the top-right corner below the status-effect icons.

## The first five minutes

| Do this | Key or command |
|---|---|
| Open the full-screen world map | **M** |
| Hide or show the minimap | **Y** |
| Zoom the minimap in and out | **=** and **-** |
| Make a waypoint where you stand | **U** |
| Mark a distant point | Look through a spyglass and press **U** |
| Make a waypoint anywhere on the map | Right-click on the world map |
| Change how the map looks | **Settings** on the world map, or bind *Open settings* in Controls |
| Switch between looks quickly | Bind *Cycle preset* in Controls |

Every key is in the NeroMaps category under Controls and can be rebound.

## What you see

- **Your explored ground.** The map fills in as you travel. Ground you have not explored is shown as dark space with a
  dithered edge.
- **Your waypoints and death points**, with beams and distance labels on screen.
- **Layers from other Nero mods** you have installed: your stations, colonies you belong to, ruins you discovered, your
  companions, and more. See [Layers](Layers). Turn each one on or off for the world map and the minimap separately in
  the world map's **Layers** panel.
- **Other players only if they share with you.** Nobody appears on your map unless they offered you their live
  position and you accepted. See [Sharing and Privacy](Sharing-and-Privacy).

## Next steps

- Craft a **Blank Atlas** to carry and copy your map, and a **Cartography Desk** to copy, merge, label and print it:
  [Atlases and the Desk](Atlases-and-the-Desk).
- Hang a **Wall Map**, raise a **Signpost**, drop **Trail Markers** in caves: [Wall Maps and Signposts](Wall-Maps-and-Signposts).
- Craft a **Waypoint Compass** that points at your waypoints: [Waypoints](Waypoints).
- Pair the **NeroLink app** to see your map on your phone, in 2D or as an orbitable 3D terrain:
  [The 3D Map](The-3D-Map).
- A NeroQuests chapter, *Charting the World*, walks you through the cartography items when NeroQuests is installed.

## Something looks wrong?

- Run `/neromaps perf` (or `/neromaps-client perf` on a server without NeroMaps) to see what the map is doing. See
  [Performance](Performance).
- A control that is greyed out and marked "(server)" was turned off by the server's policy. Its tooltip says which
  setting. See [Server Policy](Server-Policy).
