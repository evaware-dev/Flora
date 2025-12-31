package sweetie.evaware;

import sweetie.evaware.interfaces.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public abstract class Flora<T> implements Cacheable<T>, Subscribable<Listener<T>, T>, Notifiable<T> {
    private final ConcurrentSkipListSet<Listener<T>> listeners = new ConcurrentSkipListSet<>();
    private final Object writeLock = new Object();

    @SuppressWarnings("unchecked") private volatile Consumer<T>[] syncCache = (Consumer<T>[]) new Consumer[0];
    @SuppressWarnings("unchecked") private volatile Consumer<T>[] asyncCache = (Consumer<T>[]) new Consumer[0];

    @Override
    @SuppressWarnings("unchecked")
    public void rebuildCache() {
        List<Consumer<T>> syncList = new ArrayList<>();
        List<Consumer<T>> asyncList = new ArrayList<>();

        for (Listener<T> listener : listeners) {
            if (listener.isAsync()) {
                asyncList.add(listener.getHandler());
            } else {
                syncList.add(listener.getHandler());
            }
        }

        this.syncCache = syncList.toArray(new Consumer[0]);
        this.asyncCache = asyncList.toArray(new Consumer[0]);
    }

    @Override
    public EventListener subscribe(Listener<T> listener) {
        synchronized (writeLock) {
            listeners.add(listener);
            rebuildCache();
        }
        return new EventListener(() -> unsubscribe(listener));
    }

    @Override
    public void unsubscribe(Listener<T> listener) {
        synchronized (writeLock) {
            if (listeners.remove(listener)) {
                rebuildCache();
            }
        }
    }

    @Override
    public void notify(T event) {
        Consumer<T>[] syncListeners = this.syncCache;
        for (Consumer<T> consumer : syncListeners) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Consumer<T>[] asyncListeners = this.asyncCache;
        if (asyncListeners.length > 0) {
            ForkJoinPool.commonPool().execute(() -> {
                for (Consumer<T> consumer : asyncListeners) {
                    try {
                        consumer.accept(event);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}