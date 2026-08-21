package sweetie.evaware.flora;

import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloraSubscriptionTest {
    @Test
    void subscribesLambdaToCanonicalBusWithoutAnnotations() {
        AtomicInteger calls = new AtomicInteger();
        Subscription subscription = Flora.subscribe(LambdaEvent.class, event -> calls.addAndGet(event.value()));

        Flora.post(new LambdaEvent(3));
        subscription.close();
        subscription.close();
        Flora.post(new LambdaEvent(7));

        assertEquals(3, calls.get());
    }

    @Test
    void supportsPriorityAndDispatchModeOverloads() {
        AtomicInteger calls = new AtomicInteger();
        Subscription subscription = Flora.subscribe(AsyncLambdaEvent.class, 10, DispatchMode.ASYNC,
                event -> calls.incrementAndGet());

        Flora.post(new AsyncLambdaEvent());

        assertTrue(Flora.awaitQuiescence(5, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
        subscription.unsubscribe();
    }

    @Test
    void lambdaAndAnnotatedHandlersShareCanonicalBus() {
        MixedHandler handler = new MixedHandler();
        AtomicInteger lambdaCalls = new AtomicInteger();
        Subscription subscription = Flora.subscribe(MixedEvent.class, event -> lambdaCalls.incrementAndGet());

        try {
            Flora.register(handler);
            Flora.post(new MixedEvent());

            assertEquals(1, handler.calls);
            assertEquals(1, lambdaCalls.get());
        } finally {
            Flora.unregister(handler);
            subscription.unsubscribe();
        }
    }

    private record LambdaEvent(int value) {
    }

    private static final class AsyncLambdaEvent {
    }

    private static final class MixedEvent {
    }

    private static final class MixedHandler {
        private int calls;

        @Commando
        private void onEvent(MixedEvent event) {
            calls++;
        }
    }
}
