package sweetie.evaware.util;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public class LambdaFactory {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> create(Object instance, Method method, Class<T> eventType) {
        try {
            // Unreflect: Converts Method to MethodHandle
            // privateLookupIn: Access private methods in other classes
            MethodHandles.Lookup caller = MethodHandles.privateLookupIn(instance.getClass(), LOOKUP);
            MethodHandle handle = caller.unreflect(method);

            // Metafactory: Compiles the handle into a functional interface (fast as direct call)
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
            throw new RuntimeException("Flora: Unable to bind " + method.getName(), e);
        }
    }
}