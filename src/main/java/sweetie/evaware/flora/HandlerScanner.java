package sweetie.evaware.flora;

import sweetie.evaware.flora.api.Commando;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HandlerScanner {
    private static final Comparator<Method> METHOD_ORDER = Comparator
            .comparing(Method::getName)
            .thenComparing(method -> Arrays.toString(method.getParameterTypes()));
    private static final Comparator<Class<?>> TYPE_ORDER = Comparator.comparing(Class::getName);

    private HandlerScanner() {
    }

    static AnnotatedHandler[] scan(Class<?> targetType) {
        List<AnnotatedHandler> handlers = new ArrayList<>();
        Set<MethodSignature> seenSignatures = new HashSet<>();
        scanClasses(targetType, handlers, seenSignatures);
        scanInterfaces(targetType, handlers, seenSignatures, new HashSet<>());
        return handlers.toArray(AnnotatedHandler[]::new);
    }

    private static void scanClasses(Class<?> targetType, List<AnnotatedHandler> handlers,
                                    Set<MethodSignature> seenSignatures) {
        for (Class<?> type = targetType; type != null && type != Object.class; type = type.getSuperclass()) {
            scanDeclaredMethods(type, handlers, seenSignatures);
        }
    }

    private static void scanInterfaces(Class<?> targetType, List<AnnotatedHandler> handlers,
                                       Set<MethodSignature> seenSignatures, Set<Class<?>> visitedInterfaces) {
        for (Class<?> type = targetType; type != null; type = type.getSuperclass()) {
            Class<?>[] interfaces = type.getInterfaces();
            Arrays.sort(interfaces, TYPE_ORDER);
            for (Class<?> interfaceType : interfaces) {
                scanInterface(interfaceType, handlers, seenSignatures, visitedInterfaces);
            }
        }
    }

    private static void scanInterface(Class<?> interfaceType, List<AnnotatedHandler> handlers,
                                      Set<MethodSignature> seenSignatures, Set<Class<?>> visitedInterfaces) {
        if (!visitedInterfaces.add(interfaceType)) {
            return;
        }
        scanDeclaredMethods(interfaceType, handlers, seenSignatures);
        Class<?>[] parents = interfaceType.getInterfaces();
        Arrays.sort(parents, TYPE_ORDER);
        for (Class<?> parent : parents) {
            scanInterface(parent, handlers, seenSignatures, visitedInterfaces);
        }
    }

    private static void scanDeclaredMethods(Class<?> type, List<AnnotatedHandler> handlers,
                                            Set<MethodSignature> seenSignatures) {
        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, METHOD_ORDER);
        for (Method method : methods) {
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            Commando annotation = method.getAnnotation(Commando.class);
            if (annotation != null) {
                validateHandler(method);
            }
            if (!isOverrideCandidate(method)) {
                continue;
            }
            MethodSignature signature = new MethodSignature(method.getName(), method.getParameterTypes()[0]);
            if (seenSignatures.add(signature)) {
                createHandler(method, annotation, handlers);
            }
        }
    }

    private static boolean isOverrideCandidate(Method method) {
        return method.getParameterCount() == 1;
    }

    private static void validateHandler(Method method) {
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException("Flora: @Commando method must have exactly one event parameter: " + method);
        }
        if (method.getParameterTypes()[0].isPrimitive()) {
            throw new IllegalArgumentException("Flora: event parameter must be a reference type: " + method);
        }
    }

    private static void createHandler(Method method, Commando annotation, List<AnnotatedHandler> handlers) {
        if (annotation == null) {
            return;
        }

        Class<?> eventType = method.getParameterTypes()[0];
        handlers.add(new AnnotatedHandler(method, eventType, annotation.priority(), annotation.mode()));
    }

    private record MethodSignature(String name, Class<?> eventType) {
    }
}
