package za.co.neroland.neromaps.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.atlas.AtlasItem;
import za.co.neroland.neromaps.atlas.AtlasUpgradeItem;
import za.co.neroland.neromaps.item.WaypointCompassItem;

/**
 * NeroMaps items, registered through Core's registration seam: the Waypoint Compass, the five block items,
 * the atlas (blank and filled) and its two upgrades. The Survey Kit is cut for 0.1.0.
 */
public final class ModItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroMapsCommon.MOD_ID);

    public static final RegistryEntry<Item> WAYPOINT_COMPASS = ITEMS.register(MapIds.WAYPOINT_COMPASS,
            key -> new WaypointCompassItem(new Item.Properties().setId(key).stacksTo(1)));

    public static final RegistryEntry<Item> CARTOGRAPHY_DESK = block(MapIds.CARTOGRAPHY_DESK, ModBlocks.CARTOGRAPHY_DESK);
    public static final RegistryEntry<Item> WALL_MAP = block(MapIds.WALL_MAP, ModBlocks.WALL_MAP);
    public static final RegistryEntry<Item> SIGNPOST = block(MapIds.SIGNPOST, ModBlocks.SIGNPOST);
    public static final RegistryEntry<Item> MAP_TABLE = block(MapIds.MAP_TABLE, ModBlocks.MAP_TABLE);
    public static final RegistryEntry<Item> TRAIL_MARKER = block(MapIds.TRAIL_MARKER, ModBlocks.TRAIL_MARKER);

    public static final RegistryEntry<Item> ATLAS_BLANK = ITEMS.register(MapIds.ATLAS_BLANK,
            key -> new AtlasItem(new Item.Properties().setId(key).stacksTo(16), true));
    public static final RegistryEntry<Item> ATLAS = ITEMS.register(MapIds.ATLAS,
            key -> new AtlasItem(new Item.Properties().setId(key).stacksTo(1), false));
    public static final RegistryEntry<Item> ATLAS_UPGRADE_PAGES = ITEMS.register(MapIds.ATLAS_UPGRADE_PAGES,
            key -> new AtlasUpgradeItem(new Item.Properties().setId(key), false));
    public static final RegistryEntry<Item> ATLAS_UPGRADE_BINDING = ITEMS.register(MapIds.ATLAS_UPGRADE_BINDING,
            key -> new AtlasUpgradeItem(new Item.Properties().setId(key).stacksTo(16), true));

    private ModItems() {
    }

    private static RegistryEntry<Item> block(String id, Supplier<Block> block) {
        return ITEMS.register(id, key -> new BlockItem(block.get(),
                new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    /** Forces registration and queues the items in NeroMaps' creative tab (common init step 3). */
    public static void init() {
        ModCreativeTab.add(CARTOGRAPHY_DESK::get);
        ModCreativeTab.add(WALL_MAP::get);
        ModCreativeTab.add(MAP_TABLE::get);
        ModCreativeTab.add(SIGNPOST::get);
        ModCreativeTab.add(TRAIL_MARKER::get);
        ModCreativeTab.add(ATLAS_BLANK::get);
        ModCreativeTab.add(ATLAS::get);
        ModCreativeTab.add(ATLAS_UPGRADE_PAGES::get);
        ModCreativeTab.add(ATLAS_UPGRADE_BINDING::get);
        ModCreativeTab.add(WAYPOINT_COMPASS::get);
    }
}
