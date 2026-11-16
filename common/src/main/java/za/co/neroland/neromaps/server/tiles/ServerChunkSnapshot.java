package za.co.neroland.neromaps.server.tiles;

import java.util.ArrayList;
import java.util.IdentityHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
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

/**
 * The server-thread copy of one loaded chunk for capture (decision 006): WORLD_SURFACE heights, copies of the
 * block-state containers in the band the scan can reach, and the biome id of each 4 x 4 quart column at the surface.
 * It holds no reference to the chunk, no entity, block entity or text. Block classification runs later on the
 * single capture worker through {@link Keys}.
 */
public final class ServerChunkSnapshot implements ChunkAccess.Snapshot {

    public static final int SECTIONS_BELOW = 4;

    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int maxY;
    private final int minSection;
    private final boolean roofed;
    private final int roofTop;
    private final int[] surface = new int[256];
    private final @Nullable PalettedContainer<BlockState>[] sections;
    private final int[] biomeKey = new int[16];
    private final String[] biomeIds;

    @SuppressWarnings("unchecked")
    private ServerChunkSnapshot(LevelChunk chunk, ServerLevel level) {
        this.chunkX = chunk.getPos().getMinBlockX() >> 4;
        this.chunkZ = chunk.getPos().getMinBlockZ() >> 4;
        //? if >=1.21.4 {
        this.minY = level.getMinY();
        //?} else {
        /*this.minY = level.getMinBuildHeight();
        *///?}
        this.maxY = minY + level.getHeight();
        this.minSection = minY >> 4;
        this.roofed = level.dimensionType().hasCeiling();
        this.roofTop = minY + level.dimensionType().logicalHeight() - 1;
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
        int from = roofed ? 0 : ((lowest - 1) >> 4) - minSection - SECTIONS_BELOW;
        int to = roofed ? src.length - 1 : ((highest - 1) >> 4) - minSection;
        from = Math.max(0, from);
        to = Math.min(src.length - 1, to);
        for (int i = from; i <= to; i++) {
            LevelChunkSection sec = src[i];
            if (sec != null && !sec.hasOnlyAir()) {
                sections[i] = sec.getStates().copy();
            }
        }
        ArrayList<String> ids = new ArrayList<>(4);
        IdentityHashMap<Holder<Biome>, Integer> seen = new IdentityHashMap<>();
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int y = surface[((qz * 4) << 4) | (qx * 4)] - 1;
                y = Math.max(minY, Math.min(maxY - 1, y));
                Holder<Biome> h = chunk.getNoiseBiome((chunkX << 2) + qx, y >> 2, (chunkZ << 2) + qz);
                Integer k = seen.get(h);
                if (k == null) {
                    k = ids.size();
                    seen.put(h, k);
                    ids.add(h.unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:plains"));
                }
                biomeKey[(qz << 2) | qx] = k;
            }
        }
        biomeIds = ids.toArray(new String[0]);
    }

    /** Copies {@code chunk} on the server thread. */
    public static ServerChunkSnapshot of(LevelChunk chunk, ServerLevel level) {
        return new ServerChunkSnapshot(chunk, level);
    }

    @Override
    public int chunkX() {
        return chunkX;
    }

    @Override
    public int chunkZ() {
        return chunkZ;
    }

    @Override
    public boolean roofed() {
        return roofed;
    }

    @Override
    public int roofTop() {
        return roofTop;
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
        return Keys.entry(state(x, y, z)) & 7;
    }

    @Override
    public int block(int x, int y, int z) {
        return Keys.entry(state(x, y, z)) >>> 3;
    }

    @Override
    public int biome(int x, int y, int z) {
        return biomeKey[((z >> 2) << 2) | (x >> 2)];
    }

    @Override
    public int airBlock() {
        return Keys.AIR_KEY;
    }

    @Override
    public String blockId(int key) {
        return Keys.id(key);
    }

    @Override
    public String biomeId(int key) {
        return key >= 0 && key < biomeIds.length ? biomeIds[key] : "minecraft:plains";
    }

    /** Block classification memo, owned by the single capture worker thread (no locks). */
    static final class Keys {
        static final int AIR_KEY = 0;
        private static final IdentityHashMap<BlockState, Integer> MEMO = new IdentityHashMap<>();
        private static final IdentityHashMap<net.minecraft.world.level.block.Block, Integer> BLOCKS =
                new IdentityHashMap<>();
        private static final ArrayList<String> IDS = new ArrayList<>();

        static {
            IDS.add("minecraft:air");
        }

        private Keys() {
        }

        static int entry(BlockState s) {
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

        static String id(int key) {
            return key >= 0 && key < IDS.size() ? IDS.get(key) : "minecraft:air";
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
