package za.co.neroland.neromaps.atlas;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import za.co.neroland.neromaps.registry.ModDataComponents;

/**
 * An atlas upgrade: a Page Pack (+{@value AtlasData#REGIONS_PER_PAGE} regions) or the Binding Ring (lets an atlas take
 * up to {@value AtlasData#MAX_PAGES} packs instead of {@value AtlasData#MAX_PAGES_UNBOUND}). Use it with an atlas,
 * blank or filled, in the other hand; one is consumed.
 */
public final class AtlasUpgradeItem extends Item {

    private final boolean binding;

    public AtlasUpgradeItem(Properties properties, boolean binding) {
        super(properties);
        this.binding = binding;
    }

    private void act(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return;
        }
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack atlas = player.getItemInHand(other);
        if (!(atlas.getItem() instanceof AtlasItem) || atlas.getCount() != 1) {
            PlayerMessages.overlay(player, Component.translatable("item.neromaps.atlas_upgrade.hint"));
            return;
        }
        AtlasData d = AtlasItem.data(atlas);
        AtlasData up = binding ? d.withBinding() : d.withPage();
        if (up == null) {
            PlayerMessages.overlay(player, Component.translatable("item.neromaps.atlas_upgrade.full"));
            return;
        }
        atlas.set(ModDataComponents.ATLAS.get(), up);
        player.getItemInHand(hand).shrink(1);
        AtlasItem.sound(sp, "atlas.open");
        PlayerMessages.overlay(player, Component.translatable("item.neromaps.atlas_upgrade.done", up.capacity()));
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
