package sweetie.evaware.flora;

import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchConfig;
import sweetie.evaware.flora.api.DispatchMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSystemTest {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface CustomListener {
        int priority() default 0;
    }

    interface BaseEvent {
    }

    static final class SampleEvent implements BaseEvent {
        boolean cancelled;
        final List<String> trace = new ArrayList<>();
    }

    static final class AnnotatedTarget {
        @Commando(priority = 10)
        void onBase(BaseEvent event) {
            ((SampleEvent) event).trace.add("commando-base");
        }

        @CustomListener(priority = 5)
        void onSample(SampleEvent event) {
            event.trace.add("custom-sample");
        }
    }

    @Test
    void isolatedEventSystemsDoNotLeakConfigOrListeners() {
        EventSystem first = EventSystem.create(new DispatchConfig(1, 1, 16, 16, 8, 4, 1_000L));
        EventSystem second = EventSystem.create(new DispatchConfig(1, 1, 16, 16, 8, 4, 1_000L));

        try {
            first.config()
                    .registerAnnotation(CustomListener.class)
                    .registerCancellation(SampleEvent.class, e -> e.cancelled);

            AnnotatedTarget target = new AnnotatedTarget();
            first.register(target);
            second.register(target);

            first.subscribe(SampleEvent.class, 20, event -> {
                event.trace.add("cancel");
                event.cancelled = true;
            });

            SampleEvent firstEvent = first.post(new SampleEvent());
            assertEquals(List.of("cancel"), firstEvent.trace);

            SampleEvent secondEvent = second.post(new SampleEvent());
            assertEquals(List.of("commando-base"), secondEvent.trace);

            AtomicInteger asyncCount = new AtomicInteger();
            first.subscribe(String.class, DispatchMode.ASYNC, ignored -> asyncCount.incrementAndGet());
            first.post("hello");
            assertTrue(first.awaitQuiescence(5, TimeUnit.SECONDS));
            assertEquals(1, asyncCount.get());
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }
}
