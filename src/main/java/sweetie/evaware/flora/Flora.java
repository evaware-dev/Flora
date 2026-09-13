package sweetie.evaware.flora;

import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.core.engine.DispatchEngine;
import sweetie.evaware.flora.internal.AnnotationRegistry;
import sweetie.evaware.flora.internal.PolymorphicDispatcher;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Flora {
    private static final AnnotationRegistry ANNOTATIONS = new AnnotationRegistry();

    private Flora() {
    }

    public static <T> FloraBus<T> getBus(Class<T> type) {
        return PolymorphicDispatcher.getBus(type);
    }

    public static <T> T post(T event) {
        return PolymorphicDispatcher.dispatch(event);
    }

    public static <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
        return subscribe(eventType, 0, DispatchMode.SYNC, listener);
    }

    public static <T> Subscription subscribe(Class<T> eventType, DispatchMode mode, Consumer<? super T> listener) {
        return subscribe(eventType, 0, mode, listener);
    }

    public static <T> Subscription subscribe(Class<T> eventType, int priority, Consumer<? super T> listener) {
        return subscribe(eventType, priority, DispatchMode.SYNC, listener);
    }

    @SuppressWarnings("unchecked")
    public static <T> Subscription subscribe(Class<T> eventType, int priority, DispatchMode mode, Consumer<? super T> listener) {
        Objects.requireNonNull(listener, "listener");
        FloraBus<T> bus = getBus(eventType);
        return bus.subscribe(new Listener<>(priority, (Consumer<T>) listener, mode));
    }

    public static void register(Object target) {
        ANNOTATIONS.register(target);
    }

    public static void register(Class<?> targetClass) {
        ANNOTATIONS.registerStatic(targetClass);
    }

    public static void unregister(Object target) {
        ANNOTATIONS.unregister(target);
    }

    public static void unregister(Class<?> targetClass) {
        ANNOTATIONS.unregisterStatic(targetClass);
    }

    public static void shutdown() {
        DispatchEngine.defaultEngine().shutdown();
    }

    public static boolean awaitQuiescence(long timeout, TimeUnit unit) {
        return DispatchEngine.defaultEngine().awaitQuiescence(timeout, unit);
    }
}
