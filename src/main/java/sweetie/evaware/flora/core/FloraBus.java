package sweetie.evaware.flora.core;

import sweetie.evaware.flora.FloraConfigurator;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class FloraBus<T> {
    private static final Consumer<?>[] EMPTY = new Consumer<?>[0];
    private static final Comparator<Registration<?>> REGISTRATION_ORDER = (a, b) -> Integer.compare(b.listener.priority(), a.listener.priority());

    private final DispatchEngine dispatchEngine;
    private final int asynchronousLane;
    private final Runnable onChange;
    final List<Registration<T>> registrations = new ArrayList<>();

    private volatile Consumer<T>[] synchronous = emptyArray();
    private volatile Consumer<T>[] asynchronous = emptyArray();
    private volatile Consumer<T>[] parallel = emptyArray();
    private volatile boolean onlySynchronous = true;

    private volatile Consumer<Throwable> exceptionHandler;

    public FloraBus() {
        this(DispatchEngine.defaultEngine(), null);
    }

    public FloraBus(Runnable onChange) {
        this(DispatchEngine.defaultEngine(), onChange);
    }

    public FloraBus(DispatchEngine dispatchEngine) {
        this(dispatchEngine, null);
    }

    public FloraBus(DispatchEngine dispatchEngine, Runnable onChange) {
        this.dispatchEngine = Objects.requireNonNull(dispatchEngine, "dispatchEngine");
        this.asynchronousLane = dispatchEngine.acquireAsyncLane();
        this.onChange = onChange;
    }

    public T post(T event) {
        Objects.requireNonNull(event, "event");
        final Consumer<T>[] sync = this.synchronous;
        final int len = sync.length;

        if (len > 0) {
            final Predicate<Object> canceller = FloraConfigurator.getCanceller(event.getClass());
            final Consumer<Throwable> handler = this.exceptionHandler != null
                    ? this.exceptionHandler
                    : FloraConfigurator.getExceptionHandler();

            if (canceller == null) {
                dispatchDirect(event, sync, len, handler);
            } else {
                dispatchCancellable(event, sync, len, canceller, handler);
            }
        }

        if (onlySynchronous) {
            return event;
        }

        if (asynchronous.length != 0) {
            dispatchEngine.dispatchAsync(asynchronousLane, event, asynchronous);
        }
        if (parallel.length != 0) {
            dispatchEngine.dispatchParallel(event, parallel);
        }
        return event;
    }

    private void dispatchDirect(T event, Consumer<T>[] sync, int len, Consumer<Throwable> handler) {
        if (handler == null) {
            for (int i = 0; i < len; i++) sync[i].accept(event);
        } else {
            for (int i = 0; i < len; i++) {
                try {
                    sync[i].accept(event);
                } catch (Throwable t) {
                    if (t instanceof VirtualMachineError vme) throw vme;
                    handler.accept(t);
                }
            }
        }
    }

    private void dispatchCancellable(T event, Consumer<T>[] sync, int len, Predicate<Object> canceller, Consumer<Throwable> handler) {
        if (handler == null) {
            for (int i = 0; i < len; i++) {
                if (canceller.test(event)) break;
                sync[i].accept(event);
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

    public synchronized Subscription subscribe(Listener<T> listener) {
        Objects.requireNonNull(listener, "listener");
        Registration<T> reg = new Registration<>(this, listener);
        registrations.add(reg);
        registrations.sort(REGISTRATION_ORDER);
        rebuild();
        return reg;
    }

    public synchronized Subscription subscribeAll(List<? extends Listener<T>> list) {
        Objects.requireNonNull(list, "list");
        if (list.isEmpty()) {
            return () -> {};
        }
        List<Registration<T>> added = new ArrayList<>(list.size());
        for (Listener<T> l : list) {
            added.add(new Registration<>(this, l));
        }
        registrations.addAll(added);
        registrations.sort(REGISTRATION_ORDER);
        rebuild();
        return () -> {
            synchronized (FloraBus.this) {
                registrations.removeAll(added);
                rebuild();
            }
        };
    }

    public Subscription subscribe(Consumer<T> callback) {
        return subscribe(new Listener<>(0, callback, DispatchMode.SYNC));
    }

    public Subscription subscribe(int priority, Consumer<T> callback) {
        return subscribe(new Listener<>(priority, callback, DispatchMode.SYNC));
    }

    public Subscription subscribe(int priority, DispatchMode mode, Consumer<T> callback) {
        return subscribe(new Listener<>(priority, callback, mode));
    }

    void removeRegistration(Registration<T> reg) {
        if (registrations.remove(reg)) {
            rebuild();
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        int syncCount = 0;
        int asyncCount = 0;
        int parallelCount = 0;

        for (Registration<T> reg : registrations) {
            Listener<T> l = reg.listener;
            switch (l.mode()) {
                case SYNC -> syncCount++;
                case ASYNC -> asyncCount++;
                case ASYNC_PARALLEL -> parallelCount++;
            }
        }

        Consumer<T>[] newSync = syncCount == 0 ? emptyArray() : (Consumer<T>[]) new Consumer<?>[syncCount];
        Consumer<T>[] newAsync = asyncCount == 0 ? emptyArray() : (Consumer<T>[]) new Consumer<?>[asyncCount];
        Consumer<T>[] newParallel = parallelCount == 0 ? emptyArray() : (Consumer<T>[]) new Consumer<?>[parallelCount];

        int s = 0, a = 0, p = 0;
        for (Registration<T> reg : registrations) {
            Listener<T> l = reg.listener;
            switch (l.mode()) {
                case SYNC -> newSync[s++] = l.callback();
                case ASYNC -> newAsync[a++] = l.callback();
                case ASYNC_PARALLEL -> newParallel[p++] = l.callback();
            }
        }

        this.synchronous = newSync;
        this.asynchronous = newAsync;
        this.parallel = newParallel;
        this.onlySynchronous = asyncCount == 0 && parallelCount == 0;

        if (onChange != null) {
            onChange.run();
        }
    }

    public synchronized int listenerCount() {
        return registrations.size();
    }

    @SuppressWarnings("unchecked")
    public synchronized void copyListenersTo(List<Listener<Object>> destination) {
        for (Registration<T> r : registrations) {
            destination.add((Listener<Object>) r.listener);
        }
    }

    public synchronized List<Listener<T>> getListeners() {
        List<Listener<T>> list = new ArrayList<>(registrations.size());
        for (Registration<T> r : registrations) list.add(r.listener);
        return list;
    }

    public void setExceptionHandler(Consumer<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public Consumer<Throwable> getExceptionHandler() {
        return exceptionHandler;
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T>[] emptyArray() {
        return (Consumer<T>[]) EMPTY;
    }
}
