package polygone;

import sweetie.evaware.Flora;
import sweetie.evaware.FloraAutomation;
import sweetie.evaware.annotations.Commando;

public class FloraAutomationTest {
    public static class AutoEvent extends Flora<AutoEvent> {
        public static final AutoEvent INSTANCE = new AutoEvent();
        public String message;
    }

    public static class MyModule {
        private int callCount = 0;

        @Commando(priority = 2)
        public void onEvent(AutoEvent e) {
            this.callCount++;
            System.out.println("    [Listener] Packet: " + e.message);
        }

        public int getCalls() {
            return callCount;
        }

        public void reset() {
            callCount = 0;
        }
    }

    public static void main(String[] args) {
        log("Automation Suite");

        MyModule module = new MyModule();

        log("-> Testing registration...");
        FloraAutomation.register(module);

        fireEvent("Hello world");
        check(module.getCalls() == 1, "Module should receive event");

        log("-> Testing unregistration...");
        FloraAutomation.unregister(module);
        module.reset();

        fireEvent("Ignored message");
        check(module.getCalls() == 0, "Module should NOT receive event after unregister");

        log("-> Testing idempotency (double register)...");
        FloraAutomation.register(module);
        FloraAutomation.register(module);
        module.reset();

        fireEvent("Double check");
        check(module.getCalls() == 1, "Module should be registered exactly once");

        FloraAutomation.unregister(module);
        log("\n[SUCCESS] All system checks passed.");
    }

    private static void fireEvent(String msg) {
        AutoEvent.INSTANCE.message = msg;
        AutoEvent.INSTANCE.notify(AutoEvent.INSTANCE);
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    private static void check(boolean condition, String description) {
        if (condition) {
            System.out.println("  [OK] " + description);
        } else {
            System.err.println("  [FAIL] " + description);
            throw new RuntimeException("Test Failed: " + description);
        }
    }
}