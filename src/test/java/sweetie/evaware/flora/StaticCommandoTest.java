package sweetie.evaware.flora;

import org.junit.jupiter.api.Test;
import sweetie.evaware.flora.api.Commando;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticCommandoTest {

    record StaticTestEvent(String message) {
    }

    public static class StaticTarget {
        static final List<String> received = new ArrayList<>();

        @Commando(priority = 10)
        public static void handleStatic(StaticTestEvent event) {
            received.add("static:" + event.message());
        }
    }

    @Test
    void registerAndUnregisterStaticClass() {
        StaticTarget.received.clear();

        Flora.register(StaticTarget.class);
        try {
            Flora.post(new StaticTestEvent("hello"));
            assertEquals(List.of("static:hello"), StaticTarget.received);
        } finally {
            Flora.unregister(StaticTarget.class);
        }

        Flora.post(new StaticTestEvent("after-unregister"));
        assertEquals(List.of("static:hello"), StaticTarget.received);
    }
}
