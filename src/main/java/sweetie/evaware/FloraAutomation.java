package sweetie.evaware;

import sweetie.evaware.annotations.Commando;
import sweetie.evaware.internal.LambdaFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FloraAutomation {
    private static final Map<Object, List<Runnable>> subscriptions = new ConcurrentHashMap<>();

    public static void register(Object target) {
        if (subscriptions.containsKey(target)) return;

        List<Runnable> unsubscribers = new ArrayList<>();

        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Commando.class) && method.getParameterCount() == 1) {
                Class<?> eventType = method.getParameterTypes()[0];
                Commando annotation = method.getAnnotation(Commando.class);

                Flora<?> eventBus = getOrInitializeBus(eventType);

                if (eventBus == null) {
                    System.err.println("Flora: Error - Event '" + eventType.getSimpleName() + "' has no instance registered.");
                    continue;
                }

                Consumer<Object> handler = LambdaFactory.create(target, method, (Class<Object>) eventType);
                Listener rawListener = new Listener(annotation.priority(), annotation.async(), handler);
                EventListener token = eventBus.subscribe(rawListener);

                unsubscribers.add(token::unsubscribe);
            }
        }

        if (!unsubscribers.isEmpty()) {
            subscriptions.put(target, unsubscribers);
        }
    }

    public static void unregister(Object target) {
        List<Runnable> unsubscribers = subscriptions.remove(target);
        if (unsubscribers != null) {
            for (Runnable unsubscribeAction : unsubscribers) {
                unsubscribeAction.run();
            }
        }
    }

    private static Flora<?> getOrInitializeBus(Class<?> eventType) {
        Flora<?> bus = Flora.getGlobal(eventType);
        if (bus != null) return bus;

        try {
            Class.forName(eventType.getName(), true, eventType.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }

        return Flora.getGlobal(eventType);
    }
}