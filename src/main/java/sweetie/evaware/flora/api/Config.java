package sweetie.evaware.flora.api;

import sweetie.evaware.flora.internal.Hierarchy;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class Config {
    private final AtomicLong epoch = new AtomicLong(1);
    private final AtomicLong annotationEpoch = new AtomicLong(1);
    private final Map<Class<?>, Predicate<Object>> cancellers = new ConcurrentHashMap<>();
    private final Set<Class<? extends Annotation>> annotations = ConcurrentHashMap.newKeySet();

    private volatile Consumer<Throwable> exceptionHandler = Throwable::printStackTrace;
    private volatile boolean hasCancellers;
    private volatile ClassValue<Predicate<Object>> cancellerCache = createCancellerCache();

    public Config() {
        annotations.add(Commando.class);
    }

    @SuppressWarnings("unchecked")
    public <T> Config registerCancellation(Class<T> type, Predicate<T> predicate) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(predicate, "predicate");
        cancellers.put(type, (Predicate<Object>) predicate);
        cancellerCache = createCancellerCache();
        hasCancellers = true;
        epoch.incrementAndGet();
        return this;
    }

    public Predicate<Object> canceller(Class<?> type) {
        if (!hasCancellers) {
            return null;
        }
        return cancellerCache.get(type);
    }

    public Config registerAnnotation(Class<? extends Annotation> annotationType) {
        Objects.requireNonNull(annotationType, "annotationType");
        annotations.add(annotationType);
        annotationEpoch.incrementAndGet();
        epoch.incrementAndGet();
        return this;
    }

    public Set<Class<? extends Annotation>> registeredAnnotations() {
        return Collections.unmodifiableSet(annotations);
    }

    public Config exceptionHandler(Consumer<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    public Consumer<Throwable> exceptionHandler() {
        return exceptionHandler;
    }

    public Config failFast(boolean failFast) {
        this.exceptionHandler = failFast ? null : Throwable::printStackTrace;
        return this;
    }

    public long epoch() {
        return epoch.get();
    }

    public long annotationEpoch() {
        return annotationEpoch.get();
    }

    public void invalidate() {
        epoch.incrementAndGet();
    }

    private ClassValue<Predicate<Object>> createCancellerCache() {
        return new ClassValue<>() {
            @Override
            protected Predicate<Object> computeValue(Class<?> type) {
                for (Class<?> candidate : Hierarchy.get(type)) {
                    Predicate<Object> found = cancellers.get(candidate);
                    if (found != null) {
                        return found;
                    }
                }
                return null;
            }
        };
    }
}
