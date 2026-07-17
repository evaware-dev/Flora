package sweetie.evaware.flora.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class FloraBusTest {
    @AfterEach
    void restoreErrorHandler() {
        Flora.setErrorHandler(Throwable::printStackTrace);
    }

    @Test
    void dispatchesByPriorityAndPreservesOrderForEqualPriority() {
        FloraBus<Object> bus = new FloraBus<>();
        List<Integer> calls = new ArrayList<>();

        bus.subscribe(new Listener<>(1, ignored -> calls.add(1), DispatchMode.SYNC));
        bus.subscribe(new Listener<>(10, ignored -> calls.add(10), DispatchMode.SYNC));
        bus.subscribe(new Listener<>(10, ignored -> calls.add(11), DispatchMode.SYNC));
        bus.subscribe(new Listener<>(-1, ignored -> calls.add(-1), DispatchMode.SYNC));

        bus.post(new Object());

        assertEquals(List.of(10, 11, 1, -1), calls);
    }

    @Test
    void subscriptionHandleIsIdentityBasedAndIdempotent() {
        FloraBus<Object> bus = new FloraBus<>();
        AtomicInteger calls = new AtomicInteger();
        Consumer<Object> callback = ignored -> calls.incrementAndGet();
        Listener<Object> listener = new Listener<>(callback);

        Subscription first = bus.subscribe(listener);
        Subscription second = bus.subscribe(listener);
        first.unsubscribe();
        first.unsubscribe();

        bus.post(new Object());
        assertEquals(1, calls.get());

        second.unsubscribe();
        bus.post(new Object());
        assertEquals(1, calls.get());
    }

    @Test
    void batchSubscriptionPublishesAndRemovesOneSnapshot() {
        FloraBus<Object> bus = new FloraBus<>();
        List<Integer> calls = new ArrayList<>();
        Subscription subscription = bus.subscribeAll(List.of(
                new Listener<>(1, ignored -> calls.add(1), DispatchMode.SYNC),
                new Listener<>(10, ignored -> calls.add(10), DispatchMode.SYNC),
                new Listener<>(10, ignored -> calls.add(11), DispatchMode.SYNC)
        ));

        bus.post(new Object());
        subscription.unsubscribe();
        subscription.unsubscribe();
        bus.post(new Object());

        assertEquals(List.of(10, 11, 1), calls);
    }

    @Test
    void listenerFailureDoesNotStopRemainingDispatch() {
        FloraBus<Object> bus = new FloraBus<>();
        List<Throwable> failures = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        Flora.setErrorHandler(failures::add);

        bus.subscribe(new Listener<>(ignored -> {
            throw new IllegalStateException("boom");
        }));
        bus.subscribe(new Listener<>(ignored -> calls.incrementAndGet()));

        bus.post(new Object());

        assertEquals(1, calls.get());
        assertEquals(1, failures.size());
        assertEquals("boom", failures.get(0).getMessage());
    }

    @RepeatedTest(3)
    void asyncQueueDeliversConcurrentProducersWithoutLossAndInProducerOrder() throws Exception {
        int producers = 4;
        int eventsPerProducer = 10_000;
        FloraBus<SequencedEvent> bus = new FloraBus<>();
        AtomicInteger delivered = new AtomicInteger();
        AtomicIntegerArray lastSequence = new AtomicIntegerArray(producers);
        AtomicInteger orderingFailures = new AtomicInteger();
        for (int i = 0; i < producers; i++) {
            lastSequence.set(i, -1);
        }

        Subscription subscription = bus.subscribe(new Listener<>(0, event -> {
            int previous = lastSequence.getAndSet(event.producer(), event.sequence());
            if (event.sequence() != previous + 1) {
                orderingFailures.incrementAndGet();
            }
            delivered.incrementAndGet();
        }, DispatchMode.ASYNC));

        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[producers];
        for (int producer = 0; producer < producers; producer++) {
            int producerId = producer;
            threads[producer] = new Thread(() -> {
                await(start);
                for (int sequence = 0; sequence < eventsPerProducer; sequence++) {
                    bus.post(new SequencedEvent(producerId, sequence));
                }
            });
            threads[producer].start();
        }

        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(Flora.awaitQuiescence(10, TimeUnit.SECONDS));
        assertEquals(producers * eventsPerProducer, delivered.get());
        assertEquals(0, orderingFailures.get());
        subscription.unsubscribe();
    }

    @Test
    void parallelListenersCanRunConcurrently() {
        FloraBus<Object> bus = new FloraBus<>();
        CountDownLatch entered = new CountDownLatch(2);
        AtomicInteger completed = new AtomicInteger();
        Consumer<Object> listener = ignored -> {
            entered.countDown();
            await(entered);
            completed.incrementAndGet();
        };
        bus.subscribe(new Listener<>(0, listener, DispatchMode.ASYNC_PARALLEL));
        bus.subscribe(new Listener<>(0, listener, DispatchMode.ASYNC_PARALLEL));

        bus.post(new Object());

        assertTrue(Flora.awaitQuiescence(5, TimeUnit.SECONDS));
        assertEquals(2, completed.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private record SequencedEvent(int producer, int sequence) {
    }
}
