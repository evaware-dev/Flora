package example;

import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.FloraAutomation;
import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.core.FloraBus;

public class Main {

    // Bla bla bla
    public enum LogType {
        SYSTEM("--- ", " ---"),
        ACTION("> ", ""),
        ANALYTICS_FAST("[Analytics-Fast] ", ""),
        ANALYTICS_DB("[Analytics-DB] ", ""),
        MODERATOR("[Moderator] ", "");

        private final String prefix;
        private final String suffix;

        LogType(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public void log(String message) {
            System.out.println(prefix + message + suffix);
        }
    }

    // Events
    public static class UserLoginEvent {
        public static final FloraBus<UserLoginEvent> BUS = Flora.getBus(UserLoginEvent.class);

        public final String username;
        public final String ipAddress;

        public UserLoginEvent(String username, String ipAddress) {
            this.username = username;
            this.ipAddress = ipAddress;
        }
    }

    public static class ChatMessageEvent {
        public static final FloraBus<ChatMessageEvent> BUS = Flora.getBus(ChatMessageEvent.class);

        public final String sender;
        public final String message;

        public ChatMessageEvent(String sender, String message) {
            this.sender = sender;
            this.message = message;
        }
    }

    // Listeners
    public static class AnalyticsService {

        // Runs synchronously with high priority for quick validation
        @Commando(priority = 10)
        public void onUserLoginFast(UserLoginEvent event) {
            LogType.ANALYTICS_FAST.log("User " + event.username + " initiated login.");
        }

        // Asynchronous task (non-blocking, e.g., DB write or metrics tracking)
        @Commando(mode = DispatchMode.ASYNC)
        public void onUserLoginAsync(UserLoginEvent event) {
            LogType.ANALYTICS_DB.log("Saving to DB: " + event.username + " from IP: " + event.ipAddress);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {} // Simulating network/DB latency
            LogType.ANALYTICS_DB.log("Database write complete!");
        }
    }

    public static class ChatModerator {

        // Processes messages in a parallel pool (ideal for heavy content filtering)
        @Commando(mode = DispatchMode.ASYNC_PARALLEL)
        public void filterMessage(ChatMessageEvent event) {
            LogType.MODERATOR.log("Checking message from " + event.sender + " for spam...");
            if (event.message.toLowerCase().contains("spam")) {
                LogType.MODERATOR.log("Alert! Spam detected from " + event.sender);
            }
        }
    }

    // Main loop
    public static void main(String[] args) {
        LogType.SYSTEM.log("Initializing Systems");

        AnalyticsService analytics = new AnalyticsService();
        ChatModerator moderator = new ChatModerator();

        // Register services within the Flora event bus
        FloraAutomation.register(analytics);
        FloraAutomation.register(moderator);

        System.out.println(); // Clean line break
        LogType.SYSTEM.log("Simulating Actions");

        // Posting events to the bus
        LogType.ACTION.log("Posting UserLoginEvent...");
        UserLoginEvent.BUS.post(new UserLoginEvent("Alex", "192.168.1.15"));

        LogType.ACTION.log("Posting ChatMessageEvent...");
        ChatMessageEvent.BUS.post(new ChatMessageEvent("Alex", "Hey everyone! This is spam :)"));
        ChatMessageEvent.BUS.post(new ChatMessageEvent("Maria", "Hi, Alex!"));

        // Wait momentarily to allow async threads to finish processing before JVM shutdown
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}

        System.out.println(); // Clean line break
        LogType.SYSTEM.log("Shutting Down Systems");

        FloraAutomation.unregister(analytics);
        FloraAutomation.unregister(moderator);
        LogType.ACTION.log("Services successfully unregistered.");
    }
}