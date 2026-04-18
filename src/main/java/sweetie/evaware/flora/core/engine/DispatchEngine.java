package sweetie.evaware.flora.core.engine;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public final class DispatchEngine {
    private static final DispatchEngine DEFAULT = new DispatchEngine(new AsyncLoop(), ForkJoinPool.commonPool());

    private final AsyncLoop asyncLoop;
    private final Executor parallelExecutor;

    private DispatchEngine(AsyncLoop asyncLoop, Executor parallelExecutor) {
        this.asyncLoop = asyncLoop;
        this.parallelExecutor = parallelExecutor;
    }

    public static DispatchEngine defaultEngine() {
        return DEFAULT;
    }

    public <T> void dispatchAsync(T event, Consumer<T>[] consumers) {
        asyncLoop.execute(event, consumers);
    }

    public <T> void dispatchParallel(T event, Consumer<T>[] consumers) {
        for (int i = 0, length = consumers.length; i < length; i++) {
            parallelExecutor.execute(new ParallelDispatch<>(consumers[i], event));
        }
    }

    public static <T> void dispatchSafely(Consumer<T> consumer, T event) {
        try {
            consumer.accept(event);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private record ParallelDispatch<T>(Consumer<T> consumer, T event) implements Runnable {
        @Override
        public void run() {
            dispatchSafely(consumer, event);
        }
    }
}
