# NeroMaps privacy notice

NeroMaps follows the Neroland family's privacy rules (POPIA and GDPR): store as little as possible, keep it no
longer than needed, and let every player export, erase and opt out.

## Location

Where a player is, has been or has built is personal data, and NeroMaps treats it as the most sensitive thing it
touches. A player's position is never written to disk: no trails, no position history, no last-seen location.
Nobody sees another player's position, explored map or bases without that player's consent or the server
operator's stated policy.

## What NeroMaps stores about players

NeroMaps has **one** player store, `neromaps:players`, saved in the world's `data` folder (`neromaps_players.dat`) and
read through Neroland Core's corrupt-file recovery. For each player it holds only:

- **Waypoints** the player made: name (at most 32 characters, filtered), colour, icon, set, dimension, position, and
  whether its beam and distance label show. You add, edit and delete them yourself; at most 256 per player by
  default (`waypointLimit`).
- **Death points**: where you last died, the last three only, newest first. Turn them off with
  `/neromaps privacy deathpoints false` (this also deletes the stored ones). Operators can turn them off for
  everyone (`deathWaypoints`).
- **Your explored map** (fog of war): which 16 x 16-block chunks you have been near, as one bit per chunk, grouped
  in 32 x 32-chunk regions. It has no times and no order, so it is not a trail. At most `maxExploredRegions`
  (default 4096) regions per dimension are recorded.
- **Consent lists**: the shares you gave (what, and to whom, by their UUID) and the share requests waiting for you
  (who offered what, a random request id and the day it was made). Requests expire after 7 days.
- **Two privacy toggles**: death points on or off, and whether you accept share requests at all.
- **A record id**: a random number made when your record is created. It identifies nothing; it only changes when your
  record is erased, which tells the companion app to delete what it cached from this server.

It stores **no names**, no chat, no timestamps beyond a request's day, and **never your position**.

Temporary waypoints stay in your client's memory and are gone when you log out. They never reach the server.

The world also gets `neromaps_world.dat`, a random id for the save (so your client keeps separate map caches for
separate worlds on one server). It identifies the world, not anyone.

### Server terrain tiles

When something needs server-side terrain (shared maps, wall maps, map tables and the NeroLink app), the server copies
the surface of chunks that are **already loaded** into tiles under `<world>/data/neromaps/tiles/`. Tiles hold
terrain only (heights, surface blocks, biomes, water depth): no entities, no block contents, no sign text, no
player. They are not player data, are capped at `serverTileDiskMb` (default 2 GB, least recently read go first) and
can be deleted at any time; they are recaptured as needed. The server never loads or generates a chunk to map it.

### Atlases, wall maps, map tables, signposts and trail markers

These are items and blocks, and **none of them holds an identity**: no UUID, no player name, no owner, no time.

- An **atlas** holds the explored-chunk bits of one dimension, copied from the map of whoever recorded it (nearest
  regions first, up to its capacity), plus its upgrades and an optional label (filtered like a waypoint name). Using
  it merges that coverage into the reader's own explored map. Handing someone an atlas is sharing that coverage with
  them, the way handing over a paper map would be.
- A **wall map** (printed at the Cartography Desk) and a **map table** store an area and a dimension only. They draw
  server terrain through the same fog-of-war check as the world map, **for each viewer separately**: everyone sees
  only the parts of the area they have explored themselves (or everything, on a public-map server). A wall map can
  never show you terrain you could not already see. A wall map array counts as demand for server terrain while it is
  loaded.
- A **signpost** stores its label, symbol and colour and whether it shows on maps. When shown, anyone within 128
  blocks sees it and its label on their map. Nothing records who wrote it.
- A **trail marker** stores only its colour. Anyone within 64 blocks sees it as a dot on their map.

### Your client's map cache

Your client keeps the terrain it has drawn under `neromaps/cache/` in your game folder, one folder per server
(named by a hash of the server address, never the address itself), world and dimension. It holds terrain only, is
capped at `cacheMb` in `config/neromaps-client.properties` (default 256 MB, least recently used goes first), and
deleting the folder clears it. Cave-mode slices and terrain shared with you by other players are kept in memory
only and never written there.

## Sharing, consent and revocation

Nobody sees your waypoints, your explored map or your position unless you share them with that **named** player
and they accept:

- `/neromaps share waypoint <player> <id>`, `/neromaps share map <player> [here]` or
  `/neromaps share position <player>` sends an offer. The other player accepts or declines it
  (`/neromaps share accept|decline <request>`, or the buttons in chat and on the world map's Waypoints panel).
  Only the person the offer was made to can accept it.
- There is no "share with everyone" for live position.
- Either side can end a share at any time with `/neromaps unshare <player> [waypoints|map|position]`. It ends at
  once: the grant is deleted, the other player's view of your position stops, and terrain they saw through your
  share is dropped from their client.
- Your live position is sent at most once a second, only to players you share it with, only while you are both
  online, and never while you are sneaking, invisible or in spectator mode. It is held in server memory only.
- `/neromaps privacy requests false` stops all share requests reaching you.

## Public map

An operator can set `publicMap=true`. Then every player can see all terrain the server has captured, including
where others have built. Waypoints, death points and positions stay private. While it is on, every player is told
when they join.

## Export, erasure and retention

- **Erasure.** NeroMaps is registered with Neroland Core's erasure hook. `/neroland data eraseme`, an operator's
  erase, or Core's inactivity sweep deletes your whole NeroMaps record **and** removes you from every other
  player's consent lists in the same step, ends any live sharing with you at once, and refreshes the store's
  backup so the erased data cannot come back from it.
- **Retention.** Inactive players are erased by Core's scheduled sweep when the server sets `dataRetentionDays`.
  Pending share requests expire after 7 days on their own.
- **Export.** `/neromaps export` sends everything NeroMaps holds about you to your own game, which saves it as JSON
  under `neromaps/exports/` in your game folder. Nothing is written on the server. It holds your whole record, the
  shares other players gave you (what, and the sharer's UUID), and the offers you made that are still waiting (to whom,
  what, which day). It holds nothing from anyone else's record: not their waypoints, map, position or settings.
  Once a minute.

## The NeroLink companion app

If the server runs the NeroLink bridge, your paired phone can read NeroMaps through it. Everything it reads is scoped
to **you**, read live, and never copied by the bridge:

- **Terrain tiles** only inside your own fog of war, plus maps others shared with you, or everything on a public-map
  server. A tile you may not see is answered exactly like a tile that does not exist. Partly visible tiles are cut
  down to the chunks you may see before they leave the server.
- **Your waypoints and death points**, your shares and pending requests (with the other player's name while they are
  online, otherwise a short `#` handle), and the layers the map mods show you in game, as you.
- **Positions**: yours while you are online, and the positions of players who share their live position with you,
  only while both of you are online and they are not sneaking, invisible or spectating. Never stored, never cached by
  the app.
- From the app you can add, edit and delete your waypoints and accept, decline, offer and end shares, at most 20
  changes a minute. It cannot teleport or change the world.
- **Notifications** (off until you turn on the `neromaps` category in the app): "Map share request" and "Death point
  marked", with no names and no coordinates. The app then reads the details over its own connection.
- **The app's map cache** holds terrain tiles and the colour table on your phone, capped (150 MB by default) with a
  "clear map data" button. Live positions and nearby-only layers are never cached. Unpairing, the bridge refusing the
  token, or an erasure of your NeroMaps record (the record id changes) wipes it.

## Crash reports (telemetry)

NeroMaps can send anonymous crash reports about its own errors to Sentry (EU servers):

- It is on by default and you can turn it off: set `telemetryEnabled=false` in `config/neromaps.properties`
  (takes effect on restart). This setting is yours; a server cannot change it.
- A report holds the stack trace, the NeroMaps, Minecraft, loader, operating system and Java versions, and the ids
  and versions of your other installed mods.
- It never holds your IP address, username, UUID, position, world data, chat or waypoint names. File paths are
  scrubbed of your account name. Only errors from NeroMaps code are sent, at most ten per session.

## Contact

Questions or requests: <info@neroland.co.za>, or open an issue at <https://github.com/Neroland/neromaps/issues>.
