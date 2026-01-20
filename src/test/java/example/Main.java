package example;

import sweetie.evaware.Flora;
import sweetie.evaware.FloraAutomation;
import sweetie.evaware.annotations.Commando;
import sweetie.evaware.api.DispatchMode;
import sweetie.evaware.api.Subscription;
import sweetie.evaware.core.ConsumerListener;
import sweetie.evaware.core.FloraBus;

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

        @Commando(mode = DispatchMode.ASYNC_SINGLE)
        public void onHeavyLogic(MyEvent e) {
            System.out.println("[Async] Async on thread: " + Thread.currentThread().getName());
        }
    }

    public static void main(String[] args) {
        MyListener myListener = new MyListener();

        FloraAutomation.register(myListener);
        System.out.println("-> Listener registered via Annotations");

        ConsumerListener<MyEvent> manualListener = new ConsumerListener<>(
                0, e -> System.out.println("[Manual] Lambda listener executed!"
        ));
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
