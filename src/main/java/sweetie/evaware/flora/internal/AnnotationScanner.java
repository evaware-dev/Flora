package sweetie.evaware.flora.internal;

import sweetie.evaware.flora.FloraConfigurator;
import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchMode;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AnnotationScanner {
    private static final Comparator<Method> METHOD_ORDER = Comparator
            .comparing(Method::getName)
            .thenComparing(m -> Arrays.toString(m.getParameterTypes()));
    private static final Comparator<Class<?>> TYPE_ORDER = Comparator.comparing(Class::getName);

    private static volatile ClassValue<AnnotatedMethod[]> instanceMethods = createInstanceCache();
    private static volatile ClassValue<AnnotatedMethod[]> staticMethods = createStaticCache();

    private AnnotationScanner() {
    }

    public static AnnotatedMethod[] getInstanceMethods(Class<?> type) {
        return instanceMethods.get(type);
    }

    public static AnnotatedMethod[] getStaticMethods(Class<?> type) {
        return staticMethods.get(type);
    }

    public static void invalidate() {
        instanceMethods = createInstanceCache();
        staticMethods = createStaticCache();
    }

    private static ClassValue<AnnotatedMethod[]> createInstanceCache() {
        return new ClassValue<>() {
            @Override
            protected AnnotatedMethod[] computeValue(Class<?> type) {
                return scan(type, false);
            }
        };
    }

    private static ClassValue<AnnotatedMethod[]> createStaticCache() {
        return new ClassValue<>() {
            @Override
            protected AnnotatedMethod[] computeValue(Class<?> type) {
                return scan(type, true);
            }
        };
    }

    private static AnnotatedMethod[] scan(Class<?> target, boolean staticOnly) {
        List<AnnotatedMethod> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> c = target; c != null && c != Object.class; c = c.getSuperclass()) {
            scanMethods(c, list, seen, staticOnly);
        }
        if (!staticOnly) {
            scanInterfaces(target, list, seen, new HashSet<>());
        }
        return list.toArray(AnnotatedMethod[]::new);
    }

    private static void scanInterfaces(Class<?> target, List<AnnotatedMethod> list, Set<String> seen, Set<Class<?>> visited) {
        for (Class<?> c = target; c != null; c = c.getSuperclass()) {
            Class<?>[] ifaces = c.getInterfaces();
            Arrays.sort(ifaces, TYPE_ORDER);
            for (Class<?> iface : ifaces) {
                if (visited.add(iface)) {
                    scanMethods(iface, list, seen, false);
                    scanInterfaces(iface, list, seen, visited);
                }
            }
        }
    }

    private static void scanMethods(Class<?> type, List<AnnotatedMethod> list, Set<String> seen, boolean staticOnly) {
        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, METHOD_ORDER);
        for (Method m : methods) {
            if (m.isBridge() || m.isSynthetic()) continue;
            boolean isStatic = Modifier.isStatic(m.getModifiers());
            if (staticOnly && !isStatic) continue;

            Annotation match = findAnnotation(m);
            if (match != null) {
                if (m.getParameterCount() != 1) {
                    throw new IllegalArgumentException("Flora: listener method must have exactly one event parameter: " + m);
                }
                if (m.getParameterTypes()[0].isPrimitive()) {
                    throw new IllegalArgumentException("Flora: event parameter must be a reference type: " + m);
                }
            }

            if (m.getParameterCount() != 1) continue;
            String sig = m.getName() + ":" + m.getParameterTypes()[0].getName();
            if (seen.add(sig) && match != null) {
                int priority = extractPriority(match);
                DispatchMode mode = extractMode(match);
                list.add(new AnnotatedMethod(m, m.getParameterTypes()[0], priority, mode));
            }
        }
    }

    private static Annotation findAnnotation(Method method) {
        for (Class<? extends Annotation> anno : FloraConfigurator.getRegisteredAnnotations()) {
            Annotation a = method.getAnnotation(anno);
            if (a != null) return a;
        }
        return null;
    }

    private static int extractPriority(Annotation a) {
        if (a instanceof Commando c) return c.priority();
        try {
            Method m = a.annotationType().getMethod("priority");
            return ((Number) m.invoke(a)).intValue();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static DispatchMode extractMode(Annotation a) {
        if (a instanceof Commando c) return c.mode();
        try {
            Method m = a.annotationType().getMethod("mode");
            return (DispatchMode) m.invoke(a);
        } catch (Exception ignored) {
            return DispatchMode.SYNC;
        }
    }

    public record AnnotatedMethod(Method method, Class<?> eventType, int priority, DispatchMode mode) {
    }
}
