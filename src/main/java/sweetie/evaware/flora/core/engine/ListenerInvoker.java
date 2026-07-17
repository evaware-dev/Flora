package sweetie.evaware.flora.core.engine;

import java.util.Objects;
import java.util.function.Consumer;

final class ListenerInvoker {
    private volatile Consumer<Throwable> exceptionHandler;

    ListenerInvoker(Consumer<Throwable> exceptionHandler) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    void setExceptionHandler(Consumer<Throwable> exceptionHandler) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    <T> void invoke(Consumer<T> listener, T event) {
        try {
            listener.accept(event);
        } catch (Throwable listenerFailure) {
            if (listenerFailure instanceof VirtualMachineError virtualMachineFailure) {
                throw virtualMachineFailure;
            }
            report(listenerFailure);
        }
    }

    private void report(Throwable listenerFailure) {
        try {
            exceptionHandler.accept(listenerFailure);
        } catch (Throwable handlerFailure) {
            listenerFailure.addSuppressed(handlerFailure);
            listenerFailure.printStackTrace();
        }
    }
}
