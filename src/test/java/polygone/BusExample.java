package polygone;

import sweetie.evaware.Event;
import sweetie.evaware.EventListener;
import sweetie.evaware.Listener;

import java.util.ArrayList;
import java.util.List;

public class BusExample {
    public static class MeowEvent extends Event<MeowEvent> {
        public static final MeowEvent BUS = new MeowEvent();

        public int counter = 0;
    }

    private static final List<EventListener> listenersList = new ArrayList<>();

    public static void main(String[] args) {
        int events = 1_000_000;
        int listeners = 100;
        int runs = 15;

        double totalTimeMs = 0.0;

        System.out.println("Warming up...");
        benchmark(events, listeners);
        benchmark(events, listeners);

        System.out.println("Starting benchmark...");

        for (int runIndex = 0; runIndex < runs; runIndex++) {
            double[] results = benchmark(events, listeners);
            double durationMs = results[0];
            double nsPerOp = results[1];

            System.out.printf(
                    "Run %2d: %.3f ms, (ns/op): %.6f%n",
                    (runIndex + 1), durationMs, nsPerOp
            );

            totalTimeMs += durationMs;
        }

        System.out.printf(
                "%nAverage: %.3f ms total%n",
                totalTimeMs / runs
        );
    }

    private static double[] benchmark(int events, int listeners) {
        listenersList.clear();
        MeowEvent.BUS.counter = 0;

        for (int i = 0; i < listeners; i++) {
            Listener<MeowEvent> listener = new Listener<>(event -> {
                event.counter++;
            });
            listenersList.add(MeowEvent.BUS.subscribe(listener));
        }

        long start = System.nanoTime();

        for (int i = 0; i < events; i++) {
            MeowEvent.BUS.call();
        }

        long elapsedNs = System.nanoTime() - start;

        if (MeowEvent.BUS.counter != events * listeners) {
            throw new RuntimeException("Math broken! JIT cheating???");
        }

        listenersList.forEach(EventListener::unsubscribe);
        listenersList.clear();

        double elapsedMs = elapsedNs / 1_000_000.0;
        double totalCalls = (double) events * listeners;
        double nsPerOp = elapsedNs / totalCalls;

        return new double[]{elapsedMs, nsPerOp};
    }
}