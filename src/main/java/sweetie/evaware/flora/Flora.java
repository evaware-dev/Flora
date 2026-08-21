package sweetie.evaware.flora;

import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;
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

    /**
     * Subscribes a synchronous listener to the canonical bus for {@code eventType}.
     */
    public static <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
        return subscribe(eventType, 0, DispatchMode.SYNC, listener);
    }

    /**
     * Subscribes a listener with the requested dispatch mode and default priority.
     */
    public static <T> Subscription subscribe(Class<T> eventType, DispatchMode mode,
                                             Consumer<? super T> listener) {
        return subscribe(eventType, 0, mode, listener);
    }

    /**
     * Subscribes a synchronous listener with an explicit priority.
     */
    public static <T> Subscription subscribe(Class<T> eventType, int priority,
                                             Consumer<? super T> listener) {
        return subscribe(eventType, priority, DispatchMode.SYNC, listener);
    }

    /**
     * Subscribes a listener to the same canonical bus used by {@link #post(Object)} and
     * annotation-based registration. Higher priorities are invoked first.
     */
    public static <T> Subscription subscribe(Class<T> eventType, int priority, DispatchMode mode,
                                             Consumer<? super T> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(listener, "listener");
        return getBus(eventType).subscribe(new Listener<>(priority, narrow(listener), mode));
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> narrow(Consumer<? super T> listener) {
        return (Consumer<T>) listener;
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
