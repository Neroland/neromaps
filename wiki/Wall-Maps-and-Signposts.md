# Wall Maps, Map Tables, Signposts and Trail Markers

Four blocks that put the map into the world. None of them stores an owner, a player name or a player position.

## Recipes

| Block | Recipe |
|---|---|
| Wall Map | Paper in the centre, glass panes on the four sides, iron nuggets in the corners |
| Map Table | Glass pane, empty map, glass pane on top; three iron ingots in the middle; one iron ingot below the centre |
| Signpost | Iron nugget, any sign, iron nugget on top; a stick in the centre of the two rows below |
| Trail Marker | Glowstone dust over an iron nugget over a stick |

A printed Wall Map and a Signpost can also be made at the [Cartography Desk](Atlases-and-the-Desk). Every block is
mined with a pickaxe and drops itself (a signpost drops one item per plate).

## Wall Map

Place Wall Maps side by side on a wall (up to 8 x 8) and they join into one frame. Place a **printed** one (from the
desk's Print tab) and the whole array shows its area, north up, centred on the atlas's explored area, at the span you
chose on the desk (32, 64 or 128 blocks per panel). The newest print in an array wins.

**Each viewer sees only the part of the area they may already see**: what they explored, what was shared with them,
atlases they merged, or everything on a public-map server. A wall map never shows anyone new ground. The map updates as
the land changes. A wall map stores an area and a dimension, never an owner. It is drawn only within 48 blocks and only
from the front.

## Map Table

A table with a flat map of its own surroundings in its glass top. Use it to show 32, 64 or 128 blocks across. Like a
wall map, it shows each viewer only what they may see. It is drawn within 32 blocks. (A raised, three-dimensional
relief is not part of 0.1.0.)

## Signpost

A public waypoint. Use it to set its label (up to 24 characters, filtered like waypoint names), symbol, colour and
whether it shows on maps. Anyone within **128 blocks** sees a shown signpost on their minimap and world map (the layer
*Signposts*). Sneak-use adds a direction plate, up to three. A signpost stores no owner and nothing about who wrote it.

## Trail Marker

A small glowing stake for marking your way through caves and on long trips. Anyone within **64 blocks** sees it as a
dot on their map (the layer *Trail markers*). Use it to change its colour. In 0.1.0 the colour shows on the map dot;
the stake's own glow is not tinted yet.

## Server cost

Wall maps and map tables read the server's terrain tiles through the same fog-checked path as everything else, at most
one request a second per player, and only while you are near one. A loaded wall map counts as demand, so the server's
terrain capture runs while one is loaded. See [Server Policy](Server-Policy) and [Performance](Performance).
