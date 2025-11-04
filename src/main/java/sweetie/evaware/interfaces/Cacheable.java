package sweetie.evaware.interfaces;

import sweetie.evaware.Listener;

public interface Cacheable<T> {
    Listener<T>[] getCache();
}