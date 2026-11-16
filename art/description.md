[![NeroLink App — Beta](https://img.shields.io/badge/NeroLink_App-Now_in_Beta-60d4e8?style=for-the-badge)](https://nerolandmc.net/nerolink/#beta) [![Explore the Neroland ecosystem](https://img.shields.io/badge/Explore-The_Neroland_Ecosystem-1a5a6c?style=for-the-badge)](https://nerolandmc.net/ecosystem/)

> 📱 **NeroLink App Beta — your Neroland world on your phone.** Check energy, alerts and machines live, claim quest rewards and search your storage without logging in. **[Join the beta at nerolandmc.net →](https://nerolandmc.net/nerolink/#beta)**
>
> 🌌 **Explore the Neroland ecosystem.** See how NeroMaps fits together with the rest of the Nero mods — every mod, wiki and changelog in one place. **[View the ecosystem at nerolandmc.net →](https://nerolandmc.net/ecosystem/)** · [NeroMaps on the website](https://nerolandmc.net/mods/neromaps/)

---

# NeroMaps

**A fast minimap that is on from the first minute, a world map of everywhere you have been, and waypoints you share only with the people you choose.**

NeroMaps is the **minimap and world map** of the Neroland ecosystem. The minimap and the full-screen map are drawn on your own client from chunks you have already loaded, so they work on any server and cost it no terrain traffic. Waypoints follow you to the **NeroLink app**, where the same map opens in 2D or as a **3D terrain view** you can orbit. Atlases, wall maps and signposts put the map into the world as things you craft, copy and hang up. When other Nero mods are installed, their stations, colonies, ruins and companions appear as map layers.

Built on **Neroland Core**, the only mod it requires. Every other Nero mod is optional.

*(Beta, in development — first release 0.1.0-beta.1.)*

---

## What it adds

1. **Minimap, on by default.** Square or round, in any corner or a free position, with adjustable size, zoom, opacity and border, north-locked or rotating. Four map styles (Day / Night, Biome, Height-shaded and Flat) plus a high-contrast style. Readouts for coordinates, facing, biome, dimension, in-game time and light level are each switched on or off separately.
2. **World map.** Pan and zoom across everything you have explored, switch dimension, search your waypoints, click to create or edit one, measure a distance, or let the map follow you. Every dimension has its own map.
3. **Waypoints.** A name, colour, icon and optional in-world beam with a distance label, kept in sets per dimension and stored on the server so they reach the app too. Death waypoints are automatic, keep the last three and can be turned off. Temporary waypoints vanish when you log out. Look through a spyglass and press the waypoint key to mark a distant point, and carry a **Waypoint Compass** that points at the waypoint or marker you pick.
4. **Sharing by consent only.** Share a waypoint, your explored map or your live position with named players. They must accept, and either side can revoke at any time, effective immediately. Live position has no "everyone" option.
5. **Fog of war.** You see terrain you have explored, terrain shared with you and terrain from an atlas you have merged. Unsurveyed ground stays dark. Fog of war is an access rule, enforced by the server on the app and on shared maps, not a visual effect.
6. **Atlases and the Cartography Desk.** An **Atlas** is a physical copy of an explored map: using one merges its area into your own. Copy, merge and label atlases at the **Cartography Desk**, which also prints wall maps and crafts signposts. An atlas carries map coverage only, never who made it.
7. **Wall Map, Map Table, Signpost and Trail Marker.** A **Wall Map** tiles into larger panels and updates as the terrain changes. A **Map Table** shows a chosen area on a table top. A **Signpost** is a public waypoint with a label and icon that anyone nearby sees on their map. A **Trail Marker** is a small coloured stake for caves and long trips. None of them stores an owner.
8. **Presets and customisation.** A settings screen with a live preview and named presets (Compact, Explorer, Builder, Cinematic and Off), which you can import and export as text. Every layer is toggled separately for the minimap and the world map, with its own icon size and label visibility. Colour-blind-safe marker colours, scalable labels, and nothing shown by colour alone.

Default keys: **M** world map, **Y** toggle minimap, **=** and **-** zoom, **U** new waypoint here. All rebindable.

## The map in the NeroLink app

- 🗺️ **2D map.** Pan, zoom, layers, waypoints, search and follow-me, with waypoint editing and share requests handled from your phone.
- 🏔️ **3D view.** Orbit, tilt and pinch-zoom over terrain with height shading, translucent water, markers and waypoint beams.
- 📐 **What the 3D view is.** The server sends a surface height map and your phone renders it, which costs the server very little. It is a surface model: overhangs, building interiors and caves are not shown in 0.1.0.
- 🔒 **Same rules as in game.** The app receives only terrain inside your own fog of war, your own waypoints, and the position of players who have a live, mutual share with you.

## Layers from other Nero mods

Layers appear only when their mod is installed, and each one shows only what you are entitled to see: your own things, places you belong to, or world features you have discovered. No layer lists another player's base.

- 🌌 **Nerospace**: your stations and launch pads, landed rockets and terraformed regions near you, and a star chart of worlds you have visited.
- 🏘️ **NeroColonies**: the border, buildings and outposts of colonies you belong to.
- 🏛️ **NeroRuins**: ruins you have discovered and your current expedition target.
- 🐾 **NeroCompanion / NeroCreatures**: your companions, their home Bays and your own creatures.
- 🛡️ **NeroSecurity**: your installations and field boundaries.
- ⚡ **NeroPower**: your reactors and beam links, and scorch zones as a hazard overlay.
- 🎪 **NeroEvents**: live event areas and war zones, with a storm badge beside the dimension name.
- 🚚 **NeroLogistics / NeroEconomy / NeroFactions**: nearby drone ports and train stations, your own shops, and faction outposts you have come across.

A mod that is not installed leaves no empty entry in the layer panel. Mod authors can add layers through Neroland Core's map API without depending on NeroMaps.

## Performance and server control

- ⚙️ **Light on the client.** Chunk scanning runs off the render thread, and drawing the minimap is one textured quad plus one batched pass for markers. The on-disk map cache has a size cap.
- 🖥️ **Light on the server.** The server never loads or generates a chunk for the map. Terrain capture for the app and wall maps works only on chunks that are already loaded, inside a small per-tick budget that backs off when the server is behind, and can be switched off.
- 📋 **Server policy.** Operators set the waypoint limit per player, death waypoints, waypoint teleport (operators only, off by default), fog of war or a public map, and server capture. Policy is synced at login, a client cannot enable what it forbids, and the settings screen says why a control is locked.

## Privacy (POPIA / GDPR)

Where you are is personal data, and NeroMaps treats it as the most sensitive thing it touches. **Your position is never written to disk**: no trails, no position history, no last-seen location. A server stores, under your player UUID, only your waypoints, which chunks you have explored, your sharing lists and your privacy toggles. Map tiles, atlases, wall maps and signposts hold terrain and no identity. Nobody learns where you are, have been or have built without your consent or the server's stated policy; if an operator makes the map public, players are told when they join. Display settings and presets stay on your own computer. Get a copy of your data with `/neromaps export`; erase everything with Neroland Core's `/neroland data eraseme`, which also removes you from every other player's sharing lists and clears your NeroMaps data alongside every other Neroland mod.

> **Telemetry notice:** NeroMaps contains opt-out, anonymous crash reporting via Sentry (EU servers) — **on by default.** A report carries only the stack trace of an error in NeroMaps code, the mod/game/loader/Java versions and the list of loaded mod ids — never IPs, usernames, UUIDs, positions, world data, chat or waypoint names. Opt out any time by setting `telemetryEnabled=false` in `config/neromaps.properties`. Full details: [PRIVACY.md](https://github.com/Neroland/neromaps/blob/main/PRIVACY.md).

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one config framework, Core's map API for layers, and Core's shared per-player erasure hook. NeroMaps ships in its own creative tab.
- 📱 **NeroLink module** — the 2D map and 3D view in the companion app, with optional notifications (off by default) that never contain coordinates or names.
- 🤝 **Interoperates, never hard-depends** — every sibling layer is optional and reaches the map through Neroland Core. NeroMaps works in full on Core alone.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **1.21.1** to **1.21.11** and **26.1.2**, **26.2** and **26.3**.

## Requirements & compatibility

- **Requires [Neroland Core](https://nerolandmc.net/mods/neroland-core/) 1.19.0 or newer** — install it alongside NeroMaps (it loads first).
- **Minecraft** 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also 1.21.7), 1.21.10 (also 1.21.9), 1.21.11, 26.1.2, 26.2 and 26.3, on **NeoForge, Forge and Fabric** (Fabric also needs Fabric API).
- Optional: **NeroLink** for the app, and any other Nero mod for its layers. NeroMaps is complete without them.
- Maps never gate gameplay: nothing in another Nero mod needs NeroMaps to be reachable.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroMaps by Neroland* with links to this page and the [GitHub repository](https://github.com/Neroland/neromaps). Full terms: [LICENSE](https://github.com/Neroland/neromaps/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/neromaps/wiki)** — getting started, every setting and preset, waypoints, sharing and privacy, server policy, layers, commands and config.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/neromaps/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/neromaps/blob/main/CHANGELOG.md)**
- 🔥 **[Also on CurseForge](https://www.curseforge.com/minecraft/mc-mods/neromaps)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
