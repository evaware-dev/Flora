package sweetie.evaware.flora.core;

import sweetie.evaware.flora.core.engine.AsyncLoop;
import sweetie.evaware.flora.api.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public class FloraBus<T> {
    private static final AsyncLoop ASYNC_WORKER = new AsyncLoop();
    private static final ForkJoinPool PARALLEL_POOL = ForkJoinPool.commonPool();

    private final Object lock = new Object();
    private final List<Listener<T>> subscribers = new ArrayList<>();

    private volatile Listener<T>[] syncListeners;
    private volatile Listener<T>[] asyncListeners;
    private volatile Listener<T>[] parallelListeners;

    @SuppressWarnings("unchecked")
    public FloraBus() {
        this.syncListeners = new Listener[0];
        this.asyncListeners = new Listener[0];
        this.parallelListeners = new Listener[0];
    }

    public void post(T event) {
        Listener<T>[] sync = this.syncListeners;
        int i = 0;
        int syncLen = sync.length;
        while (i < syncLen) {
            sync[i++].accept(event);
        }

        Listener<T>[] async = this.asyncListeners;
        int asyncLen = async.length;
        if (asyncLen > 0) {
            ASYNC_WORKER.execute(() -> {
                int j = 0;
                while (j < asyncLen) {
                    async[j++].accept(event);
                }
            });
        }

        Listener<T>[] parallel = this.parallelListeners;
        int k = 0;
        int parLength = parallel.length;
        while (k < parLength) {
            Listener<T> l = parallel[k++];
            PARALLEL_POOL.execute(() -> l.accept(event));
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
        Collections.sort(subscribers);

        List<Listener<T>> sync = new ArrayList<>();
        List<Listener<T>> async = new ArrayList<>();
        List<Listener<T>> parallel = new ArrayList<>();

        for (Listener<T> listener : subscribers) {
            switch (listener.mode) {
                case SYNC -> sync.add(listener);
                case ASYNC -> async.add(listener);
                case ASYNC_PARALLEL -> parallel.add(listener);
            }
        }

        this.syncListeners = sync.toArray(new Listener[0]);
        this.asyncListeners = async.toArray(new Listener[0]);
        this.parallelListeners = parallel.toArray(new Listener[0]);
    }
}