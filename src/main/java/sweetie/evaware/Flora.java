package sweetie.evaware;

import sweetie.evaware.api.DispatchMode;
import sweetie.evaware.api.Subscription;
import sweetie.evaware.core.ConsumerListener;
import sweetie.evaware.core.FloraBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class Flora {
    private static final Map<Class<?>, FloraBus<?>> buses = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> FloraBus<T> getBus(Class<T> type) {
        return (FloraBus<T>) buses.computeIfAbsent(type, FloraBus::new);
    }

    @SuppressWarnings("unchecked")
    public static void post(Object event) {
        getBus((Class<Object>) event.getClass()).post(event);
    }

    public static <T> Subscription subscribe(Class<T> type, Consumer<T> listener, DispatchMode mode) {
        return getBus(type).subscribe(new ConsumerListener<>(0, listener, mode));
    }

    public static <T> Subscription subscribe(Class<T> type, Consumer<T> listener) {
        return getBus(type).subscribe(new ConsumerListener<>(0, listener, DispatchMode.SYNC));
    }
}