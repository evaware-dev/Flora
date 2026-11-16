package sweetie.evaware.flora.core.engine;

import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.Config;
import sweetie.evaware.flora.api.DispatchConfig;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class DispatchEngine {
    private static final String ASYNC_THREAD_PREFIX = "Flora-Async-";
    private static final String PARALLEL_THREAD_PREFIX = "Flora-Parallel-";
    private static final long IDLE_POLL_NANOS = 100_000L;

    private final WorkerPool asyncWorkers;
    private final WorkerPool parallelWorkers;

    public DispatchEngine(DispatchConfig dispatchConfig) {
        this(dispatchConfig, Flora.config());
    }

    public DispatchEngine(DispatchConfig dispatchConfig, Config config) {
        Objects.requireNonNull(dispatchConfig, "dispatchConfig");
        Objects.requireNonNull(config, "config");
        asyncWorkers = createPool(ASYNC_THREAD_PREFIX, dispatchConfig.asyncLaneCount(),
                dispatchConfig.asyncQueueCapacity(), dispatchConfig, config);
        parallelWorkers = createPool(PARALLEL_THREAD_PREFIX, dispatchConfig.parallelLaneCount(),
                dispatchConfig.parallelQueueCapacity(), dispatchConfig, config);
    }

    public static DispatchEngine defaultEngine() {
        return Flora.system().engine();
    }

    public int acquireAsyncLane() {
        return asyncWorkers.acquireLane();
    }

    public <T> void dispatchAsync(int lane, T event, Consumer<T>[] listeners) {
        asyncWorkers.enqueue(lane, event, listeners);
    }

    public <T> void dispatchParallel(T event, Consumer<T>[] listeners) {
        parallelWorkers.distribute(event, listeners);
    }

    public void shutdown() {
        asyncWorkers.shutdownGracefully();
        parallelWorkers.shutdownGracefully();
    }

    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadlineNanos) {
            if (asyncWorkers.isIdle() && parallelWorkers.isIdle()) {
                return true;
            }
            LockSupport.parkNanos(IDLE_POLL_NANOS);
        }
        return asyncWorkers.isIdle() && parallelWorkers.isIdle();
    }

    private static WorkerPool createPool(String prefix, int lanes, int queueCapacity,
                                         DispatchConfig dispatchConfig, Config config) {
        return new WorkerPool(
                prefix,
                lanes,
                queueCapacity,
                dispatchConfig.idleSpinLimit(),
                dispatchConfig.backpressureSpinLimit(),
                dispatchConfig.backpressureParkNanos(),
                config
        );
    }
}
