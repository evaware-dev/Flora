package sweetie.evaware;

import sweetie.evaware.interfaces.Cacheable;
import sweetie.evaware.interfaces.Notifiable;
import sweetie.evaware.interfaces.Subscribable;

import java.util.concurrent.ConcurrentSkipListSet;

public abstract class Flora<T> implements Cacheable<T>, Subscribable<Listener<T>, T>, Notifiable<T> {
    private final ConcurrentSkipListSet<Listener<T>> listeners = new ConcurrentSkipListSet<>();

    @SuppressWarnings("unchecked")
    private volatile Listener<T>[] cache = (Listener<T>[]) new Listener<?>[0];

    private volatile boolean rebuildCache = true;

    @Override
    @SuppressWarnings("unchecked")
    public Listener<T>[] getCache() {
        synchronized (this) {
            if (rebuildCache) {
                cache = listeners.toArray(Listener[]::new);
                rebuildCache = false;
            }
        }
        return cache;
    }

    @Override
    public EventListener subscribe(Listener<T> listener) {
        listeners.add(listener);
        synchronized (this) { rebuildCache = true; }
        return new EventListener(() -> unsubscribe(listener));
    }

    @Override
    public void unsubscribe(Listener<T> listener) {
        if (listeners.remove(listener))
            synchronized (this) { rebuildCache = true; }
    }

    @Override
    public void notify(T event) {
        Listener<T>[] listeners = getCache();

        for (Listener<T> listener : listeners) {
            try {
                listener.getHandler().accept(event);
            } catch (Exception e) {
                System.err.println("Error in event listener: " + e.getMessage());
            }
        }
    }
}