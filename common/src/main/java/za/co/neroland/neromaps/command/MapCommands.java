package za.co.neroland.neromaps.command;

import java.util.List;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.network.CoreNetwork;

import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.policy.PolicySnapshot;
import za.co.neroland.neromaps.server.MapServer;
import za.co.neroland.neromaps.server.ServerHandlers;
import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.Grant;
import za.co.neroland.neromaps.store.PlayerRecord;
import za.co.neroland.neromaps.store.PlayerStore;
import za.co.neroland.neromaps.store.ShareRequest;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;
import za.co.neroland.neromaps.text.NameFilter;

/**
 * The server's {@code /neromaps} root. Player subcommands act on the caller's own record only. {@code policy} and
 * {@code reload} need operator level; {@code perf} works for everyone (it toggles the caller's own client overlay)
 * and adds the server counters for operators.
 *
 * <p><b>Client root.</b> NeroMaps registers no client command under {@code /neromaps}: Fabric's client dispatcher
 * would report "unknown argument" for every server subcommand instead of forwarding it. The client-only fallback for
 * servers without NeroMaps is {@code /neromaps-client perf}.
 */
public final class MapCommands {

    private MapCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("neromaps")
                .then(Commands.literal("waypoint")
                        .then(Commands.literal("add")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                .executes(c -> add(c, true))))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> add(c, false))))
                        .then(Commands.literal("list").executes(MapCommands::list))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                        .executes(MapCommands::remove)))
                        .then(Commands.literal("death")
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1, DeathPoint.KEEP))
                                                .executes(MapCommands::removeDeath)))))
                .then(Commands.literal("share")
                        .then(Commands.literal("waypoint")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                                .executes(c -> offer(c, ShareScope.waypoint(
                                                        IntegerArgumentType.getInteger(c, "id")))))))
                        .then(Commands.literal("map")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(c -> offer(c, ShareScope.map(ShareScope.ALL)))
                                        .then(Commands.literal("here")
                                                .executes(c -> offer(c, ShareScope.map(
                                                        MapServer.dimension(c.getSource().getPlayerOrException())))))))
                        .then(Commands.literal("position")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(c -> offer(c, ShareScope.livePosition()))))
                        .then(Commands.literal("accept")
                                .then(Commands.argument("request", LongArgumentType.longArg(1))
                                        .executes(c -> answer(c, true))))
                        .then(Commands.literal("decline")
                                .then(Commands.argument("request", LongArgumentType.longArg(1))
                                        .executes(c -> answer(c, false))))
                        .then(Commands.literal("list").executes(MapCommands::shareList)))
                .then(Commands.literal("unshare")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(c -> unshare(c, null))
                                .then(Commands.literal("waypoints").executes(c -> unshare(c, ShareScope.Kind.WAYPOINT)))
                                .then(Commands.literal("map").executes(c -> unshare(c, ShareScope.Kind.EXPLORED_MAP)))
                                .then(Commands.literal("position")
                                        .executes(c -> unshare(c, ShareScope.Kind.LIVE_POSITION)))))
                .then(Commands.literal("privacy")
                        .executes(MapCommands::privacyShow)
                        .then(Commands.literal("deathpoints")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(c -> privacySet(c, true))))
                        .then(Commands.literal("requests")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(c -> privacySet(c, false)))))
                .then(Commands.literal("export").executes(MapCommands::export))
                .then(Commands.literal("policy")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(MapCommands::policy))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(MapCommands::reload))
                .then(Commands.literal("perf")
                        .executes(c -> perf(c, Payloads.Perf.TOGGLE))
                        .then(Commands.literal("reset").executes(c -> perf(c, Payloads.Perf.RESET)))
                        .then(Commands.literal("dump").executes(c -> perf(c, Payloads.Perf.DUMP)))));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static void ok(CommandContext<CommandSourceStack> c, Component msg) {
        c.getSource().sendSuccess(() -> msg, false);
    }

    // --- export -------------------------------------------------------------------------------

    private static final java.util.Map<java.util.UUID, Long> EXPORTED = new java.util.HashMap<>();

    /**
     * {@code /neromaps export}: the caller's own data, sent to their own client, which saves it in their game folder.
     * Nothing is written on the server and nothing is logged. Once a minute per player.
     */
    private static int export(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        long now = System.currentTimeMillis();
        EXPORTED.values().removeIf(t -> now - t > 60_000L);
        if (EXPORTED.containsKey(p.getUUID())) {
            c.getSource().sendFailure(Component.translatable("neromaps.export.cooldown"));
            return 0;
        }
        EXPORTED.put(p.getUUID(), now);
        String json = za.co.neroland.neromaps.store.PlayerExport.build(PlayerStore.get(server(c)), p.getUUID())
                .toString();
        byte[] data = za.co.neroland.neromaps.store.PlayerExport.deflate(json);
        if (data.length > Payloads.Export.MAX_BYTES) {
            c.getSource().sendFailure(Component.translatable("neromaps.export.too_large"));
            return 0;
        }
        if (!MapNetwork.send(p, new Payloads.Export(data))) {
            c.getSource().sendFailure(Component.translatable("neromaps.export.no_client"));
            return 0;
        }
        ok(c, Component.translatable("neromaps.export.sent"));
        return 1;
    }

    // --- waypoints -------------------------------------------------------------------------------------------

    private static int add(CommandContext<CommandSourceStack> c, boolean at) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos pos = at ? new BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"),
                IntegerArgumentType.getInteger(c, "z")) : p.blockPosition();
        String name = NameFilter.clean(StringArgumentType.getString(c, "name"));
        Waypoint w = new Waypoint(0, Waypoint.DEFAULT_SET, name, 0xE69F00, Waypoint.DEFAULT_ICON,
                MapServer.dimension(p), pos.getX(), pos.getY(), pos.getZ(), false, true, true);
        int id = ServerHandlers.create(server(c), p, w);
        if (id < 0) {
            return 0;
        }
        ok(c, Component.translatable("neromaps.command.waypoint.added", name, id));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PlayerRecord r = PlayerStore.get(server(c)).find(p.getUUID()).orElse(null);
        List<Waypoint> ws = r == null ? List.of() : r.waypoints();
        ok(c, Component.translatable("neromaps.command.waypoint.header", ws.size(),
                MapServer.policy().waypointLimit()));
        for (Waypoint w : ws) {
            ok(c, Component.translatable("neromaps.command.waypoint.line", w.id(), w.name(), w.dimension(), w.x(),
                    w.y(), w.z()));
        }
        if (r != null) {
            for (int i = 0; i < r.deaths().size(); i++) {
                DeathPoint d = r.deaths().get(i);
                ok(c, Component.translatable("neromaps.command.waypoint.death", i + 1, d.dimension(), d.x(), d.y(),
                        d.z()));
            }
        }
        return ws.size();
    }

    private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int id = IntegerArgumentType.getInteger(c, "id");
        if (!ServerHandlers.remove(server(c), p, id)) {
            return 0;
        }
        ok(c, Component.translatable("neromaps.command.waypoint.removed", id));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeDeath(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!PlayerStore.get(server(c)).removeDeath(p.getUUID(), IntegerArgumentType.getInteger(c, "index") - 1)) {
            c.getSource().sendFailure(Component.translatable("neromaps.waypoint.not_found"));
            return 0;
        }
        MapServer.syncWaypoints(server(c), p);
        ok(c, Component.translatable("neromaps.command.waypoint.death_removed"));
        return Command.SINGLE_SUCCESS;
    }

    // --- sharing ---------------------------------------------------------------------------------------------

    private static int offer(CommandContext<CommandSourceStack> c, ShareScope scope) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerPlayer target = server(c).getPlayerList().getPlayerByName(StringArgumentType.getString(c, "player"));
        return ServerHandlers.offer(server(c), p, target, scope) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int answer(CommandContext<CommandSourceStack> c, boolean accept) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        return ServerHandlers.answer(server(c), p, LongArgumentType.getLong(c, "request"), accept) ? 1 : 0;
    }

    private static int unshare(CommandContext<CommandSourceStack> c, ShareScope.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        return ServerHandlers.revoke(server(c), p, StringArgumentType.getString(c, "player"), kind);
    }

    private static int shareList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MinecraftServer s = server(c);
        PlayerRecord r = PlayerStore.get(s).find(p.getUUID()).orElse(null);
        int n = 0;
        if (r != null) {
            for (Grant g : r.grants()) {
                ok(c, Component.translatable("neromaps.command.share.out", MapServer.nameOf(s, g.to()),
                        Component.translatable("neromaps.share.kind." + ServerHandlers.kindKey(g.scope()))));
                n++;
            }
            for (ShareRequest q : r.incoming()) {
                ok(c, Component.translatable("neromaps.command.share.pending", MapServer.nameOf(s, q.from()),
                        Component.translatable("neromaps.share.kind." + ServerHandlers.kindKey(q.scope())),
                        Long.toString(q.requestId())));
                n++;
            }
        }
        int[] in = {0};
        PlayerStore.get(s).forEach((sharer, rec) -> {
            for (Grant g : rec.grants()) {
                if (g.to().equals(p.getUUID())) {
                    ok(c, Component.translatable("neromaps.command.share.in", MapServer.nameOf(s, sharer),
                            Component.translatable("neromaps.share.kind." + ServerHandlers.kindKey(g.scope()))));
                    in[0]++;
                }
            }
        });
        if (n + in[0] == 0) {
            ok(c, Component.translatable("neromaps.command.share.none"));
        }
        return n + in[0];
    }

    // --- privacy ---------------------------------------------------------------------------------------------

    private static int privacyShow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PlayerRecord r = PlayerStore.get(server(c)).find(p.getUUID()).orElse(null);
        boolean deaths = r == null || r.deathWaypoints();
        boolean requests = r == null || r.acceptRequests();
        ok(c, Component.translatable("neromaps.command.privacy.deathpoints", onOff(deaths)));
        ok(c, Component.translatable("neromaps.command.privacy.requests", onOff(requests)));
        ok(c, Component.translatable("neromaps.command.privacy.position_note"));
        return Command.SINGLE_SUCCESS;
    }

    private static int privacySet(CommandContext<CommandSourceStack> c, boolean deathPoints)
            throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MinecraftServer s = server(c);
        PlayerRecord r = PlayerStore.get(s).find(p.getUUID()).orElse(null);
        boolean on = BoolArgumentType.getBool(c, "on");
        boolean deaths = deathPoints ? on : r == null || r.deathWaypoints();
        boolean requests = deathPoints ? r == null || r.acceptRequests() : on;
        ServerHandlers.setPrivacy(s, p, deaths, requests);
        return privacyShow(c);
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "neromaps.command.on" : "neromaps.command.off");
    }

    // --- operator --------------------------------------------------------------------------------------------

    private static int policy(CommandContext<CommandSourceStack> c) {
        PolicySnapshot pol = MapServer.policy();
        ok(c, Component.translatable("neromaps.command.policy.header", pol.revision()));
        for (String line : List.of(
                "caveMode=" + pol.caveMode(), "radarHostile=" + pol.radarHostile(),
                "radarPassive=" + pol.radarPassive(), "radarTamed=" + pol.radarTamed(),
                "radarVehicles=" + pol.radarVehicles(), "radarItems=" + pol.radarItems(),
                "radarPlayers=" + pol.radarPlayers(), "waypointTeleport=" + pol.waypointTeleport(),
                "deathWaypoints=" + pol.deathWaypoints(), "waypointLimit=" + pol.waypointLimit(),
                "publicMap=" + pol.publicMap(), "exploreRadiusChunks=" + pol.exploreRadiusChunks(),
                "serverCapture=" + pol.serverCapture(), "debugOverlays=" + pol.debugOverlays())) {
            ok(c, Component.literal("  " + line));
        }
        ok(c, Component.translatable("neromaps.command.policy.edit"));
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        MinecraftServer s = server(c);
        ConfigManager.reloadAll();
        CoreNetwork.syncAll(s);
        MapServer.policyReloaded(s);
        ok(c, Component.translatable("neromaps.command.reloaded"));
        return Command.SINGLE_SUCCESS;
    }

    private static int perf(CommandContext<CommandSourceStack> c, int op) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        boolean operator = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(c.getSource());
        List<String> lines = operator ? MapServer.perfLines() : List.of();
        if (op == Payloads.Perf.RESET && operator) {
            za.co.neroland.neromaps.server.tiles.CaptureService.resetCounters();
        }
        if (!MapNetwork.send(p, new Payloads.Perf(op, lines))) {
            for (String line : lines) {
                ok(c, Component.literal(line));
            }
        }
        return Command.SINGLE_SUCCESS;
    }
}
