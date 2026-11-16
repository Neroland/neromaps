package za.co.neroland.neromaps.platform;

import java.util.ServiceLoader;

import za.co.neroland.neromaps.NeroMapsCommon;

/**
 * Loads loader-specific platform-seam implementations via {@link ServiceLoader}.
 *
 * <p>Every seam is resolved during class initialisation, which happens the first time
 * {@code NeroMapsCommon.init()} touches this class, <b>never lazily mid-tick</b>: a lazy {@link ServiceLoader}
 * read can throw {@code ServiceConfigurationError} out of gameplay code if the jar has become unreadable.
 */
public final class Services {

    public static final PlatformInfo PLATFORM = load(PlatformInfo.class);

    private Services() {
    }

    /** Resolves every seam now, so nothing has to be resolved on a tick path later. */
    public static void init() {
        NeroMapsCommon.LOGGER.debug("[NeroMaps] platform seams resolved ({})", PLATFORM.getPlatformName());
    }

    public static <T> T load(Class<T> clazz) {
        final T loaded = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException(
                        "No implementation found for service " + clazz.getName()));
        NeroMapsCommon.LOGGER.debug("Loaded service {} -> {}", clazz.getSimpleName(), loaded.getClass().getName());
        return loaded;
    }
}
