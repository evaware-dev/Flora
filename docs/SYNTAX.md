# Flora syntax

Flora is a Java 17 library and can be used from applications running on Java 17, 21, or 25.
Kotlin projects may target any of those JVM versions as long as the chosen target is supported by
their Kotlin compiler.

## Java

### Lambda subscription

This is the recommended API when a class has one or a few handlers. No annotation or stored bus is
required.

```java
Subscription subscription = Flora.subscribe(PlayerEvent.class, event -> {
    handle(event);
});

Flora.post(new PlayerEvent());
```

Keep the returned subscription for the lifetime of its owner and close it when that owner is
disabled or destroyed:

```java
subscription.close();
```

`close()` and `unsubscribe()` are equivalent. Calling either one more than once is safe.

### Priority and dispatch mode

```java
Subscription subscription = Flora.subscribe(
        PlayerEvent.class,
        10,
        DispatchMode.ASYNC,
        event -> handle(event)
);
```

Higher priority values run first. Available modes are:

- `SYNC` — invokes the listener on the posting thread before `post` returns.
- `ASYNC` — preserves ordering on the event type's asynchronous lane.
- `ASYNC_PARALLEL` — allows handlers to run concurrently.

Asynchronous queues are bounded. If a queue is full, the producer applies backpressure instead of
silently dropping an event.

### Annotation handlers

Annotations are convenient for services containing several handlers:

```java
public final class PlayerService {
    @Commando(priority = 10, mode = DispatchMode.SYNC)
    public void onPlayer(PlayerEvent event) {
        handle(event);
    }
}
```

Register and unregister the service itself:

```java
PlayerService service = new PlayerService();

Flora.register(service);
Flora.unregister(service);
```

A handler must accept exactly one event parameter. Annotated handlers and lambda subscriptions use
the same bus and may be mixed.

### Direct bus

Use direct access only for a measured hot path or when a separately owned bus is needed:

```java
FloraBus<PlayerEvent> bus = Flora.getBus(PlayerEvent.class);
Subscription subscription = bus.subscribe(new Listener<>(event -> handle(event)));

bus.post(new PlayerEvent());
subscription.close();
```

`Flora.getBus(PlayerEvent.class)` returns the canonical bus also used by `Flora.post`, annotations,
and lambda subscriptions. Constructing `new FloraBus<>()` creates an independent bus.

### Generated accessor

Annotate a public, non-generic event type:

```java
@EventType
public final class PlayerEvent {
}
```

Enable Flora as both the dependency and annotation processor:

```groovy
dependencies {
    implementation 'sweetie.evaware:flora:VERSION'
    annotationProcessor 'sweetie.evaware:flora:VERSION'
}
```

The processor creates `PlayerEventBus`:

```java
Subscription subscription = PlayerEventBus.BUS.subscribe(
        new Listener<>(event -> handle(event))
);

PlayerEventBus.post(new PlayerEvent());
```

The generated accessor caches the canonical bus, avoiding the event-class lookup during posting.

## Kotlin

### Java API from Kotlin

Kotlin lambdas are converted to the Java `Consumer` interface:

```kotlin
val subscription = Flora.subscribe(PlayerEvent::class.java) { event ->
    handle(event)
}

Flora.post(PlayerEvent())
subscription.close()
```

Priority and dispatch mode use the same overload as Java:

```kotlin
val subscription = Flora.subscribe(
    PlayerEvent::class.java,
    10,
    DispatchMode.ASYNC
) { event ->
    handle(event)
}
```

### Optional reified helper

Java cannot infer the runtime event class from a generic lambda. A Kotlin application can hide the
class token with its own small inline helper while Flora itself remains entirely Java:

```kotlin
inline fun <reified E : Any> subscribe(
    priority: Int = 0,
    mode: DispatchMode = DispatchMode.SYNC,
    noinline handler: (E) -> Unit
): Subscription = Flora.subscribe(
    E::class.java,
    priority,
    mode,
    java.util.function.Consumer(handler)
)
```

Usage:

```kotlin
val subscription = subscribe<PlayerEvent> { event ->
    handle(event)
}
```

The helper improves syntax but does not materially change dispatch performance.

### Suspend handlers

A `suspend` function is not a synchronous Java `Consumer`, so it is deliberately not accepted
directly. Launch it explicitly from a lifecycle-owned coroutine scope:

```kotlin
val subscription = Flora.subscribe(PlayerEvent::class.java) { event ->
    serviceScope.launch {
        handleSuspending(event)
    }
}
```

The owner of `serviceScope` remains responsible for cancellation and exception handling. Avoid a
global scope. Flora's `ASYNC` modes use Java worker threads; they do not turn a listener into a
coroutine.

### Generated accessors with kapt

```kotlin
plugins {
    kotlin("kapt")
}

dependencies {
    implementation("sweetie.evaware:flora:VERSION")
    kapt("sweetie.evaware:flora:VERSION")
}
```

The generated Java accessor can then be used normally from Kotlin:

```kotlin
val subscription = PlayerEventBus.BUS.subscribe(
    Listener { event -> handle(event) }
)

PlayerEventBus.post(PlayerEvent())
```

## Errors and shutdown

Listener failures are isolated and passed to the configured error handler:

```java
Flora.setErrorHandler(Throwable::printStackTrace);
```

Applications using asynchronous modes can wait for queued work and shut the shared engine down:

```java
Flora.awaitQuiescence(5, TimeUnit.SECONDS);
Flora.shutdown();
```

`shutdown()` is application-wide and should normally be called only during final application
shutdown, not when an individual service is disabled.
