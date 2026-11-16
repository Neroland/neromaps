package za.co.neroland.neromaps.tile;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The tile codec, schema 1 (decision 002). One class, one exception. Layout:
 * <pre>
 * 0  u8  schema          1  u8 lod   2  u8 header flags (bit0 roofed, bit1 has-fluid, bit2 partial)   3 u8 0
 * 4  u64 content hash (first 8 bytes of SHA-256 over schema, lod, BODY)
 * 12 u32 uncompressed BODY length (&lt;= 1 MiB)
 * 16 zlib(BODY)
 * BODY: block palette (varint n, n x (varint len + UTF-8)), biome palette (same), then planar arrays of 65 536:
 *       flags u8, height u16 zigzag-delta from the west neighbour (row start: from the column north), block u8
 *       or u16 (palette &gt; 256), biome u8, depth u8.
 * </pre>
 * Palettes are compacted to the ids actually used, in first-use order, so the hash depends only on the columns.
 * Off-thread only (allocates).
 */
public final class TileCodec {

    public static final int SCHEMA = 1;
    public static final int HEADER = 16;
    public static final int MAX_BODY = 1 << 20;
    public static final int MAX_ID_BYTES = 256;
    private static final int H_ROOFED = 1;
    private static final int H_FLUID = 2;
    private static final int H_PARTIAL = 4;

    private TileCodec() {
    }

    /** Encodes {@code t}. */
    @SuppressWarnings("resource") // Deflater is AutoCloseable only from Java 25; end() runs below
    public static byte[] encode(Tile t) {
        int[] blockMap = new int[Math.max(1, t.blockPaletteSize())];
        int[] biomeMap = new int[Math.max(1, t.biomePaletteSize())];
        Arrays.fill(blockMap, -1);
        Arrays.fill(biomeMap, -1);
        ArrayList<String> blocks = new ArrayList<>();
        ArrayList<String> biomes = new ArrayList<>();
        int header = 0;
        for (int i = 0; i < Tile.AREA; i++) {
            int f = t.flags[i] & 0xFF;
            if ((f & Tile.F_PRESENT) == 0) {
                header |= H_PARTIAL;
                continue;
            }
            if ((f & Tile.F_CEILING) != 0) {
                header |= H_ROOFED;
            }
            if ((f & Tile.F_FLUID) != 0) {
                header |= H_FLUID;
            }
            int b = t.block[i];
            if (blockMap[b] < 0) {
                blockMap[b] = blocks.size();
                blocks.add(t.blockId(b));
            }
            int bi = t.biome[i] & 0xFF;
            if (biomeMap[bi] < 0) {
                biomeMap[bi] = biomes.size();
                biomes.add(t.biomeId(bi));
            }
        }
        if (blocks.isEmpty()) {
            blocks.add("minecraft:air");
        }
        if (biomes.isEmpty()) {
            biomes.add("minecraft:plains");
        }
        boolean wide = blocks.size() > 256;
        Out out = new Out(Tile.AREA * 7 + 4096);
        palette(out, blocks);
        palette(out, biomes);
        for (int i = 0; i < Tile.AREA; i++) {
            out.u8(t.present(i) ? t.flags[i] & Tile.F_KNOWN : 0);
        }
        short[] hv = new short[Tile.AREA];
        for (int i = 0; i < Tile.AREA; i++) {
            hv[i] = t.present(i) ? t.height[i] : 0;
        }
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = 0; x < Tile.SIZE; x++) {
                out.u16(zigzag(hv[Tile.index(x, z)] - predictor(hv, x, z)));
            }
        }
        for (int i = 0; i < Tile.AREA; i++) {
            int v = t.present(i) ? blockMap[t.block[i]] : 0;
            if (wide) {
                out.u16(v);
            } else {
                out.u8(v);
            }
        }
        for (int i = 0; i < Tile.AREA; i++) {
            out.u8(t.present(i) ? biomeMap[t.biome[i] & 0xFF] : 0);
        }
        for (int i = 0; i < Tile.AREA; i++) {
            out.u8(t.present(i) ? t.depth[i] & 0xFF : 0);
        }
        byte[] body = out.toArray();
        long hash = hash(t.lod, body, body.length);
        Deflater deflater = new Deflater(6, false);
        deflater.setInput(body);
        deflater.finish();
        byte[] buf = new byte[body.length / 2 + 64];
        int n = 0;
        while (!deflater.finished()) {
            if (n == buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            n += deflater.deflate(buf, n, buf.length - n);
        }
        deflater.end();
        ByteBuffer bb = ByteBuffer.allocate(HEADER + n);
        bb.put((byte) SCHEMA).put((byte) t.lod).put((byte) header).put((byte) 0);
        bb.putLong(hash);
        bb.putInt(body.length);
        bb.put(buf, 0, n);
        return bb.array();
    }

    /** The etag in an encoded tile's header, without decoding. */
    public static long contentHash(byte[] encoded) throws TileCorruptException {
        if (encoded.length < HEADER) {
            throw new TileCorruptException("short header");
        }
        return ByteBuffer.wrap(encoded).getLong(4);
    }

    /** Decodes {@code data}. Any defect throws {@link TileCorruptException}; nothing else escapes. */
    public static Tile decode(byte[] data) throws TileCorruptException {
        try {
            return decodeUnchecked(data);
        } catch (TileCorruptException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TileCorruptException("malformed: " + e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("resource") // Inflater is AutoCloseable only from Java 25; end() runs in finally
    private static Tile decodeUnchecked(byte[] data) throws TileCorruptException {
        if (data == null || data.length < HEADER) {
            throw new TileCorruptException("short header");
        }
        ByteBuffer hb = ByteBuffer.wrap(data);
        int schema = hb.get() & 0xFF;
        if (schema != SCHEMA) {
            throw new TileCorruptException("unknown schema " + schema);
        }
        int lod = hb.get() & 0xFF;
        if (lod > 4) {
            throw new TileCorruptException("bad lod");
        }
        hb.get();
        if (hb.get() != 0) {
            throw new TileCorruptException("reserved byte");
        }
        long hash = hb.getLong();
        int bodyLen = hb.getInt();
        if (bodyLen <= 0 || bodyLen > MAX_BODY) {
            throw new TileCorruptException("bad body length");
        }
        byte[] body = new byte[bodyLen];
        Inflater inflater = new Inflater(false);
        try {
            inflater.setInput(data, HEADER, data.length - HEADER);
            int n = 0;
            int stalls = 0;
            while (n < bodyLen) {
                int r = inflater.inflate(body, n, bodyLen - n);
                if (r == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary()
                        || ++stalls > 16)) {
                    throw new TileCorruptException("short body");
                }
                n += r;
            }
            if (!inflater.finished()) {
                byte[] probe = new byte[1];
                if (inflater.inflate(probe) != 0 || !inflater.finished()) {
                    throw new TileCorruptException("long body");
                }
            }
            if (inflater.getRemaining() != 0) {
                throw new TileCorruptException("trailing bytes");
            }
        } catch (DataFormatException e) {
            throw new TileCorruptException("inflate");
        } finally {
            inflater.end();
        }
        if (hash(lod, body, bodyLen) != hash) {
            throw new TileCorruptException("hash mismatch");
        }
        In in = new In(body);
        Tile t = new Tile(lod);
        int nb = in.varint();
        if (nb < 1 || nb > Tile.MAX_BLOCKS) {
            throw new TileCorruptException("block palette size");
        }
        for (int i = 0; i < nb; i++) {
            String id = in.string();
            if (t.internBlock(id) != i) {
                throw new TileCorruptException("duplicate block id");
            }
        }
        int nbi = in.varint();
        if (nbi < 1 || nbi > Tile.MAX_BIOMES) {
            throw new TileCorruptException("biome palette size");
        }
        for (int i = 0; i < nbi; i++) {
            String id = in.string();
            if (t.internBiome(id) != i) {
                throw new TileCorruptException("duplicate biome id");
            }
        }
        boolean wide = nb > 256;
        int need = Tile.AREA * (1 + 2 + (wide ? 2 : 1) + 1 + 1);
        if (in.remaining() != need) {
            throw new TileCorruptException("body size");
        }
        for (int i = 0; i < Tile.AREA; i++) {
            t.flags[i] = (byte) (in.u8() & Tile.F_KNOWN);
        }
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = 0; x < Tile.SIZE; x++) {
                int i = Tile.index(x, z);
                int h = unzigzag(in.u16()) + predictor(t.height, x, z);
                if (h < Short.MIN_VALUE || h > Short.MAX_VALUE) {
                    throw new TileCorruptException("height range");
                }
                t.height[i] = (short) h;
            }
        }
        for (int i = 0; i < Tile.AREA; i++) {
            int v = wide ? in.u16() : in.u8();
            if (v >= nb) {
                throw new TileCorruptException("block index");
            }
            t.block[i] = (char) v;
        }
        for (int i = 0; i < Tile.AREA; i++) {
            int v = in.u8();
            if (v >= nbi) {
                throw new TileCorruptException("biome index");
            }
            t.biome[i] = (byte) v;
        }
        for (int i = 0; i < Tile.AREA; i++) {
            t.depth[i] = (byte) in.u8();
        }
        return t;
    }

    /** West neighbour, or the column north at the start of a row (absent columns count as 0). */
    private static int predictor(short[] h, int x, int z) {
        if (x > 0) {
            return h[Tile.index(x - 1, z)];
        }
        return z > 0 ? h[Tile.index(0, z - 1)] : 0;
    }

    static int zigzag(int v) {
        return ((v << 1) ^ (v >> 31)) & 0xFFFF;
    }

    static int unzigzag(int v) {
        return (v >>> 1) ^ -(v & 1);
    }

    private static long hash(int lod, byte[] body, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((byte) SCHEMA);
            md.update((byte) lod);
            md.update(body, 0, len);
            return ByteBuffer.wrap(md.digest()).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void palette(Out out, ArrayList<String> ids) {
        out.varint(ids.size());
        for (String id : ids) {
            byte[] b = id.getBytes(StandardCharsets.UTF_8);
            out.varint(b.length);
            out.bytes(b);
        }
    }

    private static final class Out {
        private byte[] buf;
        private int n;

        Out(int cap) {
            buf = new byte[cap];
        }

        private void ensure(int k) {
            if (n + k > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length * 2, n + k));
            }
        }

        void u8(int v) {
            ensure(1);
            buf[n++] = (byte) v;
        }

        void u16(int v) {
            ensure(2);
            buf[n++] = (byte) (v >>> 8);
            buf[n++] = (byte) v;
        }

        void varint(int v) {
            while ((v & ~0x7F) != 0) {
                u8((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            u8(v);
        }

        void bytes(byte[] b) {
            ensure(b.length);
            System.arraycopy(b, 0, buf, n, b.length);
            n += b.length;
        }

        byte[] toArray() {
            return Arrays.copyOf(buf, n);
        }
    }

    private static final class In {
        private final byte[] buf;
        private int p;
        private final CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);

        In(byte[] buf) {
            this.buf = buf;
        }

        int remaining() {
            return buf.length - p;
        }

        int u8() throws TileCorruptException {
            if (p >= buf.length) {
                throw new TileCorruptException("eof");
            }
            return buf[p++] & 0xFF;
        }

        int u16() throws TileCorruptException {
            return (u8() << 8) | u8();
        }

        int varint() throws TileCorruptException {
            int v = 0;
            for (int shift = 0; shift < 28; shift += 7) {
                int b = u8();
                v |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return v;
                }
            }
            throw new TileCorruptException("varint");
        }

        String string() throws TileCorruptException {
            int len = varint();
            if (len < 1 || len > MAX_ID_BYTES || len > remaining()) {
                throw new TileCorruptException("id length");
            }
            try {
                CharBuffer cb = utf8.decode(ByteBuffer.wrap(buf, p, len));
                p += len;
                return cb.toString();
            } catch (CharacterCodingException e) {
                throw new TileCorruptException("id utf-8");
            }
        }
    }
}
