package sweetie.evaware.core;

import sweetie.evaware.api.Dispatcher;
import sweetie.evaware.api.Subscription;
import sweetie.evaware.async.AsyncEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class FloraBus<T> {
    protected static final class Executors {
        static final AsyncEngine SINGLE_WORKER = new AsyncEngine();
        static final ForkJoinPool PARALLEL_POOL = ForkJoinPool.commonPool();
    }

    private volatile Dispatcher<T> currentDispatcher;
    private final List<ConsumerListener<T>> consumerListenerList = new ArrayList<>();
    private final Object lock = new Object();

    public FloraBus(Class<T> type) {
        this.currentDispatcher = e -> {};
    }

    public void post(T event) {
        currentDispatcher.dispatch(event);
    }

    public Subscription subscribe(ConsumerListener<T> consumerListener) {
        synchronized (lock) {
            consumerListenerList.add(consumerListener);
            rebuild();
        }
        return () -> {
            synchronized (lock) {
                if (consumerListenerList.remove(consumerListener)) rebuild();
            }
        };
    }

    public void unsubscribe(ConsumerListener<T> listener) {
        synchronized (lock) {
            if (consumerListenerList.remove(listener)) {
                rebuild();
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void rebuild() {
        Collections.sort(consumerListenerList);

        List<Consumer<T>> sync = new ArrayList<>();
        List<Consumer<T>> single = new ArrayList<>();
        List<Consumer<T>> parallel = new ArrayList<>();

        for (ConsumerListener<T> h : consumerListenerList) {
            switch (h.mode) {
                case SYNC -> sync.add(h.consumer);
                case ASYNC_SINGLE -> single.add(h.consumer);
                case ASYNC_PARALLEL -> parallel.add(h.consumer);
            }
        }

        Consumer<T>[] syncArr = sync.toArray((Consumer<T>[]) new Consumer[0]);

        boolean hasAsync = !single.isEmpty() || !parallel.isEmpty();

        if (!hasAsync) {
            this.currentDispatcher = new SyncDispatcher<>(syncArr);
        } else {
            Consumer<T>[] singleArr = single.toArray((Consumer<T>[]) new Consumer[0]);
            Consumer<T>[] parallelArr = parallel.toArray((Consumer<T>[]) new Consumer[0]);

            this.currentDispatcher = new AsyncDispatcher<>(
                    syncArr, singleArr, parallelArr
            );
        }
    }
}