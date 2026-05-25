package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.function.Consumer;

public record Listener<E>(int priority, Consumer<E> consumer, DispatchMode mode) implements Comparable<Listener<E>> {

    public Listener(Consumer<E> consumer, DispatchMode mode) {
        this(0, consumer, mode);
    }

    public Listener(Consumer<E> consumer) {
        this(0, consumer, DispatchMode.SYNC);
    }

    public void accept(E event) {
        DispatchEngine.dispatchSafely(consumer, event);
    }

    @Override
    public int compareTo(Listener<E> o) {
        return Integer.compare(o.priority(), priority());
    }
}
