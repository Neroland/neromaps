# Waypoints

Waypoints are named markers you place on your map. On a server with NeroMaps they are stored on the server, so they
follow you between computers and to the NeroLink app, where you can also add, edit and delete them.

## Making a waypoint

- Press **U** (rebindable: *New waypoint here*) to open the waypoint editor at your feet.
- **Spyglass marking:** look through a spyglass and press **U** to drop a waypoint where you are looking (up to 512
  blocks away, loaded terrain only). No editor opens; edit it later from the world map.
- On the world map (**M**), **right-click** anywhere to make a waypoint there, or open the **Waypoints** panel and
  choose *New waypoint here*.
- Command: `/neromaps waypoint add <name>` (at your feet) or `/neromaps waypoint add <x> <y> <z> <name>`.

The editor sets the name (up to 32 characters), position, colour, icon, whether the in-world beam and the distance
label show, and, when creating, *Temporary*.

## Kinds of waypoint

| Kind | Where it lives | How long |
|------|----------------|----------|
| Waypoint | The server's NeroMaps player store | Until you delete it |
| Death point | The server, automatically when you die (last three, newest first) | Until you delete it or a newer death pushes it out |
| Temporary waypoint | Your client's memory only | Until you log out |
| Shared waypoint | Someone else's waypoint they shared with you | Until either of you ends the share |

On a server without NeroMaps every waypoint you make is temporary.

Waypoints belong to a dimension and only show there. Each also has a *set* name (`default` unless you choose one).

## Beams and distance labels

Waypoints within the beam range (1024 blocks by default) show a coloured beam and a label with their name and
distance. In 0.1.0 the beam is drawn on your screen (a column projected from your view), not as a block-like beam in
the world. Toggle all beams with the *Toggle waypoint beams* key (unbound by default). Per-waypoint switches are in
the editor. Settings in `config/neromaps-client.properties`: `beams`, `distanceLabels`, `beamRange`,
`waypointsOnMinimap`, `deathPointsShown`.

## Limits

A server allows up to 256 waypoints per player by default (`waypointLimit`). Names pass a filter: formatting codes,
control and direction-override characters are removed, and the server can reject listed words (`nameDenyList`).

## Death points

When you die, the server marks the spot. Only the last three are kept. Turn them off for yourself with
`/neromaps privacy deathpoints false` (this also deletes the stored ones). A server can turn them off for everyone.

## Waypoint Compass

Craft it from a compass, an empty map and redstone. **Use** it to lock onto the next of your waypoints (or death
points) in this dimension; **sneak-use** to clear it. The needle points like a lodestone compass. The compass holds
only the position it points at, never who set it.

## Commands

| Command | What it does |
|---------|--------------|
| `/neromaps waypoint add [<x> <y> <z>] <name>` | Save a waypoint |
| `/neromaps waypoint list` | List your waypoints and death points |
| `/neromaps waypoint remove <id>` | Delete a waypoint (the number `list` shows after `#`) |
| `/neromaps waypoint death remove <1-3>` | Delete a death point |

## Sharing a waypoint

`/neromaps share waypoint <player> <id>` offers one waypoint to an online player. They see it on their map once they
accept, and it follows your edits until either of you ends the share. See [Sharing and Privacy](Sharing-and-Privacy).

## In the companion app

With the NeroLink bridge on the server, the app lists your waypoints and death points, and can add, edit and delete
them (death points can only be deleted). Changes go through the same name filter and limit as in game, and the app
never shows a change until the server has accepted it. See [Link Module](Link-Module).
