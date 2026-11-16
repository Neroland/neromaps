package za.co.neroland.neromaps.registry;

import java.util.List;

/**
 * Every registry path NeroMaps owns, in one place, so the asset checker, the identity scan and the quest-pack test
 * can enumerate them without bootstrapping the game. Block id budget: 5 (decision 013 section 8). The Survey Kit is
 * cut (first in the build prompt's cut order); its art stays unregistered.
 */
public final class MapIds {

    public static final String CARTOGRAPHY_DESK = "cartography_desk";
    public static final String WALL_MAP = "wall_map";
    public static final String SIGNPOST = "signpost";
    public static final String MAP_TABLE = "map_table";
    public static final String TRAIL_MARKER = "trail_marker";

    public static final String ATLAS_BLANK = "atlas_blank";
    public static final String ATLAS = "atlas";
    public static final String ATLAS_UPGRADE_PAGES = "atlas_upgrade_pages";
    public static final String ATLAS_UPGRADE_BINDING = "atlas_upgrade_binding";
    public static final String WAYPOINT_COMPASS = "waypoint_compass";

    /** Every registered block (each also has a block item). */
    public static final List<String> BLOCKS = List.of(CARTOGRAPHY_DESK, WALL_MAP, SIGNPOST, MAP_TABLE, TRAIL_MARKER);

    /** Every registered item, block items included. */
    public static final List<String> ITEMS = List.of(CARTOGRAPHY_DESK, WALL_MAP, SIGNPOST, MAP_TABLE, TRAIL_MARKER,
            ATLAS_BLANK, ATLAS, ATLAS_UPGRADE_PAGES, ATLAS_UPGRADE_BINDING, WAYPOINT_COMPASS);

    /** The {@code sounds.json} keys registered as sound events. */
    public static final List<String> SOUNDS = List.of("atlas.merge", "atlas.open", "compass.lock", "desk.copy",
            "desk.print", "map_table.hum", "share.accept", "share.request", "share.revoke", "signpost.write",
            "spyglass.mark", "survey.ping", "trail_marker.place", "ui.click", "ui.map_close", "ui.map_open", "ui.zoom",
            "wall_map.set", "waypoint.create", "waypoint.death", "waypoint.remove");

    private MapIds() {
    }
}
