package za.co.neroland.neromaps.store;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * An accepted share, stored on the sharer's record only (decision 009): the recipient agreed, so the sharer's
 * {@code scope} is visible to {@code to} until either side revokes it.
 */
public record Grant(UUID to, ShareScope scope) {

    public static final Codec<Grant> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("to").forGetter(Grant::to),
            ShareScope.CODEC.fieldOf("scope").forGetter(Grant::scope)
    ).apply(i, Grant::new));
}
