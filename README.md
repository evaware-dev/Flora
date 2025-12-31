# Flora - EventBus for Java

_A high-performance hybrid event bus with direct handler calls, priority-based execution, and async support._

---

## Features

* **High Performance** - Direct `Consumer` array iteration without reflection. Sub-nanosecond overhead for sync calls.
* **Hybrid Dispatch** - Supports both **Synchronous** (Blocking) and **Asynchronous** (Threaded) listeners on the same event.
* **Thread-Safety** - Concurrent collections and Copy-On-Write architecture ensure safe operations across threads.
* **Zero-Allocation** - Dispatching events creates zero garbage (GC friendly).
* **Pure Java** - No external dependencies.

---

## Usage

### Define an event

Create a class that extends `Event`.

```java
public class UpdateEvent extends Event<UpdateEvent> {
    public static final UpdateEvent BUS = new UpdateEvent();
    
    private final float partialTicks;
    
    public UpdateEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }
    
    public float partialTicks() { return partialTicks; }
}
```

### Create listeners

Flora supports two types of listeners:
* **Sync (Default):** Runs on the current thread. Use for rendering and thread-unsafe operations.
* **Async:** Runs in `ForkJoinPool`. Use for heavy calculations, I/O, or pathfinding.

```java
// Synchronous Listener
Listener<UpdateEvent> renderListener = new Listener<>(Priority.HIGH, event -> {
    RenderSystem.render(event.partialTicks());
});

// Asynchronous Listener (async = true)
Listener<UpdateEvent> logicListener = new Listener<>(Priority.NORMAL, true, event -> {
    // Runs in a separate thread
    Pathfinder.calculate();
});
```

### Subscribe & Notify

Register your listeners and dispatch the event.

```java
// Subscription
EventListener renderSub = UpdateEvent.BUS.subscribe(renderListener);
EventListener logicSub = UpdateEvent.BUS.subscribe(logicListener);

// Notification (dispatches to both sync and async listeners)
UpdateEvent.BUS.notify(new UpdateEvent(1.0f));

// Unsubscription
renderSub.unsubscribe();
```