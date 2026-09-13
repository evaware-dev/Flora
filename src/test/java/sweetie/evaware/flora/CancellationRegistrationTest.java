package sweetie.evaware.flora;

import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.Commando;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationRegistrationTest {

    interface MyCancellable {
        boolean isCancelled();
        void cancel();
    }

    static class CustomCancellable implements MyCancellable {
        final String payload;
        private boolean cancelled;

        CustomCancellable(String payload) {
            this.payload = payload;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }
    }

    static class SpecificEvent {
        boolean stopped;
    }

    static class ListenerWithShortCircuit {
        final List<String> log = new ArrayList<>();

        @Commando(priority = 100)
        public void onFirst(CustomCancellable event) {
            log.add("first");
            event.cancel();
        }

        @Commando(priority = 50)
        public void onSecond(CustomCancellable event) {
            log.add("second");
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface CustomHandler {
        int priority() default 0;
    }

    static class CustomAnnotationTarget {
        final List<String> log = new ArrayList<>();

        @CustomHandler(priority = 10)
        public void handle(SpecificEvent event) {
            log.add("custom-handled");
            event.stopped = true;
        }
    }

    @Test
    void interfaceCancellationShortCircuits() {
        FloraConfigurator.registerCancellation(MyCancellable.class, MyCancellable::isCancelled);

        ListenerWithShortCircuit listener = new ListenerWithShortCircuit();
        Flora.register(listener);
        try {
            CustomCancellable event = new CustomCancellable("test");
            assertFalse(event.isCancelled());

            CustomCancellable returned = Flora.post(event);
            assertTrue(returned.isCancelled());

            assertEquals(List.of("first"), listener.log);
        } finally {
            Flora.unregister(listener);
        }
    }

    @Test
    void specificClassCancellationAndCustomAnnotation() {
        FloraConfigurator.registerCancellation(SpecificEvent.class, e -> e.stopped);
        FloraConfigurator.registerAnnotation(CustomHandler.class);

        CustomAnnotationTarget target = new CustomAnnotationTarget();
        Flora.register(target);

        List<String> postLog = new ArrayList<>();
        var sub = Flora.subscribe(SpecificEvent.class, 0, e -> postLog.add("after"));

        try {
            SpecificEvent event = new SpecificEvent();
            Flora.post(event);
            assertTrue(event.stopped);
            assertEquals(List.of("custom-handled"), target.log);
            assertEquals(List.of(), postLog);
        } finally {
            Flora.unregister(target);
            sub.unsubscribe();
        }
    }
}
