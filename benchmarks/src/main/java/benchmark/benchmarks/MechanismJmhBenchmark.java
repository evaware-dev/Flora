package benchmark.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import sweetie.evaware.flora.EventSystem;
import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchConfig;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.EventBus;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MechanismJmhBenchmark {

    public static final class SampleEvent {
        public int value;
    }

    public static final class AnnotatedTarget {
        @Commando
        public void onEvent(SampleEvent event) {
            event.value++;
        }
    }

    /**
     * Holds a cached direct EventBus reference (Flora.bus(...)).
     */
    public static final class DirectSampleEventBus {
        public final EventBus<SampleEvent> bus;
        private final EventSystem system;

        public DirectSampleEventBus(EventSystem system) {
            this.system = system;
            this.bus = system.bus(SampleEvent.class);
        }

        public void post(SampleEvent event) {
            system.post(event);
        }

        public void postDirect(SampleEvent event) {
            bus.post(event);
        }
    }

    @State(Scope.Thread)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    @Warmup(iterations = 3, time = 400, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
    public static class PostState {
        @Param({"1", "8"})
        public int listeners;

        private EventSystem lambdaSystem;
        private EventSystem annotationSystem;
        private EventSystem directSystem;
        private DirectSampleEventBus directBusHolder;
        private AnnotatedTarget[] plainTargets;
        private SampleEvent event;

        @Setup(Level.Trial)
        public void setup() {
            DispatchConfig minimal = new DispatchConfig(1, 1, 16, 16, 8, 4, 1_000L);
            event = new SampleEvent();

            lambdaSystem = EventSystem.create(minimal);
            for (int i = 0; i < listeners; i++) {
                lambdaSystem.subscribe(SampleEvent.class, e -> e.value++);
            }

            annotationSystem = EventSystem.create(minimal);
            for (int i = 0; i < listeners; i++) {
                annotationSystem.register(new AnnotatedTarget());
            }

            directSystem = EventSystem.create(minimal);
            directBusHolder = new DirectSampleEventBus(directSystem);
            for (int i = 0; i < listeners; i++) {
                directBusHolder.bus.subscribe(e -> e.value++);
            }

            plainTargets = new AnnotatedTarget[listeners];
            for (int i = 0; i < listeners; i++) {
                plainTargets[i] = new AnnotatedTarget();
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            lambdaSystem.shutdown();
            annotationSystem.shutdown();
            directSystem.shutdown();
        }

        @Benchmark
        public int postLambda() {
            event.value = 0;
            lambdaSystem.post(event);
            return event.value;
        }

        @Benchmark
        public int postAnnotation() {
            event.value = 0;
            annotationSystem.post(event);
            return event.value;
        }

        @Benchmark
        public int postDirectBusWrapper() {
            event.value = 0;
            directBusHolder.post(event);
            return event.value;
        }

        @Benchmark
        public int postDirectBus() {
            event.value = 0;
            directBusHolder.postDirect(event);
            return event.value;
        }

        // Lower bound: direct method calls bypass all event-system guarantees.
        @Benchmark
        public int postPlainMethodLoop() {
            event.value = 0;
            for (int i = 0; i < plainTargets.length; i++) {
                plainTargets[i].onEvent(event);
            }
            return event.value;
        }
    }

    @State(Scope.Thread)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    @Warmup(iterations = 3, time = 400, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
    public static class LifecycleState {
        private EventSystem lambdaSystem;
        private EventSystem annotationSystem;
        private EventSystem directSystem;
        private DirectSampleEventBus directBusHolder;
        private Consumer<SampleEvent> lambdaCallback;
        private AnnotatedTarget target;

        @Setup(Level.Trial)
        public void setup() {
            DispatchConfig minimal = new DispatchConfig(1, 1, 16, 16, 8, 4, 1_000L);
            lambdaSystem = EventSystem.create(minimal);
            annotationSystem = EventSystem.create(minimal);
            directSystem = EventSystem.create(minimal);
            directBusHolder = new DirectSampleEventBus(directSystem);

            lambdaCallback = e -> e.value++;
            target = new AnnotatedTarget();

            // Warm class caches before measuring steady-state lifecycle
            lambdaSystem.subscribe(SampleEvent.class, lambdaCallback).unsubscribe();
            annotationSystem.register(target);
            annotationSystem.unregister(target);
            directBusHolder.bus.subscribe(lambdaCallback).unsubscribe();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            lambdaSystem.shutdown();
            annotationSystem.shutdown();
            directSystem.shutdown();
        }

        @Benchmark
        public void cycleLambdaGlobal() {
            Subscription sub = lambdaSystem.subscribe(SampleEvent.class, lambdaCallback);
            sub.unsubscribe();
        }

        @Benchmark
        public void cycleDirectBus() {
            Subscription sub = directBusHolder.bus.subscribe(lambdaCallback);
            sub.unsubscribe();
        }

        @Benchmark
        public void cycleAnnotation() {
            annotationSystem.register(target);
            annotationSystem.unregister(target);
        }
    }

    @State(Scope.Thread)
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    @Warmup(iterations = 15, batchSize = 500)
    @Measurement(iterations = 30, batchSize = 500)
    public static class SplitLifecycleState {
        private static final int BATCH = 500;

        private EventSystem lambdaSystem;
        private EventSystem annotationSystem;
        private EventSystem directSystem;
        private DirectSampleEventBus directBusHolder;
        private Consumer<SampleEvent> lambdaCallback;
        private AnnotatedTarget[] targets;
        private Subscription[] lambdaSubs;
        private Subscription[] directSubs;
        private int cursor;

        @Setup(Level.Trial)
        public void setupTrial() {
            DispatchConfig minimal = new DispatchConfig(1, 1, 16, 16, 8, 4, 1_000L);
            lambdaSystem = EventSystem.create(minimal);
            annotationSystem = EventSystem.create(minimal);
            directSystem = EventSystem.create(minimal);
            directBusHolder = new DirectSampleEventBus(directSystem);

            lambdaCallback = e -> e.value++;
            targets = new AnnotatedTarget[BATCH];
            lambdaSubs = new Subscription[BATCH];
            directSubs = new Subscription[BATCH];
            for (int i = 0; i < BATCH; i++) {
                targets[i] = new AnnotatedTarget();
            }

            // Warm annotation scan & LambdaMetafactory
            annotationSystem.register(targets[0]);
            annotationSystem.unregister(targets[0]);
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() {
            lambdaSystem.shutdown();
            annotationSystem.shutdown();
            directSystem.shutdown();
        }

        @Setup(Level.Iteration)
        public void setupIteration() {
            cursor = 0;
        }

        @TearDown(Level.Iteration)
        public void tearDownIteration() {
            for (int i = 0; i < BATCH; i++) {
                if (lambdaSubs[i] != null) {
                    lambdaSubs[i].unsubscribe();
                    lambdaSubs[i] = null;
                }
                if (directSubs[i] != null) {
                    directSubs[i].unsubscribe();
                    directSubs[i] = null;
                }
                annotationSystem.unregister(targets[i]);
            }
        }

        @Benchmark
        public Subscription registerOnlyLambda() {
            Subscription s = lambdaSystem.subscribe(SampleEvent.class, lambdaCallback);
            lambdaSubs[cursor++] = s;
            return s;
        }

        @Benchmark
        public Subscription registerOnlyDirectBus() {
            Subscription s = directBusHolder.bus.subscribe(lambdaCallback);
            directSubs[cursor++] = s;
            return s;
        }

        @Benchmark
        public void registerOnlyAnnotation() {
            annotationSystem.register(targets[cursor++]);
        }
    }

    @State(Scope.Thread)
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    @Warmup(iterations = 15, batchSize = 500)
    @Measurement(iterations = 30, batchSize = 500)
    public static class UnregisterOnlyState {
        private static final int BATCH = 500;

        private EventSystem lambdaSystem;
        private EventSystem annotationSystem;
        private EventSystem directSystem;
        private DirectSampleEventBus directBusHolder;
        private Consumer<SampleEvent> lambdaCallback;
        private AnnotatedTarget[] targets;
        private Subscription[] lambdaSubs;
        private Subscription[] directSubs;
        private int cursor;

        @Setup(Level.Trial)
        public void setupTrial() {
            DispatchConfig minimal = new DispatchConfig(1, 1, 16, 16, 8, 4, 1_000L);
            lambdaSystem = EventSystem.create(minimal);
            annotationSystem = EventSystem.create(minimal);
            directSystem = EventSystem.create(minimal);
            directBusHolder = new DirectSampleEventBus(directSystem);

            lambdaCallback = e -> e.value++;
            targets = new AnnotatedTarget[BATCH];
            lambdaSubs = new Subscription[BATCH];
            directSubs = new Subscription[BATCH];
            for (int i = 0; i < BATCH; i++) {
                targets[i] = new AnnotatedTarget();
            }
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() {
            lambdaSystem.shutdown();
            annotationSystem.shutdown();
            directSystem.shutdown();
        }

        @Setup(Level.Iteration)
        public void setupIteration() {
            cursor = BATCH - 1;
            for (int i = 0; i < BATCH; i++) {
                lambdaSubs[i] = lambdaSystem.subscribe(SampleEvent.class, lambdaCallback);
                directSubs[i] = directBusHolder.bus.subscribe(lambdaCallback);
                annotationSystem.register(targets[i]);
            }
        }

        @Benchmark
        public void unregisterOnlyLambda() {
            lambdaSubs[cursor--].unsubscribe();
        }

        @Benchmark
        public void unregisterOnlyDirectBus() {
            directSubs[cursor--].unsubscribe();
        }

        @Benchmark
        public void unregisterOnlyAnnotation() {
            annotationSystem.unregister(targets[cursor--]);
        }
    }
}
