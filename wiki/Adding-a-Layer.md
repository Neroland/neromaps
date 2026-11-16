# Adding a Layer

Any mod can put its things on NeroMaps' minimap, world map and companion-app map **by depending only on Neroland
Core**. Core 1.19.0 added a small map API in `za.co.neroland.nerolandcore.map`: you declare layers and answer queries;
NeroMaps (or any other map) asks. Core renders nothing, stores nothing and never calls your provider on its own. With
no map mod installed, registering costs one map entry. Your mod never depends on NeroMaps, and NeroMaps never
depends on your mod.

## 1. Pin Core

Pin `nerolandcore_version=1.19.0` or later and floor your loader range at the same version (`[1.19.0,2.0)` on
NeoForge and Forge, `>=1.19.0 <2.0.0` in `fabric.mod.json`).

## 2. Declare layers and implement the provider

In common code:

```java
public final class MyMapLayers implements MapLayerProvider {
    static final Identifier BASES = Identifier.fromNamespaceAndPath("mymod", "bases");
    static final Identifier PADS = Identifier.fromNamespaceAndPath("mymod", "pads_nearby");

    @Override public String modId() { return "mymod"; }

    @Override public List<MapLayerInfo> layers() {
        return List.of(
                MapLayerInfo.of(BASES, "map.layer.mymod.bases",
                        Identifier.fromNamespaceAndPath("mymod", "base"), MapScope.OWNER),
                MapLayerInfo.proximityBuilt(PADS, "map.layer.mymod.pads_nearby",
                        Identifier.fromNamespaceAndPath("mymod", "pad")));   // 64 blocks, minimap off
    }

    @Override public void query(MapViewer viewer, ResourceKey<Level> dim, MapBounds bounds, MapLayerSink sink) {
        for (Base b : store().basesOwnedBy(viewer.id(), dim)) {            // owner-scoped: the whole rule
            if (sink.isFull(BASES)) break;
            sink.marker(MapMarker.builder("base." + b.number(), BASES, dim, b.pos())
                    .label(MapLabel.literal(b.name())).build());          // the viewer's own base name
        }
        for (Pad p : store().unownedPads(dim)) {                         // no owner field at all
            if (viewer.within(dim, p.x(), p.z(), MapLayerInfo.BUILT_PROXIMITY)) {
                sink.marker(MapMarker.builder("pad." + p.number(), PADS, dim, p.pos()).build());
            }
        }
    }
}
```

The pieces:

| Type | What it is |
|---|---|
| `MapLayerInfo` | A layer: id (namespace = your mod id), title lang key, icon id, one `MapScope`, default visibility on the world map and minimap, a per-query cap |
| `MapScope` | `OWNER`, `MEMBERS`, `DISCOVERED`, `PROXIMITY` or `PUBLIC` |
| `MapMarker` | A point: id, layer, dimension, position, optional icon, label, colour and up to four detail lines |
| `MapArea` | A `Rect`, `Circle`, `Polygon` (up to 64 vertices) or `Chunks` set (up to 1 024 chunks) with a fill, border and label |
| `MapLabel` | A lang key with arguments, or a short literal |
| `MapViewer` | Who is asking: their UUID, and (only while online) their dimension and server-side position; `within(...)` is the one proximity test |
| `MapLayerSink` | Where you write; it refuses items outside the bounds, of another dimension or layer, past the cap |
| `MapDimensionBadge` | A positionless badge for a whole dimension (for example "visited" or "storm"), from `dimensionBadges(viewer)`; up to 8 |

## 3. Register once

During common init: `MapLayerRegistry.register(new MyMapLayers());`. Wrap it in a `try`/`catch` so a problem never
stops your mod from loading.

## 4. Hint when something changes

`MapEvents.fire(BASES, dim, MapBounds.around(pos, 16));` when something is added, removed or changed, or
`MapEvents.fireFor(playerId, ...)` when only one player's view changed (for example they left a member list). Do not
fire for things that merely move: the map refreshes on its own schedule.

## 5. Prove the scoping

Core ships a test fixture in its main source set. Run it from your tests:

```java
MapLayerConformance.create(new MyMapLayers())
        .viewers(MapViewer.forTest(owner, null, overworld, new BlockPos(0, 64, 0)),
                 MapViewer.forTest(stranger, null, overworld, new BlockPos(4, 64, 0)))
        .dimensions(overworld, someDimensionYouKnowNothingAbout)
        .bounds(MapBounds.around(0, 0, 512))
        .farBounds(MapBounds.around(20_000, 20_000, 256))
        .forbiddenText("ThirdPlayerName").forbiddenIds(thirdPlayer)   // seed a third player's base
        .expectEntitledSees(MyMapLayers.BASES, 1)
        .expectEntitledSees(MyMapLayers.PADS, 1)
        .verify();
```

It checks that a second viewer receives nothing of the first's, that caps hold, that a dimension you know nothing
about returns nothing, and that panning the bounds far away never widens a proximity layer.

## The rule

Return only what **that viewer** may see:

- Where your mod records an owner UUID or a member list, that is the whole rule: only the viewer's own entries, or
  entries they are listed on.
- Proximity (`viewer.within(...)`, from the viewer's own **server-side** position, never from the requested bounds)
  is only for things with **no owner at all**: 64 blocks for player-built things, 128 for world hazards, nothing while
  the viewer is offline.
- World features (ruins, lairs, outposts) only once **that viewer** has discovered them.
- One scope per layer: split a layer that would mix owned and unowned things.
- Never a server-wide roster. Never `getNearestPlayer` or any nearest-player lookup. No other player's name, UUID or
  online state in any id, label, detail or colour.
- A direction-only target (one your own tool shows only as a compass point and a range band) is drawn at a fixed 96
  blocks from the viewer along the centre of that direction's sector, never at the true position.

## Cost

`query` runs on the server thread, at most once per viewer per refresh. Read in-memory state only: never load or
generate a chunk, never touch disk, never block. Stop as soon as `sink.isFull(layer)`. Exceptions are caught and that
provider is skipped for the query; NeroMaps also skips a provider that takes longer than 2 ms for its next 10
refreshes.

## Limits

At most 16 layers per provider. Per query each layer holds at most its cap (default 128, hard cap 512) and 8 192 chunks
or 2 048 polygon vertices across its areas; an area spans at most 16 384 blocks per axis. Programmer errors (bad ids,
keys, vertex or chunk counts) throw `IllegalArgumentException`; data-derived values (label length, extra details,
circle radius) are clamped.

## Icons and names

Name an icon id; NeroMaps draws it from its shared icon atlas and falls back to `neromaps:generic`, so your mod ships
no map art. Put the layer title and any label keys in your own `en_us.json`. The companion app receives the names
resolved in the server's language.
