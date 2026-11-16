package za.co.neroland.neromaps.tile;

import java.util.Random;

/**
 * Synthetic terrain fixtures for the codec size and speed tests (decision 004 M1). Deterministic, shaped like
 * captured terrain: plains with rivers and flowers, a deep ocean with a sand/gravel floor, and mountains.
 */
public final class Fixtures {

    private Fixtures() {
    }

    private static double noise(double x, double z, long seed) {
        return Math.sin(x * 0.031 + seed) * 0.5 + Math.sin(z * 0.027 + seed * 3) * 0.5
                + Math.sin((x + z) * 0.071 + seed * 7) * 0.25 + Math.sin((x - z) * 0.013) * 0.4;
    }

    public static Tile plains() {
        Tile t = new Tile(0);
        Random r = new Random(1);
        int grass = t.internBlock("minecraft:grass_block");
        int water = t.internBlock("minecraft:dirt");
        int sand = t.internBlock("minecraft:sand");
        int[] flowers = {t.internBlock("minecraft:poppy"), t.internBlock("minecraft:dandelion"),
                t.internBlock("minecraft:oak_leaves"), t.internBlock("minecraft:short_grass")};
        int plains = t.internBiome("minecraft:plains");
        int river = t.internBiome("minecraft:river");
        for (int z = 0; z < 256; z++) {
            for (int x = 0; x < 256; x++) {
                double n = noise(x, z, 1);
                int h = 66 + (int) Math.round(n * 4);
                if (Math.abs(Math.sin(x * 0.02) * 40 + 128 - z) < 4) {
                    t.set(x, z, Tile.F_PRESENT | Tile.F_WATER, 62, water, river, 2 + (int) Math.abs(n * 3));
                } else if (Math.abs(Math.sin(x * 0.02) * 40 + 128 - z) < 6) {
                    t.set(x, z, Tile.F_PRESENT, 63, sand, river, 0);
                } else {
                    int b = r.nextInt(12) == 0 ? flowers[r.nextInt(flowers.length)] : grass;
                    t.set(x, z, Tile.F_PRESENT, h + (b == flowers[2] ? 4 : 0), b, plains, 0);
                }
            }
        }
        return t;
    }

    public static Tile ocean() {
        Tile t = new Tile(0);
        int sand = t.internBlock("minecraft:sand");
        int gravel = t.internBlock("minecraft:gravel");
        int kelp = t.internBlock("minecraft:kelp_plant");
        int ocean = t.internBiome("minecraft:deep_ocean");
        for (int z = 0; z < 256; z++) {
            for (int x = 0; x < 256; x++) {
                double n = noise(x, z, 2);
                int depth = 30 + (int) Math.round(n * 8);
                int b = n > 0.6 ? kelp : n > 0 ? sand : gravel;
                t.set(x, z, Tile.F_PRESENT | Tile.F_WATER, 62, b, ocean, depth);
            }
        }
        return t;
    }

    public static Tile mountains() {
        Tile t = new Tile(0);
        Random r = new Random(3);
        int stone = t.internBlock("minecraft:stone");
        int snow = t.internBlock("minecraft:snow_block");
        int grass = t.internBlock("minecraft:grass_block");
        int spruce = t.internBlock("minecraft:spruce_leaves");
        int[] biomes = {t.internBiome("minecraft:jagged_peaks"), t.internBiome("minecraft:meadow"),
                t.internBiome("minecraft:snowy_slopes")};
        for (int z = 0; z < 256; z++) {
            for (int x = 0; x < 256; x++) {
                double n = noise(x * 2.3, z * 2.1, 3) + noise(x * 5.1, z * 4.7, 5) * 0.3;
                int h = 120 + (int) Math.round(n * 60) + r.nextInt(3);
                int b = h > 170 ? snow : h > 140 ? stone : r.nextInt(6) == 0 ? spruce : grass;
                t.set(x, z, Tile.F_PRESENT, h, b, biomes[h > 170 ? 0 : h > 140 ? 2 : 1], 0);
            }
        }
        return t;
    }

    /** A tile with random columns everywhere (worst case for the codec). */
    public static Tile random(long seed, int blocks, int biomes, double presentRatio) {
        Random r = new Random(seed);
        Tile t = new Tile(0);
        for (int i = 0; i < blocks; i++) {
            t.internBlock("test:block_" + i);
        }
        for (int i = 0; i < biomes; i++) {
            t.internBiome("test:biome_" + i);
        }
        for (int z = 0; z < 256; z++) {
            for (int x = 0; x < 256; x++) {
                if (r.nextDouble() >= presentRatio) {
                    continue;
                }
                int f = Tile.F_PRESENT | (r.nextInt(8) == 0 ? Tile.F_WATER : 0) | (r.nextInt(16) == 0 ? Tile.F_CEILING : 0);
                t.set(x, z, f, r.nextInt(600) - 64, r.nextInt(blocks), r.nextInt(biomes), (f & Tile.F_WATER) != 0 ? r.nextInt(256) : 0);
            }
        }
        return t;
    }
}
