package sweetie.evaware.core;

import sweetie.evaware.api.DispatchMode;

import java.util.function.Consumer;

public final class Listener<E> implements Comparable<Listener<E>> {
    public final int priority;
    public final Consumer<E> consumer;
    public final DispatchMode mode;

    public Listener(int priority, Consumer<E> consumer, DispatchMode mode) {
        this.priority = priority;
        this.consumer = consumer;
        this.mode = mode;
    }

    public Listener(Consumer<E> consumer, DispatchMode mode) {
        this(0, consumer, mode);
    }

    public Listener(Consumer<E> consumer) {
        this(0, consumer, DispatchMode.SYNC);
    }

    public void accept(E event) {
        try {
            consumer.accept(event);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public int compareTo(Listener<E> o) {
        return Integer.compare(o.priority, this.priority);
    }
}