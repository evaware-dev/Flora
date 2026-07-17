package sweetie.evaware.flora.core.engine;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

final class WorkerPool {
    private final String threadNamePrefix;
    private final int queueCapacity;
    private final int idleSpinLimit;
    private final int backpressureSpinLimit;
    private final long backpressureParkNanos;
    private final ListenerInvoker listenerInvoker;
    private final AtomicReferenceArray<EventWorker> workers;
    private final AtomicInteger nextLane = new AtomicInteger();
    private volatile boolean accepting = true;

    WorkerPool(String threadNamePrefix, int laneCount, int queueCapacity, int idleSpinLimit,
               int backpressureSpinLimit, long backpressureParkNanos, ListenerInvoker listenerInvoker) {
        this.threadNamePrefix = threadNamePrefix;
        this.queueCapacity = queueCapacity;
        this.idleSpinLimit = idleSpinLimit;
        this.backpressureSpinLimit = backpressureSpinLimit;
        this.backpressureParkNanos = backpressureParkNanos;
        this.listenerInvoker = listenerInvoker;
        this.workers = new AtomicReferenceArray<>(laneCount);
    }

    int acquireLane() {
        return Math.floorMod(nextLane.getAndIncrement(), workers.length());
    }

    <T> void enqueue(int lane, T event, Consumer<T>[] listeners) {
        requireValidLane(lane);
        worker(lane).submit(event, listeners);
    }

    <T> void distribute(T event, Consumer<T>[] listeners) {
        int firstLane = spread(System.identityHashCode(event));
        for (int index = 0; index < listeners.length; index++) {
            int lane = Math.floorMod(firstLane + index, workers.length());
            worker(lane).submit(event, listeners[index]);
        }
    }

    void shutdownGracefully() {
        synchronized (workers) {
            accepting = false;
            for (int lane = 0; lane < workers.length(); lane++) {
                EventWorker worker = workers.get(lane);
                if (worker != null) {
                    worker.requestShutdown();
                }
            }
        }
    }

    boolean isIdle() {
        for (int lane = 0; lane < workers.length(); lane++) {
            EventWorker worker = workers.get(lane);
            if (worker != null && !worker.idle()) {
                return false;
            }
        }
        return true;
    }

    private EventWorker worker(int lane) {
        EventWorker worker = workers.get(lane);
        if (worker != null) {
            return worker;
        }
        synchronized (workers) {
            if (!accepting) {
                throw new RejectedExecutionException("Flora dispatch engine is shut down");
            }
            worker = workers.get(lane);
            if (worker == null) {
                worker = new EventWorker(threadNamePrefix + lane, queueCapacity, idleSpinLimit,
                        backpressureSpinLimit, backpressureParkNanos, listenerInvoker);
                workers.set(lane, worker);
            }
            return worker;
        }
    }

    private void requireValidLane(int lane) {
        if (lane < 0 || lane >= workers.length()) {
            throw new IllegalArgumentException("lane must be between 0 and " + (workers.length() - 1));
        }
    }

    private static int spread(int value) {
        return value ^ (value >>> 16);
    }
}
