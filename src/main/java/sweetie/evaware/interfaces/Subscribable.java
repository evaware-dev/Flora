package sweetie.evaware.interfaces;

import sweetie.evaware.EventListener;

public interface Subscribable<L, T> {
    EventListener subscribe(L listener);
    void unsubscribe(L listener);
}
