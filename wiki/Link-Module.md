# Link Module

NeroMaps registers the `neromaps` module (schema 1) with Neroland Core's link API. The NeroLink bridge serves it to
paired companion apps. **Every answer is scoped to the player the device is paired to**: the app sees what that
player sees in game, and nothing more. The app's map screens are described on [The 3D Map](The-3D-Map).

## Sections

All are `GET /neromaps/<section>`; with no parameters each answers a small valid document.

| Section | Parameters | What it returns |
|---------|------------|-----------------|
| `dimensions` | none | Dimensions with anything you may see, the bounding box of what you may see (in 8 x 8-tile blocks), the server's map policy, and an opaque record id that changes when your record is erased |
| `tiles` | `dim`, `lod` (0-4), `bx`, `bz`, and `since=<cursor>` or `want=<mask>` | An index of the block's 64 tiles (etag or null, a changed mask, a new cursor), or a page of the wanted tiles, at most 256 KiB, with `next` for those that did not fit and `pending` for those still being prepared |
| `palette` | `v` (the etag you hold) | The block and biome colour table, or `{"same":true}` |
| `waypoints` | none | Your waypoints and death points |
| `layers` | `dim`, `x0`, `z0`, `x1`, `z1` | Markers and areas from the map layers, exactly as the map mods show them to you in game; nearby-only layers only while you are online and near. At most 1 024 items |
| `position` | none | Yours while online; players who share their live position with you while both of you are online |
| `shares` | none | Shares you gave, shares you received, requests waiting for you |

A tile outside your fog of war is answered exactly like a tile the server never mapped: `null` in the index, left out
of a page, nothing queued. Partly visible tiles are cut down to the chunks you may see, and their etag differs from
the full tile's.

### Just after a server start

For a few seconds after a start the server learns which tiles it has on disk and builds the colour table. During that
time it never answers with an error:

- A `tiles` index answers with an empty `cursor`, `retryMs`, a `pending` mask, and the placeholder etag `"pending"`
  for each visible tile it has not learned yet (hidden tiles are still `null`). The placeholder is never a real etag,
  so a client fetches those tiles (they come back as `pending` in the page until they are ready) and keeps everything
  it already holds.
- `palette` answers `{"v":"","pending":true,"retryMs":...}`. Keep the table you hold and ask again later.

## Actions

`POST /actions/neromaps/<action>`: `waypoint_add`, `waypoint_edit`, `waypoint_delete`, `share_offer`,
`share_accept`, `share_decline`, `share_revoke`. They follow the in-game rules (name filter, waypoint limit, consent),
work while you are offline, and are limited to 20 a minute (offers to 10). Refusals come back as `VALIDATION` with a
short reason such as `rate`, `limit`, `bad_name` or `unknown_request`. There is no teleport and no world change.

Notes for 0.1.0: the waypoint `minimap` flag is not stored (it always reads `true`); death points can be deleted but
not edited; offers go to online players only; shares carry no start time.

## Events and notifications

Topics `neromaps.waypoints`, `neromaps.shares`, `neromaps.deaths` and `neromaps.tiles` (changed blocks you can see, at
most every 30 seconds while you use the map). Payloads hold no names or coordinates. Share requests and new death
points can raise a push notification in the `neromaps` category, which is off until you turn it on in the app.

## Budgets

The server never compresses or encodes on its main thread for the app: tiles are prepared by NeroMaps' capture worker
and served from memory. Tile reads use the bridge's separate bulk budget (`bulkSnapshotRoutes`, from the NeroLink
release after 1.2.2), and NeroMaps caps each player at 8 MiB of tiles a minute. A response never exceeds 256 KiB, far
below the relay's 1 MiB frame limit.

## Privacy

What reaches the app is listed in the mod's `PRIVACY.md`. When a player is erased, their record id changes and the
app wipes the map data it cached for that server; unpairing a device wipes it too.
