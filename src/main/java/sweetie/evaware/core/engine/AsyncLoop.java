package sweetie.evaware.core.engine;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class AsyncLoop extends Thread {
    private static final int CAPACITY = 65536;
    private static final int MASK = CAPACITY - 1;

    long p01, p02, p03, p04, p05, p06, p07;
    private volatile long producerIndex;
    long p11, p12, p13, p14, p15, p16, p17;

    private volatile long consumerIndex;
    long p21, p22, p23, p24, p25, p26, p27;

    private final Runnable[] buffer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private static final VarHandle PRODUCER_IDX;
    private static final VarHandle CONSUMER_IDX;
    private static final VarHandle ARRAY_ELEM;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            PRODUCER_IDX = l.findVarHandle(AsyncLoop.class, "producerIndex", long.class);
            CONSUMER_IDX = l.findVarHandle(AsyncLoop.class, "consumerIndex", long.class);
            ARRAY_ELEM = MethodHandles.arrayElementVarHandle(Runnable[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public AsyncLoop() {
        super("Async-Worker");
        this.buffer = new Runnable[CAPACITY];
        this.setDaemon(true);
        this.start();
    }

    public void execute(Runnable task) {
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
        ARRAY_ELEM.setRelease(buffer, offset, task);

        if (currTail == currHead) {
            LockSupport.unpark(this);
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            long currHead = (long) CONSUMER_IDX.getVolatile(this);
            long currTail = (long) PRODUCER_IDX.getVolatile(this);

            if (currHead < currTail) {
                int offset = (int) (currHead & MASK);
                Runnable task = (Runnable) ARRAY_ELEM.getAcquire(buffer, offset);

                if (task == null) {
                    Thread.onSpinWait();
                    continue;
                }

                try {
                    task.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                ARRAY_ELEM.setRelease(buffer, offset, null);
                CONSUMER_IDX.setRelease(this, currHead + 1);
            } else {
                LockSupport.park();
            }
        }
    }

    public void shutdown() {
        running.set(false);
        LockSupport.unpark(this);
    }
}