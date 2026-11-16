package sweetie.evaware.flora;

import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.Commando;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolymorphicDispatchTest {
    @Test
    void localErrorHandlersSurviveHierarchyChangesAndHandlerUpdates() {
        EventSystem system = EventSystem.create();
        AtomicInteger childErrors = new AtomicInteger();
        AtomicInteger parentErrors = new AtomicInteger();
        AtomicInteger globalErrors = new AtomicInteger();
        system.config().exceptionHandler(ignored -> globalErrors.incrementAndGet());
        system.bus(ChildEvent.class).setExceptionHandler(ignored -> childErrors.incrementAndGet());
        try {
            system.subscribe(ChildEvent.class, ignored -> { throw new IllegalStateException(); });
            system.post(new ChildEvent());
            system.bus(ParentEvent.class).setExceptionHandler(ignored -> parentErrors.incrementAndGet());
            system.subscribe(ParentEvent.class, ignored -> { throw new IllegalArgumentException(); });
            system.post(new ChildEvent());
            assertEquals(2, childErrors.get());
            assertEquals(1, parentErrors.get());
            assertEquals(0, globalErrors.get());

            system.bus(ChildEvent.class).setExceptionHandler(ignored -> globalErrors.addAndGet(10));
            system.post(new ChildEvent());
            assertEquals(10, globalErrors.get());
            assertEquals(2, parentErrors.get());

            system.bus(ChildEvent.class).setExceptionHandler(null);
            system.post(new ChildEvent());
            assertEquals(11, globalErrors.get());
        } finally {
            system.shutdown();
        }
    }

    @Test
    void polymorphicLocalHandlerFailurePropagatesWithoutCallingGlobalHandler() {
        EventSystem system = EventSystem.create();
        AtomicInteger globalErrors = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("handler failure");
        system.config().exceptionHandler(ignored -> globalErrors.incrementAndGet());
        system.bus(ChildEvent.class).setExceptionHandler(ignored -> { throw expected; });
        try {
            system.subscribe(ChildEvent.class, ignored -> { throw new IllegalArgumentException(); });
            system.subscribe(ParentEvent.class, ignored -> { });
            assertSame(expected, assertThrows(IllegalStateException.class,
                    () -> system.post(new ChildEvent())));
            assertEquals(0, globalErrors.get());
        } finally {
            system.shutdown();
        }
    }


    interface BaseEvent {
    }

    interface TaggedEvent {
    }

    static class ParentEvent implements BaseEvent {
    }

    static class ChildEvent extends ParentEvent implements TaggedEvent {
    }

    static class PolymorphicListener {
        final List<String> invocations = new ArrayList<>();

        @Commando(priority = 120)
        public void onBase(BaseEvent event) {
            invocations.add("BaseEvent");
        }

        @Commando(priority = 50)
        public void onParent(ParentEvent event) {
            invocations.add("ParentEvent");
        }

        @Commando(priority = 0)
        public void onTagged(TaggedEvent event) {
            invocations.add("TaggedEvent");
        }

        @Commando(priority = -50)
        public void onChild(ChildEvent event) {
            invocations.add("ChildEvent");
        }
    }

    @Test
    void childEventInvokesAllSuperclassesAndInterfacesInPriorityOrder() {
        PolymorphicListener listener = new PolymorphicListener();
        Flora.register(listener);
        try {
            Flora.post(new ChildEvent());

            assertEquals(
                    List.of("BaseEvent", "ParentEvent", "TaggedEvent", "ChildEvent"),
                    listener.invocations
            );
        } finally {
            Flora.unregister(listener);
        }
    }

    @Test
    void parentEventOnlyInvokesParentAndBase() {
        PolymorphicListener listener = new PolymorphicListener();
        Flora.register(listener);
        try {
            Flora.post(new ParentEvent());

            assertEquals(
                    List.of("BaseEvent", "ParentEvent"),
                    listener.invocations
            );
        } finally {
            Flora.unregister(listener);
        }
    }
}
