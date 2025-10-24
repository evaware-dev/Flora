package sweetie.evaware.interfaces;

public interface Notifiable<E> {
    void notify(E event);
}
