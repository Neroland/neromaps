# NeroMaps privacy notice

NeroMaps follows the Neroland family's privacy rules (POPIA and GDPR): store as little as possible, keep it no
longer than needed, and let every player export, erase and opt out.

## What 0.0.1-alpha.1 stores

**Nothing.** This version keeps no data about any player: no positions, no explored map, no waypoints, no
settings tied to a player. It writes no world data at all.

It still registers with Neroland Core's data-erasure hook, so an erasure request (`/neroland data eraseme` or an
operator's erase) reaches NeroMaps from the first version that does store something.

## Location

Where a player is, has been or has built is personal data, and NeroMaps treats it as the most sensitive thing it
touches:

- A player's position is never written to disk: no trails, no position history, no last-seen location.
- Nobody sees another player's position, explored map or bases without that player's consent or the server
  operator's stated policy. If an operator makes the map public, NeroMaps will say so here and tell players when
  they join.

Each future store will be listed here, with what it holds, how long it is kept and how to export or erase it, in
the same release that adds it.

## Crash reports (telemetry)

NeroMaps can send anonymous crash reports about its own errors to Sentry (EU servers). In this build no reporting
project is configured, so **nothing is sent**. When reporting is switched on in a later build:

- It is on by default and you can turn it off: set `telemetryEnabled=false` in `config/neromaps.properties`
  (takes effect on restart). This setting is yours; a server cannot change it.
- A report holds the stack trace, the NeroMaps, Minecraft, loader, operating system and Java versions, and the ids
  and versions of your other installed mods.
- It never holds your IP address, username, UUID, position, world data, chat or waypoint names. File paths are
  scrubbed of your account name. Only errors from NeroMaps code are sent, at most ten per session.

## Contact

Questions or requests: <info@neroland.co.za>, or open an issue at <https://github.com/Neroland/neromaps/issues>.
