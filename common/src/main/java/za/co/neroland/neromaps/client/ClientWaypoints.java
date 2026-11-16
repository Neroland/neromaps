package za.co.neroland.neromaps.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;

import za.co.neroland.neromaps.client.screen.WaypointEditScreen;
import za.co.neroland.neromaps.compat.PlayerMessages;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.platform.Services;
import za.co.neroland.neromaps.store.Waypoint;
import za.co.neroland.neromaps.text.NameFilter;

/**
 * Client waypoint actions: the "new waypoint here" key (and spyglass marking: pressed while scoping it drops a
 * waypoint at the looked-at point), and the create / update / delete intents. On a server with NeroMaps they go to the
 * server, which validates them; without it the waypoint is temporary (client memory only, gone on logout).
 */
public final class ClientWaypoints {

    /** How far spyglass marking reaches, in blocks (only loaded terrain can be hit). */
    public static final double SPYGLASS_RANGE = 512.0;
    /** The waypoint colour picker: the seven colour-blind-safe marker colours, in picker order. */
    public static final int[] PALETTE = {0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0xD55E00, 0xCC79A7, 0xF2F5F8};
    public static final String[] PALETTE_NAMES = {"orange", "sky", "green", "yellow", "vermillion", "purple", "white"};
    public static final String[] GLYPHS = {"dot", "home", "bed", "pickaxe", "sword", "chest", "portal", "anchor",
            "wheat", "tower", "cave", "star", "heart", "flag", "question", "exclamation"};

    private ClientWaypoints() {
    }

    public static String glyph(int i) {
        return "neromaps:glyph/" + GLYPHS[Math.floorMod(i, GLYPHS.length)];
    }

    /** The waypoint key: a spyglass mark while scoping, otherwise the edit screen at the player's feet. */
    public static void key(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            return;
        }
        String dim = ClientMap.dimensionId();
        if (p.isScoping()) {
            HitResult hit = p.pick(SPYGLASS_RANGE, 1.0f, false);
            if (hit == null || hit.getType() == HitResult.Type.MISS) {
                PlayerMessages.overlay(p, Component.translatable("neromaps.spyglass.no_target"));
                return;
            }
            BlockPos at = BlockPos.containing(hit.getLocation());
            String name = NameFilter.clean(Component.translatable("neromaps.spyglass.name", at.getX(), at.getZ())
                    .getString());
            save(new Waypoint(0, Waypoint.DEFAULT_SET, name, PALETTE[1], glyph(13), dim, at.getX(), at.getY() + 1,
                    at.getZ(), false, true, true), false, true);
            return;
        }
        BlockPos at = p.blockPosition();
        String name = NameFilter.clean(Component.translatable("neromaps.waypoint.default_name", at.getX(), at.getZ())
                .getString());
        ClientMap.setScreen(mc, new WaypointEditScreen(new Waypoint(0, Waypoint.DEFAULT_SET, name, PALETTE[0],
                glyph(0), dim, at.getX(), at.getY(), at.getZ(), false, true, true), true, false));
    }

    /** Saves a new or edited waypoint: to the server when it has NeroMaps (and not temporary), else in memory. */
    public static void save(Waypoint w, boolean temporary, boolean create) {
        ClientState st = ClientState.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (temporary || w.id() < 0 || !st.serverHasMod()) {
            if (create || w.id() >= 0) {
                st.addTemporary(w);
            } else {
                st.updateTemporary(w);
            }
            if (mc.player != null) {
                PlayerMessages.overlay(mc.player, Component.translatable(st.serverHasMod()
                        ? "neromaps.waypoint.temporary_set" : "neromaps.waypoint.client_only", w.name()));
            }
            return;
        }
        Services.NETWORK.sendToServer(new Payloads.WaypointEdit(create ? Payloads.WaypointEdit.CREATE
                : Payloads.WaypointEdit.UPDATE, w, 0));
        if (mc.player != null) {
            PlayerMessages.overlay(mc.player, Component.translatable("neromaps.waypoint.set", w.name()));
        }
    }

    public static void delete(Waypoint w) {
        ClientState st = ClientState.INSTANCE;
        if (w.id() < 0 || !st.serverHasMod()) {
            st.removeTemporary(w.id());
            return;
        }
        Services.NETWORK.sendToServer(new Payloads.WaypointEdit(Payloads.WaypointEdit.DELETE, w, 0));
    }

    public static void deleteDeath(int index) {
        if (ClientState.INSTANCE.serverHasMod()) {
            Services.NETWORK.sendToServer(new Payloads.WaypointEdit(Payloads.WaypointEdit.DELETE_DEATH,
                    new Waypoint(0, "", "x", 0, "", ClientMap.dimensionId(), 0, 0, 0, false, false, false), index));
        }
    }
}
