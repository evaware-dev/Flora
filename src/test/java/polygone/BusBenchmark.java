package polygone;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sweetie.evaware.Event;
import sweetie.evaware.EventListener;
import sweetie.evaware.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, warmups = 1)
public class BusBenchmark {

    @Param({"10"})
    private static int LISTENERS;

    @Param({"10000"})
    private static int EVENTS;

    @State(Scope.Thread)
    public static class FloraState {
        public Event<MeowEvent> bus = new Event<>();
        public List<EventListener> subscriptions;

        @Setup(Level.Trial)
        public void setup() {
            subscriptions = new ArrayList<>();
            for (int i = 0; i < LISTENERS; i++) {
                Listener<MeowEvent> listener = new Listener<>(event -> {});
                subscriptions.add(bus.subscribe(listener));
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            subscriptions.forEach(EventListener::unsubscribe);
        }
    }

    @Benchmark
    public void floraBenchmark(FloraState state) {
        for (int i = 0; i < EVENTS; i++) {
            state.bus.notify(new MeowEvent());
        }
    }

    public static class MeowEvent { }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BusBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}