package za.co.neroland.neromaps.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
//? if >=1.20.5 {
import net.minecraft.world.item.component.LodestoneTracker;
//?}
import net.minecraft.world.level.Level;

import za.co.neroland.neromaps.compat.PlayerMessages;
import za.co.neroland.neromaps.server.MapServer;
import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.PlayerRecord;
import za.co.neroland.neromaps.store.PlayerStore;
import za.co.neroland.neromaps.store.Waypoint;

/**
 * The Waypoint Compass: use it to lock onto the next of your waypoints (and death points) in this dimension, sneak-use
 * to clear. The target is written as the vanilla lodestone tracker (not tracked), so the needle uses vanilla's compass
 * property; the stack holds a block position the owner chose and no identity. The lock is resolved on the server from
 * the user's own record, so a compass handed to someone else keeps pointing where it was set and reveals nothing more.
 */
public final class WaypointCompassItem extends Item {

    public WaypointCompassItem(Properties properties) {
        super(properties);
    }

    private record Target(String name, String dimension, BlockPos pos) { }

    private static boolean act(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return true;
        }
        ItemStack stack = player.getItemInHand(hand);
        ServerLevel sl = sp.level();
        MinecraftServer server = sl.getServer();
        if (player.isShiftKeyDown()) {
            //? if >=1.20.5 {
            stack.remove(DataComponents.LODESTONE_TRACKER);
            //?} else {
            /*stack.removeTagKey("LodestonePos");
            stack.removeTagKey("LodestoneDimension");
            stack.removeTagKey("LodestoneTracked");
            *///?}
            PlayerMessages.overlay(player, Component.translatable("neromaps.compass.cleared"));
            return true;
        }
        String dim = MapServer.dimension(sp);
        List<Target> targets = new ArrayList<>();
        PlayerRecord r = PlayerStore.get(server).find(sp.getUUID()).orElse(null);
        if (r != null) {
            for (Waypoint w : r.waypoints()) {
                if (w.dimension().equals(dim)) {
                    targets.add(new Target(w.name(), w.dimension(), new BlockPos(w.x(), w.y(), w.z())));
                }
            }
            int i = 1;
            for (DeathPoint d : r.deaths()) {
                if (d.dimension().equals(dim)) {
                    targets.add(new Target(Component.translatable("neromaps.compass.death", i).getString(),
                            d.dimension(), new BlockPos(d.x(), d.y(), d.z())));
                }
                i++;
            }
        }
        if (targets.isEmpty()) {
            PlayerMessages.overlay(player, Component.translatable("neromaps.compass.none"));
            return true;
        }
        //? if >=1.20.5 {
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        int next = 0;
        if (current != null && current.target().isPresent()) {
            BlockPos at = current.target().get().pos();
        //?} else {
        /*var tag = stack.getTag();
        int next = 0;
        if (tag != null && tag.contains("LodestonePos", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            BlockPos at = net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("LodestonePos"));
        *///?}
            for (int k = 0; k < targets.size(); k++) {
                if (targets.get(k).pos().equals(at)) {
                    next = (k + 1) % targets.size();
                    break;
                }
            }
        }
        Target t = targets.get(next);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.dimension()));
        //? if >=1.20.5 {
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(key, t.pos())), false));
        //?} else {
        /*var lodestone = stack.getOrCreateTag();
        lodestone.put("LodestonePos", net.minecraft.nbt.NbtUtils.writeBlockPos(t.pos()));
        lodestone.putString("LodestoneDimension", t.dimension());
        lodestone.putBoolean("LodestoneTracked", false);
        *///?}
        PlayerMessages.overlay(player, Component.translatable("neromaps.compass.locked", t.name()));
        return true;
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
