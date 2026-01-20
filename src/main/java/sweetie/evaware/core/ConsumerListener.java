package sweetie.evaware.core;

import sweetie.evaware.api.DispatchMode;

import java.util.function.Consumer;

public final class ConsumerListener<E> implements Comparable<ConsumerListener<E>> {
    public final int priority;
    public final Consumer<E> consumer;
    public final DispatchMode mode;

    public ConsumerListener(int priority, Consumer<E> consumer, DispatchMode mode) {
        this.priority = priority;
        this.consumer = consumer;
        this.mode = mode;
    }

    public ConsumerListener(int priority, Consumer<E> consumer) {
        this(priority, consumer, DispatchMode.SYNC);
    }

    @Override
    public int compareTo(ConsumerListener<E> o) {
        return Integer.compare(o.priority, this.priority);
    }
}
