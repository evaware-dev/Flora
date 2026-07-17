package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.DispatchMode;

import java.util.Objects;
import java.util.function.Consumer;

public record Listener<E>(int priority, Consumer<E> callback, DispatchMode mode) {

    public Listener {
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(mode, "mode");
    }

    public Listener(Consumer<E> callback, DispatchMode mode) {
        this(0, callback, mode);
    }

    public Listener(Consumer<E> callback) {
        this(0, callback, DispatchMode.SYNC);
    }
}
