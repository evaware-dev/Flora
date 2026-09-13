package sweetie.evaware.flora;

import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.Commando;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolymorphicDispatchTest {

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
