package sweetie.evaware.flora;

import sweetie.evaware.flora.core.FloraBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Flora {
    private static final Map<Class<?>, FloraBus<?>> BUSES = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> FloraBus<T> getBus(Class<T> type) {
        return (FloraBus<T>) BUSES.computeIfAbsent(type, k -> new FloraBus<T>());
    }

    @SuppressWarnings("unchecked")
    public static void post(Object event) {
        // Optimized: computeIfAbsent used directly
        ((FloraBus<Object>) BUSES.computeIfAbsent(event.getClass(), k -> new FloraBus<>()))
                .post(event);
    }
}