package sweetie.evaware.flora;

import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.util.LambdaFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FloraAutomation {
    private static final Map<Object, List<Subscription>> registry = new ConcurrentHashMap<>();

    public static void register(Object target) {
        if (registry.containsKey(target)) return;

        List<Subscription> subs = new ArrayList<>();
        Class<?> clazz = target.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Commando.class) && method.getParameterCount() == 1) {
                Class<?> eventType = method.getParameterTypes()[0];
                Commando info = method.getAnnotation(Commando.class);

                FloraBus<Object> bus = Flora.getBus((Class<Object>) eventType);
                Consumer<Object> handler = LambdaFactory.create(target, method, (Class<Object>) eventType);

                subs.add(bus.subscribe(new Listener<>(info.priority(), handler, info.mode())));
            }
        }

        if (!subs.isEmpty()) {
            registry.put(target, subs);
        }
    }

    public static void unregister(Object target) {
        List<Subscription> subs = registry.remove(target);
        if (subs != null) {
            subs.forEach(Subscription::unsubscribe);
        }
    }
}