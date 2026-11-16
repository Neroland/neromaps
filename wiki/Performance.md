# Performance

NeroMaps is built to stay out of your frame time and your server's tick. Every budget below is checked by the test
suite (allocations and bounds are asserted; timings are measured, never asserted), and `/neromaps perf` shows the live
numbers.

## The budgets

| Where | Budget | How it is kept |
|---|---|---|
| Minimap draw (client) | under 0.3 ms per frame including the GPU; NeroMaps' own per-frame code allocates **0 bytes** in steady state | One map texture and one batched overlay pass; the CPU side measures about 0.3 to 2 microseconds per frame |
| Map uploads (client) | at most one upload batch per frame, `uploadBudget` patches (8) | The texture is updated a 16 x 16 patch at a time, nearest first |
| Chunk scanning (client) | 1 ms and 16 chunk copies per tick on the game thread | All scanning runs on one background thread from copies; the queue is bounded at 256 and a full queue keeps chunks waiting, never drops them |
| Memory (client) | `textureBudgetMb` (64 MB, both the GPU copy and the CPU copy are counted), `memoryTiles` (48) | Least recently used textures and tiles are released |
| Disk (client) | `cacheMb` (256 MB) | Least recently used tiles are removed first |
| Network (client) | none for your own map | The minimap and world map use chunks your game already has |
| Terrain capture (server) | `captureBudgetMicros` (1 ms) per tick; a quarter while ticks take over 45 ms; nothing below `captureMinTps` (15) | Only already-loaded chunks are copied; never loads, generates or keeps a chunk loaded |
| Idle (server) | no work at all while nothing needs server tiles | Capture runs only after a request for server tiles within `captureDemandMinutes`, or while a wall map or map table is loaded |
| Encoding and disk I/O (server) | never on the server thread | One worker thread encodes, builds the lower-detail levels and writes atomically; disk capped by `serverTileDiskMb` |
| Companion app tiles (server) | well under 1 ms of server-thread time per request; pages at most 256 KiB; 8 MiB per player per minute | Tiles are prepared on the worker and served from memory |
| Map layers (server) | up to 16 players refreshed per second, each every 5 seconds or on a change; 1 024 items per player; a provider slower than 2 ms is skipped for 10 refreshes | Providers read in-memory state only |

## `/neromaps perf`

`/neromaps perf` (or `/neromaps-client perf` on a server without NeroMaps) toggles a small overlay:

- the minimap's CPU time per frame, and the bytes NeroMaps allocated inside the minimap's draw code in the last frame
  (this should read 0)
- uploads and upload batches per frame
- scan queue depth
- texture memory, tiles in memory and the disk cache size

Operators also see the server's counters: capture time per tick (average and worst), snapshots, the worker queue,
encodes, tile disk use, tiles sent and hidden, chunk loads (always 0), and the companion app's tile service (time per
call, largest page, cache misses).

`/neromaps perf reset` clears the counters. `/neromaps perf dump` prints them to chat. The overlay holds only global
numbers. It never shows a player, a name or a position.

## Tuning

**A slow client:**

- Lower `uploadBudget` (for example 4) if the minimap stutters while you fly fast. The map fills in a little later.
- Lower `textureBudgetMb` and `memoryTiles` on a machine with little memory; raise them if the world map redraws
  while you pan.
- Lower `cacheMb`, or set it to 0 to keep no cache at all (the map is rebuilt as you play).
- A smaller minimap, square shape and north-locked rotation are the cheapest look.

**A busy server:**

- Lower `captureBudgetMicros` (for example 500), or raise `captureMinTps`, if capture shows up in a profile.
- Lower `captureDemandMinutes` so capture stops sooner after the last map request.
- Lower `serverTileDiskMb` to keep the tile folder small; tiles are recaptured as needed.
- Set `serverCapture=false` to switch server capture off entirely. Shared maps, merged atlases, wall maps and map tables
  then show only what each player's own game mapped, and the companion app shows no terrain.
- Lower `maxExploredRegions` to cap how much exploration each player's record holds.

On the NeroLink bridge, `bulkRateLimitPerMinute` sets how many tile requests a device may make each minute.

## Reporting a performance problem

Run `/neromaps perf dump` and include the lines, your Minecraft version, loader, GPU and the other mods installed. The
dump contains no names or positions.
