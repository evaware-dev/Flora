package sweetie.evaware.flora.internal;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class LambdaFactory {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final ClassValue<MethodHandles.Lookup> LOOKUPS = new ClassValue<>() {
        @Override
        protected MethodHandles.Lookup computeValue(Class<?> type) {
            try {
                return MethodHandles.privateLookupIn(type, LOOKUP);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to access " + type.getName(), e);
            }
        }
    };

    private static final ClassValue<ConcurrentHashMap<Method, MethodHandle>> FACTORIES = new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<Method, MethodHandle> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private LambdaFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> createConsumer(Object instance, Method method) {
        try {
            MethodHandle factory = FACTORIES.get(method.getDeclaringClass())
                    .computeIfAbsent(method, LambdaFactory::buildFactory);
            return (Consumer<T>) factory.invokeExact(instance);
        } catch (Throwable failure) {
            if (failure instanceof RuntimeException re) throw re;
            if (failure instanceof Error err) throw err;
            throw new IllegalStateException("Unable to bind " + method.getName(), failure);
        }
    }

    private static MethodHandle buildFactory(Method method) {
        try {
            Class<?> owner = method.getDeclaringClass();
            Class<?> eventType = method.getParameterTypes()[0];
            MethodHandles.Lookup lookup = LOOKUPS.get(owner);
            MethodHandle target = lookup.unreflect(method);
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            MethodType invokedType = isStatic
                    ? MethodType.methodType(Consumer.class)
                    : MethodType.methodType(Consumer.class, owner);
            CallSite site = LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    invokedType,
                    MethodType.methodType(void.class, Object.class),
                    target,
                    MethodType.methodType(void.class, eventType)
            );
            MethodHandle factory = site.getTarget();
            if (isStatic) {
                factory = MethodHandles.dropArguments(factory, 0, Object.class);
            }
            return factory.asType(MethodType.methodType(Consumer.class, Object.class));
        } catch (Throwable failure) {
            if (failure instanceof RuntimeException re) throw re;
            if (failure instanceof Error err) throw err;
            throw new IllegalStateException("Unable to create factory for " + method.getName(), failure);
        }
    }
}
