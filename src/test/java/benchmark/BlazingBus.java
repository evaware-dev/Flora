package benchmark;

import java.util.function.Consumer;

final class BlazingBus<T> {
    private Consumer<T>[] listeners;
    private int size;

    @SuppressWarnings("unchecked")
    BlazingBus(int capacity) {
        listeners = new Consumer[capacity];
    }

    void subscribe(Consumer<T> listener) {
        listeners[size++] = listener;
    }

    void seal() {
        Consumer<T>[] current = listeners;
        if (current.length == size) {
            return;
        }

        @SuppressWarnings("unchecked")
        Consumer<T>[] compact = new Consumer[size];
        System.arraycopy(current, 0, compact, 0, size);
        listeners = compact;
    }

    void post(T event) {
        Consumer<T>[] current = listeners;
        for (int i = 0, length = current.length; i < length; i++) {
            current[i].accept(event);
        }
    }
}
