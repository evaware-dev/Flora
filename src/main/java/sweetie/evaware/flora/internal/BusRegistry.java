package sweetie.evaware.flora.internal;

import sweetie.evaware.flora.api.Config;
import sweetie.evaware.flora.core.EventBus;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.core.SyncDispatcher;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class BusRegistry {
    private static final Consumer<?>[] EMPTY = new Consumer<?>[0];

    private final Config config;
    private final DispatchEngine engine;
    private final ConcurrentHashMap<Class<?>, EventBus<?>> createdBuses = new ConcurrentHashMap<>();
    private final ClassValue<EventBus<?>> buses = new ClassValue<>() {
        @Override
        protected EventBus<?> computeValue(Class<?> type) {
            EventBus<?> bus = new EventBus<>(config, engine, config::invalidate);
            EventBus<?> existing = createdBuses.putIfAbsent(type, bus);
            return existing != null ? existing : bus;
        }
    };
    private final ClassValue<CompositeHolder> compositeCache = new ClassValue<>() {
        @Override
        protected CompositeHolder computeValue(Class<?> type) {
            return new CompositeHolder(buildComposite(type, config.epoch()));
        }
    };
    private volatile int compositeAsyncLane = -1;

    public BusRegistry(Config config, DispatchEngine engine) {
        this.config = Objects.requireNonNull(config, "config");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @SuppressWarnings("unchecked")
    public <T> EventBus<T> bus(Class<T> type) {
        return (EventBus<T>) buses.get(Objects.requireNonNull(type, "type"));
    }

    @SuppressWarnings("unchecked")
    public <T> T dispatch(T event) {
        Objects.requireNonNull(event, "event");
        Class<?> eventClass = event.getClass();
        CompositeHolder holder = compositeCache.get(eventClass);
        CompositeDispatch cache = holder.dispatch;
        long currentEpoch = config.epoch();
        if (cache.epoch != currentEpoch) {
            cache = updateComposite(holder, eventClass, currentEpoch);
        }
        cache.dispatch((T) event);
        return event;
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T>[] emptyArray() {
        return (Consumer<T>[]) EMPTY;
    }

    private CompositeDispatch updateComposite(CompositeHolder holder, Class<?> eventClass, long currentEpoch) {
        synchronized (holder) {
            CompositeDispatch current = holder.dispatch;
            if (current.epoch == currentEpoch) {
                return current;
            }
            CompositeDispatch fresh = buildComposite(eventClass, currentEpoch);
            holder.dispatch = fresh;
            return fresh;
        }
    }

    @SuppressWarnings("unchecked")
    private CompositeDispatch buildComposite(Class<?> eventClass, long epoch) {
        Class<?>[] hierarchy = Hierarchy.get(eventClass);
        List<EventBus<?>> activeBuses = new ArrayList<>(hierarchy.length);
        int total = 0;

        for (Class<?> type : hierarchy) {
            EventBus<?> bus = createdBuses.get(type);
            if (bus != null) {
                int count = bus.listenerCount();
                if (count > 0) {
                    activeBuses.add(bus);
                    total += count;
                }
            }
        }

        if (total == 0) {
            return new CompositeDispatch(epoch, null, null, emptyArray(), emptyArray(), emptyArray(), -1, null);
        }

        if (activeBuses.size() == 1 && activeBuses.get(0) == createdBuses.get(eventClass)) {
            EventBus<Object> directBus = (EventBus<Object>) activeBuses.get(0);
            return new CompositeDispatch(epoch, directBus, null, emptyArray(), emptyArray(), emptyArray(), -1, null);
        }

        List<ResolvedListener> merged = new ArrayList<>(total);
        List<Listener<Object>> definitions = new ArrayList<>();
        for (EventBus<?> bus : activeBuses) {
            definitions.clear();
            bus.copyListenersTo(definitions);
            for (Listener<Object> listener : definitions) {
                merged.add(new ResolvedListener(listener, bus.getExceptionHandler()));
            }
        }
        merged.sort((a, b) -> a.listener.compareTo(b.listener));

        int syncCount = 0;
        int asyncCount = 0;
        int parallelCount = 0;
        boolean hasLocalSyncHandler = false;
        for (ResolvedListener resolved : merged) {
            Listener<Object> l = resolved.listener;
            switch (l.mode()) {
                case SYNC -> {
                    syncCount++;
                    hasLocalSyncHandler |= resolved.handler != null;
                }
                case ASYNC -> asyncCount++;
                case ASYNC_PARALLEL -> parallelCount++;
            }
        }

        Consumer<Object>[] sync = syncCount == 0 ? emptyArray() : (Consumer<Object>[]) new Consumer<?>[syncCount];
        Consumer<Object>[] async = asyncCount == 0 ? emptyArray() : (Consumer<Object>[]) new Consumer<?>[asyncCount];
        Consumer<Object>[] parallel = parallelCount == 0 ? emptyArray() : (Consumer<Object>[]) new Consumer<?>[parallelCount];
        Consumer<Throwable>[] handlers = hasLocalSyncHandler ? (Consumer<Throwable>[]) new Consumer<?>[syncCount] : null;

        int s = 0, a = 0, p = 0;
        for (ResolvedListener resolved : merged) {
            Listener<Object> l = resolved.listener;
            switch (l.mode()) {
                case SYNC -> {
                    if (handlers != null) handlers[s] = resolved.handler;
                    sync[s++] = l.callback();
                }
                case ASYNC -> async[a++] = l.callback();
                case ASYNC_PARALLEL -> parallel[p++] = l.callback();
            }
        }

        int lane = asyncCount > 0 ? ensureCompositeAsyncLane() : -1;
        return new CompositeDispatch(epoch, null, config.canceller(eventClass), sync, async, parallel, lane, handlers);
    }

    private record ResolvedListener(Listener<Object> listener, Consumer<Throwable> handler) {
    }

    private int ensureCompositeAsyncLane() {
        int lane = compositeAsyncLane;
        if (lane >= 0) {
            return lane;
        }
        synchronized (this) {
            if (compositeAsyncLane < 0) {
                compositeAsyncLane = engine.acquireAsyncLane();
            }
            return compositeAsyncLane;
        }
    }

    private static final class CompositeHolder {
        volatile CompositeDispatch dispatch;

        CompositeHolder(CompositeDispatch dispatch) {
            this.dispatch = dispatch;
        }
    }

    private final class CompositeDispatch {
        final long epoch;
        final EventBus<Object> directBus;
        final Predicate<Object> canceller;
        final Consumer<Object>[] synchronous;
        final Consumer<Object>[] asynchronous;
        final Consumer<Object>[] parallel;
        final int asyncLane;
        final boolean onlySynchronous;
        final Consumer<Throwable>[] handlers;

        CompositeDispatch(long epoch, EventBus<Object> directBus, Predicate<Object> canceller,
                          Consumer<Object>[] sync, Consumer<Object>[] async, Consumer<Object>[] parallel,
                          int asyncLane, Consumer<Throwable>[] handlers) {
            this.epoch = epoch;
            this.directBus = directBus;
            this.canceller = canceller;
            this.synchronous = sync;
            this.asynchronous = async;
            this.parallel = parallel;
            this.asyncLane = asyncLane;
            this.handlers = handlers;
            this.onlySynchronous = async.length == 0 && parallel.length == 0;
        }

        @SuppressWarnings("unchecked")
        <T> void dispatch(T event) {
            if (directBus != null) {
                directBus.post((Object) event);
                return;
            }

            final Consumer<Object>[] sync = this.synchronous;
            final int len = sync.length;
            if (len == 0 && onlySynchronous) {
                return;
            }

            final Predicate<Object> canceller = this.canceller;
            if (len > 0) {
                SyncDispatcher.dispatch(event, sync, len, canceller, config.exceptionHandler(), handlers);
            }

            if (onlySynchronous || (canceller != null && canceller.test(event))) {
                return;
            }

            if (asynchronous.length != 0) {
                engine.dispatchAsync(asyncLane, event, asynchronous);
            }
            if (parallel.length != 0) {
                engine.dispatchParallel(event, parallel);
            }
        }
    }
}
