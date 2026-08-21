package sweetie.evaware.flora.core;

import java.util.function.Consumer;

final class ListenerSnapshot<T> {
    private static final Consumer<?>[] EMPTY_LISTENERS = new Consumer<?>[0];
    private static final ListenerSnapshot<?> EMPTY = new ListenerSnapshot<>(
            emptyListeners(), emptyListeners(), emptyListeners());

    final Consumer<T>[] synchronous;
    final Consumer<T>[] asynchronous;
    final Consumer<T>[] parallel;
    final boolean onlySynchronous;

    ListenerSnapshot(Consumer<T>[] synchronous, Consumer<T>[] asynchronous, Consumer<T>[] parallel) {
        this.synchronous = synchronous;
        this.asynchronous = asynchronous;
        this.parallel = parallel;
        this.onlySynchronous = asynchronous.length == 0 && parallel.length == 0;
    }

    @SuppressWarnings("unchecked")
    static <T> ListenerSnapshot<T> empty() {
        return (ListenerSnapshot<T>) EMPTY;
    }

    @SuppressWarnings("unchecked")
    static <T> Consumer<T>[] emptyListeners() {
        return (Consumer<T>[]) EMPTY_LISTENERS;
    }
}
