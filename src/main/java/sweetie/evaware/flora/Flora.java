package sweetie.evaware.flora;

import sweetie.evaware.flora.core.FloraBus;

import java.util.concurrent.ConcurrentHashMap;

public final class Flora {
    private static final ConcurrentHashMap<Class<?>, FloraBus<?>> BUSES = new ConcurrentHashMap<>();
    private static volatile Class<?> cachedType;
    private static volatile FloraBus<?> cachedBus;

    private Flora() {
    }

    @SuppressWarnings("unchecked")
    public static <T> FloraBus<T> getBus(Class<T> type) {
        Class<?> lastType = cachedType;
        FloraBus<?> bus = lastType == type ? cachedBus : BUSES.get(type);
        if (bus != null) {
            if (lastType != type) {
                cachedBus = bus;
                cachedType = type;
            }
            return (FloraBus<T>) bus;
        }

        FloraBus<T> created = (FloraBus<T>) BUSES.computeIfAbsent(type, ignored -> new FloraBus<>());
        cachedBus = created;
        cachedType = type;
        return created;
    }

    @SuppressWarnings("unchecked")
    public static void post(Object event) {
        Class<?> type = event.getClass();
        Class<?> lastType = cachedType;
        FloraBus<?> bus = lastType == type ? cachedBus : BUSES.get(type);

        if (bus != null) {
            if (lastType != type) {
                cachedBus = bus;
                cachedType = type;
            }
            ((FloraBus<Object>) bus).post(event);
            return;
        }

        FloraBus<Object> created = (FloraBus<Object>) BUSES.computeIfAbsent(type, ignored -> new FloraBus<>());
        cachedBus = created;
        cachedType = type;
        created.post(event);
    }
}
