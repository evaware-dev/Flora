package sweetie.evaware.flora.core;

import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.DispatchConfig;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomDispatchEngineTest {
    @Test
    void busCanUseAnIsolatedConfiguredEngine() {
        DispatchConfig config = new DispatchConfig(1, 2, 16, 16, 8, 4, 1_000L);
        DispatchEngine engine = new DispatchEngine(config);
        FloraBus<Object> bus = new FloraBus<>(engine);
        AtomicInteger calls = new AtomicInteger();
        bus.subscribe(new Listener<>(0, ignored -> calls.incrementAndGet(), DispatchMode.ASYNC));

        bus.post(new Object());

        assertTrue(engine.awaitQuiescence(5, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
        engine.shutdown();
    }
}
