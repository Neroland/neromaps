package za.co.neroland.neromaps.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Where the player last died: a death point, not a trail (at most three, newest first). */
public record DeathPoint(String dimension, int x, int y, int z) {

    public static final int KEEP = 3;

    public static final Codec<DeathPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dim").forGetter(DeathPoint::dimension),
            Codec.INT.fieldOf("x").forGetter(DeathPoint::x),
            Codec.INT.fieldOf("y").forGetter(DeathPoint::y),
            Codec.INT.fieldOf("z").forGetter(DeathPoint::z)
    ).apply(i, DeathPoint::new));

    public DeathPoint {
        dimension = Waypoint.cleanId(dimension, "minecraft:overworld");
    }
}
