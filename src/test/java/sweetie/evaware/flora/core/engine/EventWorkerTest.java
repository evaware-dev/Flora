package sweetie.evaware.flora.core.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventWorkerTest {
    @Test
    void shutdownDrainsClaimedAndQueuedWorkThenRejectsNewWork() throws Exception {
        ListenerInvoker invoker = new ListenerInvoker(Throwable::printStackTrace);
        EventWorker worker = new EventWorker("Flora-Test", 16, 8, 4, 1_000L, invoker);
        AtomicInteger calls = new AtomicInteger();
        @SuppressWarnings("unchecked")
        Consumer<Object>[] listeners = new Consumer[]{ignored -> calls.incrementAndGet()};

        for (int index = 0; index < 16; index++) {
            worker.submit(new Object(), listeners);
        }
        worker.requestShutdown();
        worker.join(5_000L);

        assertEquals(16, calls.get());
        assertThrows(RejectedExecutionException.class, () -> worker.submit(new Object(), listeners));
    }

    @Test
    void shutdownRacingProducersDrainsEveryAcceptedSubmission() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            AtomicInteger accepted = new AtomicInteger();
            AtomicInteger calls = new AtomicInteger();
            EventWorker worker = new EventWorker("Flora-Test", 64, 8, 4, 1_000L,
                    new ListenerInvoker(Throwable::printStackTrace));
            Thread[] producers = new Thread[4];

            for (int producerIndex = 0; producerIndex < producers.length; producerIndex++) {
                producers[producerIndex] = new Thread(() -> {
                    for (;;) {
                        try {
                            worker.submit(new Object(), ignored -> calls.incrementAndGet());
                            accepted.incrementAndGet();
                        } catch (RejectedExecutionException ignored) {
                            return;
                        }
                    }
                });
                producers[producerIndex].start();
            }

            worker.requestShutdown();
            for (Thread producer : producers) {
                producer.join(5_000L);
            }
            worker.join(5_000L);

            assertEquals(accepted.get(), calls.get());
        }
    }

    @Test
    void assertionFailureDoesNotTerminateWorker() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventWorker worker = new EventWorker("Flora-Test", 16, 8, 4, 1_000L,
                new ListenerInvoker(failure::set));
        AtomicInteger calls = new AtomicInteger();
        AssertionError assertionFailure = new AssertionError("listener failure");

        worker.submit(new Object(), ignored -> {
            throw assertionFailure;
        });
        worker.submit(new Object(), ignored -> calls.incrementAndGet());

        long deadline = System.nanoTime() + 5_000_000_000L;
        while (calls.get() == 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }

        worker.requestShutdown();
        worker.join(5_000L);

        assertSame(assertionFailure, failure.get());
        assertEquals(1, calls.get());
    }
}
