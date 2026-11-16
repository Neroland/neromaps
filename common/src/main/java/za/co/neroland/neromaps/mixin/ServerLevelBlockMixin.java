package za.co.neroland.neromaps.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import za.co.neroland.neromaps.server.tiles.CaptureService;

/**
 * Server. Every block change a server level applies (players, pistons, explosions, fluids) passes through
 * {@code ServerLevel.sendBlockUpdated} on every line from 1.21.1 to 26.3 (decision 006). NeroMaps only adds a dirty
 * mark for the chunk here; capture happens later, within its budget, and only when tiles are needed. Optional
 * ({@code require = 0}): without it, terrain is still captured when first explored.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockMixin {

    @Inject(method = "sendBlockUpdated", at = @At("TAIL"), require = 0)
    private void neromaps$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags,
            CallbackInfo ci) {
        if (oldState != newState) {
            CaptureService.blockChanged((ServerLevel) (Object) this, pos);
        }
    }
}
