# The 3D Map (NeroLink app)

With the NeroLink bridge mod on the server and the NeroLink companion app paired to your account, the app gains a
**Maps** screen: your map in 2D, or as a 3D terrain you can orbit. The app shows the map module only when the server
runs NeroMaps.

## What you can do in the app

- **2D map**: pan, zoom, switch dimension, search your waypoints, follow yourself, and see the map layers the Nero
  mods show you.
- **3D view**: orbit, tilt and pinch-zoom over the same terrain. Reset the tilt from the side rail.
- **Waypoints**: list, search, add (long-press the map), edit and delete; delete death points. Every change is checked
  by the server with the in-game rules, and the app shows it only once the server accepted it.
- **Shares**: see what you share and what is shared with you, accept or decline requests, and end a share.
- **Map data**: the size of the map cache (150 MB by default, capped, least recently used tiles go first), a
  **Clear map data** button, a **Wi-Fi only** switch for background downloads (tiles on screen always load), and the
  notifications switch for share requests and death points (off by default).
- **Demo mode** includes a sample terrain, so you can try the 3D view without a server.

## What the 3D view is

The server keeps a **surface height map** of the terrain it captured: for every block column, the height of the
surface and a colour (plus water depth). The app downloads those tiles (only the ones inside your fog of war) and builds
the 3D terrain on your phone:

- one mesh per tile, built in the background, with lower detail for distant tiles;
- height-shaded lighting baked into the terrain, with translucent water;
- markers, layer icons and waypoint beams standing on the terrain, always facing you;
- a sky that follows the dimension (the Nether, the End and the Nerospace worlds each have their own);
- a hard triangle budget (150 000 by default) that drops to 100 000 by itself when your phone falls behind, and comes
  back when it catches up.

Because the phone does the drawing, the server only has to send small compressed tiles it already keeps for the map,
so the 3D view costs the server almost nothing.

## What it is not

The 3D view is a **surface model**. In 0.1.0 it does not show:

- **overhangs and arches** (each column has one surface height, so the ground under an overhang is filled in);
- **interiors** of buildings, or anything under a roof;
- **caves** and anything else below the surface;
- **individual blocks**: a column is one colour at one height, not a block model;
- entities, players (apart from positions shared with you, while you are both online) or block entities.

### Compared with DynMap

DynMap renders full block geometry on the **server**, from fixed isometric angles, into image tiles your browser
shows. That gives true block detail, including overhangs and building sides, but it costs the server rendering time
and disk for every angle and zoom, and the view angle is fixed.

NeroMaps sends a **surface height map** and lets your **phone** render and orbit it. The server does almost no extra
work and you can look from any angle, but you get the shape of the land, not every block. NeroMaps also applies fog
of war: the app only ever receives terrain you have explored, terrain shared with you, or everything on a public-map
server, never a server-wide map.

## Data and privacy

- The app caches tiles and the colour table for each server, up to the cache size you set. Live positions and
  nearby-only layers are never cached.
- Unpairing, a lost pairing, or your erasure on the server wipes the server's map data from the app.
- Notifications carry no names or coordinates.

See [Link Module](Link-Module) for exactly what the app can read and do.
