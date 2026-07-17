package sweetie.evaware.flora.api;

public record DispatchConfig(
        int asyncLaneCount,
        int parallelLaneCount,
        int asyncQueueCapacity,
        int parallelQueueCapacity,
        int idleSpinLimit,
        int backpressureSpinLimit,
        long backpressureParkNanos
) {
    private static final int DEFAULT_ASYNC_QUEUE_CAPACITY = 16_384;
    private static final int DEFAULT_PARALLEL_QUEUE_CAPACITY = 8_192;
    private static final int DEFAULT_IDLE_SPIN_LIMIT = 256;
    private static final int DEFAULT_BACKPRESSURE_SPIN_LIMIT = 64;
    private static final long DEFAULT_BACKPRESSURE_PARK_NANOS = 1_000L;

    public DispatchConfig {
        requirePositive(asyncLaneCount, "asyncLaneCount");
        requirePositive(parallelLaneCount, "parallelLaneCount");
        requirePowerOfTwo(asyncQueueCapacity, "asyncQueueCapacity");
        requirePowerOfTwo(parallelQueueCapacity, "parallelQueueCapacity");
        requireNonNegative(idleSpinLimit, "idleSpinLimit");
        requireNonNegative(backpressureSpinLimit, "backpressureSpinLimit");
        requirePositive(backpressureParkNanos, "backpressureParkNanos");
    }

    public static DispatchConfig defaults() {
        int processors = Runtime.getRuntime().availableProcessors();
        return new DispatchConfig(
                Math.min(4, Math.max(1, processors)),
                Math.min(8, Math.max(1, processors)),
                DEFAULT_ASYNC_QUEUE_CAPACITY,
                DEFAULT_PARALLEL_QUEUE_CAPACITY,
                DEFAULT_IDLE_SPIN_LIMIT,
                DEFAULT_BACKPRESSURE_SPIN_LIMIT,
                DEFAULT_BACKPRESSURE_PARK_NANOS
        );
    }

    private static void requirePowerOfTwo(int value, String name) {
        if (value < 2 || Integer.bitCount(value) != 1) {
            throw new IllegalArgumentException(name + " must be a power of two and at least 2");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
