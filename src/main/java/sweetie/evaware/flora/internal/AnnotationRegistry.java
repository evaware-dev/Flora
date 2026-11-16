package sweetie.evaware.flora.internal;

import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.EventBus;
import sweetie.evaware.flora.core.Listener;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class AnnotationRegistry {
    private static final Subscription[] EMPTY_SUBS = new Subscription[0];

    private final BusRegistry buses;
    private final AnnotationScanner scanner;
    private final Map<Object, Subscription[]> instanceSubs = new IdentityHashMap<>();
    private final Map<Class<?>, Subscription[]> staticSubs = new IdentityHashMap<>();

    public AnnotationRegistry(BusRegistry buses, AnnotationScanner scanner) {
        this.buses = Objects.requireNonNull(buses, "buses");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
    }

    public synchronized void register(Object target) {
        Objects.requireNonNull(target, "target");
        if (target instanceof Class<?> targetClass) {
            registerStatic(targetClass);
            return;
        }
        if (instanceSubs.containsKey(target)) return;
        instanceSubs.put(target, subscribeAll(target, scanner.getInstanceScan(target.getClass())));
    }

    public synchronized void registerStatic(Class<?> targetClass) {
        Objects.requireNonNull(targetClass, "targetClass");
        if (staticSubs.containsKey(targetClass)) return;
        staticSubs.put(targetClass, subscribeAll(targetClass, scanner.getStaticScan(targetClass)));
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
    private Subscription[] subscribeAll(Object target, AnnotationScanner.ScanResult scan) {
        AnnotationScanner.AnnotatedMethod[] methods = scan.methods();
        int len = methods.length;
        if (len == 0) return EMPTY_SUBS;
        if (len == 1) {
            AnnotationScanner.AnnotatedMethod m = methods[0];
            Consumer<Object> consumer = m.createConsumer(target);
            Listener<Object> listener = new Listener<>(m.priority(), consumer, m.mode());
            EventBus<Object> bus = buses.bus((Class<Object>) m.eventType());
            return new Subscription[]{bus.subscribe(listener)};
        }
        if (!scan.hasDuplicateEventTypes()) {
            Subscription[] subs = new Subscription[len];
            for (int i = 0; i < len; i++) {
                AnnotationScanner.AnnotatedMethod m = methods[i];
                Consumer<Object> consumer = m.createConsumer(target);
                Listener<Object> listener = new Listener<>(m.priority(), consumer, m.mode());
                EventBus<Object> bus = buses.bus((Class<Object>) m.eventType());
                subs[i] = bus.subscribe(listener);
            }
            return subs;
        }
        Map<EventBus<Object>, List<Listener<Object>>> byBus = new IdentityHashMap<>();
        for (AnnotationScanner.AnnotatedMethod m : methods) {
            Consumer<Object> consumer = m.createConsumer(target);
            Listener<Object> listener = new Listener<>(m.priority(), consumer, m.mode());
            EventBus<Object> bus = buses.bus((Class<Object>) m.eventType());
            byBus.computeIfAbsent(bus, k -> new ArrayList<>()).add(listener);
        }
        Subscription[] subs = new Subscription[byBus.size()];
        int i = 0;
        for (Map.Entry<EventBus<Object>, List<Listener<Object>>> entry : byBus.entrySet()) {
            subs[i++] = entry.getKey().subscribeAll(entry.getValue());
        }
        return subs;
    }
}
