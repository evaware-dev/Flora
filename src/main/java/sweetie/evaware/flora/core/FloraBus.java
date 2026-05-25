package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FloraBus<T> {
    private static final Consumer<?>[] EMPTY_CONSUMERS = new Consumer[0];

    private final DispatchEngine engine = DispatchEngine.defaultEngine();
    private final List<Listener<T>> subscribers = new ArrayList<>();

    private Consumer<T>[] syncConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
    private Consumer<T>[] asyncConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
    private Consumer<T>[] parallelConsumers = (Consumer<T>[]) EMPTY_CONSUMERS;
    private boolean syncOnly = true;

    public void post(T event) {
        for (Consumer<T> consumer : syncConsumers) {
            consumer.accept(event);
        }

        if (syncOnly) {
            return;
        }

        if (asyncConsumers.length > 0) {
            engine.dispatchAsync(event, asyncConsumers);
        }

        if (parallelConsumers.length > 0) {
            engine.dispatchParallel(event, parallelConsumers);
        }
    }

    public synchronized Subscription subscribe(Listener<T> listener) {
        subscribers.add(listener);
        rebuild();
        return () -> unsubscribe(listener);
    }

    public synchronized void unsubscribe(Listener<T> listener) {
        if (subscribers.remove(listener)) {
            rebuild();
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        subscribers.sort(null);

        List<Consumer<T>> sync = new ArrayList<>();
        List<Consumer<T>> async = new ArrayList<>();
        List<Consumer<T>> parallel = new ArrayList<>();

        for (Listener<T> listener : subscribers) {
            switch (listener.mode()) {
                case SYNC -> sync.add(listener.consumer());
                case ASYNC -> async.add(listener.consumer());
                case ASYNC_PARALLEL -> parallel.add(listener.consumer());
            }
        }

        syncConsumers = sync.toArray(new Consumer[0]);
        asyncConsumers = async.toArray(new Consumer[0]);
        parallelConsumers = parallel.toArray(new Consumer[0]);
        syncOnly = asyncConsumers.length == 0 && parallelConsumers.length == 0;
    }
}
