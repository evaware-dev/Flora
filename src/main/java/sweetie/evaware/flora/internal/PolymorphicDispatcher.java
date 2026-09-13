package sweetie.evaware.flora.internal;

import sweetie.evaware.flora.FloraConfigurator;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PolymorphicDispatcher {
    private static final ClassValue<FloraBus<?>> BUSES = new ClassValue<>() {
        @Override
        protected FloraBus<?> computeValue(Class<?> type) {
            return new FloraBus<>(FloraConfigurator::invalidate);
        }
    };

    private static final ClassValue<CompositeDispatch> COMPOSITE_CACHE = new ClassValue<>() {
        @Override
        protected CompositeDispatch computeValue(Class<?> type) {
            return buildComposite(type, FloraConfigurator.getEpoch());
        }
    };

    private PolymorphicDispatcher() {
    }

    @SuppressWarnings("unchecked")
    public static <T> FloraBus<T> getBus(Class<T> type) {
        return (FloraBus<T>) BUSES.get(Objects.requireNonNull(type, "type"));
    }

    @SuppressWarnings("unchecked")
    public static <T> T dispatch(T event) {
        Objects.requireNonNull(event, "event");
        Class<?> eventClass = event.getClass();
        CompositeDispatch cache = COMPOSITE_CACHE.get(eventClass);
        long currentEpoch = FloraConfigurator.getEpoch();
        if (cache.epoch != currentEpoch) {
            cache = updateComposite(eventClass, currentEpoch);
        }
        cache.dispatch((T) event, FloraConfigurator.getExceptionHandler());
        return event;
    }

    private static final Consumer<?>[] EMPTY = new Consumer<?>[0];

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T>[] emptyArray() {
        return (Consumer<T>[]) EMPTY;
    }

    private static synchronized CompositeDispatch updateComposite(Class<?> eventClass, long currentEpoch) {
        COMPOSITE_CACHE.remove(eventClass);
        CompositeDispatch fresh = COMPOSITE_CACHE.get(eventClass);
        if (fresh.epoch == currentEpoch) {
            return fresh;
        }
        return buildComposite(eventClass, currentEpoch);
    }

    @SuppressWarnings("unchecked")
    private static CompositeDispatch buildComposite(Class<?> eventClass, long epoch) {
        Class<?>[] hierarchy = Hierarchy.get(eventClass);
        List<FloraBus<?>> activeBuses = new ArrayList<>(hierarchy.length);
        int total = 0;

        for (Class<?> type : hierarchy) {
            FloraBus<?> bus = BUSES.get(type);
            int count = bus.listenerCount();
            if (count > 0) {
                activeBuses.add(bus);
                total += count;
            }
        }

        if (total == 0) {
            return new CompositeDispatch(epoch, null, null, emptyArray(), emptyArray(), emptyArray());
        }

        if (activeBuses.size() == 1 && activeBuses.get(0) == BUSES.get(eventClass)) {
            FloraBus<Object> directBus = (FloraBus<Object>) activeBuses.get(0);
            return new CompositeDispatch(epoch, directBus, null, emptyArray(), emptyArray(), emptyArray());
        }

        List<Listener<Object>> merged = new ArrayList<>(total);
        for (FloraBus<?> bus : activeBuses) {
            bus.copyListenersTo(merged);
        }
        merged.sort(Listener::compareTo);

        int syncCount = 0, asyncCount = 0, parallelCount = 0;
        for (Listener<Object> l : merged) {
            switch (l.mode()) {
                case SYNC -> syncCount++;
                case ASYNC -> asyncCount++;
                case ASYNC_PARALLEL -> parallelCount++;
            }
        }

        Consumer<Object>[] sync = syncCount == 0 ? emptyArray() : (Consumer<Object>[]) new Consumer<?>[syncCount];
        Consumer<Object>[] async = asyncCount == 0 ? emptyArray() : (Consumer<Object>[]) new Consumer<?>[asyncCount];
        Consumer<Object>[] parallel = parallelCount == 0 ? emptyArray() : (Consumer<Object>[]) new Consumer<?>[parallelCount];

        int s = 0, a = 0, p = 0;
        for (Listener<Object> l : merged) {
            switch (l.mode()) {
                case SYNC -> sync[s++] = l.callback();
                case ASYNC -> async[a++] = l.callback();
                case ASYNC_PARALLEL -> parallel[p++] = l.callback();
            }
        }

        return new CompositeDispatch(epoch, null, FloraConfigurator.getCanceller(eventClass), sync, async, parallel);
    }

    private static final class CompositeDispatch {
        final long epoch;
        final FloraBus<Object> directBus;
        final Predicate<Object> canceller;
        final Consumer<Object>[] synchronous;
        final Consumer<Object>[] asynchronous;
        final Consumer<Object>[] parallel;
        final boolean onlySynchronous;

        CompositeDispatch(long epoch, FloraBus<Object> directBus, Predicate<Object> canceller,
                          Consumer<Object>[] sync, Consumer<Object>[] async, Consumer<Object>[] parallel) {
            this.epoch = epoch;
            this.directBus = directBus;
            this.canceller = canceller;
            this.synchronous = sync;
            this.asynchronous = async;
            this.parallel = parallel;
            this.onlySynchronous = async.length == 0 && parallel.length == 0;
        }

        @SuppressWarnings("unchecked")
        <T> void dispatch(T event, Consumer<Throwable> handler) {
            if (directBus != null) {
                directBus.post((Object) event);
                return;
            }

            final Consumer<Object>[] sync = this.synchronous;
            final int len = sync.length;
            if (len > 0) {
                final Predicate<Object> canceller = this.canceller;
                if (handler == null) {
                    if (canceller == null) {
                        for (int i = 0; i < len; i++) sync[i].accept(event);
                    } else {
                        for (int i = 0; i < len; i++) {
                            if (canceller.test(event)) break;
                            sync[i].accept(event);
                        }
                    }
                } else {
                    if (canceller == null) {
                        for (int i = 0; i < len; i++) {
                            try {
                                sync[i].accept(event);
                            } catch (Throwable t) {
                                if (t instanceof VirtualMachineError vme) throw vme;
                                handler.accept(t);
                            }
                        }
                    } else {
                        for (int i = 0; i < len; i++) {
                            if (canceller.test(event)) break;
                            try {
                                sync[i].accept(event);
                            } catch (Throwable t) {
                                if (t instanceof VirtualMachineError vme) throw vme;
                                handler.accept(t);
                            }
                        }
                    }
                }
            }

            if (onlySynchronous) {
                return;
            }

            if (asynchronous.length != 0) {
                DispatchEngine.defaultEngine().dispatchAsync(0, event, asynchronous);
            }
            if (parallel.length != 0) {
                DispatchEngine.defaultEngine().dispatchParallel(event, parallel);
            }
        }
    }
}
