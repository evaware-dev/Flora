package benchmark.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import sweetie.evaware.flora.api.DispatchConfig;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class FloraAsyncJmhBenchmark {
    private DispatchEngine engine;
    private FloraBus<Event> bus;
    private Event event;

    @Setup(Level.Trial)
    public void setup() {
        engine = new DispatchEngine(DispatchConfig.defaults());
        bus = new FloraBus<>(engine);
        bus.subscribe(new Listener<>(0, ignored -> {
        }, DispatchMode.ASYNC));
        event = new Event();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        engine.awaitQuiescence(10, TimeUnit.SECONDS);
        engine.shutdown();
    }

    @Benchmark
    @Threads(1)
    public void oneProducer() {
        bus.post(event);
    }

    @Benchmark
    @Threads(4)
    public void fourProducers() {
        bus.post(event);
    }

    public static final class Event {
    }
}
