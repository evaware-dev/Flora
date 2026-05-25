package sweetie.evaware.flora.core.engine;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public final class DispatchEngine {
    private static final DispatchEngine DEFAULT = new DispatchEngine();

    private final AsyncLoop asyncLoop = new AsyncLoop();
    private final Executor parallelExecutor = ForkJoinPool.commonPool();

    private DispatchEngine() {
    }

    public static DispatchEngine defaultEngine() {
        return DEFAULT;
    }

    public <T> void dispatchAsync(T event, Consumer<T>[] consumers) {
        asyncLoop.execute(event, consumers);
    }

    public <T> void dispatchParallel(T event, Consumer<T>[] consumers) {
        for (Consumer<T> consumer : consumers) {
            parallelExecutor.execute(() -> dispatchSafely(consumer, event));
        }
    }

    public static <T> void dispatchSafely(Consumer<T> consumer, T event) {
        try {
            consumer.accept(event);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
