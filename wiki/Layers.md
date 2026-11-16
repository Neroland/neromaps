# Layers

Other Nero mods put their things on the map through Neroland Core's map API. NeroMaps asks each mod, **as you**, what
you may see, and draws the answer on the world map, the minimap and the companion app.

- A layer exists only when its mod is installed on the server. A mod that is absent leaves no empty entry.
- Switch every layer on or off separately for the world map and the minimap in the world map's **Layers** panel.
  "Minimap: off" below is only the starting choice.
- Layers are scoped in one of five ways, and each layer uses exactly one:
  - **Yours**: things you own.
  - **Members**: things you belong to (an owner or member list kept by the mod).
  - **Discovered**: world features you yourself discovered.
  - **Nearby**: things with no owner at all, shown only while you are online, in the same dimension and within a
    fixed distance of **your own position on the server** (panning the map never reveals more). These are not kept by
    the app.
  - **Public**: things the mod already makes public.
- No layer ever lists another player's base, shows another player's things by proximity, or carries another
  player's name or online state.

Each mod's version below is the first one with map layers; all need Neroland Core 1.19.0 or newer.

## NeroMaps

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Signposts | Nearby, 128 blocks | Signposts set to show on maps, with their label and symbol | on |
| Trail markers | Nearby, 64 blocks | Trail Markers as coloured dots | on |

## Nerospace (2.2.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Stations | Members | Stations you own or are a member of, with the station's name and "Yours" or "Member" | on |
| Launch pads | Members | Cargo pads you own or are on the access list of | on |
| Pads nearby | Nearby, 64 blocks | Other public pads, unowned cargo pads and launch-pad nodes near you, with a generic label (no name, no owner) | off |
| Rockets | Nearby, 64 blocks | Parked rockets near you (never one that is launching, landing or carrying anyone; no crew data) | off |
| Terraforming | Nearby, 64 blocks | The reach of terraformers near you, as an area (never the machine itself) | off |

Badges: a **star chart** badge for each world you have visited (from your own record), beside the dimension name.

## NeroPower (0.3.2-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Power | Yours | Your reactors and beam endpoints, among machines loaded within 128 blocks of you | on |
| Power nearby | Nearby, 64 blocks | Machines that have no recorded owner. Another player's machine is never shown | off |
| Scorch zones | Nearby, 128 blocks (hazard) | Live scorch zones as areas | on |

## NeroLogistics (0.7.4-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Drone ports | Nearby, 64 blocks | Drone ports, with a generic label (no port names, modes or drone counts) | off |
| Train stations | Nearby, 64 blocks | Train stations, with a generic label | off |

Storage networks and drive bays are not shown: NeroLogistics records no owner for them, and a server-wide list would
map every base.

## NeroColonies (0.8.1-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Colony border | Members (owner, Chief, Ally) | The claim of each colony you belong to, labelled with the colony's name | on |
| Colony buildings | Members | Finished buildings; Storage Crates and Colony Depots only to the colony's owner and Chief | off |
| Outposts | Members | Each outpost's beacon and claim | on |

Sandbox colonies and enemies never appear.

## NeroFactions (0.7.1-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Outposts | Nearby, 48 blocks | The footprint of a faction outpost while you stand within 48 blocks of it, with a badge for **your** standing (hostile, neutral or friendly) | off |

Outposts you visited do not stay on your map after you leave in 0.1.0; a "discovered outposts" layer needs NeroFactions
to remember where you visited, which it does not do yet.

## NeroEconomy (0.5.2-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| My shops | Yours | Your own shop blocks | on |

Other players' shops are not shown. Letting a shop owner list a shop on everyone's map needs an opt-in setting in
NeroEconomy that does not exist yet.

## NeroSecurity (0.7.1-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Installations | Members (owner, officer, member) | Each installation's hub, labelled with the installation's name | on |
| Installation devices | Members | Every other linked device | off |
| Fields | Members | The footprint of each loaded, enabled emitter's field | on |

An installation that denies you through an access source is not shown to you.

## NeroEvents (0.9.1-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Events | Public | Events whose location the event itself makes public, pinned at their public anchor, with an area for events that have a radius. Raids, invasions, private and finished events never appear | on |
| War zones | Public | Faction-war objectives through the same rule, with a badge for the side that holds each one | on |

Badges: a **storm** badge beside the name of each dimension with an active storm.

## NeroRuins (0.5.2-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Ruins | Discovered | Ruins in your own expedition record that still have a position; a ruin you claimed a vault at shows the claimed icon | on |

Nothing is shown while the server has turned off recording discovery positions in NeroRuins. An expedition-target
marker is not part of 0.1.0.

## NeroCreatures (0.5.1-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| My creatures | Yours | Your own pets, mounts and drones that are loaded in this dimension, at their live position (never stored) | on |

A mount is not shown while another player is riding it (or riding with it), so it can never track them.

## NeroCompanion (0.7.1-beta.1)

| Layer | Scope | Shows | Minimap |
|---|---|---|---|
| Companions | Yours | Your companions: live ones where they are, unloaded or lost ones at their last known spot. Parked and capsuled companions are not drawn | on |
| Home Bays | Yours | Bays your companions are parked in or call home, with the number parked | on |

## Mods with no layer in 0.1.0

- **Nerotech**: its pollution readings are not kept per dimension, so a map could draw a reading in the wrong world.
  A pollution layer waits for Nerotech to record the dimension.
- **NeroAgriculture**, **NeroQuests**, **NeroArcade**, **NeroNotes**, **NeroDecor**, **NeroTrophies**, **NeroLink**:
  nothing they record belongs on a map.

## Adding your own

Any mod can add a layer by depending only on Neroland Core: see [Adding a Layer](Adding-a-Layer).
