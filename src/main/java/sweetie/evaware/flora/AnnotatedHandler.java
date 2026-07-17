package sweetie.evaware.flora;

import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;

import java.lang.reflect.Method;
import java.util.function.Consumer;

record AnnotatedHandler(Method method, Class<?> eventType, int priority, DispatchMode dispatchMode) {
    @SuppressWarnings("unchecked")
    FloraBus<Object> bus() {
        return Flora.getBus((Class<Object>) eventType);
    }

    Listener<Object> bind(Object target) {
        Consumer<Object> listener = HandlerLambdaFactory.bind(target, method);
        return new Listener<>(priority, listener, dispatchMode);
    }
}
