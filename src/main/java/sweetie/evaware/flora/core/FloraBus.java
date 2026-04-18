package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FloraBus<T> {
    private static final Consumer<?>[] EMPTY_CONSUMERS = new Consumer[0];

    private final DispatchEngine engine;
    private final Object lock = new Object();
    private final List<Listener<T>> subscribers = new ArrayList<>();

    private Consumer<T>[] syncConsumers;
    private Consumer<T>[] asyncConsumers;
    private Consumer<T>[] parallelConsumers;
    private boolean syncOnly;

    @SuppressWarnings("unchecked")
    public FloraBus() {
        this.engine = DispatchEngine.defaultEngine();
        this.syncConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
        this.asyncConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
        this.parallelConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
        this.syncOnly = true;
    }

    public void post(T event) {
        Consumer<T>[] sync = syncConsumers;
        for (Consumer<T> consumer : sync) {
            consumer.accept(event);
        }

        if (syncOnly) {
            return;
        }

        Consumer<T>[] async = asyncConsumers;
        if (async.length > 0) {
            engine.dispatchAsync(event, async);
        }

        Consumer<T>[] parallel = parallelConsumers;
        if (parallel.length > 0) {
            engine.dispatchParallel(event, parallel);
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

        asyncConsumers = async;
        parallelConsumers = parallel;
        syncOnly = asyncCount == 0 && parallelCount == 0;
        syncConsumers = sync;
    }
}
