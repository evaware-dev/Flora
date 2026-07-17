package sweetie.evaware.flora;

import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.Commando;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnotationRegistrationTest {
    @Test
    void supportsStaticAndInheritedHandlers() {
        Handler.calls.set(0);
        ChildHandler target = new ChildHandler();

        Flora.register(target);
        Flora.post(new AnnotationEvent());
        Flora.unregister(target);
        Flora.post(new AnnotationEvent());

        assertEquals(11, Handler.calls.get());
    }

    @Test
    void unannotatedOverrideSuppressesInheritedHandler() {
        Handler.calls.set(0);
        OverridingHandler target = new OverridingHandler();

        Flora.register(target);
        Flora.post(new AnnotationEvent());
        Flora.unregister(target);

        assertEquals(10, Handler.calls.get());
    }

    @Test
    void rejectsAnnotatedMethodWithoutExactlyOneParameter() {
        assertThrows(IllegalArgumentException.class, () -> Flora.register(new MissingParameterHandler()));
        assertThrows(IllegalArgumentException.class, () -> Flora.register(new ExtraParameterHandler()));
    }

    @Test
    void equalPriorityHandlersUseStableMethodOrder() {
        OrderedHandler target = new OrderedHandler();

        Flora.register(target);
        Flora.post(new OrderedEvent());
        Flora.unregister(target);

        assertEquals("az", target.order.toString());
    }

    @Test
    void ignoresCompilerBridgeMethod() {
        Handler.calls.set(0);
        GenericHandler target = new GenericHandler();

        Flora.register(target);
        Flora.post(new AnnotationEvent());
        Flora.unregister(target);

        assertEquals(1, Handler.calls.get());
    }

    static class Handler {
        static final AtomicInteger calls = new AtomicInteger();

        @Commando
        public static void staticHandler(AnnotationEvent event) {
            calls.addAndGet(10);
        }

        @Commando
        protected void inheritedHandler(AnnotationEvent event) {
            calls.incrementAndGet();
        }
    }

    static final class ChildHandler extends Handler {
    }

    static final class OverridingHandler extends Handler {
        @Override
        protected void inheritedHandler(AnnotationEvent event) {
            calls.addAndGet(100);
        }
    }

    static final class AnnotationEvent {
    }

    static final class MissingParameterHandler {
        @Commando
        void invalid() {
        }
    }

    static final class ExtraParameterHandler {
        @Commando
        void invalid(AnnotationEvent first, AnnotationEvent second) {
        }
    }

    static final class OrderedHandler {
        private final StringBuilder order = new StringBuilder();

        @Commando
        void zeta(OrderedEvent event) {
            order.append('z');
        }

        @Commando
        void alpha(OrderedEvent event) {
            order.append('a');
        }
    }

    static final class OrderedEvent {
    }

    interface GenericContract<T> {
        void handle(T event);
    }

    static final class GenericHandler implements GenericContract<AnnotationEvent> {
        @Override
        @Commando
        public void handle(AnnotationEvent event) {
            Handler.calls.incrementAndGet();
        }
    }
}
