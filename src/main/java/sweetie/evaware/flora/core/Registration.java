package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.Subscription;

final class Registration<T> implements Subscription {
    final FloraBus<T> bus;
    final Listener<T> listener;
    private boolean active = true;

    Registration(FloraBus<T> bus, Listener<T> listener) {
        this.bus = bus;
        this.listener = listener;
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
