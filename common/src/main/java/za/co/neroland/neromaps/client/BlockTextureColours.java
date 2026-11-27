package za.co.neroland.neromaps.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;
*///?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.neromaps.colour.ColourTable;

/**
 * Reads a block's map colour from the textures the client actually renders: the sprites of the top face of its
 * default state's model (what a map seen from above shows), averaged over their visible pixels in linear light,
 * read from each sprite's texture file through every active resource pack (first animation frame only). Cut-out and see-through pixels are left out (alpha below 128, or any
 * non-zero alpha when a texture has nothing more opaque), so glass, leaves and flowers read as what is drawn.
 * Blocks without a top face (crosses, fluids, block-entity renderers) use their other quads, then their particle
 * texture. A tinted face keeps the biome tint class for the rasteriser (grass, foliage, water) or, for a constant
 * tint such as spruce leaves or a modded block's own colour, has the block-colour handler's default colour
 * multiplied in.
 *
 * <p>Called by {@link ColourTable} once per block id per table, on the render thread, after the models are baked.
 * Never reads the atlas per pixel per frame: the result is memoised by the table, and sprite averages are cached
 * here until the next resource reload.
 */
public final class BlockTextureColours {

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final HashMap<Object, Integer> SPRITES = new HashMap<>();
    private static final float[] TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            TO_LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
    }

    private BlockTextureColours() {
    }

    /** Forgets the cached sprite averages (a resource reload replaces every sprite). */
    public static void clear() {
        SPRITES.clear();
    }

    /** The {@link ColourTable.Sampler}: packed {@code tint << 24 | rgb}, or -1 when the textures cannot be read. */
    public static int sample(String blockId, int tableTint) {
        Minecraft mc = Minecraft.getInstance();
        Identifier rl = Identifier.tryParse(blockId);
        if (mc == null || rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            return -1;
        }
        BlockState state = BuiltInRegistries.BLOCK.getValue(rl).defaultBlockState();
        Acc acc = new Acc();
        boolean modelRead = quads(mc, state, acc);
        if (acc.weight == 0) {
            TextureAtlasSprite particle = particle(mc, state);
            if (particle != null) {
                acc.add(particle, -1);
            }
            modelRead = false;
        }
        if (acc.weight == 0) {
            return -1;
        }
        int rgb = acc.rgb();
        int tint;
        if (!modelRead) {
            // no quads to say whether the face is tinted: trust the bundled table (water, lava and other fluids)
            tint = Math.max(ColourTable.TINT_NONE, tableTint);
        } else if (acc.tintIndex < 0) {
            tint = ColourTable.TINT_NONE;
        } else if (tableTint > ColourTable.TINT_NONE) {
            tint = tableTint; // vanilla's biome-tinted blocks: the table knows grass from foliage from water
        } else if (tableTint < 0 && state.is(BlockTags.LEAVES)) {
            tint = ColourTable.TINT_FOLIAGE; // a modded leaf block follows the biome's foliage
        } else {
            // a constant tint (spruce and birch leaves, lily pads, a modded block's own colour)
            int c = defaultTint(mc, state, acc.tintIndex);
            if (c != -1) {
                rgb = za.co.neroland.neromaps.colour.MapRasterizer.multiply(rgb, c & 0xFFFFFF);
            }
            tint = ColourTable.TINT_NONE;
        }
        return (tint << 24) | (rgb & 0xFFFFFF);
    }

    /** Adds the top-face sprites (or, with none, every other face) of the state's model; false when none. */
    private static boolean quads(Minecraft mc, BlockState state, Acc acc) {
        RandomSource random = RandomSource.create(42L);
        List<List<BakedQuad>> up = new ArrayList<>();
        List<List<BakedQuad>> other = new ArrayList<>();
        //? if >=26.1 {
        var model = mc.getModelManager().getBlockStateModelSet().get(state);
        List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (var part : parts) {
            up.add(part.getQuads(Direction.UP));
            other.add(part.getQuads(null));
            for (Direction d : HORIZONTAL) {
                other.add(part.getQuads(d));
            }
        }
        //?} else if >=1.21.5 {
        /*var model = mc.getBlockRenderer().getBlockModel(state);
        for (var part : model.collectParts(random)) {
            up.add(part.getQuads(Direction.UP));
            other.add(part.getQuads(null));
            for (Direction d : HORIZONTAL) {
                other.add(part.getQuads(d));
            }
        }
        *///?} else {
        /*var model = mc.getBlockRenderer().getBlockModel(state);
        up.add(model.getQuads(state, Direction.UP, random));
        other.add(model.getQuads(state, null, random));
        for (Direction d : HORIZONTAL) {
            other.add(model.getQuads(state, d, random));
        }
        *///?}
        // unculled quads facing up (slabs, carpets, snow layers) are top faces too
        for (List<BakedQuad> list : other) {
            for (BakedQuad q : list) {
                if (direction(q) == Direction.UP) {
                    acc.add(sprite(q), tintIndex(q));
                }
            }
        }
        for (List<BakedQuad> list : up) {
            for (BakedQuad q : list) {
                acc.add(sprite(q), tintIndex(q));
            }
        }
        if (acc.weight == 0) {
            for (List<BakedQuad> list : other) {
                for (BakedQuad q : list) {
                    acc.add(sprite(q), tintIndex(q));
                }
            }
        }
        return acc.weight > 0;
    }

    //? if >=26.1 {
    private static TextureAtlasSprite sprite(BakedQuad q) {
        return q.materialInfo().sprite();
    }

    private static int tintIndex(BakedQuad q) {
        return q.materialInfo().tintIndex();
    }

    private static Direction direction(BakedQuad q) {
        return q.direction();
    }

    private static TextureAtlasSprite particle(Minecraft mc, BlockState state) {
        return mc.getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();
    }

    private static int defaultTint(Minecraft mc, BlockState state, int index) {
        try {
            var source = mc.getBlockColors().getTintSource(state, index);
            return source == null ? -1 : source.color(state);
        } catch (RuntimeException e) {
            return -1;
        }
    }
    //?} else if >=1.21.5 {
    /*private static TextureAtlasSprite sprite(BakedQuad q) {
        return q.sprite();
    }

    private static int tintIndex(BakedQuad q) {
        return q.tintIndex();
    }

    private static Direction direction(BakedQuad q) {
        return q.direction();
    }
    *///?} else {
    /*private static TextureAtlasSprite sprite(BakedQuad q) {
        return q.getSprite();
    }

    private static int tintIndex(BakedQuad q) {
        return q.getTintIndex();
    }

    private static Direction direction(BakedQuad q) {
        return q.getDirection();
    }
    *///?}

    //? if <26.1 {
    /*private static TextureAtlasSprite particle(Minecraft mc, BlockState state) {
        return mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
    }

    private static int defaultTint(Minecraft mc, BlockState state, int index) {
        try {
            return mc.getBlockColors().getColor(state, null, null, index);
        } catch (RuntimeException e) {
            return -1; // a handler that needs a world
        }
    }
    *///?}

    /** The average colour of one sprite's first frame (cached per sprite), or -1 when it has nothing visible. */
    private static int average(TextureAtlasSprite sprite) {
        Object key = sprite.contents().name();
        Integer cached = SPRITES.get(key);
        if (cached != null) {
            return cached;
        }
        int result = -1;
        Identifier name = sprite.contents().name();
        if (!"missingno".equals(name.getPath())) {
            // the sprite's own texture file, through the resource packs (the same file the atlas was stitched from)
            Identifier file = Identifier.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
                if (res.isPresent()) {
                    try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                        result = averagePixels(img, sprite.contents().width(), sprite.contents().height());
                    }
                }
            } catch (IOException | RuntimeException e) {
                result = -1; // generated or unreadable sprite: the table and map colour fallbacks take over
            }
        }
        SPRITES.put(key, result);
        return result;
    }

    private static int averagePixels(NativeImage img, int frameW, int frameH) {
        int w = Math.min(frameW, img.getWidth());
        int h = Math.min(frameH, img.getHeight());
        double r = 0;
        double g = 0;
        double b = 0;
        int n = 0;
        double r2 = 0;
        double g2 = 0;
        double b2 = 0;
        int n2 = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = pixel(img, x, y);
                int a = argb >>> 24;
                if (a == 0) {
                    continue;
                }
                float lr = TO_LINEAR[(argb >> 16) & 0xFF];
                float lg = TO_LINEAR[(argb >> 8) & 0xFF];
                float lb = TO_LINEAR[argb & 0xFF];
                if (a >= 128) {
                    r += lr;
                    g += lg;
                    b += lb;
                    n++;
                } else {
                    r2 += lr;
                    g2 += lg;
                    b2 += lb;
                    n2++;
                }
            }
        }
        if (n == 0) {
            if (n2 == 0) {
                return -1;
            }
            r = r2;
            g = g2;
            b = b2;
            n = n2;
        }
        return (toSrgb(r / n) << 16) | (toSrgb(g / n) << 8) | toSrgb(b / n);
    }

    private static int pixel(NativeImage img, int x, int y) {
        //? if >=1.21.2 {
        return img.getPixel(x, y);
        //?} else {
        /*return za.co.neroland.neromaps.engine.RgbaStaging.toAbgr(img.getPixelRGBA(x, y));
        *///?}
    }

    private static int toSrgb(double linear) {
        double c = Math.max(0.0, Math.min(1.0, linear));
        double s = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(s * 255.0);
    }

    /** Equal-weight average of the face sprites, remembering the first tint index seen. */
    private static final class Acc {
        double r;
        double g;
        double b;
        int weight;
        int tintIndex = -1;

        void add(TextureAtlasSprite sprite, int tint) {
            int rgb = average(sprite);
            if (rgb < 0) {
                return;
            }
            r += TO_LINEAR[(rgb >> 16) & 0xFF];
            g += TO_LINEAR[(rgb >> 8) & 0xFF];
            b += TO_LINEAR[rgb & 0xFF];
            weight++;
            if (tintIndex < 0 && tint >= 0) {
                tintIndex = tint;
            }
        }

        int rgb() {
            return (toSrgb(r / weight) << 16) | (toSrgb(g / weight) << 8) | toSrgb(b / weight);
        }
    }
}
