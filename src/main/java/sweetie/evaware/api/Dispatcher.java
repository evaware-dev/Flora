package sweetie.evaware.api;

public interface Dispatcher<E> {
    void dispatch(E event);
}
