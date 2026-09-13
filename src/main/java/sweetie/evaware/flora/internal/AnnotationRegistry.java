package sweetie.evaware.flora.internal;

import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class AnnotationRegistry {
    private static final Subscription[] EMPTY_SUBS = new Subscription[0];

    private final Map<Object, Subscription[]> instanceSubs = new IdentityHashMap<>();
    private final Map<Class<?>, Subscription[]> staticSubs = new ConcurrentHashMap<>();

    public synchronized void register(Object target) {
        Objects.requireNonNull(target, "target");
        if (target instanceof Class<?> targetClass) {
            registerStatic(targetClass);
            return;
        }
        if (instanceSubs.containsKey(target)) return;
        instanceSubs.put(target, subscribeAll(target, AnnotationScanner.getInstanceMethods(target.getClass())));
    }

    public synchronized void registerStatic(Class<?> targetClass) {
        Objects.requireNonNull(targetClass, "targetClass");
        if (staticSubs.containsKey(targetClass)) return;
        staticSubs.put(targetClass, subscribeAll(targetClass, AnnotationScanner.getStaticMethods(targetClass)));
    }

    public synchronized void unregister(Object target) {
        Objects.requireNonNull(target, "target");
        if (target instanceof Class<?> targetClass) {
            unregisterStatic(targetClass);
            return;
        }
        Subscription[] subs = instanceSubs.remove(target);
        if (subs != null) {
            for (Subscription s : subs) s.unsubscribe();
        }
    }

    public synchronized void unregisterStatic(Class<?> targetClass) {
        Objects.requireNonNull(targetClass, "targetClass");
        Subscription[] subs = staticSubs.remove(targetClass);
        if (subs != null) {
            for (Subscription s : subs) s.unsubscribe();
        }
    }

    @SuppressWarnings("unchecked")
    private Subscription[] subscribeAll(Object target, AnnotationScanner.AnnotatedMethod[] methods) {
        if (methods.length == 0) return EMPTY_SUBS;
        if (methods.length == 1) {
            AnnotationScanner.AnnotatedMethod m = methods[0];
            Consumer<Object> consumer = LambdaFactory.createConsumer(target, m.method());
            Listener<Object> listener = new Listener<>(m.priority(), consumer, m.mode());
            FloraBus<Object> bus = Flora.getBus((Class<Object>) m.eventType());
            return new Subscription[]{bus.subscribe(listener)};
        }
        Map<FloraBus<Object>, List<Listener<Object>>> byBus = new IdentityHashMap<>();
        for (AnnotationScanner.AnnotatedMethod m : methods) {
            Consumer<Object> consumer = LambdaFactory.createConsumer(target, m.method());
            Listener<Object> listener = new Listener<>(m.priority(), consumer, m.mode());
            FloraBus<Object> bus = Flora.getBus((Class<Object>) m.eventType());
            byBus.computeIfAbsent(bus, k -> new ArrayList<>()).add(listener);
        }
        Subscription[] subs = new Subscription[byBus.size()];
        int i = 0;
        for (Map.Entry<FloraBus<Object>, List<Listener<Object>>> entry : byBus.entrySet()) {
            subs[i++] = entry.getKey().subscribeAll(entry.getValue());
        }
        return subs;
    }
}
