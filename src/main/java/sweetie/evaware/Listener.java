package sweetie.evaware;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Listener<T> implements Comparable<Listener<T>> {
    private static final AtomicInteger counter = new AtomicInteger(0);

    private final int priority;
    private final boolean async;
    private final Consumer<T> handler;
    private final int id;

    public Listener(int priority, boolean async, Consumer<T> handler) {
        this.priority = priority;
        this.async = async;
        this.handler = handler;
        this.id = counter.getAndIncrement();
    }

    public Listener(int priority, Consumer<T> handler) {
        this(priority, false, handler);
    }

    public Listener(Consumer<T> handler) {
        this(0, false, handler);
    }

    public Consumer<T> getHandler() {
        return handler;
    }

    public boolean isAsync() {
        return async;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public int compareTo(Listener<T> other) {
        int prioCompare = Integer.compare(other.priority, this.priority);
        return (prioCompare != 0) ? prioCompare : Long.compare(this.id, other.id);
    }
}