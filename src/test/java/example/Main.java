package example;

import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.FloraAutomation;
import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.core.FloraBus;

public class Main {
    // Example event type.
    // Each event class can expose its own strongly-typed bus for direct posting/subscription.
    public static class TickEvent {
        public static final FloraBus<TickEvent> BUS = Flora.getBus(TickEvent.class);

        public final double x, y;
        public final long tickCount;

        public TickEvent(double x, double y, long tickCount) {
            this.x = x;
            this.y = y;
            this.tickCount = tickCount;
        }
    }

    // Example listener registered through annotations.
    public static class ExampleListener {
        // Higher priority listeners run first inside the same dispatch mode.
        @Commando(priority = 2)
        public void onPreTick(TickEvent event) {
            System.out.println("[High Priority] Pre-calculation on tick: " + event.tickCount);
        }

        // Default mode is SYNC.
        @Commando
        public void onTick(TickEvent event) {
            System.out.println("[Normal] Tick at: " + event.x + ", " + event.y);
        }

        // ASYNC listeners run on Flora's dedicated async worker thread.
        @Commando(mode = DispatchMode.ASYNC)
        public void onAsyncTick(TickEvent event) {
            System.out.println("[Async] Async on thread: " + Thread.currentThread().getName());
        }
    }

    public static void main(String[] args) {
        // Create an annotation-driven listener instance.
        ExampleListener annotationListener = new ExampleListener();

        // Register all @Commando methods on the instance.
        FloraAutomation.register(annotationListener);
        System.out.println("-> Annotation listener registered");

        // Create and register a manual listener.
        Listener<TickEvent> manualListener = new Listener<>(
                0,
                event -> System.out.println("[Manual] Lambda listener executed on tick: " + event.tickCount),
                DispatchMode.SYNC
        );
        Subscription manualSubscription = TickEvent.BUS.subscribe(manualListener);
        System.out.println("-> Manual listener subscribed");

        // Post a few events through the strongly-typed event bus.
        // SYNC listeners run immediately, while ASYNC listeners may print later.
        System.out.println("\n--- Starting loop ---");

        for (int i = 0; i < 3; i++) {
            System.out.println("\n> Run " + i);
            TickEvent.BUS.post(new TickEvent(60 * i, 5 + i, i));
        }

        // Cleanup is explicit.
        // Async output may still appear after this section because async dispatch is non-blocking.
        System.out.println("\n--- Cleanup ---");

        FloraAutomation.unregister(annotationListener);
        manualSubscription.unsubscribe(); // or TickEvent.BUS.unsubscribe(manualListener);

        System.out.println("-> All listeners removed.");
    }
}
