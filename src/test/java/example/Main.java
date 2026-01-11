package example;

import sweetie.evaware.Flora;
import sweetie.evaware.FloraAutomation;
import sweetie.evaware.Listener;
import sweetie.evaware.EventListener;
import sweetie.evaware.annotations.Commando;

public class Main {
    public static class MyEvent extends Flora<MyEvent> {
        public static final MyEvent INSTANCE = new MyEvent();

        public double x, y;
        public long tickCount;

        private MyEvent() {}
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

        @Commando(async = true)
        public void onHeavyLogic(MyEvent e) {
            System.out.println("[Async] Async on thread: " + Thread.currentThread().getName());
        }
    }

    public static void main(String[] args) {
        MyListener myListener = new MyListener();

        FloraAutomation.register(myListener);
        System.out.println("-> Listener registered via Annotations");

        Listener<MyEvent> manualListener = new Listener<>(0, e -> {
            System.out.println("[Manual] Lambda listener executed!");
        });
        EventListener manualToken = MyEvent.INSTANCE.subscribe(manualListener);
        System.out.println("-> Manual listener subscribed");

        System.out.println("\n--- Starting loop ---");

        for (int i = 0; i < 3; i++) {
            System.out.println("\n> Run " + i);

            MyEvent.INSTANCE.tickCount = i;
            MyEvent.INSTANCE.x = 70 + i;
            MyEvent.INSTANCE.y = 2;

            MyEvent.INSTANCE.notify(MyEvent.INSTANCE);

        }

        System.out.println("\n--- Cleanup ---");

        FloraAutomation.unregister(myListener);

        manualToken.unsubscribe(); // or UpdateEvent.INSTANCE.unsubscribe(manualListener);

        System.out.println("-> All listeners removed.");
    }
}
