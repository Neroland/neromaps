package za.co.neroland.neromaps.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.menu.CartographyDeskMenu;

/** Menu types (a plain vanilla {@code MenuType}: the desk needs no open data). */
public final class ModMenus {

    public static final RegistrationProvider<MenuType<?>> MENUS =
            RegistrationProvider.get(Registries.MENU, NeroMapsCommon.MOD_ID);

    public static final RegistryEntry<MenuType<CartographyDeskMenu>> CARTOGRAPHY_DESK = MENUS.register(
            MapIds.CARTOGRAPHY_DESK, key -> new MenuType<>(CartographyDeskMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
