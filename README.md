# Flora

Fast and lightweight event bus for Java with priority dispatch, annotations, and built-in asynchronous modes.

## Features

- Exact-type event routing with allocation-free synchronous dispatch.
- Ordered `ASYNC` and concurrent `ASYNC_PARALLEL` modes.
- Priority-based listeners with immutable snapshots and lock-free reads.
- Annotation registration through `@Commando` and `Flora.register()`.
- Compile-time generation of direct, type-safe bus accessors with `@EventType`.
- Bounded worker queues with backpressure instead of dropped events.
- Pure Java 17 with no runtime dependencies.

Flora runs on Java 17, 21, and 25. It is built with JDK 25 and Gradle 9.5.1 using
`--release 17`, so consumers do not need to target Java 25.

## Usage

See the compile-ready [example](src/test/java/example/Main.java) for manual listeners, annotations, priorities, and asynchronous dispatch.
The complete Java and Kotlin syntax reference is available in [docs/SYNTAX.md](docs/SYNTAX.md).

### Lambda subscriptions

No annotation or manually declared bus field is required:

```java
Subscription subscription = Flora.subscribe(UserLoginEvent.class, event -> audit(event));
Flora.post(new UserLoginEvent());

subscription.close();
```

Priority and dispatch mode are optional:

```java
Subscription subscription = Flora.subscribe(
        UserLoginEvent.class,
        10,
        DispatchMode.ASYNC,
        event -> audit(event)
);
```

The same Java API receives Kotlin lambdas through normal JVM SAM conversion:

```kotlin
val subscription = Flora.subscribe(UserLoginEvent::class.java) { event ->
    audit(event)
}
```

Lambda subscriptions and `@Commando` handlers use the same canonical bus for an event type and may
be mixed safely. Keep the returned `Subscription` and unsubscribe it when the owning component is
disabled or destroyed.

## Generated bus accessors

Annotate a public, non-generic event type and enable Flora as an annotation processor:

```java
@EventType
public final class UserLoginEvent {
}
```

```groovy
dependencies {
    implementation 'sweetie.evaware:flora:VERSION'
    annotationProcessor 'sweetie.evaware:flora:VERSION'
}
```

Flora generates `UserLoginEventBus`. Its public `BUS` field caches the exact bus once, so posting
does not perform the runtime-class lookup used by `Flora.post(Object)`:

```java
UserLoginEventBus.BUS.subscribe(new Listener<>(event -> audit(event)));
UserLoginEventBus.post(new UserLoginEvent());
```

Kotlin/JVM can use the same generated accessor through kapt:

```kotlin
plugins {
    kotlin("kapt")
}

dependencies {
    implementation("sweetie.evaware:flora:VERSION")
    kapt("sweetie.evaware:flora:VERSION")
}
```

Regular Kotlin functions and inline call sites work through the normal JVM ABI. A `suspend`
function is not a synchronous `Consumer` and is intentionally not accepted as a `@Commando`
handler. Bridge one explicitly from a lifecycle-owned `CoroutineScope` when coroutine cancellation
and failure semantics are required; do not use an implicit or global scope.

### GitHub Packages

```groovy
repositories {
    maven {
        url 'https://maven.pkg.github.com/evaware-dev/Flora'
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'sweetie.evaware:flora:VERSION'
}
```

### JitPack

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.evaware-dev:Flora:TAG'
}
```

JitPack coordinates are derived from the GitHub owner and repository name and therefore differ from GitHub Packages coordinates.

## Dispatch behavior

- Events are dispatched by exact runtime class.
- Each bus is assigned to one ordered `ASYNC` worker lane.
- `ASYNC_PARALLEL` listener order is intentionally unspecified.
- Asynchronous listeners may finish after `post()` returns.
- Removing a subscription does not cancel callbacks that are already queued.

## License

GNU Lesser General Public License v3.0 only. See [LICENSE](LICENSE).
