# Flora Syntax

Flora is an allocation-free event bus for Java 17+ and Kotlin with priority dispatch, annotations, and built-in async worker pools.

---

# Java

## Lambda Subscriptions

```java
Subscription sub = Flora.subscribe(UserLoginEvent.class, event -> {
    System.out.println("User: " + event.username());
});

Flora.post(new UserLoginEvent("Alex"));
sub.unsubscribe();
```

### Priority and Dispatch Mode

```java
Subscription sub = Flora.subscribe(
    UserLoginEvent.class,
    100,                     // Priority: higher runs first (default: 0)
    DispatchMode.ASYNC,      // SYNC (default), ASYNC, ASYNC_PARALLEL
    event -> saveToDatabase(event)
);
```

- `SYNC`: executes inline on the posting thread.
- `ASYNC`: executes in order on an asynchronous ring-buffer lane.
- `ASYNC_PARALLEL`: distributes concurrently across worker threads.

## Annotations (`@Commando`)

```java
public class PlayerService {
    @Commando(priority = 10)
    public void onLogin(UserLoginEvent event) {
        log(event);
    }

    @Commando(mode = DispatchMode.ASYNC)
    public void onMessage(ChatMessageEvent event) {
        process(event);
    }
}

// Instance registration
PlayerService service = new PlayerService();
Flora.register(service);
Flora.unregister(service);
```

### Static Handlers

```java
public class SecurityModule {
    @Commando(priority = 100)
    public static void onAttack(PlayerAttackEvent event) {
        if ("friendly-npc".equals(event.target())) {
            event.setCancelled(true);
        }
    }
}

Flora.register(SecurityModule.class);
Flora.unregister(SecurityModule.class);
```

## Cancellation (`FloraConfigurator`)

Register cancellation predicates dynamically for any class or interface without forced marker interfaces:

```java
FloraConfigurator.registerCancellation(PlayerAttackEvent.class, PlayerAttackEvent::isCancelled);

// Or for an interface:
FloraConfigurator.registerCancellation(Cancellable.class, Cancellable::isCancelled);
```

When cancelled, subsequent lower-priority listeners are short-circuited immediately.

## Custom Annotations & Error Handling

```java
// Register custom annotation
FloraConfigurator.registerAnnotation(MyHandler.class);

// Error handling
FloraConfigurator.setErrorHandler(error -> logger.error("Dispatch error", error));

// Throw exceptions immediately
FloraConfigurator.setFailFast(true);
```

## Direct Bus & `@EventType`

```java
// Direct bus access
FloraBus<UserLoginEvent> bus = Flora.getBus(UserLoginEvent.class);
bus.subscribe(event -> handle(event));
bus.post(new UserLoginEvent("Alex"));

// Generated accessor (@EventType)
@EventType
public record UserLoginEvent(String username) {}

UserLoginEventBus.post(new UserLoginEvent("Alex"));
UserLoginEventBus.BUS.subscribe(event -> handle(event));
```

## Lifecycle & Shutdown

```java
Flora.awaitQuiescence(5, TimeUnit.SECONDS);
Flora.shutdown();
```

---

# Kotlin

## Lambda Subscriptions

```kotlin
val sub = Flora.subscribe(UserLoginEvent::class.java) { event ->
    println("User: ${event.username}")
}

Flora.post(UserLoginEvent("Alex"))
sub.unsubscribe()
```

### Reified Helper

```kotlin
inline fun <reified T : Any> subscribe(
    priority: Int = 0,
    mode: DispatchMode = DispatchMode.SYNC,
    noinline listener: (T) -> Unit
): Subscription = Flora.subscribe(T::class.java, priority, mode, listener)

val sub = subscribe<UserLoginEvent>(priority = 10) { event ->
    println(event.username)
}
```

## Annotations (`@Commando`)

```kotlin
class PlayerService {
    @Commando(priority = 10)
    fun onLogin(event: UserLoginEvent) {
        println(event.username)
    }

    @Commando(mode = DispatchMode.ASYNC)
    fun onMessage(event: ChatMessageEvent) {
        process(event)
    }
}

val service = PlayerService()
Flora.register(service)
Flora.unregister(service)
```

### Singleton & Companion Objects

```kotlin
object SecurityModule {
    @JvmStatic
    @Commando(priority = 100)
    fun onAttack(event: PlayerAttackEvent) {
        if (event.target == "friendly-npc") {
            event.cancelled = true
        }
    }
}

Flora.register(SecurityModule::class.java)
Flora.unregister(SecurityModule::class.java)
```

## Cancellation (`FloraConfigurator`)

```kotlin
FloraConfigurator.registerCancellation(PlayerAttackEvent::class.java) { it.cancelled }

// Reified helper
inline fun <reified T : Any> registerCancellation(noinline predicate: (T) -> Boolean) {
    FloraConfigurator.registerCancellation(T::class.java, predicate)
}

registerCancellation<PlayerAttackEvent> { it.cancelled }
```

## Coroutines Integration

To call suspending functions from a listener, bridge them via a lifecycle-scoped `CoroutineScope`:

```kotlin
Flora.subscribe(ChatMessageEvent::class.java) { event ->
    serviceScope.launch {
        processSuspending(event)
    }
}
```

## Direct Bus & `@EventType`

```kotlin
@EventType
class UserLoginEvent(val username: String)

UserLoginEventBus.post(UserLoginEvent("Alex"))
UserLoginEventBus.BUS.subscribe { handle(it) }
```

## Lifecycle & Shutdown

```kotlin
Flora.awaitQuiescence(5, TimeUnit.SECONDS)
Flora.shutdown()
```
