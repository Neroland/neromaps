package za.co.neroland.neromaps.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neromaps.NeroMapsCommon;

/**
 * NeroMaps' own creative tab, registered cross-loader through Neroland Core's {@link RegistrationProvider} over
 * the vanilla {@code CREATIVE_MODE_TAB} registry. Every Nero mod owns its tab; nothing is added to Core's
 * "Neroland" tab, which lists Core's own items only.
 *
 * <p>0.0.1 registers no items, so the tab is empty (and vanilla hides an empty tab). The icon is the vanilla
 * filled map until NeroMaps has an item of its own.
 */
public final class ModCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroMapsCommon.MOD_ID);

    /** Item suppliers in display order, drained into the tab when it is built. */
    private static final List<Supplier<? extends ItemLike>> CONTENTS = new ArrayList<>();

    // NOTE: vanilla CreativeModeTab.builder takes (Row, column); the no-arg overload and withTabsBefore/After are
    // NeoForge-only extensions, so they are avoided here (common = raw vanilla).
    public static final RegistryEntry<CreativeModeTab> NEROMAPS = TABS.register("neromaps",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.neromaps"))
                    .icon(() -> new ItemStack(Items.FILLED_MAP))
                    .displayItems((params, output) -> CONTENTS.forEach(s -> output.accept(s.get())))
                    .build());

    private ModCreativeTab() {
    }

    /** Append an item to the NeroMaps tab. Call during common init, before tabs are built. */
    public static void add(Supplier<? extends ItemLike> item) {
        CONTENTS.add(item);
    }

    /** Forces the tab registration (common init step 3, last). Items queue themselves with {@link #add}. */
    public static void init() {
        NeroMapsCommon.LOGGER.debug("[NeroMaps] creative tab registered ({} item(s))", CONTENTS.size());
    }
}
