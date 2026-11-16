package za.co.neroland.neromaps.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import za.co.neroland.neromaps.client.hud.PerfOverlay;
import za.co.neroland.neromaps.compat.PlayerMessages;
import za.co.neroland.neromaps.perf.PerfCounters;

/**
 * Client-side commands: {@code /neromaps-client perf} toggles the perf overlay, {@code /neromaps-client perf reset} clears the
 * counters, {@code /neromaps-client perf dump} prints them to chat (local only) and {@code /neromaps-client export save}
 * saves the export the server last sent (the Save button runs it). Works on any server: these never reach it.
 */
public final class ClientCommands {

    /**
     * The client-only root. It is not {@code neromaps}: Fabric's client dispatcher answers "unknown argument" for a
     * client root that shares its name with a server root, so {@code /neromaps waypoint ...} would never reach the
     * server. On a server with NeroMaps, {@code /neromaps perf} (server) toggles this same overlay.
     */
    public static final String ROOT = "neromaps-client";

    private ClientCommands() {
    }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal(ROOT)
                .then(LiteralArgumentBuilder.<S>literal("export")
                        .then(LiteralArgumentBuilder.<S>literal("save").executes(ctx -> {
                            ClientState.saveExport();
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(LiteralArgumentBuilder.<S>literal("perf")
                        .executes(ctx -> {
                            PerfOverlay.enabled = !PerfOverlay.enabled;
                            say(Component.translatable(PerfOverlay.enabled ? "gui.neromaps.perf.on" : "gui.neromaps.perf.off"));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(LiteralArgumentBuilder.<S>literal("reset").executes(ctx -> {
                            PerfCounters.reset();
                            say(Component.translatable("gui.neromaps.perf.reset"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(LiteralArgumentBuilder.<S>literal("dump").executes(ctx -> {
                            for (String line : PerfOverlay.lines()) {
                                say(Component.literal(line));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    private static void say(Component c) {
        var p = Minecraft.getInstance().player;
        if (p != null) {
            PlayerMessages.chat(p, c);
        }
    }
}
