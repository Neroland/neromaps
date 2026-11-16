# Commands

All commands are under `/neromaps` on a server with NeroMaps. Player commands act on your own data only.

## Waypoints

| Command | Who | What it does |
|---------|-----|--------------|
| `/neromaps waypoint add <name>` | Everyone | Save a waypoint at your feet |
| `/neromaps waypoint add <x> <y> <z> <name>` | Everyone | Save a waypoint at a position |
| `/neromaps waypoint list` | Everyone | List your waypoints (with their numbers) and death points |
| `/neromaps waypoint remove <id>` | Everyone | Delete a waypoint by the number `list` shows |
| `/neromaps waypoint death remove <1-3>` | Everyone | Delete a death point (1 is the newest) |

## Sharing

| Command | Who | What it does |
|---------|-----|--------------|
| `/neromaps share waypoint <player> <id>` | Everyone | Offer one of your waypoints to an online player |
| `/neromaps share map <player>` | Everyone | Offer your explored map of every dimension |
| `/neromaps share map <player> here` | Everyone | Offer your explored map of this dimension only |
| `/neromaps share position <player>` | Everyone | Offer your live position |
| `/neromaps share accept <request>` | Everyone | Accept an offer made to you (its number is in the chat message and in `share list`) |
| `/neromaps share decline <request>` | Everyone | Decline an offer made to you |
| `/neromaps share list` | Everyone | Your shares, the shares you received and pending offers |
| `/neromaps unshare <player>` | Everyone | End every share with a player, in both directions |
| `/neromaps unshare <player> waypoints\|map\|position` | Everyone | End one kind of share with a player |

`<player>` is an online player's name. For an offline counterparty use the handle `list` shows (`#` and eight hex
digits), with or without the `#`.

## Privacy and your data

| Command | Who | What it does |
|---------|-----|--------------|
| `/neromaps privacy` | Everyone | Show your privacy settings |
| `/neromaps privacy deathpoints <true\|false>` | Everyone | Record your death points or not (off also deletes them) |
| `/neromaps privacy requests <true\|false>` | Everyone | Accept share requests or not (off also drops pending ones) |
| `/neromaps export` | Everyone | Save everything NeroMaps holds about you as JSON in your own game folder (`neromaps/exports/`); once a minute |
| `/neroland data eraseme` | Everyone | (Neroland Core) Erase everything every Nero mod holds about you, NeroMaps included |

## Diagnostics and operators

| Command | Who | What it does |
|---------|-----|--------------|
| `/neromaps perf` | Everyone | Toggle your performance overlay (operators also get the server's counters) |
| `/neromaps perf reset` | Everyone | Reset the counters |
| `/neromaps perf dump` | Everyone | Print the counters to chat |
| `/neromaps policy` | Operators | Show the server's map policy |
| `/neromaps reload` | Operators | Reload `config/neromaps.properties` and send the policy to everyone |
| `/neroland data erase <uuid>` | Operators | (Neroland Core) Erase one player's data in every Nero mod |

On a server **without** NeroMaps, use `/neromaps-client perf`, `/neromaps-client perf reset` and
`/neromaps-client perf dump` for the client overlay. (A client command named `/neromaps` would hide the server's
subcommands, so the client-only form has its own name.)

There is no teleport command in 0.1.0.
