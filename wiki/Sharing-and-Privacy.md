# Sharing and Privacy

The full text of what is stored is in the mod's `PRIVACY.md`.

Where you are, where you have been and what you have built are personal. NeroMaps never shows any of it to another
player unless **you** share it with **that** player and they accept, or the server operator has turned on a public
map (you are told when you join).

## What you can share

| Share | What the other player gets |
|-------|----------------------------|
| A waypoint | That waypoint on their map, kept up to date while the share lasts |
| Your explored map (one dimension, or all) | Terrain you have explored, drawn on their world map. It is kept in their client's memory only |
| Your live position | Your position on their map, at most once a second, while you are both online |

There is no "everyone" option for your live position.

## Offering, accepting and ending a share

```
/neromaps share waypoint <player> <id>
/neromaps share map <player> [here]
/neromaps share position <player>
```

The other player gets a chat message with **Accept** and **Decline** buttons. Pending requests are also on the world
map's Waypoints panel and in `/neromaps share list`. Only the player you offered it to can accept it. Requests
expire after 7 days.

End a share at any time, from either side:

```
/neromaps unshare <player> [waypoints|map|position]
```

It ends at once, on both sides, in game and in the app. Offers go to online players only. A counterparty who is
offline is shown as `#` and eight hex digits (a handle, never their UUID); unshare them by that handle, with or without
the `#` (for example `/neromaps unshare 1a2b3c4d`).

## Sharing without a share

- **Atlases** carry explored coverage, never who recorded them. Giving someone an atlas is a deliberate, physical
  share: when they use it, its area joins their map. See [Atlases and the Desk](Atlases-and-the-Desk).
- **Wall maps and map tables** show each viewer only what that viewer may already see, so they never reveal new
  ground. **Signposts** and **Trail Markers** are public by design and store no owner. See
  [Wall Maps and Signposts](Wall-Maps-and-Signposts).
- **Map layers** from other Nero mods show you only your own things, places you belong to, things near you that have no
  owner, and public events. See [Layers](Layers).

## Never shown

- Your live position while you sneak, are invisible or are in spectator mode.
- Anything after you or the other player ends the share, or after an erasure.
- Terrain you have not explored, through any path: the minimap, the world map, wall maps, shared maps and the
  NeroLink app. A tile you may not see looks exactly like one that does not exist.

## Your settings

| Command | Effect |
|---------|--------|
| `/neromaps privacy` | Show your settings |
| `/neromaps privacy deathpoints <true|false>` | Record your death points or not (off also deletes them) |
| `/neromaps privacy requests <true|false>` | Accept share requests or not (off also drops pending ones) |

## Export

`/neromaps export` sends everything NeroMaps holds about you to your own game. Click **Save** on the chat line that
announces it and your game saves it as JSON under `neromaps/exports/` in your game folder (the ten newest are kept).
Nothing is written on the server, and your game writes nothing you did not ask for. It holds your record (waypoints, death
points, explored map, the shares you gave, the requests waiting for you, your settings), the shares others gave you
and the offers you made that are still waiting. Nothing from anyone else's record.

## Map milestones

Revealing 1,000, 10,000 and 50,000 chunks of your own map, and having a waypoint share accepted, open one-off
milestones that other mods such as trophies can recognise. Only your UUID is stored for them, in Neroland Core,
and erasing your data removes it. No position, dimension, waypoint name or other player is recorded.

## The companion app

With the NeroLink bridge on the server, your paired phone sees the same things you do in game, and nothing more: your
explored terrain (and maps shared with you), your waypoints and shares, the layers the map mods show you, your own
position while you are online, and the positions of players who share theirs with you while you are both online.
Share-request and death-point notifications are off by default and carry no names or coordinates. See
[Link Module](Link-Module).

## Erasure

`/neroland data eraseme` (Neroland Core) removes everything NeroMaps holds about you, and you from every other
player's share lists, in one step. Anyone you were sharing with stops seeing your position, map and waypoints in
the same tick, the store's backup is refreshed, and the companion app wipes the map data it cached for you.
