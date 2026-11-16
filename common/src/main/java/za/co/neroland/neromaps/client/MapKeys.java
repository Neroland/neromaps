package za.co.neroland.neromaps.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?}

/**
 * Key mappings (category {@code key.category.neromaps.neromaps}): M world map, Y toggle minimap,
 * = and - zoom, U new waypoint here; toggle beams, cycle preset and open settings ship unbound. All are rebindable
 * and every action is safe when unbound. Client only.
 */
public final class MapKeys {

    //? if >=1.21.9 {
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("neromaps", "neromaps"));
    //?} else {
    /*public static final String CATEGORY = "key.category.neromaps.neromaps";
    *///?}

    public static final KeyMapping WORLD_MAP = key("world_map", InputConstants.KEY_M);
    public static final KeyMapping TOGGLE_MINIMAP = key("toggle_minimap", InputConstants.KEY_Y);
    public static final KeyMapping ZOOM_IN = key("zoom_in", InputConstants.KEY_EQUALS);
    public static final KeyMapping ZOOM_OUT = key("zoom_out", InputConstants.KEY_MINUS);
    public static final KeyMapping NEW_WAYPOINT = key("new_waypoint", InputConstants.KEY_U);
    public static final KeyMapping TOGGLE_BEAMS = key("toggle_beams", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping CYCLE_PRESET = key("cycle_preset", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping OPEN_SETTINGS = key("open_settings", InputConstants.UNKNOWN.getValue());

    private static final List<KeyMapping> ALL = List.of(WORLD_MAP, TOGGLE_MINIMAP, ZOOM_IN, ZOOM_OUT, NEW_WAYPOINT,
            TOGGLE_BEAMS, CYCLE_PRESET, OPEN_SETTINGS);

    private MapKeys() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.neromaps." + name, code, CATEGORY);
    }

    /** Every mapping, for the loaders' registration events. */
    public static List<KeyMapping> all() {
        return ALL;
    }
}
