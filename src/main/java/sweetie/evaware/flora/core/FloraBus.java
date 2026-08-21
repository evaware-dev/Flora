package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class FloraBus<T> {
    private final DispatchEngine dispatchEngine;
    private final int asynchronousLane;
    private final ListenerRegistry<T> listenerRegistry = new ListenerRegistry<>();

    public FloraBus() {
        this(DispatchEngine.defaultEngine());
    }

    public FloraBus(DispatchEngine dispatchEngine) {
        this.dispatchEngine = Objects.requireNonNull(dispatchEngine, "dispatchEngine");
        this.asynchronousLane = dispatchEngine.acquireAsyncLane();
    }

    public void post(T event) {
        Objects.requireNonNull(event, "event");
        ListenerSnapshot<T> listeners = listenerRegistry.snapshot();

        for (Consumer<T> listener : listeners.synchronous) {
            dispatchEngine.invokeSafely(listener, event);
        }
        if (listeners.onlySynchronous) {
            return;
        }
        if (listeners.asynchronous.length != 0) {
            dispatchEngine.dispatchAsync(asynchronousLane, event, listeners.asynchronous);
        }
        if (listeners.parallel.length != 0) {
            dispatchEngine.dispatchParallel(event, listeners.parallel);
        }
    }

    public Subscription subscribe(Listener<T> listener) {
        return listenerRegistry.add(listener);
    }

    public Subscription subscribeAll(List<? extends Listener<T>> listeners) {
        return listenerRegistry.addAll(listeners);
    }
}
