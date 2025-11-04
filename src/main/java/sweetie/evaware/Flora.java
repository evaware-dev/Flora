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
        if (rebuildCache) {
            cache = listeners.toArray(Listener[]::new);
            rebuildCache = false;
        }
        return cache;
    }

    @Override
    public EventListener subscribe(Listener<T> listener) {
        listeners.add(listener);
        rebuildCache = true;
        return new EventListener(() -> unsubscribe(listener));
    }

    @Override
    public void unsubscribe(Listener<T> listener) {
        if (listeners.remove(listener))
            rebuildCache = true;
    }

    @Override
    public void notify(T event) {
        for (Listener<T> tListener : getCache()) {
            tListener.getHandler().accept(event);
        }
    }
}