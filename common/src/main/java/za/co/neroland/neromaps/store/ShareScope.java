package za.co.neroland.neromaps.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one share covers (decision 009): a single waypoint ({@code arg} = its id), the explored map of one dimension or
 * of all ({@code arg} = dimension id or {@link #ALL}), or the live position ({@code arg} empty).
 */
public record ShareScope(Kind kind, String arg) {

    public static final String ALL = "*";

    public enum Kind {
        WAYPOINT, EXPLORED_MAP, LIVE_POSITION;

        public static Kind byOrdinal(int o) {
            Kind[] v = values();
            return o >= 0 && o < v.length ? v[o] : LIVE_POSITION;
        }
    }

    public static final Codec<ShareScope> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(ShareScope::kindOf, Kind::name).fieldOf("kind").forGetter(ShareScope::kind),
            Codec.STRING.optionalFieldOf("arg", "").forGetter(ShareScope::arg)
    ).apply(i, ShareScope::new));

    public ShareScope {
        if (kind == null) {
            kind = Kind.LIVE_POSITION;
        }
        arg = switch (kind) {
            case LIVE_POSITION -> "";
            case WAYPOINT -> Waypoint.cleanId(arg, "0");
            case EXPLORED_MAP -> ALL.equals(arg) ? ALL : Waypoint.cleanId(arg, ALL);
        };
    }

    private static Kind kindOf(String s) {
        try {
            return Kind.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Kind.LIVE_POSITION;
        }
    }

    public static ShareScope waypoint(int id) {
        return new ShareScope(Kind.WAYPOINT, Integer.toString(id));
    }

    public static ShareScope map(String dimensionOrAll) {
        return new ShareScope(Kind.EXPLORED_MAP, dimensionOrAll);
    }

    public static ShareScope livePosition() {
        return new ShareScope(Kind.LIVE_POSITION, "");
    }

    /** The waypoint id of a {@code WAYPOINT} scope, or -1. */
    public int waypointId() {
        if (kind != Kind.WAYPOINT) {
            return -1;
        }
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** True when this explored-map scope covers {@code dimension}. */
    public boolean coversDimension(String dimension) {
        return kind == Kind.EXPLORED_MAP && (ALL.equals(arg) || arg.equals(dimension));
    }
}
