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

class EventBusTest {
    @AfterEach
    void restoreErrorHandler() {
        Flora.config().exceptionHandler(Throwable::printStackTrace);
    }

    @Test
    void dispatchesByPriorityAndPreservesOrderForEqualPriority() {
        EventBus<Object> bus = new EventBus<>();
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
        EventBus<Object> bus = new EventBus<>();
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
        EventBus<Object> bus = new EventBus<>();
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
        EventBus<Object> bus = new EventBus<>();
        List<Throwable> failures = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        Flora.config().exceptionHandler(failures::add);

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
        EventBus<SequencedEvent> bus = new EventBus<>();
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
        EventBus<Object> bus = new EventBus<>();
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

    @Test
    void mergesBatchesAndRemovesViaBinarySearchAboveThreshold() {
        EventBus<Object> bus = new EventBus<>();
        List<String> calls = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Subscription> individual = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            int priority = i % 5;
            String label = "i" + i;
            individual.add(bus.subscribe(new Listener<>(priority, ignored -> calls.add(label), DispatchMode.SYNC)));
        }

        Subscription batch = bus.subscribeAll(List.of(
                new Listener<>(4, ignored -> calls.add("b4"), DispatchMode.SYNC),
                new Listener<>(10, ignored -> calls.add("b10"), DispatchMode.SYNC),
                new Listener<>(2, ignored -> calls.add("b2"), DispatchMode.ASYNC)
        ));

        bus.post(new Object());
        assertTrue(Flora.awaitQuiescence(5, TimeUnit.SECONDS));

        assertEquals("b10", calls.get(0));
        assertTrue(calls.contains("b4"));
        assertTrue(calls.contains("b2"));
        assertEquals(23, bus.listenerCount());

        individual.get(4).unsubscribe(); // priority 4, >16 elements -> binary search path
        individual.get(0).unsubscribe(); // priority 0
        assertEquals(21, bus.listenerCount());

        batch.unsubscribe();
        batch.unsubscribe();
        assertEquals(18, bus.listenerCount());

        calls.clear();
        bus.post(new Object());
        assertEquals(18, calls.size());
        assertFalse(calls.contains("b10"));
        assertFalse(calls.contains("b4"));
        assertFalse(calls.contains("i4"));
        assertFalse(calls.contains("i0"));
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
