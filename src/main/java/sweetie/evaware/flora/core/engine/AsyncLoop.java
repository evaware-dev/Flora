package sweetie.evaware.flora.core.engine;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class AsyncLoop extends Thread {
    private static final int CAPACITY = 65536;
    private static final int MASK = CAPACITY - 1;
    private static final int IDLE_SPINS = 256;

    long p01, p02, p03, p04, p05, p06, p07;
    private volatile long producerIndex;
    long p11, p12, p13, p14, p15, p16, p17;

    private volatile long consumerIndex;
    long p21, p22, p23, p24, p25, p26, p27;

    private final Object[] events;
    private final Consumer<?>[][] listeners;
    private volatile boolean running = true;

    private static final VarHandle PRODUCER_IDX;
    private static final VarHandle CONSUMER_IDX;
    private static final VarHandle EVENT_ELEM;
    private static final VarHandle LISTENER_ELEM;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            PRODUCER_IDX = l.findVarHandle(AsyncLoop.class, "producerIndex", long.class);
            CONSUMER_IDX = l.findVarHandle(AsyncLoop.class, "consumerIndex", long.class);
            EVENT_ELEM = MethodHandles.arrayElementVarHandle(Object[].class);
            LISTENER_ELEM = MethodHandles.arrayElementVarHandle(Consumer[][].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public AsyncLoop() {
        super("Async-Worker");
        events = new Object[CAPACITY];
        listeners = new Consumer[CAPACITY][];
        setDaemon(true);
        start();
    }

    public <T> void execute(T event, Consumer<T>[] consumers) {
        long currHead;
        long currTail;

        do {
            currHead = (long) CONSUMER_IDX.getVolatile(this);
            currTail = (long) PRODUCER_IDX.getVolatile(this);

            if (currTail - currHead >= CAPACITY) {
                Thread.onSpinWait();
                continue;
            }
        } while (!PRODUCER_IDX.compareAndSet(this, currTail, currTail + 1));

        int offset = (int) (currTail & MASK);
        EVENT_ELEM.set(events, offset, event);
        LISTENER_ELEM.setRelease(listeners, offset, consumers);

        if (currTail == currHead) {
            LockSupport.unpark(this);
        }
    }

    @Override
    public void run() {
        int idleSpins = 0;

        while (running) {
            long currHead = (long) CONSUMER_IDX.getVolatile(this);
            long currTail = (long) PRODUCER_IDX.getVolatile(this);

            if (currHead < currTail) {
                idleSpins = 0;
                int offset = (int) (currHead & MASK);
                Consumer<?>[] consumers = (Consumer<?>[]) LISTENER_ELEM.getAcquire(listeners, offset);

                if (consumers == null) {
                    Thread.onSpinWait();
                    continue;
                }

                dispatch(consumers, EVENT_ELEM.get(events, offset));

                EVENT_ELEM.set(events, offset, null);
                LISTENER_ELEM.setRelease(listeners, offset, null);
                CONSUMER_IDX.setRelease(this, currHead + 1);
                continue;
            }

            if (idleSpins++ < IDLE_SPINS) {
                Thread.onSpinWait();
                continue;
            }

            idleSpins = 0;
            LockSupport.park();
        }
    }

    public void shutdown() {
        running = false;
        LockSupport.unpark(this);
    }

    @SuppressWarnings("unchecked")
    private static <T> void dispatch(Consumer<?>[] consumers, Object event) {
        Consumer<T>[] typedConsumers = (Consumer<T>[]) consumers;
        T typedEvent = (T) event;

        for (Consumer<T> consumer : typedConsumers) {
            try {
                consumer.accept(typedEvent);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
