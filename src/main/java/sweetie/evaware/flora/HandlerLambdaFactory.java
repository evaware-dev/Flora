package sweetie.evaware.flora;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class HandlerLambdaFactory {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final ClassValue<MethodHandles.Lookup> PRIVATE_LOOKUPS = new ClassValue<>() {
        @Override
        protected MethodHandles.Lookup computeValue(Class<?> type) {
            try {
                return MethodHandles.privateLookupIn(type, LOOKUP);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Flora: unable to access " + type.getName(), exception);
            }
        }
    };
    private static final ClassValue<ConcurrentHashMap<Method, MethodHandle>> FACTORIES = new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<Method, MethodHandle> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private HandlerLambdaFactory() {
    }

    @SuppressWarnings("unchecked")
    static <T> Consumer<T> bind(Object instance, Method method) {
        try {
            MethodHandle factory = FACTORIES.get(method.getDeclaringClass())
                    .computeIfAbsent(method, HandlerLambdaFactory::createFactory);
            return (Consumer<T>) factory.invokeExact(instance);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Flora: unable to bind " + method.getName(), failure);
        }
    }

    private static MethodHandle createFactory(Method method) {
        try {
            Class<?> owner = method.getDeclaringClass();
            Class<?> eventType = method.getParameterTypes()[0];
            MethodHandles.Lookup lookup = PRIVATE_LOOKUPS.get(owner);
            MethodHandle target = lookup.unreflect(method);
            boolean staticMethod = Modifier.isStatic(method.getModifiers());
            MethodType invokedType = staticMethod
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
            if (staticMethod) {
                factory = MethodHandles.dropArguments(factory, 0, Object.class);
            }
            return factory.asType(MethodType.methodType(Consumer.class, Object.class));
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Flora: unable to create handler for " + method.getName(), failure);
        }
    }
}
