package sweetie.evaware.flora.api;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    void unsubscribe();

    @Override
    default void close() {
        unsubscribe();
    }
}
