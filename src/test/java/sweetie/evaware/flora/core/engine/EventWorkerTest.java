package sweetie.evaware.flora.core.engine;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.Config;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventWorkerTest {
    @Test
    void nestedSubmissionRejectsFullQueueWithoutDeadlocking() throws Exception {
        EventWorker worker = new EventWorker("Flora-Nested-Test", 2, 8, 4, 1_000L, new Config());
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        try {
            worker.submit(0, ignored -> {
                try {
                    worker.submit(1, value -> calls.incrementAndGet());
                    assertThrows(RejectedExecutionException.class,
                            () -> worker.submit(2, value -> calls.incrementAndGet()));
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    finished.countDown();
                }
            });
            assertTrue(finished.await(2, TimeUnit.SECONDS), "nested producer deadlocked");
        } finally {
            worker.requestShutdown();
            worker.join(2_000L);
        }
        assertNull(failure.get());
        assertEquals(1, calls.get());
        assertFalse(worker.isAlive());
    }

    @Test
    void cancellationFailureIsReportedAndLaterEventsStillRun() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        IllegalStateException expected = new IllegalStateException("predicate failure");
        Config config = new Config().exceptionHandler(failure::set);
        config.registerCancellation(Integer.class, n -> {
            if (n == 0) throw expected;
            return false;
        });
        EventWorker worker = new EventWorker("Flora-Predicate-Test", 4, 8, 4, 1_000L, config);
        CountDownLatch delivered = new CountDownLatch(1);
        try {
            worker.submit(0, ignored -> { });
            worker.submit(1, ignored -> delivered.countDown());
            assertTrue(delivered.await(2, TimeUnit.SECONDS), "worker stopped after predicate failure");
            assertSame(expected, failure.get());
        } finally {
            worker.requestShutdown();
            worker.join(2_000L);
        }
    }

    @Test
    void fatalWorkerFailureRejectsSubsequentSubmissions() throws Exception {
        EventWorker worker = new EventWorker("Flora-Fatal-Test", 4, 8, 4, 1_000L, new Config());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        worker.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
        InternalError fatal = new InternalError("fatal test failure");
        try {
            worker.submit(0, ignored -> { throw fatal; });
            worker.join(2_000L);
            assertFalse(worker.isAlive());
            assertSame(fatal, failure.get());
            assertThrows(RejectedExecutionException.class, () -> worker.submit(1, ignored -> { }));
        } finally {
            worker.requestShutdown();
        }
    }

    @RepeatedTest(3)
    void sustainedProducerCannotMissWorkerWakeup() throws Exception {
        int submissions = 250_000;
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Throwable> producerFailure = new AtomicReference<>();
        EventWorker worker = new EventWorker("Flora-Saturation-Test", 64, 8, 4, 1_000L, new Config());
        Thread producer = new Thread(() -> {
            try {
                for (int index = 0; index < submissions; index++) {
                    worker.submit(index, ignored -> calls.incrementAndGet());
                }
            } catch (Throwable failure) {
                producerFailure.set(failure);
            }
        });

        try {
            producer.start();
            producer.join(10_000L);

            assertFalse(producer.isAlive(), "producer remained blocked by a missed worker wakeup");
            assertNull(producerFailure.get());

            long deadline = System.nanoTime() + 10_000_000_000L;
            while (calls.get() != submissions && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(submissions, calls.get());
        } finally {
            worker.requestShutdown();
            worker.join(10_000L);
        }
    }

    @Test
    void shutdownDrainsClaimedAndQueuedWorkThenRejectsNewWork() throws Exception {
        EventWorker worker = new EventWorker("Flora-Test", 16, 8, 4, 1_000L, new Config());
        AtomicInteger calls = new AtomicInteger();
        @SuppressWarnings("unchecked")
        Consumer<Object>[] listeners = new Consumer[]{ignored -> calls.incrementAndGet()};

        try {
            worker.submit(new Object(), listeners);
            worker.requestShutdown();

            assertThrows(RejectedExecutionException.class, () -> worker.submit(new Object(), listeners));
        } finally {
            worker.join(5_000L);
        }
        assertEquals(1, calls.get());
    }

    @RepeatedTest(3)
    void concurrentShutdownDoesNotLoseWork() throws Exception {
        int producersCount = 4;
        int operationsPerProducer = 10_000;
        EventWorker worker = new EventWorker("Flora-Test", 64, 8, 4, 1_000L, new Config());
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger();
        Thread[] producers = new Thread[producersCount];

        for (int index = 0; index < producersCount; index++) {
            producers[index] = new Thread(() -> {
                for (int operation = 0; operation < operationsPerProducer; operation++) {
                    try {
                        worker.submit(operation, ignored -> calls.incrementAndGet());
                        accepted.incrementAndGet();
                    } catch (RejectedExecutionException expected) {
                        break;
                    }
                }
            });
        }

        for (Thread producer : producers) {
            producer.start();
        }

        try {
            Thread.sleep(5L);
            worker.requestShutdown();
        } finally {
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
        Config config = new Config().exceptionHandler(failure::set);

        EventWorker worker = new EventWorker("Flora-Test", 16, 8, 4, 1_000L, config);
        AtomicInteger calls = new AtomicInteger();
        AssertionError assertionFailure = new AssertionError("listener failure");

        try {
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
        } finally {
            worker.requestShutdown();
        }
    }
}
