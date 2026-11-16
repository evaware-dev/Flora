package sweetie.evaware.flora.core;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SyncDispatcher {
    private SyncDispatcher() {
    }

    public static <T> void dispatch(
            T event, Consumer<T>[] listeners, int len, Predicate<Object> canceller,
            Consumer<Throwable> fallback, Consumer<Throwable>[] handlers
    ) {
        if (handlers == null) {
            dispatch(event, listeners, len, canceller, fallback);
            return;
        }
        for (int i = 0; i < len; i++) {
            if (canceller != null && canceller.test(event)) break;
            Consumer<Throwable> handler = handlers[i] != null ? handlers[i] : fallback;
            if (handler == null) {
                listeners[i].accept(event);
            } else {
                try {
                    listeners[i].accept(event);
                } catch (Throwable t) {
                    if (t instanceof VirtualMachineError vme) throw vme;
                    handler.accept(t);
                }
            }
        }
    }

    public static <T> void dispatch(
            T event,
            Consumer<T>[] listeners,
            int len,
            Predicate<Object> canceller,
            Consumer<Throwable> handler
    ) {
        if (canceller == null) {
            dispatchDirect(event, listeners, len, handler);
        } else {
            dispatchCancellable(event, listeners, len, canceller, handler);
        }
    }

    private static <T> void dispatchDirect(T event, Consumer<T>[] listeners, int len, Consumer<Throwable> handler) {
        if (handler == null) {
            for (int i = 0; i < len; i++) {
                listeners[i].accept(event);
            }
            return;
        }
        for (int i = 0; i < len; i++) {
            try {
                listeners[i].accept(event);
            } catch (Throwable t) {
                if (t instanceof VirtualMachineError vme) throw vme;
                handler.accept(t);
            }
        }
    }

    private static <T> void dispatchCancellable(
            T event,
            Consumer<T>[] listeners,
            int len,
            Predicate<Object> canceller,
            Consumer<Throwable> handler
    ) {
        if (handler == null) {
            for (int i = 0; i < len; i++) {
                if (canceller.test(event)) break;
                listeners[i].accept(event);
            }
            return;
        }
        for (int i = 0; i < len; i++) {
            if (canceller.test(event)) break;
            try {
                listeners[i].accept(event);
            } catch (Throwable t) {
                if (t instanceof VirtualMachineError vme) throw vme;
                handler.accept(t);
            }
        }
    }
}
