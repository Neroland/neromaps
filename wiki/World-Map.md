# World Map

Press **M** to open the full-screen world map. It shows everything you may see on the current server and world, in
the same colours and style as the minimap.

## Moving around

- **Pan**: drag with the left mouse button.
- **Zoom**: the mouse wheel, from 1/16 to 8 pixels per block. The block under the cursor stays where it is. Far out,
  the map uses lower-detail tiles, built in the background the first time you need them.
- **Follow me**: keeps the map centred on you. Dragging turns it off.
- **Dimension**: switches to any other dimension you have mapped on this server.
- **Measure**: click two points to see the distance between them in blocks.
- The coordinates under the cursor are shown in the bottom-left corner.

## Waypoints

- **Right-click** anywhere to make a waypoint there. **Click** a waypoint marker to edit it.
- The **Waypoints** panel lists your waypoints and death points, with a search field (typing never closes the map)
  and a **Go** button that centres the map on one. Pending share requests for you are listed there too.

See [Waypoints](Waypoints).

## Layers

The **Layers** panel lists the map layers of the Nero mods on this server, and only those: a mod that is not
installed leaves no empty entry. Each layer has two switches, one for the world map and one for the minimap. Your
choices are kept in your own settings file. A small badge on a marker
carries a state that is never shown by colour alone (for example a faction outpost's standing toward you, or which
side holds a war zone). A row of badges beside the dimension name shows positionless facts, such as worlds on your
Nerospace star chart or an active NeroEvents storm; hover a badge for its label.

See [Layers](Layers) for what each mod shows and to whom.

## What you see on it

- **Your own exploration**, from your game's own cache.
- On a server with NeroMaps: **terrain other players shared with you** (cut to what they explored, kept in memory
  only), **terrain from atlases you merged**, and everything when the server runs a public map.
- **Players who share their live position with you**, while you are both online. Nobody else.
- The **Settings** button opens the settings screen ([Customising](Customising)).

## The map cache

Explored terrain is cached in `neromaps/cache/` inside your game folder. There is one folder per server (named by a
hash of the address, never the address itself), then per world and per dimension. The cache holds terrain colours and
heights only. It never holds positions, names or anything you typed. Its size is capped (`cacheMb`, 256 MB by
default), and the least recently used tiles are removed first. Delete the folder at any time to clear it. Cave slices
and terrain shared with you are never written to it.
