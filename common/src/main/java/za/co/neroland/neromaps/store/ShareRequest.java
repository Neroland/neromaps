package za.co.neroland.neromaps.store;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A pending offer, stored on the <em>recipient's</em> record. Only the recipient can accept or decline
 * it, by the server-issued random {@code requestId}; nobody can accept on someone else's behalf.
 *
 * @param day the epoch day it was made (requests expire after {@link #EXPIRE_DAYS})
 */
public record ShareRequest(long requestId, UUID from, ShareScope scope, long day) {

    public static final int EXPIRE_DAYS = 7;
    public static final int MAX_PENDING = 16;

    public static final Codec<ShareRequest> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(ShareRequest::requestId),
            UUIDUtil.STRING_CODEC.fieldOf("from").forGetter(ShareRequest::from),
            ShareScope.CODEC.fieldOf("scope").forGetter(ShareRequest::scope),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(ShareRequest::day)
    ).apply(i, ShareRequest::new));
}
