package sweetie.evaware.flora;

import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.internal.AnnotationScanner;
import sweetie.evaware.flora.internal.Hierarchy;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class FloraConfigurator {
    private static final AtomicLong EPOCH = new AtomicLong(1);
    private static final Map<Class<?>, Predicate<Object>> CANCELLERS = new ConcurrentHashMap<>();
    private static final Set<Class<? extends Annotation>> ANNOTATIONS = ConcurrentHashMap.newKeySet();
    private static volatile Consumer<Throwable> exceptionHandler = Throwable::printStackTrace;
    private static volatile ClassValue<Predicate<Object>> cancellerCache = createCancellerCache();

    static {
        ANNOTATIONS.add(Commando.class);
    }

    private FloraConfigurator() {
    }

    @SuppressWarnings("unchecked")
    public static <T> void registerCancellation(Class<T> type, Predicate<T> predicate) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(predicate, "predicate");
        CANCELLERS.put(type, (Predicate<Object>) predicate);
        cancellerCache = createCancellerCache();
        EPOCH.incrementAndGet();
    }

    public static Predicate<Object> getCanceller(Class<?> type) {
        return cancellerCache.get(type);
    }

    public static void registerAnnotation(Class<? extends Annotation> annotationType) {
        Objects.requireNonNull(annotationType, "annotationType");
        ANNOTATIONS.add(annotationType);
        AnnotationScanner.invalidate();
        EPOCH.incrementAndGet();
    }

    public static Set<Class<? extends Annotation>> getRegisteredAnnotations() {
        return ANNOTATIONS;
    }

    public static void setExceptionHandler(Consumer<Throwable> handler) {
        exceptionHandler = handler;
    }

    public static void setErrorHandler(Consumer<Throwable> handler) {
        setExceptionHandler(handler);
    }

    public static Consumer<Throwable> getExceptionHandler() {
        return exceptionHandler;
    }

    public static void setFailFast(boolean failFast) {
        exceptionHandler = failFast ? null : Throwable::printStackTrace;
    }

    public static long getEpoch() {
        return EPOCH.get();
    }

    public static void invalidate() {
        EPOCH.incrementAndGet();
    }

    private static ClassValue<Predicate<Object>> createCancellerCache() {
        return new ClassValue<>() {
            @Override
            protected Predicate<Object> computeValue(Class<?> type) {
                for (Class<?> candidate : Hierarchy.get(type)) {
                    Predicate<Object> found = CANCELLERS.get(candidate);
                    if (found != null) return found;
                }
                return null;
            }
        };
    }
}
