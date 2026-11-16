package za.co.neroland.neromaps.atlas;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
//? if >=1.21.2 {
import net.minecraft.world.InteractionResult;
//?} else {
/*import net.minecraft.world.InteractionResultHolder;
*///?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import za.co.neroland.neromaps.compat.PlayerMessages;
import za.co.neroland.neromaps.config.NeroMapsConfig;
import za.co.neroland.neromaps.registry.ModDataComponents;
import za.co.neroland.neromaps.registry.ModItems;
import za.co.neroland.neromaps.registry.ModSounds;
import za.co.neroland.neromaps.server.MapServer;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.PlayerStore;

/**
 * The Atlas, blank and filled. A blank atlas records the user's own explored map of the current dimension (nearest
 * regions first, up to its capacity) and becomes a filled atlas. A filled atlas, used, merges its coverage into the
 * reader's own map (permanent, physical sharing: decision 007); sneak-used, it re-records the user's map. The stack
 * carries coverage only ({@link AtlasData}), never who recorded it. All of it resolves on the server.
 */
public final class AtlasItem extends Item {

    private final boolean blank;

    public AtlasItem(Properties properties, boolean blank) {
        super(properties);
        this.blank = blank;
    }

    public static AtlasData data(ItemStack stack) {
        AtlasData d = stack.get(ModDataComponents.ATLAS.get());
        return d == null ? AtlasData.BLANK : d;
    }

    private void act(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return;
        }
        MinecraftServer server = sp.level().getServer();
        MapPlayerState state = PlayerStore.get(server);
        ItemStack stack = player.getItemInHand(hand);
        AtlasData d = data(stack);
        if (blank || player.isShiftKeyDown()) {
            String dim = MapServer.dimension(sp);
            AtlasData recorded = d.record(state.record(sp.getUUID()).explored(), dim, sp.getBlockX() >> 4,
                    sp.getBlockZ() >> 4);
            if (!recorded.filled()) {
                PlayerMessages.overlay(player, Component.translatable("item.neromaps.atlas.nothing"));
                return;
            }
            ItemStack filled = new ItemStack(ModItems.ATLAS.get());
            filled.set(ModDataComponents.ATLAS.get(), recorded);
            if (blank) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, filled);
                } else if (!player.getInventory().add(filled)) {
                    level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, player.getX(),
                            player.getY(), player.getZ(), filled));
                }
            } else {
                stack.set(ModDataComponents.ATLAS.get(), recorded);
            }
            sound(sp, "atlas.open");
            PlayerMessages.overlay(player, Component.translatable("item.neromaps.atlas.recorded",
                    recorded.regions().size(), recorded.capacity()));
            return;
        }
        if (!d.filled()) {
            return;
        }
        int n = d.mergeInto(state.record(sp.getUUID()).explored(), NeroMapsConfig.maxExploredRegions());
        state.changed();
        sound(sp, "atlas.merge");
        PlayerMessages.overlay(player, Component.translatable("item.neromaps.atlas.merged", n));
    }

    static void sound(ServerPlayer p, String key) {
        SoundEvent e = ModSounds.get(key);
        if (e != null) {
            p.level().playSound(null, p.blockPosition(), e, SoundSource.PLAYERS, 0.8f, 1f);
        }
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        act(level, player, hand);
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        act(level, player, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
    *///?}
}
