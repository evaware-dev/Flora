package polygone;

import sweetie.evaware.Event;
import sweetie.evaware.EventListener;
import sweetie.evaware.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class BusExample {
    private static final boolean BENCHMARK_ASYNC = true;
    private static final boolean COMPLEX_MATH = true;

    public static class MeowEvent extends Event<MeowEvent> {
        public static final MeowEvent BUS = new MeowEvent();
        public AtomicInteger counter = new AtomicInteger(0);
    }

    private static final List<EventListener> listenersList = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        int events = COMPLEX_MATH ? 5_000 : 100_000;
        int listeners = 100;
        int runs = 10;

        System.out.println("========================================");
        System.out.println("Mode: " + (BENCHMARK_ASYNC ? "ASYNC (Threaded)" : "SYNC (Blocking)"));
        System.out.println("Workload: " + (COMPLEX_MATH ? "HEAVY MATH" : "SIMPLE INCREMENT"));
        System.out.println("Events: " + events + " | Listeners: " + listeners);
        System.out.println("Total ops: " + (long)events * listeners);
        System.out.println("========================================");

        System.out.println("Warming up...");
        for (int i = 0; i < 3; i++) benchmark(events, listeners, -1);

        System.out.println("Starting benchmark...");

        double totalNsPerOp = 0;

        for (int runIndex = 0; runIndex < runs; runIndex++) {
            double[] results = benchmark(events, listeners, runIndex);
            totalNsPerOp += results[1];
        }

        System.out.println("----------------------------------------");
        System.out.printf("Average: %.6f ms total%n", totalNsPerOp / runs);
    }

    private static double[] benchmark(int events, int listeners, int runIndex) throws InterruptedException {
        listenersList.clear();
        MeowEvent.BUS.counter.set(0);

        long totalOps = (long) events * listeners;
        CountDownLatch latch = new CountDownLatch((int) totalOps);

        for (int i = 0; i < listeners; i++) {
            Listener<MeowEvent> listener = new Listener<>(0, BENCHMARK_ASYNC, event -> {

                if (COMPLEX_MATH) {
                    double result = 0;
                    for (int j = 0; j < 800; j++) {
                        result += Math.sin(j) * Math.sqrt(j);
                    }
                    if (result > 0) event.counter.incrementAndGet();
                } else {
                    event.counter.incrementAndGet();
                }

                latch.countDown();
            });
            listenersList.add(MeowEvent.BUS.subscribe(listener));
        }

        long start = System.nanoTime();

        for (int i = 0; i < events; i++) {
            MeowEvent.BUS.call();
        }

        latch.await();

        long elapsedNs = System.nanoTime() - start;

        listenersList.forEach(EventListener::unsubscribe);
        listenersList.clear();

        double elapsedMs = elapsedNs / 1_000_000.0;
        double nsPerOp = (double) elapsedNs / totalOps;

        if (runIndex >= 0) {
            System.out.printf(
                    "Run %2d: %8.3f ms, (ns/op): %8.3f%n",
                    (runIndex + 1), elapsedMs, nsPerOp
            );
        }

        return new double[]{elapsedMs, nsPerOp};
    }
}