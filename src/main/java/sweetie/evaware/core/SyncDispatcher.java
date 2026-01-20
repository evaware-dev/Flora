package sweetie.evaware.core;

import sweetie.evaware.api.Dispatcher;

import java.util.function.Consumer;

public class SyncDispatcher<E> implements Dispatcher<E> {
    private final Consumer<E>[] listeners;

    public SyncDispatcher(Consumer<E>[] listeners) {
        this.listeners = listeners;
    }

    @Override
    public void dispatch(E event) {
        for (Consumer<E> eConsumer : this.listeners) {
            try {
                eConsumer.accept(event);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
