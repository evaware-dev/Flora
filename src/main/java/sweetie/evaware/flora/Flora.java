package sweetie.evaware.flora;

import sweetie.evaware.flora.core.FloraBus;

import java.util.concurrent.ConcurrentHashMap;

public final class Flora {
    private static final ConcurrentHashMap<Class<?>, FloraBus<?>> BUSES = new ConcurrentHashMap<>();
    private static volatile CacheEntry cache;

    private record CacheEntry(Class<?> type, FloraBus<?> bus) { }

    @SuppressWarnings("unchecked")
    public static <T> FloraBus<T> getBus(Class<T> type) {
        CacheEntry cached = cache;
        FloraBus<?> bus = cached != null && cached.type == type ? cached.bus : BUSES.get(type);
        if (bus != null) {
            if (cached == null || cached.type != type || cached.bus != bus) {
                cache = new CacheEntry(type, bus);
            }
            return (FloraBus<T>) bus;
        }

        FloraBus<T> created = (FloraBus<T>) BUSES.computeIfAbsent(type, ignored -> new FloraBus<>());
        cache = new CacheEntry(type, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    public static void post(Object event) {
        Class<?> type = event.getClass();
        CacheEntry cached = cache;
        FloraBus<?> bus = cached != null && cached.type == type ? cached.bus : BUSES.get(type);

        if (bus != null) {
            if (cached == null || cached.type != type || cached.bus != bus) {
                cache = new CacheEntry(type, bus);
            }
            ((FloraBus<Object>) bus).post(event);
            return;
        }

        FloraBus<Object> created = (FloraBus<Object>) BUSES.computeIfAbsent(type, ignored -> new FloraBus<>());
        cache = new CacheEntry(type, created);
        created.post(event);
    }
}