# Flora - EventBus for Java

_A lightweight, high-performance event bus with direct handler calls and priority-based execution_.

---

## Features

* **Blazing fast dispatch** — no reflection, sub-nanosecond per handler call.
* **Thread-safe** — concurrent collections ensure safe notifications across threads.
* **Pure Java** — no external dependencies.

---

## Quick Start

### Define an event and bus

```java
public class MeowEvent extends Event<MeowEvent.MeowEventData> {
    public static final MeowEvent BUS = new MeowEvent();

    public record MeowEventData(String message) { }
}
```

### Create listeners

```java
// Default priority (0)
Listener<MeowEvent> defaultHandler = new Listener<>(event -> 
    System.out.println("Meow: " + event.msg()));

// Custom priority
Listener<MeowEvent> highPriorityHandler = new Listener<>(5, event -> 
    System.out.println("High priority meow: " + event.msg()));
```

### Subscribe, dispatch, unsubscribe

```java
EventListener sub = MeowEvent.BUS.subscribe(defaultHandler);  // register
MeowEvent.BUS.notify(new MeowEvent(new MeowEvent.MeowEventData("Hello!"))); // dispatch
sub.unsubscribe();  // unregister
```

> Listeners are executed based on priority: the higher the priority, the earlier they run.