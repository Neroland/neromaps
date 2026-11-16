package za.co.neroland.neromaps.network;

/**
 * Where clientbound payloads land. Common code only sees this interface; the client installs its implementation at
 * client init, so no client class loads on a dedicated server. Until then every payload is ignored.
 */
public interface ClientSink {

    ClientSink NONE = new ClientSink() { };

    default void hello(Payloads.Hello p) { }

    default void waypoints(Payloads.Waypoints p) { }

    default void shares(Payloads.Shares p) { }

    default void positions(Payloads.Positions p) { }

    default void notice(Payloads.Notice p) { }

    default void layerCatalogue(Payloads.LayerCatalogue p) { }

    default void layerDelta(Payloads.LayerDelta p) { }

    default void dimensionBadges(Payloads.DimensionBadges p) { }

    default void tile(Payloads.TileData p) { }

    default void perf(Payloads.Perf p) { }

    default void signpostOpen(Payloads.SignpostOpen p) { }

    default void export(Payloads.Export p) { }

    /** The installed sink. */
    final class Holder {
        static volatile ClientSink sink = NONE;

        private Holder() {
        }

        public static void install(ClientSink s) {
            sink = s == null ? NONE : s;
        }

        public static ClientSink get() {
            return sink;
        }
    }
}
