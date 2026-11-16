package za.co.neroland.neromaps.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import za.co.neroland.neromaps.text.NameFilter;

/**
 * One saved waypoint: the player's own content, created and deleted at will. The name is already
 * filtered ({@link NameFilter}); the colour is RGB; the icon is a waypoint glyph id from the icon atlas.
 *
 * @param id     server-assigned, unique within the owner's record
 * @param set    a free grouping label ("default" unless the player chose one); sets are per dimension
 * @param shared true once the owner has offered it to someone 
 */
public record Waypoint(int id, String set, String name, int colour, String icon, String dimension, int x, int y,
        int z, boolean shared, boolean beam, boolean distanceLabel) {

    public static final String DEFAULT_SET = "default";
    public static final String DEFAULT_ICON = "neromaps:glyph/dot";
    public static final int MAX_ID_LENGTH = 64;

    public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(Waypoint::id),
            Codec.STRING.optionalFieldOf("set", DEFAULT_SET).forGetter(Waypoint::set),
            Codec.STRING.fieldOf("name").forGetter(Waypoint::name),
            Codec.INT.optionalFieldOf("colour", 0xE69F00).forGetter(Waypoint::colour),
            Codec.STRING.optionalFieldOf("icon", DEFAULT_ICON).forGetter(Waypoint::icon),
            Codec.STRING.fieldOf("dim").forGetter(Waypoint::dimension),
            Codec.INT.fieldOf("x").forGetter(Waypoint::x),
            Codec.INT.fieldOf("y").forGetter(Waypoint::y),
            Codec.INT.fieldOf("z").forGetter(Waypoint::z),
            Codec.BOOL.optionalFieldOf("shared", false).forGetter(Waypoint::shared),
            Codec.BOOL.optionalFieldOf("beam", true).forGetter(Waypoint::beam),
            Codec.BOOL.optionalFieldOf("label", true).forGetter(Waypoint::distanceLabel)
    ).apply(i, Waypoint::new));

    public Waypoint {
        set = cleanId(set, DEFAULT_SET);
        name = NameFilter.clean(name);
        colour &= 0xFFFFFF;
        icon = cleanId(icon, DEFAULT_ICON);
        dimension = cleanId(dimension, "minecraft:overworld");
    }

    /** Keeps an id-like string to {@code [a-z0-9_.:/-]}, at most {@link #MAX_ID_LENGTH} characters. */
    public static String cleanId(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        StringBuilder b = new StringBuilder(Math.min(raw.length(), MAX_ID_LENGTH));
        for (int i = 0; i < raw.length() && b.length() < MAX_ID_LENGTH; i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == ':' || c == '/'
                    || c == '-') {
                b.append(c);
            }
        }
        return b.length() == 0 ? fallback : b.toString();
    }

    public Waypoint withShared(boolean value) {
        return new Waypoint(id, set, name, colour, icon, dimension, x, y, z, value, beam, distanceLabel);
    }
}
