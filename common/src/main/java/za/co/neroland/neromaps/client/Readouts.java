package za.co.neroland.neromaps.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;

import za.co.neroland.neromaps.settings.MinimapSettings;

/**
 * The minimap's text readouts (coordinates, facing, biome, dimension, time, light), each toggled separately.
 * Computed once per tick and rebuilt only when a value changes, so a still player costs no string building. The
 * strings are drawn on the client only and never logged or stored.
 */
public final class Readouts {

    public static final int MAX = 6;
    /** Lines to draw this frame, in order; only the first {@link #count} are valid. */
    public static final String[] LINES = new String[MAX];
    public static int count;

    private static int lx = Integer.MIN_VALUE;
    private static int ly;
    private static int lz;
    private static String coords = "";
    private static int lastFacing = -1;
    private static String facing = "";
    private static String lastBiome = "";
    private static String biome = "";
    private static String lastDim = "";
    private static String dim = "";
    private static int lastMinute = -1;
    private static String time = "";
    private static int lastLight = -1;
    private static String light = "";

    private static final String[] DIRS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private Readouts() {
    }

    public static void update(Minecraft mc, ClientLevel level, LocalPlayer player) {
        MinimapSettings s = ClientMap.SETTINGS;
        int n = 0;
        BlockPos pos = player.blockPosition();
        if (s.showCoords) {
            if (pos.getX() != lx || pos.getY() != ly || pos.getZ() != lz) {
                lx = pos.getX();
                ly = pos.getY();
                lz = pos.getZ();
                coords = lx + ", " + ly + ", " + lz;
            }
            LINES[n++] = coords;
        }
        if (s.showFacing) {
            int f = Math.floorMod(Math.round(player.getYRot() / 45f), 8);
            if (f != lastFacing) {
                lastFacing = f;
                facing = Component.translatable("gui.neromaps.readout.facing." + DIRS[f].toLowerCase(java.util.Locale.ROOT)).getString();
            }
            LINES[n++] = facing;
        }
        if (s.showBiome) {
            String id = level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
            if (!id.equals(lastBiome)) {
                lastBiome = id;
                int c = id.indexOf(':');
                biome = c < 0 ? id
                        : Component.translatableWithFallback("biome." + id.substring(0, c) + "." + id.substring(c + 1),
                                id.substring(c + 1)).getString();
            }
            LINES[n++] = biome;
        }
        if (s.showDimension) {
            String id = ClientMap.dimensionId();
            if (!id.equals(lastDim)) {
                lastDim = id;
                int c = id.indexOf(':');
                String path = c < 0 ? id : id.substring(c + 1);
                dim = Component.translatableWithFallback("gui.neromaps.dimension." + id.replace(':', '.'), path).getString();
            }
            LINES[n++] = dim;
        }
        if (s.showTime) {
            //? if >=26.1 {
            long t = level.getOverworldClockTime();
            //?} else {
            /*long t = level.getDayTime();
            *///?}
            int day = (int) Math.floorMod(t, 24000L);
            int minute = (day / 1000 * 60 + (day % 1000) * 60 / 1000 + 360) % 1440;
            if (minute != lastMinute) {
                lastMinute = minute;
                int h = minute / 60;
                int m = minute % 60;
                time = (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
            }
            LINES[n++] = time;
        }
        if (s.showLight) {
            int bl = level.getBrightness(LightLayer.BLOCK, pos);
            int sl = level.getBrightness(LightLayer.SKY, pos);
            int key = bl * 16 + sl;
            if (key != lastLight) {
                lastLight = key;
                light = Component.translatable("gui.neromaps.readout.light", bl, sl).getString();
            }
            LINES[n++] = light;
        }
        count = n;
    }

    /** Time of day for the night tint (0..23999). */
    public static long dayTime(ClientLevel level) {
        //? if >=26.1 {
        return level.getOverworldClockTime();
        //?} else {
        /*return level.getDayTime();
        *///?}
    }
}
