package sweetie.evaware.flora;

import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Flora {
    private static final AnnotationSubscriptionRegistry ANNOTATED_SUBSCRIPTIONS =
            new AnnotationSubscriptionRegistry();
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
        return (FloraBus<T>) BUSES.get(Objects.requireNonNull(type, "type"));
    }

    @SuppressWarnings("unchecked")
    public static void post(Object event) {
        Objects.requireNonNull(event, "event");
        ((FloraBus<Object>) BUSES.get(event.getClass())).post(event);
    }

    public static void register(Object target) {
        ANNOTATED_SUBSCRIPTIONS.register(target);
    }

    public static void unregister(Object target) {
        ANNOTATED_SUBSCRIPTIONS.unregister(target);
    }

    public static void setErrorHandler(Consumer<Throwable> exceptionHandler) {
        DispatchEngine.defaultEngine().setListenerExceptionHandler(exceptionHandler);
    }

    public static boolean awaitQuiescence(long timeout, TimeUnit unit) {
        return DispatchEngine.defaultEngine().awaitQuiescence(timeout, unit);
    }

    public static void shutdown() {
        DispatchEngine.defaultEngine().shutdown();
    }
}
