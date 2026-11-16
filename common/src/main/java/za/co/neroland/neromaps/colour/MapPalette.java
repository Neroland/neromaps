package za.co.neroland.neromaps.colour;

/**
 * The NeroMaps palette (decision 013 section 1), byte-for-byte with the art tools. ARGB, opaque unless stated.
 */
public final class MapPalette {

    public static final int G_DARK = 0xFF1A1D22;
    public static final int GRAPHITE = 0xFF2C3137;
    public static final int TRIM = 0xFF5B6670;
    public static final int STEEL = 0xFF7A8492;
    public static final int G_LIGHT = 0xFF8E98A4;
    public static final int GLASS = 0xFF0B1119;
    public static final int PANEL = 0xFF141C26;
    public static final int PANEL_EDGE = 0xFF2A3A4D;
    public static final int TEXT = 0xFFE8ECF4;
    public static final int TEXT_SECONDARY = 0xFF97A6BC;

    public static final int MAP_ACCENT = 0xFF7C8CFF;
    public static final int MAP_ACCENT_BRIGHT = 0xFFB2BEFF;
    public static final int MAP_ACCENT_DIM = 0xFF3A4270;
    public static final int VOID = 0xFF070B12;
    public static final int VOID_STAR = 0xFF1E2A3A;
    public static final int VOID_EDGE = 0xFF2A3350;

    public static final int M_ORANGE = 0xFFE69F00;
    public static final int M_SKY = 0xFF56B4E9;
    public static final int M_GREEN = 0xFF009E73;
    public static final int M_YELLOW = 0xFFF0E442;
    public static final int M_VERMILLION = 0xFFD55E00;
    public static final int M_PURPLE = 0xFFCC79A7;
    public static final int M_WHITE = 0xFFF2F5F8;

    /** Night multiply colour (Day / Night style). */
    public static final int NIGHT = 0xFF6F7FC8;
    public static final float NIGHT_MAX = 0.45f;
    public static final int LAVA = 0xFFE8651A;
    public static final int HC_LAVA = 0xFFFF7A00;
    public static final int HC_WATER = 0xFF0B2A7A;
    public static final int HEIGHT_WATER = 0xFF2C5FA8;
    public static final int[] HC_BANDS = {0xFF3A3A3A, 0xFF6B6B6B, 0xFF9C9C9C, 0xFFD0D0D0};

    /** Hypsometric stops for the Height-shaded style: height offset from sea level, colour. */
    public static final int[] HEIGHT_STOPS = {-95, 0xFF101A33, -63, 0xFF1B3A5C, 0, 0xFF1F6F78, 24, 0xFF3F9E78,
            64, 0xFF9CC76B, 112, 0xFFE3D77A, 176, 0xFFF1EBCB, 256, 0xFFFFFFFF};

    private MapPalette() {
    }
}
