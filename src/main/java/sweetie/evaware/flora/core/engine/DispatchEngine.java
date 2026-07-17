package sweetie.evaware.flora.core.engine;

import sweetie.evaware.flora.api.DispatchConfig;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class DispatchEngine {
    private static final String ASYNC_THREAD_PREFIX = "Flora-Async-";
    private static final String PARALLEL_THREAD_PREFIX = "Flora-Parallel-";
    private static final long IDLE_POLL_NANOS = 100_000L;
    private static final DispatchEngine DEFAULT_ENGINE = new DispatchEngine(DispatchConfig.defaults());

    private final ListenerInvoker listenerInvoker;
    private final WorkerPool asyncWorkers;
    private final WorkerPool parallelWorkers;

    public DispatchEngine(DispatchConfig config) {
        Objects.requireNonNull(config, "config");
        listenerInvoker = new ListenerInvoker(Throwable::printStackTrace);
        asyncWorkers = createPool(ASYNC_THREAD_PREFIX, config.asyncLaneCount(),
                config.asyncQueueCapacity(), config);
        parallelWorkers = createPool(PARALLEL_THREAD_PREFIX, config.parallelLaneCount(),
                config.parallelQueueCapacity(), config);
    }

    public static DispatchEngine defaultEngine() {
        return DEFAULT_ENGINE;
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

    public void setListenerExceptionHandler(Consumer<Throwable> exceptionHandler) {
        listenerInvoker.setExceptionHandler(exceptionHandler);
    }

    public <T> void invokeSafely(Consumer<T> listener, T event) {
        listenerInvoker.invoke(listener, event);
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

        long timeoutNanos = unit.toNanos(timeout);
        long start = System.nanoTime();
        while (!isIdle()) {
            long remaining = timeoutNanos - (System.nanoTime() - start);
            if (remaining <= 0) {
                return false;
            }
            LockSupport.parkNanos(Math.min(IDLE_POLL_NANOS, remaining));
        }
        return true;
    }

    private WorkerPool createPool(String threadPrefix, int lanes, int capacity, DispatchConfig config) {
        return new WorkerPool(threadPrefix, lanes, capacity, config.idleSpinLimit(),
                config.backpressureSpinLimit(), config.backpressureParkNanos(), listenerInvoker);
    }

    private boolean isIdle() {
        return asyncWorkers.isIdle() && parallelWorkers.isIdle();
    }
}
