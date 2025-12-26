package sweetie.evaware;

import sweetie.evaware.interfaces.Cacheable;
import sweetie.evaware.interfaces.Notifiable;
import sweetie.evaware.interfaces.Subscribable;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

public abstract class Flora<T> implements Cacheable<T>, Subscribable<Listener<T>, T>, Notifiable<T> {
    private final ConcurrentSkipListSet<Listener<T>> listeners = new ConcurrentSkipListSet<>();
    private final Object writeLock = new Object();

    @SuppressWarnings("unchecked")
    private volatile Consumer<T>[] cache = (Consumer<T>[]) new Consumer<?>[0];

    @Override
    @SuppressWarnings("unchecked")
    public void rebuildCache() {
        Consumer<T>[] tempCache = new Consumer[listeners.size()];
        int i = 0;
        for (Listener<T> listener : listeners) {
            tempCache[i++] = listener.getHandler();
        }
        this.cache = tempCache;
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
        Consumer<T>[] currentListeners = cache;

        for (Consumer<T> consumer : currentListeners) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                System.err.println("Error in event listener: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}