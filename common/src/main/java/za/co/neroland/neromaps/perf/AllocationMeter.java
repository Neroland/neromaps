package za.co.neroland.neromaps.perf;

import java.lang.management.ManagementFactory;

/**
 * Bytes allocated by the current thread, through {@code com.sun.management.ThreadMXBean} when the JVM has it
 * (HotSpot does). Reading it allocates nothing once the bean is cached. Returns -1 when unsupported.
 */
public final class AllocationMeter {

    private static final com.sun.management.ThreadMXBean BEAN = bean();

    private AllocationMeter() {
    }

    private static com.sun.management.ThreadMXBean bean() {
        try {
            java.lang.management.ThreadMXBean b = ManagementFactory.getThreadMXBean();
            if (b instanceof com.sun.management.ThreadMXBean sun && sun.isThreadAllocatedMemorySupported()) {
                if (!sun.isThreadAllocatedMemoryEnabled()) {
                    sun.setThreadAllocatedMemoryEnabled(true);
                }
                return sun;
            }
        } catch (RuntimeException | LinkageError e) {
            // unsupported JVM: the counter reports -1
        }
        return null;
    }

    public static boolean supported() {
        return BEAN != null;
    }

    public static long current() {
        com.sun.management.ThreadMXBean b = BEAN;
        return b == null ? -1 : b.getCurrentThreadAllocatedBytes();
    }
}
