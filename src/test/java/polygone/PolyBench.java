package polygone;

import sweetie.evaware.Event;
import sweetie.evaware.EventListener;
import sweetie.evaware.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public class PolyBench {
    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASURE_ITERATIONS = 10;

    private static final int SYNC_EVENTS = 50_000;
    private static final int ASYNC_EVENTS = 100_000;

    private static final int LISTENERS_COUNT = 15;

    public static class BenchEvent extends Event<BenchEvent> {
        public static final BenchEvent INSTANCE = new BenchEvent();
        public final LongAdder counter = new LongAdder();
    }

    public enum BenchMode {
        SYNC_LIGHT("Sync / Increment", false, e -> e.counter.increment()),

        SYNC_HEAVY("Sync / Math", false, e -> {
            double res = 0;
            for (int i = 0; i < 100; i++) res += Math.sin(i) * Math.sqrt(i);
            e.counter.increment();
        }),

        ASYNC_LIGHT("Async / Increment", true, e -> e.counter.increment()),

        ASYNC_HEAVY("Async / Math", true, e -> {
            double res = 0;
            for (int i = 0; i < 100; i++) res += Math.sin(i) * Math.sqrt(i);
            e.counter.increment();
        });

        final String title;
        final boolean isAsync;
        final Consumer<BenchEvent> task;

        BenchMode(String title, boolean isAsync, Consumer<BenchEvent> task) {
            this.title = title;
            this.isAsync = isAsync;
            this.task = task;
        }
    }

    public static void main(String[] args) {
        for (BenchMode mode : BenchMode.values()) {
            runScenario(mode);
        }
    }

    private static void runScenario(BenchMode mode) {
        int eventsCount = mode.isAsync ? ASYNC_EVENTS : SYNC_EVENTS;
        long totalOps = (long) eventsCount * LISTENERS_COUNT;

        System.out.printf("%nRunning mode: [%s]%n", mode.title);
        System.out.printf("Events: %,d | Ops/Run: %,d%n", eventsCount, totalOps);

        List<Double> results = new ArrayList<>();

        try {
            System.out.print("Warmup... ");
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                performRun(mode, eventsCount, false);
            }
            System.out.println("Done.");

            System.out.println("Measuring...");
            for (int i = 0; i < MEASURE_ITERATIONS; i++) {
                System.gc();
                Thread.sleep(20);

                double ms = performRun(mode, eventsCount, true);
                results.add(ms);
                System.out.printf("   Run %2d: %8.3f ms%n", i + 1, ms);
            }

            double avgMs = results.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double opsPerSec = totalOps / (avgMs / 1000.0);

            System.out.println("-----------------------------------------------------");
            System.out.printf("RESULT [%s]:%n", mode.title);
            System.out.printf("Average Time:   %8.3f ms%n", avgMs);
            System.out.printf("Throughput:     %,15.0f ops/sec%n", opsPerSec);
            System.out.println("-----------------------------------------------------");

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static double performRun(BenchMode mode, int events, boolean validate) throws InterruptedException {
        List<EventListener> localTokens = new ArrayList<>();
        BenchEvent.INSTANCE.counter.reset();

        long totalExpectedOps = (long) events * LISTENERS_COUNT;
        CountDownLatch latch = new CountDownLatch(mode.isAsync ? (int) totalExpectedOps : 0);

        Consumer<BenchEvent> handler;
        if (mode.isAsync) {
            handler = event -> {
                mode.task.accept(event);
                latch.countDown();
            };
        } else {
            handler = mode.task;
        }

        try {
            for (int i = 0; i < LISTENERS_COUNT; i++) {
                Listener<BenchEvent> listener = new Listener<>(0, mode.isAsync, handler);
                localTokens.add(BenchEvent.INSTANCE.subscribe(listener));
            }

            long start = System.nanoTime();

            for (int i = 0; i < events; i++) {
                BenchEvent.INSTANCE.call();
            }

            if (mode.isAsync) {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Async timeout! Deadlock or logic error.");
                }
            }

            long end = System.nanoTime();

            if (validate) {
                long actual = BenchEvent.INSTANCE.counter.sum();
                if (actual != totalExpectedOps) {
                    System.err.printf("ERROR: Data loss! Expected %,d but got %,d%n", totalExpectedOps, actual);
                }
            }

            return (end - start) / 1_000_000.0;

        } finally {
            localTokens.forEach(EventListener::unsubscribe);
            localTokens.clear();
        }
    }
}