# Flora - EventBus for Java

_A fast, lightweight event bus with priority-based dispatch, annotation support, and built-in async modes._

---

## Features

*   **Fast Hot Path** - Snapshot arrays, exact-type routing, and minimal overhead on `post()`.
*   **Hybrid Dispatch** - Supports **SYNC**, ordered striped **ASYNC**, and allocation-free **ASYNC_PARALLEL** worker lanes.
*   **Priority System** - Higher priority listeners are invoked first in **SYNC** and **ASYNC** modes.
*   **Annotation Registration** - Register `@Commando` methods through `Flora.register()`.
*   **Thread-Safe Core** - Subscribe/unsubscribe atomically publishes immutable snapshots; reads stay lock-free.
*   **Zero Dependencies** - Pure Java with no external runtime libraries.

---

## Requirements

*   **Java 17+**

---

## Installation

### GitHub Packages

GitHub Packages publishes Flora as `sweetie.evaware:flora` and requires GitHub credentials when resolving Maven packages.

```gradle
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

JitPack derives coordinates from the GitHub owner and repository name, so its dependency remains repository-based.

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.evaware-dev:Flora:TAG'
}
```

### GitHub Packages with Maven

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/evaware-dev/Flora</url>
    </repository>
</repositories>

<dependency>
    <groupId>sweetie.evaware</groupId>
    <artifactId>flora</artifactId>
    <version>VERSION</version>
</dependency>
```

---

## Usage

For a complete, compile-ready example with **manual listeners**, **annotations**, and **async dispatch**, see [example](src/test/java/example/Main.java).

---

## Notes

*   Events are dispatched by **exact class**.
*   Each bus is assigned to one ordered `ASYNC` worker lane. Independent buses are striped across lanes to reduce head-of-line blocking.
*   `ASYNC_PARALLEL` listeners run on Flora's parallel worker lanes; execution order is intentionally unspecified.
*   Async listeners may finish **after** `post()` returns.
*   Async queues are bounded. When a lane is full, `post()` applies backpressure with a short spin followed by parking instead of dropping events.
*   An already queued async callback may still run after its subscription is removed.

---

## License

GNU Lesser General Public License v3.0 only. See [LICENSE](LICENSE).
