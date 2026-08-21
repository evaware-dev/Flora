package example;

import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.Commando;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;

import java.util.concurrent.TimeUnit;

public class Main {

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

    public static class UserLoginEvent {
        public final String username;
        public final String ipAddress;

        public UserLoginEvent(String username, String ipAddress) {
            this.username = username;
            this.ipAddress = ipAddress;
        }
    }

    public static class ChatMessageEvent {
        public final String sender;
        public final String message;

        public ChatMessageEvent(String sender, String message) {
            this.sender = sender;
            this.message = message;
        }
    }

    public static class AnalyticsService {

        @Commando(priority = 10)
        public void onUserLoginFast(UserLoginEvent event) {
            LogType.ANALYTICS_FAST.log("User " + event.username + " initiated login.");
        }

        @Commando(mode = DispatchMode.ASYNC)
        public void onUserLoginAsync(UserLoginEvent event) {
            LogType.ANALYTICS_DB.log("Saving to DB: " + event.username + " from IP: " + event.ipAddress);
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            LogType.ANALYTICS_DB.log("Database write complete!");
        }
    }

    public static class ChatModerator {

        @Commando(mode = DispatchMode.ASYNC_PARALLEL)
        public void filterMessage(ChatMessageEvent event) {
            LogType.MODERATOR.log("Checking message from " + event.sender + " for spam...");
            if (event.message.toLowerCase().contains("spam")) {
                LogType.MODERATOR.log("Alert! Spam detected from " + event.sender);
            }
        }
    }

    public static void main(String[] args) {
        LogType.SYSTEM.log("Initializing Systems");

        AnalyticsService analytics = new AnalyticsService();
        ChatModerator moderator = new ChatModerator();

        Flora.register(analytics);
        Flora.register(moderator);
        Subscription audit = Flora.subscribe(UserLoginEvent.class,
                event -> LogType.ACTION.log("Audit: " + event.username));

        System.out.println();
        LogType.SYSTEM.log("Simulating Actions");

        LogType.ACTION.log("Posting UserLoginEvent...");
        Flora.post(new UserLoginEvent("Alex", "192.168.1.15"));

        LogType.ACTION.log("Posting ChatMessageEvent...");
        Flora.post(new ChatMessageEvent("Alex", "Hey everyone! This is spam :)"));
        Flora.post(new ChatMessageEvent("Maria", "Hi, Alex!"));

        if (!Flora.awaitQuiescence(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Flora workers did not become idle");
        }

        System.out.println();
        LogType.SYSTEM.log("Shutting Down Systems");

        Flora.unregister(analytics);
        Flora.unregister(moderator);
        audit.unsubscribe();
        Flora.shutdown();
        LogType.ACTION.log("Services successfully unregistered.");
    }
}
