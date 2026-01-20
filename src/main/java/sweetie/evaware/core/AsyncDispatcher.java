package sweetie.evaware.core;

import sweetie.evaware.api.Dispatcher;

import java.util.function.Consumer;

public class AsyncDispatcher<E> implements Dispatcher<E> {
    private final Consumer<E>[] sync;
    private final Consumer<E>[] single;
    private final Consumer<E>[] parallel;

    public AsyncDispatcher(Consumer<E>[] sync, Consumer<E>[] single, Consumer<E>[] parallel) {
        this.sync = sync;
        this.single = single;
        this.parallel = parallel;
    }

    @Override
    public void dispatch(E event) {
        final Consumer<E>[] s = this.sync;
        final int sLen = s.length;
        if (sLen > 0) {
            for (Consumer<E> eConsumer : s) {
                try {
                    eConsumer.accept(event);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        final Consumer<E>[] sg = this.single;
        if (sg.length > 0) {
            FloraBus.Executors.SINGLE_WORKER.execute(() -> {
                for (Consumer<E> l : sg) {
                    try {
                        l.accept(event);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
        }

        for (Consumer<E> l : this.parallel) {
            FloraBus.Executors.PARALLEL_POOL.execute(() -> {
                try {
                    l.accept(event);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }
    }
}