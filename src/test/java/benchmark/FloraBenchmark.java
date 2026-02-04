package benchmark;

import org.openjdk.jmh.infra.Blackhole;
import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;

public class FloraBenchmark implements IBenchmark {
    public static class TestEvent {
        public Blackhole value;
        public TestEvent(Blackhole value) {
            this.value = value;
        }
    }

    private static final TestEvent EVENT = new TestEvent(null);

    @Override
    public void prepare() {
        FloraBus<TestEvent> bus = Flora.getBus(TestEvent.class);
        for (int i = 0; i < Constants.LISTENERS; i++) {
            bus.subscribe(new Listener<>(0, event -> event.value.consume(Integer.bitCount(Integer.parseInt("123"))), DispatchMode.SYNC));
        }
    }

    @Override
    public void benchmark(Blackhole blackhole) {
        EVENT.value = blackhole;
        Flora.post(EVENT);
    }
}
