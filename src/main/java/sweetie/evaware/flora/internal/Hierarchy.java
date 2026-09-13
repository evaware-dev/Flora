package sweetie.evaware.flora.internal;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Hierarchy {
    private static final ClassValue<Class<?>[]> CACHE = new ClassValue<>() {
        @Override
        protected Class<?>[] computeValue(Class<?> type) {
            LinkedHashSet<Class<?>> types = new LinkedHashSet<>();
            traverse(type, types);
            return types.toArray(Class<?>[]::new);
        }
    };

    private Hierarchy() {
    }

    public static Class<?>[] get(Class<?> type) {
        return CACHE.get(type);
    }

    private static void traverse(Class<?> type, Set<Class<?>> result) {
        if (type == null || !result.add(type)) {
            return;
        }
        for (Class<?> iface : type.getInterfaces()) {
            traverse(iface, result);
        }
        traverse(type.getSuperclass(), result);
    }
}
