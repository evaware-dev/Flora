package sweetie.evaware.flora.internal;

import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.Config;
import sweetie.evaware.flora.api.DispatchMode;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class AnnotationScanner {
    private static final Comparator<Method> METHOD_ORDER = Comparator
            .comparing(Method::getName)
            .thenComparing(m -> Arrays.toString(m.getParameterTypes()));
    private static final Comparator<Class<?>> TYPE_ORDER = Comparator.comparing(Class::getName);

    private final Config config;
    private volatile long seenAnnotationEpoch;
    private volatile ClassValue<ScanResult> instanceCache;
    private volatile ClassValue<ScanResult> staticCache;

    public AnnotationScanner(Config config) {
        this.config = Objects.requireNonNull(config, "config");
        this.seenAnnotationEpoch = config.annotationEpoch();
        this.instanceCache = createCache(false);
        this.staticCache = createCache(true);
    }

    public ScanResult getInstanceScan(Class<?> type) {
        ensureFreshCaches();
        return instanceCache.get(type);
    }

    public ScanResult getStaticScan(Class<?> type) {
        ensureFreshCaches();
        return staticCache.get(type);
    }

    private void ensureFreshCaches() {
        long current = config.annotationEpoch();
        if (current != seenAnnotationEpoch) {
            synchronized (this) {
                if (current != seenAnnotationEpoch) {
                    instanceCache = createCache(false);
                    staticCache = createCache(true);
                    seenAnnotationEpoch = current;
                }
            }
        }
    }

    private ClassValue<ScanResult> createCache(boolean staticOnly) {
        return new ClassValue<>() {
            @Override
            protected ScanResult computeValue(Class<?> type) {
                return scan(type, staticOnly);
            }
        };
    }

    private ScanResult scan(Class<?> target, boolean staticOnly) {
        List<AnnotatedMethod> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> c = target; c != null && c != Object.class; c = c.getSuperclass()) {
            scanMethods(c, list, seen, staticOnly);
        }
        if (!staticOnly) {
            scanInterfaces(target, list, seen, new HashSet<>());
        }
        AnnotatedMethod[] methods = list.toArray(AnnotatedMethod[]::new);
        return new ScanResult(methods, hasDuplicateEventTypes(methods));
    }

    private static boolean hasDuplicateEventTypes(AnnotatedMethod[] methods) {
        int len = methods.length;
        if (len <= 1) return false;
        for (int i = 0; i < len - 1; i++) {
            Class<?> type = methods[i].eventType();
            for (int j = i + 1; j < len; j++) {
                if (methods[j].eventType() == type) {
                    return true;
                }
            }
        }
        return false;
    }

    private void scanInterfaces(Class<?> target, List<AnnotatedMethod> list, Set<String> seen, Set<Class<?>> visited) {
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

    private void scanMethods(Class<?> type, List<AnnotatedMethod> list, Set<String> seen, boolean staticOnly) {
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

    private Annotation findAnnotation(Method method) {
        for (Class<? extends Annotation> anno : config.registeredAnnotations()) {
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

    public record ScanResult(AnnotatedMethod[] methods, boolean hasDuplicateEventTypes) {
    }

    public static final class AnnotatedMethod {
        private final Method method;
        private final Class<?> eventType;
        private final int priority;
        private final DispatchMode mode;
        private volatile MethodHandle factory;

        public AnnotatedMethod(Method method, Class<?> eventType, int priority, DispatchMode mode) {
            this.method = method;
            this.eventType = eventType;
            this.priority = priority;
            this.mode = mode;
        }

        public Method method() {
            return method;
        }

        public Class<?> eventType() {
            return eventType;
        }

        public int priority() {
            return priority;
        }

        public DispatchMode mode() {
            return mode;
        }

        @SuppressWarnings("unchecked")
        public Consumer<Object> createConsumer(Object instance) {
            try {
                MethodHandle f = this.factory;
                if (f == null) {
                    f = LambdaFactory.getFactory(method);
                    this.factory = f;
                }
                return (Consumer<Object>) f.invokeExact(instance);
            } catch (Throwable failure) {
                if (failure instanceof RuntimeException re) throw re;
                if (failure instanceof Error err) throw err;
                throw new IllegalStateException("Unable to bind " + method.getName(), failure);
            }
        }
    }
}
