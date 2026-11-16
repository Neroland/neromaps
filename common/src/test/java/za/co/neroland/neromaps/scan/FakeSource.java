package za.co.neroland.neromaps.scan;

/** An in-memory ColumnSource: a kind and block key per position, uniform per column type. */
public final class FakeSource implements ColumnSource {

    public final int minY;
    public final int maxY;
    private final int[][] kinds;
    private final int[][] blocks;
    private final int[] surface = new int[256];

    public FakeSource(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
        kinds = new int[256][maxY - minY];
        blocks = new int[256][maxY - minY];
        java.util.Arrays.fill(surface, minY);
    }

    /** Sets a vertical run in every column. */
    public FakeSource fill(int y0, int y1, int kind, int block) {
        for (int c = 0; c < 256; c++) {
            for (int y = y0; y <= y1; y++) {
                kinds[c][y - minY] = kind;
                blocks[c][y - minY] = block;
                if (kind != KIND_INVISIBLE) {
                    surface[c] = Math.max(surface[c], y + 1);
                }
            }
        }
        return this;
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
        int base = (y >> 4) << 4;
        for (int k = 0; k < 16; k++) {
            int yy = base + k;
            if (yy >= minY && yy < maxY && kinds[0][yy - minY] != KIND_INVISIBLE) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int kind(int x, int y, int z) {
        return kinds[(z << 4) | x][y - minY];
    }

    @Override
    public int block(int x, int y, int z) {
        return blocks[(z << 4) | x][y - minY];
    }

    @Override
    public int biome(int x, int y, int z) {
        return 7;
    }

    @Override
    public int airBlock() {
        return 0;
    }
}
