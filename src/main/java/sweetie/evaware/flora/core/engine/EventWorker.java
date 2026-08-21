package sweetie.evaware.flora.core.engine;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

class EventWorker extends Thread {
    private static final long CLOSED_MASK = 1L;
    private static final long SEQUENCE_INCREMENT = 2L;
    private static final VarHandle SEQUENCE_VALUE;
    private static final VarHandle ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(Object[].class);

    static {
        try {
            SEQUENCE_VALUE = MethodHandles.lookup().findVarHandle(Sequence.class, "value", long.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final Sequence producerState = new Sequence();
    private final Sequence consumerSequence = new Sequence();
    private final Object[] events;
    private final Object[] callbacks;
    private final int indexMask;
    private final int idleSpinLimit;
    private final int backpressureSpinLimit;
    private final long backpressureParkNanos;
    private final ListenerInvoker listenerInvoker;
    // Producer/consumer handshake preventing a publication from racing past park().
    private volatile boolean parked;

    EventWorker(String name, int capacity, int idleSpinLimit, int backpressureSpinLimit,
                long backpressureParkNanos, ListenerInvoker listenerInvoker) {
        super(name);
        if (capacity < 2 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two and at least 2");
        }
        this.events = new Object[capacity];
        this.callbacks = new Object[capacity];
        this.indexMask = capacity - 1;
        this.idleSpinLimit = idleSpinLimit;
        this.backpressureSpinLimit = backpressureSpinLimit;
        this.backpressureParkNanos = backpressureParkNanos;
        this.listenerInvoker = listenerInvoker;
        setDaemon(true);
        start();
    }

    final <T> void submit(T event, Consumer<T>[] listeners) {
        enqueueCallback(event, listeners);
    }

    final <T> void submit(T event, Consumer<T> listener) {
        enqueueCallback(event, listener);
    }

    private void enqueueCallback(Object event, Object callback) {
        publish(event, callback);
    }

    private void publish(Object event, Object callback) {
        int spins = 0;
        long producerIndex;
        long consumerIndex;

        for (;;) {
            long state = sequence(producerState);
            rejectIfStopped(state);
            consumerIndex = sequence(consumerSequence);
            producerIndex = producerIndex(state);
            if (producerIndex - consumerIndex >= events.length) {
                spins = applyBackpressure(spins);
                continue;
            }
            if (SEQUENCE_VALUE.compareAndSet(producerState, state, state + SEQUENCE_INCREMENT)) {
                break;
            }
        }

        int index = (int) (producerIndex & indexMask);
        ARRAY_ELEMENT.set(events, index, event);
        ARRAY_ELEMENT.setRelease(callbacks, index, callback);
        if (parked) {
            LockSupport.unpark(this);
        }
    }

    private int applyBackpressure(int spins) {
        if (spins < backpressureSpinLimit) {
            Thread.onSpinWait();
            return spins + 1;
        }
        LockSupport.parkNanos(backpressureParkNanos);
        return 0;
    }

    @Override
    public final void run() {
        int idleSpins = 0;
        while (shouldRun()) {
            long consumerIndex = sequence(consumerSequence);
            if (consumerIndex < producerIndex(sequence(producerState))) {
                idleSpins = 0;
                consume(consumerIndex);
            } else if (idleSpins < idleSpinLimit) {
                idleSpins++;
                Thread.onSpinWait();
            } else {
                idleSpins = 0;
                awaitWork();
            }
        }
    }

    private void awaitWork() {
        parked = true;
        if (sequence(consumerSequence) >= producerIndex(sequence(producerState))) {
            LockSupport.park();
        }
        parked = false;
    }

    final void requestShutdown() {
        for (;;) {
            long state = sequence(producerState);
            if (isClosed(state) || SEQUENCE_VALUE.compareAndSet(producerState, state, state | CLOSED_MASK)) {
                break;
            }
        }
        LockSupport.unpark(this);
    }

    final boolean idle() {
        return sequence(consumerSequence) == producerIndex(sequence(producerState));
    }

    private boolean shouldRun() {
        return !isClosed(sequence(producerState)) || !idle();
    }

    private void consume(long consumerIndex) {
        int index = (int) (consumerIndex & indexMask);
        Object callback = ARRAY_ELEMENT.getAcquire(callbacks, index);
        if (callback == null) {
            Thread.onSpinWait();
            return;
        }

        try {
            invoke(callback, ARRAY_ELEMENT.get(events, index));
        } finally {
            ARRAY_ELEMENT.set(events, index, null);
            ARRAY_ELEMENT.setRelease(callbacks, index, null);
            SEQUENCE_VALUE.setRelease(consumerSequence, consumerIndex + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void invoke(Object callback, Object event) {
        T typedEvent = (T) event;
        if (callback instanceof Consumer<?>[] listeners) {
            for (Consumer<?> listener : listeners) {
                listenerInvoker.invoke((Consumer<T>) listener, typedEvent);
            }
            return;
        }
        listenerInvoker.invoke((Consumer<T>) callback, typedEvent);
    }

    private void rejectIfStopped(long state) {
        if (isClosed(state)) {
            throw new RejectedExecutionException(getName() + " is shut down");
        }
    }

    private static boolean isClosed(long state) {
        return (state & CLOSED_MASK) != 0;
    }

    private static long producerIndex(long state) {
        return state >>> 1;
    }

    private static long sequence(Sequence sequence) {
        return (long) SEQUENCE_VALUE.getVolatile(sequence);
    }

    private static final class Sequence {
        private volatile long value;
    }
}
