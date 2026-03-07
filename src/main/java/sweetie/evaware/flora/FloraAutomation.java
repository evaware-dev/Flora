package sweetie.evaware.flora;

import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.util.LambdaFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class FloraAutomation {
    private static final Object LOCK = new Object();
    private static final Subscription[] EMPTY_SUBSCRIPTIONS = new Subscription[0];
    private static final HandlerMethod[] EMPTY_HANDLERS = new HandlerMethod[0];
    private static final ClassValue<HandlerMethod[]> HANDLERS = new ClassValue<>() {
        @Override
        protected HandlerMethod[] computeValue(Class<?> type) {
            return scanHandlers(type);
        }
    };
    private static final Map<Object, Subscription[]> REGISTRY = new ConcurrentHashMap<>();

    private FloraAutomation() {
    }

    public static void register(Object target) {
        synchronized (LOCK) {
            if (REGISTRY.containsKey(target)) {
                return;
            }

            Subscription[] subscriptions = subscribe(target);
            if (subscriptions.length > 0) {
                REGISTRY.put(target, subscriptions);
            }
        }
    }

    public static void unregister(Object target) {
        Subscription[] subscriptions;
        synchronized (LOCK) {
            subscriptions = REGISTRY.remove(target);
        }

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
        List<HandlerMethod> handlers = new ArrayList<>();

        for (Method method : type.getDeclaredMethods()) {
            Commando info = method.getAnnotation(Commando.class);
            if (info == null || method.getParameterCount() != 1) {
                continue;
            }

            handlers.add(new HandlerMethod(method, method.getParameterTypes()[0], info.priority(), info.mode()));
        }

        return handlers.isEmpty() ? EMPTY_HANDLERS : handlers.toArray(EMPTY_HANDLERS);
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
