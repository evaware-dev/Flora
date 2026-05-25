package benchmark.blazebus;

import java.util.function.Consumer;

public final class BlazingBus<T> {
    private Consumer<T>[] listeners;
    private int size;

    @SuppressWarnings("unchecked")
    public BlazingBus(int capacity) {
        listeners = new Consumer[capacity];
    }

    public void subscribe(Consumer<T> listener) {
        listeners[size++] = listener;
    }

    public void seal() {
        Consumer<T>[] current = listeners;
        if (current.length == size) {
            return;
        }

        @SuppressWarnings("unchecked")
        Consumer<T>[] compact = new Consumer[size];
        System.arraycopy(current, 0, compact, 0, size);
        listeners = compact;
    }

    public void post(T event) {
        for (Consumer<T> listener : listeners) {
            listener.accept(event);
        }
    }
}
