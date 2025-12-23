package polygone;

import sweetie.evaware.Event;
import sweetie.evaware.EventListener;
import sweetie.evaware.Listener;

import java.util.ArrayList;
import java.util.List;

public class BusExample {
    public static class MeowEvent extends Event<MeowEvent> {
        public static final MeowEvent BUS = new MeowEvent();
    }

    private static final List<EventListener> listenersList = new ArrayList<>();

    public static void main(String[] args) {
        int events = 1_000_000;
        int listeners = 100;
        int runs = 25;

        double totalTimeMs = 0.0;
        double totalNsPerOp = 0.0;

        for (int runIndex = 0; runIndex < runs; runIndex++) {
            double[] results = benchmark(events, listeners);
            double durationMs = results[0];
            double nsPerOp = results[1];

            System.out.printf(
                    "Run %2d: completed %,d events & %,d listeners in %.3f ms, (ns/op): %.6f%n",
                    (runIndex + 1), events, listeners, durationMs, nsPerOp
            );

            totalTimeMs += durationMs;
            totalNsPerOp += nsPerOp;
        }

        double avgTimeMs = totalTimeMs / runs;
        double avgNsPerOp = totalNsPerOp / runs;

        System.out.printf(
                "%nAverage over %d runs: %.3f ms total, %.6f ns/op%n",
                runs, avgTimeMs, avgNsPerOp
        );
    }

    private static double[] benchmark(int events, int listeners) {
        listenersList.clear();

        for (int i = 0; i < listeners; i++) {
            Listener<MeowEvent> listener = new Listener<>(event -> {});
            listenersList.add(MeowEvent.BUS.subscribe(listener));
        }

        long start = System.nanoTime();

        for (int i = 0; i < events; i++) {
            MeowEvent.BUS.call();
        }

        long elapsedNs = System.nanoTime() - start;

        listenersList.forEach(EventListener::unsubscribe);
        listenersList.clear();

        double elapsedMs = elapsedNs / 1_000_000.0;
        double totalCalls = (double) events * listeners;
        double nsPerOp = elapsedNs / totalCalls;

        return new double[]{elapsedMs, nsPerOp};
    }
}
