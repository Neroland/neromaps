package za.co.neroland.neromaps.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import za.co.neroland.neromaps.block.SignpostBlockEntity;
import za.co.neroland.neromaps.menu.CartographyDeskMenu;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.network.RateLimiter;
import za.co.neroland.neromaps.registry.ModSounds;

/** Serverbound handlers of the survival layer: validated against the sender, rate-limited, server thread. */
public final class SurvivalHandlers {

    static final RateLimiter LABELS = new RateLimiter(20, 100);
    static final RateLimiter SIGNPOSTS = new RateLimiter(5, 500);
    /** How close a player must be to edit a signpost (squared distance to the block centre). */
    static final double REACH_SQ = 8.0 * 8.0;

    private SurvivalHandlers() {
    }

    /** Drops a player's rate windows (logout, erasure). */
    public static void forget(java.util.UUID id) {
        LABELS.forget(id);
        SIGNPOSTS.forget(id);
    }

    public static void deskLabel(Payloads.DeskLabel msg, ServerPlayer p) {
        if (p.containerMenu instanceof CartographyDeskMenu menu && LABELS.tryAcquire(p.getUUID())) {
            menu.setLabel(msg.label());
        }
    }

    public static void signpostEdit(Payloads.SignpostEdit msg, ServerPlayer p) {
        if (!SIGNPOSTS.tryAcquire(p.getUUID())) {
            return;
        }
        BlockPos pos = BlockPos.of(msg.pos());
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQ
                || !p.level().isLoaded(pos) || !(p.level().getBlockEntity(pos) instanceof SignpostBlockEntity be)) {
            return;
        }
        if (!mayEdit(p, pos)) {
            return;
        }
        if (be.edit(msg.label(), msg.glyph(), msg.colour(), msg.shown())) {
            SoundEvent e = ModSounds.get("signpost.write");
            if (e != null) {
                p.level().playSound(null, pos, e, SoundSource.BLOCKS, 0.8f, 1f);
            }
        }
    }

    /**
     * The checks vanilla makes before a player may change a block at {@code pos}: not a spectator, allowed to build
     * (adventure mode is not), and allowed to interact there ({@code mayInteract} covers spawn protection and is what
     * protection mods hook).
     */
    static boolean mayEdit(ServerPlayer p, BlockPos pos) {
        return !p.isSpectator() && p.mayBuild() && p.level().mayInteract(p, pos);
    }
}
