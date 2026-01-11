package sweetie.evaware;

import sweetie.evaware.interfaces.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public abstract class Flora<T> implements Cacheable<T>, Subscribable<Listener<T>, T>, Notifiable<T> {
    private static final Map<Class<?>, Flora<?>> globals = new ConcurrentHashMap<>();

    protected Flora() {
        try {
            globals.put(getClass(), this);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    public static <E> Flora<E> getGlobal(Class<E> clazz) {
        return (Flora<E>) globals.get(clazz);
    }

    private final List<Listener<T>> listeners = new ArrayList<>();
    private final Object writeLock = new Object();

    @SuppressWarnings("unchecked") private volatile Consumer<T>[] syncCache = (Consumer<T>[]) new Consumer[0];
    @SuppressWarnings("unchecked") private volatile Consumer<T>[] asyncCache = (Consumer<T>[]) new Consumer[0];

    @Override
    @SuppressWarnings("unchecked")
    public void rebuildCache() {
        synchronized (writeLock) {
            listeners.sort(Listener::compareTo);

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
        final Consumer<T>[] asyncListeners = asyncCache;
        final Consumer<T>[] syncListeners = syncCache;
        final int syncLen = syncListeners.length;

        for (int i = 0; i < syncLen; i++) {
            try {
                syncListeners[i].accept(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

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