package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.engine.AsyncLoop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class FloraBus<T> {
    private static final Consumer<?>[] EMPTY_CONSUMERS = new Consumer[0];
    private static final AsyncLoop ASYNC_WORKER = new AsyncLoop();
    private static final ForkJoinPool PARALLEL_POOL = ForkJoinPool.commonPool();

    private final Object lock = new Object();
    private final List<Listener<T>> subscribers = new ArrayList<>();

    private volatile Consumer<T>[] syncConsumers;
    private volatile Consumer<T>[] asyncConsumers;
    private volatile Consumer<T>[] parallelConsumers;

    @SuppressWarnings("unchecked")
    public FloraBus() {
        this.syncConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
        this.asyncConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
        this.parallelConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
    }

    public void post(T event) {
        Consumer<T>[] sync = syncConsumers;
        for (Consumer<T> consumer : sync) {
            accept(consumer, event);
        }

        Consumer<T>[] async = asyncConsumers;
        if (async.length > 0) {
            ASYNC_WORKER.execute(event, async);
        }

        Consumer<T>[] parallel = parallelConsumers;
        for (Consumer<T> consumer : parallel) {
            PARALLEL_POOL.execute(() -> accept(consumer, event));
        }
    }

    public Subscription subscribe(Listener<T> listener) {
        synchronized (lock) {
            subscribers.add(listener);
            rebuild();
        }
        return () -> unsubscribe(listener);
    }

    public void unsubscribe(Listener<T> listener) {
        synchronized (lock) {
            if (subscribers.remove(listener)) {
                rebuild();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        subscribers.sort(null);

        int syncCount = 0;
        int asyncCount = 0;
        int parallelCount = 0;

        for (Listener<T> listener : subscribers) {
            switch (listener.mode) {
                case SYNC -> syncCount++;
                case ASYNC -> asyncCount++;
                case ASYNC_PARALLEL -> parallelCount++;
            }
        }

        Consumer<T>[] sync = syncCount == 0 ? (Consumer<T>[]) EMPTY_CONSUMERS : new Consumer[syncCount];
        Consumer<T>[] async = asyncCount == 0 ? (Consumer<T>[]) EMPTY_CONSUMERS : new Consumer[asyncCount];
        Consumer<T>[] parallel = parallelCount == 0 ? (Consumer<T>[]) EMPTY_CONSUMERS : new Consumer[parallelCount];

        int syncIndex = 0;
        int asyncIndex = 0;
        int parallelIndex = 0;

        for (Listener<T> listener : subscribers) {
            switch (listener.mode) {
                case SYNC -> sync[syncIndex++] = listener.consumer;
                case ASYNC -> async[asyncIndex++] = listener.consumer;
                case ASYNC_PARALLEL -> parallel[parallelIndex++] = listener.consumer;
            }
        }

        syncConsumers = sync;
        asyncConsumers = async;
        parallelConsumers = parallel;
    }

    private static <T> void accept(Consumer<T> consumer, T event) {
        try {
            consumer.accept(event);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
