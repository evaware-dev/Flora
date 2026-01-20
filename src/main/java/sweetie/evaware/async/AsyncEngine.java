package sweetie.evaware.async;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class AsyncEngine extends Thread {
    long p01, p02, p03, p04, p05, p06, p07;
    private final AtomicLong producerIndex = new AtomicLong(0);
    long p11, p12, p13, p14, p15, p16, p17;
    private final AtomicLong consumerIndex = new AtomicLong(0);
    long p21, p22, p23, p24, p25, p26, p27;

    private final AtomicReferenceArray<Runnable> buffer;
    private final int mask;
    private final int capacity;

    public AsyncEngine() {
        super("Single-Worker");
        this.capacity = 65536;
        this.mask = capacity - 1;
        this.buffer = new AtomicReferenceArray<>(capacity);
        this.setDaemon(true);
        this.setPriority(Thread.NORM_PRIORITY);
        this.start();
    }

    public void execute(Runnable task) {
        long currentTail;
        long currentHead;
        do {
            currentTail = producerIndex.get();
            currentHead = consumerIndex.get();
            if (currentTail - currentHead >= capacity) {
                Thread.onSpinWait();
                continue;
            }
            if (producerIndex.compareAndSet(currentTail, currentTail + 1)) {
                break;
            }
        } while (true);

        int offset = (int) (currentTail & mask);
        buffer.lazySet(offset, task);
    }

    @Override
    public void run() {
        while (true) {
            long currentHead = consumerIndex.get();
            long currentTail = producerIndex.get();

            if (currentHead < currentTail) {
                int offset = (int) (currentHead & mask);
                Runnable task = buffer.get(offset);

                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    buffer.lazySet(offset, null);
                    consumerIndex.lazySet(currentHead + 1);
                }
            } else {
                Thread.onSpinWait();
            }
        }
    }
}