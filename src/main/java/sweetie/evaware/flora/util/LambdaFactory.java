package sweetie.evaware.flora.util;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class LambdaFactory {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final ClassValue<MethodHandles.Lookup> PRIVATE_LOOKUPS = new ClassValue<>() {
        @Override
        protected MethodHandles.Lookup computeValue(Class<?> type) {
            try {
                return MethodHandles.privateLookupIn(type, LOOKUP);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Flora: Unable to access " + type.getName(), e);
            }
        }
    };
    private static final ConcurrentHashMap<Method, MethodHandle> FACTORIES = new ConcurrentHashMap<>();

    private LambdaFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> create(Object instance, Method method) {
        try {
            return (Consumer<T>) FACTORIES.computeIfAbsent(method, LambdaFactory::createFactory).invokeExact((Object) instance);
        } catch (Throwable e) {
            throw new RuntimeException("Flora: Unable to bind " + method.getName(), e);
        }
    }

    private static MethodHandle createFactory(Method method) {
        try {
            Class<?> owner = method.getDeclaringClass();
            Class<?> eventType = method.getParameterTypes()[0];
            MethodHandles.Lookup lookup = PRIVATE_LOOKUPS.get(owner);
            MethodHandle target = lookup.unreflect(method);
            CallSite site = LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    MethodType.methodType(Consumer.class, owner),
                    MethodType.methodType(void.class, Object.class),
                    target,
                    MethodType.methodType(void.class, eventType)
            );
            return site.getTarget().asType(MethodType.methodType(Consumer.class, Object.class));
        } catch (Throwable e) {
            throw new IllegalStateException("Flora: Unable to create factory for " + method.getName(), e);
        }
    }
}
