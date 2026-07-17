package sweetie.evaware.flora;

import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AnnotationSubscriptionRegistry {
    private static final Subscription[] NO_SUBSCRIPTIONS = new Subscription[0];

    private final ClassValue<AnnotatedHandler[]> handlers = new ClassValue<>() {
        @Override
        protected AnnotatedHandler[] computeValue(Class<?> type) {
            return HandlerScanner.scan(type);
        }
    };
    private final Map<Object, Subscription[]> subscriptionsByTarget = new IdentityHashMap<>();

    synchronized void register(Object target) {
        Objects.requireNonNull(target, "target");
        if (subscriptionsByTarget.containsKey(target)) {
            return;
        }
        subscriptionsByTarget.put(target, subscribeAll(target));
    }

    synchronized void unregister(Object target) {
        Objects.requireNonNull(target, "target");
        Subscription[] subscriptions = subscriptionsByTarget.remove(target);
        if (subscriptions == null) {
            return;
        }
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
    }

    private Subscription[] subscribeAll(Object target) {
        AnnotatedHandler[] targetHandlers = handlers.get(target.getClass());
        if (targetHandlers.length == 0) {
            return NO_SUBSCRIPTIONS;
        }

        Map<FloraBus<Object>, List<Listener<Object>>> listenersByBus = new IdentityHashMap<>();
        for (AnnotatedHandler handler : targetHandlers) {
            listenersByBus.computeIfAbsent(handler.bus(), ignored -> new ArrayList<>()).add(handler.bind(target));
        }

        Subscription[] subscriptions = new Subscription[listenersByBus.size()];
        int subscribedCount = 0;
        try {
            for (Map.Entry<FloraBus<Object>, List<Listener<Object>>> entry : listenersByBus.entrySet()) {
                subscriptions[subscribedCount] = entry.getKey().subscribeAll(entry.getValue());
                subscribedCount++;
            }
            return subscriptions;
        } catch (RuntimeException | Error failure) {
            rollback(subscriptions, subscribedCount, failure);
            throw failure;
        }
    }

    private static void rollback(Subscription[] subscriptions, int subscribedCount, Throwable failure) {
        while (subscribedCount > 0) {
            try {
                subscriptions[--subscribedCount].unsubscribe();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }
}
