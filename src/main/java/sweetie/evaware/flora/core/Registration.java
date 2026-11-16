package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.Subscription;

final class Registration<T> implements Subscription {
    final EventBus<T> bus;
    final Listener<T> listener;
    boolean active = true;

    Registration(EventBus<T> bus, Listener<T> listener) {
        this.bus = bus;
        this.listener = listener;
    }

    boolean markInactive() {
        if (!active) return false;
        active = false;
        return true;
    }

    @Override
    public void unsubscribe() {
        synchronized (bus) {
            if (!active) return;
            active = false;
            bus.removeRegistration(this);
        }
    }
}
