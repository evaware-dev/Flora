package sweetie.evaware.flora;

import sweetie.evaware.flora.api.Config;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.EventBus;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Flora {
    private Flora() {
    }

    public static EventSystem system() {
        return EventSystem.global();
    }

    public static Config config() {
        return EventSystem.global().config();
    }

    public static <T> EventBus<T> bus(Class<T> type) {
        return EventSystem.global().bus(type);
    }

    public static <T> T post(T event) {
        return EventSystem.global().post(event);
    }

    public static <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
        return EventSystem.global().subscribe(eventType, listener);
    }

    public static <T> Subscription subscribe(Class<T> eventType, DispatchMode mode, Consumer<? super T> listener) {
        return EventSystem.global().subscribe(eventType, mode, listener);
    }

    public static <T> Subscription subscribe(Class<T> eventType, int priority, Consumer<? super T> listener) {
        return EventSystem.global().subscribe(eventType, priority, listener);
    }

    public static <T> Subscription subscribe(Class<T> eventType, int priority, DispatchMode mode, Consumer<? super T> listener) {
        return EventSystem.global().subscribe(eventType, priority, mode, listener);
    }

    public static void register(Object target) {
        EventSystem.global().register(target);
    }

    public static void register(Class<?> targetClass) {
        EventSystem.global().register(targetClass);
    }

    public static void unregister(Object target) {
        EventSystem.global().unregister(target);
    }

    public static void unregister(Class<?> targetClass) {
        EventSystem.global().unregister(targetClass);
    }

    public static void shutdown() {
        EventSystem.global().shutdown();
    }

    public static boolean awaitQuiescence(long timeout, TimeUnit unit) {
        return EventSystem.global().awaitQuiescence(timeout, unit);
    }
}
