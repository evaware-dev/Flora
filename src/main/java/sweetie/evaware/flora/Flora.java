package sweetie.evaware.flora;

import sweetie.evaware.flora.core.FloraBus;

public final class Flora {
    private static final ClassValue<FloraBus<?>> BUSES = new ClassValue<>() {
        @Override
        protected FloraBus<?> computeValue(Class<?> type) {
            return new FloraBus<>();
        }
    };

    private Flora() {
    }

    @SuppressWarnings("unchecked")
    public static <T> FloraBus<T> getBus(Class<T> type) {
        return (FloraBus<T>) BUSES.get(type);
    }

    @SuppressWarnings("unchecked")
    public static void post(Object event) {
        ((FloraBus<Object>) BUSES.get(event.getClass())).post(event);
    }
}
