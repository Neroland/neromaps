package za.co.neroland.neromaps.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import za.co.neroland.neromaps.client.ClientMap;

/**
 * Client only. Every block change the client applies passes through {@code ClientLevel.sendBlockUpdated} on every
 * line from 1.21.1 to 26.3; NeroMaps only marks the chunk dirty here (coalesced and rescanned off-thread later).
 * Optional ({@code require = 0}): without it the map still refreshes when chunks reload.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelBlockMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), require = 0)
    private void neromaps$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags,
            CallbackInfo ci) {
        if (oldState != newState) {
            ClientMap.blockChanged(pos.getX(), pos.getZ());
        }
    }
}
