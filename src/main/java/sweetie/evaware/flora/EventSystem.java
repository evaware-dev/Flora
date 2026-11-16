package sweetie.evaware.flora;

import sweetie.evaware.flora.api.Config;
import sweetie.evaware.flora.api.DispatchConfig;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.EventBus;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.core.engine.DispatchEngine;
import sweetie.evaware.flora.internal.AnnotationRegistry;
import sweetie.evaware.flora.internal.AnnotationScanner;
import sweetie.evaware.flora.internal.BusRegistry;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class EventSystem {
    private static final EventSystem GLOBAL = create();

    private final Config config;
    private final DispatchEngine engine;
    private final BusRegistry buses;
    private final AnnotationRegistry annotations;

    private EventSystem(DispatchConfig dispatchConfig) {
        this.config = new Config();
        this.engine = new DispatchEngine(Objects.requireNonNull(dispatchConfig, "dispatchConfig"), this.config);
        this.buses = new BusRegistry(this.config, this.engine);
        this.annotations = new AnnotationRegistry(this.buses, new AnnotationScanner(this.config));
    }

    public static EventSystem global() {
        return GLOBAL;
    }

    public static EventSystem create() {
        return new EventSystem(DispatchConfig.defaults());
    }

    public static EventSystem create(DispatchConfig dispatchConfig) {
        return new EventSystem(dispatchConfig);
    }

    public Config config() {
        return config;
    }

    public DispatchEngine engine() {
        return engine;
    }

    public <T> EventBus<T> bus(Class<T> type) {
        return buses.bus(type);
    }

    public <T> T post(T event) {
        return buses.dispatch(event);
    }

    public <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
        return subscribe(eventType, 0, DispatchMode.SYNC, listener);
    }

    public <T> Subscription subscribe(Class<T> eventType, DispatchMode mode, Consumer<? super T> listener) {
        return subscribe(eventType, 0, mode, listener);
    }

    public <T> Subscription subscribe(Class<T> eventType, int priority, Consumer<? super T> listener) {
        return subscribe(eventType, priority, DispatchMode.SYNC, listener);
    }

    @SuppressWarnings("unchecked")
    public <T> Subscription subscribe(Class<T> eventType, int priority, DispatchMode mode, Consumer<? super T> listener) {
        Objects.requireNonNull(listener, "listener");
        return bus(eventType).subscribe(new Listener<>(priority, (Consumer<T>) listener, mode));
    }

    public void register(Object target) {
        annotations.register(target);
    }

    public void register(Class<?> targetClass) {
        annotations.registerStatic(targetClass);
    }

    public void unregister(Object target) {
        annotations.unregister(target);
    }

    public void unregister(Class<?> targetClass) {
        annotations.unregisterStatic(targetClass);
    }

    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        return engine.awaitQuiescence(timeout, unit);
    }

    public void shutdown() {
        engine.shutdown();
    }
}
