package za.co.neroland.neromaps.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.scan.ColumnSource;

/**
 * An immutable copy of what the scan needs from one loaded chunk, taken on the client thread and read on the
 * scan worker: the WORLD_SURFACE heights, copies of the block-state containers in the band the scan
 * can reach, and the biome of each 4 x 4 quart column at the surface. Nothing else leaves the chunk.
 */
public final class ChunkSnapshot implements ColumnSource {

    /** Sections kept below the lowest surface section (fluid depth and cave floors). */
    public static final int SECTIONS_BELOW = 4;

    public final int chunkX;
    public final int chunkZ;
    private final int minY;
    private final int maxY;
    private final int minSection;
    private final int[] surface = new int[256];
    private final @Nullable PalettedContainer<BlockState>[] sections;
    private final int[] biomeKey = new int[16];
    private final String[] biomeIds;

    public enum Mode { SURFACE, ROOFED, CAVE }

    @SuppressWarnings("unchecked")
    private ChunkSnapshot(LevelChunk chunk, Level level, Mode mode, int caveY) {
        this.chunkX = chunk.getPos().getMinBlockX() >> 4;
        this.chunkZ = chunk.getPos().getMinBlockZ() >> 4;
        //? if >=1.21.4 {
        this.minY = level.getMinY();
        //?} else {
        /*this.minY = level.getMinBuildHeight();
        *///?}
        this.maxY = minY + level.getHeight();
        this.minSection = minY >> 4;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int s = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                surface[(z << 4) | x] = s;
                lowest = Math.min(lowest, s);
                highest = Math.max(highest, s);
            }
        }
        LevelChunkSection[] src = chunk.getSections();
        sections = new PalettedContainer[src.length];
        int from;
        int to;
        if (mode == Mode.CAVE) {
            from = ((caveY - 40) >> 4) - minSection;
            to = ((caveY + 2) >> 4) - minSection;
        } else if (mode == Mode.ROOFED) {
            from = 0;
            to = src.length - 1;
        } else {
            from = ((lowest - 1) >> 4) - minSection - SECTIONS_BELOW;
            to = ((highest - 1) >> 4) - minSection;
        }
        from = Math.max(0, from);
        to = Math.min(src.length - 1, to);
        for (int i = from; i <= to; i++) {
            LevelChunkSection sec = src[i];
            if (sec != null && !sec.hasOnlyAir()) {
                sections[i] = sec.getStates().copy();
            }
        }
        // biome at the surface of each quart column, resolved to ids here (main thread)
        ArrayList<String> ids = new ArrayList<>(4);
        IdentityHashMap<Holder<Biome>, Integer> seen = new IdentityHashMap<>();
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int y = mode == Mode.CAVE ? caveY : surface[((qz * 4) << 4) | (qx * 4)] - 1;
                y = Math.max(minY, Math.min(maxY - 1, y));
                Holder<Biome> h = chunk.getNoiseBiome((chunkX << 2) + qx, y >> 2, (chunkZ << 2) + qz);
                Integer k = seen.get(h);
                if (k == null) {
                    k = ids.size();
                    seen.put(h, k);
                    String bid = biomeId(h);
                    ids.add(bid);
                    ColourTables.resolveBiome(h, bid);
                }
                biomeKey[(qz << 2) | qx] = k;
            }
        }
        biomeIds = ids.toArray(new String[0]);
    }

    /** Snapshots a chunk on the client thread. */
    public static ChunkSnapshot of(LevelChunk chunk, Level level, Mode mode, int caveY) {
        return new ChunkSnapshot(chunk, level, mode, caveY);
    }

    static String biomeId(Holder<Biome> h) {
        return h.unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:plains");
    }

    public String biomeIdOf(int key) {
        return biomeIds[key];
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public int surfaceY(int x, int z) {
        return surface[(z << 4) | x];
    }

    @Override
    public boolean sectionEmpty(int y) {
        int i = (y >> 4) - minSection;
        return i < 0 || i >= sections.length || sections[i] == null;
    }

    private BlockState state(int x, int y, int z) {
        PalettedContainer<BlockState> c = sections[(y >> 4) - minSection];
        return c.get(x, y & 15, z);
    }

    @Override
    public int kind(int x, int y, int z) {
        return StateKeys.kind(state(x, y, z));
    }

    @Override
    public int block(int x, int y, int z) {
        return StateKeys.key(state(x, y, z));
    }

    @Override
    public int biome(int x, int y, int z) {
        return biomeKey[((z >> 2) << 2) | (x >> 2)];
    }

    @Override
    public int airBlock() {
        return StateKeys.AIR_KEY;
    }

    /**
     * Block-state classification and block ids for the scan worker. One worker thread owns the memo, so it needs
     * no locks; state methods used here only read immutable state data.
     */
    public static final class StateKeys {

        public static final int AIR_KEY = 0;
        private static final IdentityHashMap<BlockState, Integer> MEMO = new IdentityHashMap<>();
        private static final IdentityHashMap<net.minecraft.world.level.block.Block, Integer> BLOCKS =
                new IdentityHashMap<>();
        private static final ArrayList<String> IDS = new ArrayList<>();

        static {
            IDS.add("minecraft:air");
        }

        private StateKeys() {
        }

        private static int entry(BlockState s) {
            Integer v = MEMO.get(s);
            if (v != null) {
                return v;
            }
            int kind = classify(s);
            Integer id = BLOCKS.get(s.getBlock());
            if (id == null) {
                id = IDS.size();
                IDS.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
                BLOCKS.put(s.getBlock(), id);
            }
            int packed = (id << 3) | kind;
            MEMO.put(s, packed);
            return packed;
        }

        static int kind(BlockState s) {
            return entry(s) & 7;
        }

        static int key(BlockState s) {
            return entry(s) >>> 3;
        }

        /** The id of a block key (worker thread). */
        public static String id(int key) {
            return IDS.get(key);
        }

        private static int classify(BlockState s) {
            if (s.isAir()) {
                return KIND_INVISIBLE;
            }
            FluidState f = s.getFluidState();
            if (!f.isEmpty()) {
                boolean water = f.is(FluidTags.WATER);
                if (s.getBlock() instanceof LiquidBlock) {
                    return water ? KIND_WATER : f.is(FluidTags.LAVA) ? KIND_LAVA : KIND_FLUID_OTHER;
                }
                if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    return KIND_WATERLOGGED;
                }
                // kelp, seagrass, bubble columns: always wet, read as water
                return water ? KIND_WATER : KIND_FLUID_OTHER;
            }
            try {
                if (s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == MapColor.NONE) {
                    return KIND_INVISIBLE;
                }
            } catch (RuntimeException e) {
                return KIND_SOLID;
            }
            return KIND_SOLID;
        }
    }
}
