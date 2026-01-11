package sweetie.evaware.internal;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public class LambdaFactory {
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> create(Object instance, Method method, Class<T> eventType) {
        try {
            MethodHandles.Lookup caller = MethodHandles.privateLookupIn(instance.getClass(), lookup);
            MethodHandle handle = caller.unreflect(method);

            CallSite site = LambdaMetafactory.metafactory(
                    caller,
                    "accept",
                    MethodType.methodType(Consumer.class, instance.getClass()),
                    MethodType.methodType(void.class, Object.class),
                    handle,
                    MethodType.methodType(void.class, eventType)
            );

            return (Consumer<T>) site.getTarget().invoke(instance);
        } catch (Throwable e) {
            throw new RuntimeException("Flora: Failed to create lambda for " + method.getName(), e);
        }
    }
}