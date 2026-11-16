package za.co.neroland.neromaps.server.tiles;

/**
 * How much server-thread time capture may spend this tick (decision 006): the configured budget, a quarter of it
 * while the server is behind (average MSPT, vanilla's {@code getAverageTickTimeNanos}, above 45), and nothing below
 * the minimum TPS. Pure.
 */
public final class CaptureBudget {

    public static final double BEHIND_MSPT = 45.0;

    private CaptureBudget() {
    }

    public static long nanos(int budgetMicros, double averageMspt, int minTps) {
        double tps = 1000.0 / Math.max(50.0, averageMspt);
        if (tps < minTps) {
            return 0L;
        }
        long b = budgetMicros * 1_000L;
        if (averageMspt > BEHIND_MSPT) {
            b /= 4;
        }
        return b;
    }
}
