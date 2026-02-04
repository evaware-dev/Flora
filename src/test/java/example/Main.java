package example;

import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.FloraAutomation;
import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.Listener;
import sweetie.evaware.flora.core.FloraBus;

public class Main {
    public static class MyEvent {
        public static final FloraBus<MyEvent> BUS = Flora.getBus(MyEvent.class);

        public final double x, y;
        public final long tickCount;

        public MyEvent(double x, double y, long tickCount) {
            this.x = x;
            this.y = y;
            this.tickCount = tickCount;
        }
    }

    public static class MyListener {
        @Commando(priority = 2)
        public void onPreEvent(MyEvent e) {
            System.out.println("[High Priority] Pre-calculation on tick: " + e.tickCount);
        }

        @Commando
        public void onEvent(MyEvent e) {
            System.out.println("[Normal] Tick at: " + e.x + ", " + e.y);
        }

        @Commando(mode = DispatchMode.ASYNC)
        public void onHeavyLogic(MyEvent e) {
            System.out.println("[Async] Async on thread: " + Thread.currentThread().getName());
        }
    }

    public static void main(String[] args) {
        MyListener myListener = new MyListener();

        FloraAutomation.register(myListener);
        System.out.println("-> Listener registered via Annotations");

        Listener<MyEvent> manualListener = new Listener<>(
                0, e -> System.out.println("[Manual] Lambda listener executed!"), DispatchMode.SYNC
        );
        Subscription manualToken = MyEvent.BUS.subscribe(manualListener);
        System.out.println("-> Manual listener subscribed");

        System.out.println("\n--- Starting loop ---");

        for (int i = 0; i < 3; i++) {
            System.out.println("\n> Run " + i);

            MyEvent.BUS.post(new MyEvent(60 * i, 5 + i, i));

        }

        System.out.println("\n--- Cleanup ---");

        FloraAutomation.unregister(myListener);

        manualToken.unsubscribe(); // or MyEvent.BUS.unsubscribe(manualListener);

        System.out.println("-> All listeners removed.");
    }
}
