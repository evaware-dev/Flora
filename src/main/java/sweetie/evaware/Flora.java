package sweetie.evaware;

import sweetie.evaware.interfaces.Cacheable;
import sweetie.evaware.interfaces.Notifiable;
import sweetie.evaware.interfaces.Subscribable;

import java.util.concurrent.ConcurrentSkipListSet;

public abstract class Flora<T> implements Cacheable, Subscribable<Listener<T>, T>, Notifiable<T> {
    private final ConcurrentSkipListSet<Listener<T>> listeners = new ConcurrentSkipListSet<>();

    @SuppressWarnings("unchecked")
    private volatile Listener<T>[] cache = (Listener<T>[]) new Listener<?>[0];

    @Override
    @SuppressWarnings("unchecked")
    public void refresh() {
        cache = listeners.toArray(Listener[]::new);
    }

    @Override
    public EventListener subscribe(Listener<T> listener) {
        if (listeners.add(listener)) refresh();
        return new EventListener(() -> unsubscribe(listener));
    }

    @Override
    public void unsubscribe(Listener<T> listener) {
        if (listeners.remove(listener)) refresh();
    }

    @Override
    public void notify(T event) {
        for (Listener<T> tListener : cache) {
            tListener.getHandler().accept(event);
        }
    }
}