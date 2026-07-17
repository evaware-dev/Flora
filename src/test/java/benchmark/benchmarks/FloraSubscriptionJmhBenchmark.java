package benchmark.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class FloraSubscriptionJmhBenchmark {
    @Param({"1", "8", "64"})
    public int listeners;

    private FloraBus<Object> bus;
    private List<Listener<Object>> definitions;
    private Subscription[] subscriptions;

    @Setup
    public void setup() {
        bus = new FloraBus<>();
        definitions = new ArrayList<>(listeners);
        subscriptions = new Subscription[listeners];
        for (int index = 0; index < listeners; index++) {
            definitions.add(new Listener<>(index, ignored -> {
            }, DispatchMode.SYNC));
        }
    }

    @Benchmark
    public void batchSubscribeAndUnsubscribe() {
        Subscription subscription = bus.subscribeAll(definitions);
        subscription.unsubscribe();
    }

    @Benchmark
    public void individualSubscribeAndUnsubscribe() {
        for (int index = 0; index < listeners; index++) {
            subscriptions[index] = bus.subscribe(definitions.get(index));
        }
        for (int index = listeners - 1; index >= 0; index--) {
            subscriptions[index].unsubscribe();
        }
    }
}
