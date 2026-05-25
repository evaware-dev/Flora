package sweetie.evaware.flora;

import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.util.LambdaFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

public final class FloraAutomation {
    private static final Subscription[] EMPTY_SUBSCRIPTIONS = new Subscription[0];
    private static final ClassValue<HandlerMethod[]> HANDLERS = new ClassValue<>() {
        @Override
        protected HandlerMethod[] computeValue(Class<?> type) {
            return scanHandlers(type);
        }
    };
    private static final Map<Object, Subscription[]> REGISTRY = new IdentityHashMap<>();

    private FloraAutomation() {
    }

    public static synchronized void register(Object target) {
        if (REGISTRY.containsKey(target)) {
            return;
        }

        Subscription[] subscriptions = subscribe(target);
        if (subscriptions.length > 0) {
            REGISTRY.put(target, subscriptions);
        }
    }

    public static synchronized void unregister(Object target) {
        Subscription[] subscriptions = REGISTRY.remove(target);
        if (subscriptions == null) {
            return;
        }

        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
    }

    private static Subscription[] subscribe(Object target) {
        HandlerMethod[] handlers = HANDLERS.get(target.getClass());
        if (handlers.length == 0) {
            return EMPTY_SUBSCRIPTIONS;
        }

        Subscription[] subscriptions = new Subscription[handlers.length];
        int index = 0;

        try {
            for (HandlerMethod handler : handlers) {
                subscriptions[index++] = handler.subscribe(target);
            }
        } catch (Throwable t) {
            while (index > 0) {
                subscriptions[--index].unsubscribe();
            }
            throw t;
        }

        return subscriptions;
    }

    private static HandlerMethod[] scanHandlers(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Commando.class) && m.getParameterCount() == 1)
                .map(m -> new HandlerMethod(m, m.getParameterTypes()[0], m.getAnnotation(Commando.class).priority(), m.getAnnotation(Commando.class).mode()))
                .toArray(HandlerMethod[]::new);
    }

    private record HandlerMethod(Method method, Class<?> eventType, int priority, DispatchMode mode) {
        @SuppressWarnings("unchecked")
        private Subscription subscribe(Object target) {
            FloraBus<Object> bus = Flora.getBus((Class<Object>) eventType);
            Consumer<Object> handler = LambdaFactory.create(target, method);
            return bus.subscribe(new Listener<>(priority, handler, mode));
        }
    }
}