package sweetie.evaware;

public class Event<T> extends Flora<T> {
    private boolean cancel = false;

    @SuppressWarnings("unchecked")
    protected T getSelf() {
        return (T) this;
    }

    public void setCancel(boolean v) {
        cancel = v;
    }

    public boolean isCancel() {
        return cancel;
    }

    public boolean call() {
        cancel = false;
        notify(getSelf());
        return cancel;
    }

    public boolean call(T any) {
        cancel = false;
        notify(any);
        return cancel;
    }
}
