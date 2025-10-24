package sweetie.evaware;

public record EventListener(Runnable action) {
    public void unsubscribe() {
        action.run();
    }
}
