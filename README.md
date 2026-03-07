# Flora - EventBus for Java

_A fast, lightweight event bus with priority-based dispatch, annotation support, and built-in async modes._

---

## Features

*   **Fast Hot Path** - Snapshot arrays, exact-type routing, and minimal overhead on `post()`.
*   **Hybrid Dispatch** - Supports **SYNC**, **ASYNC** (single worker), and **ASYNC_PARALLEL** (ForkJoinPool).
*   **Priority System** - Higher priority listeners run first inside their dispatch mode.
*   **Annotation Automation** - Register handlers with `@Commando` through `FloraAutomation`.
*   **Thread-Safe Core** - Subscribe/unsubscribe rebuilds snapshots, reads stay lock-free.
*   **Zero Dependencies** - Pure Java with no external runtime libraries.

---

## Requirements

*   **Java 17+**

---

## Installation

### Gradle
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.evaware-dev:Flora:VERSION'
}
```

### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.evaware-dev</groupId>
    <artifactId>Flora</artifactId>
    <version>VERSION</version>
</dependency>
```

---

## Usage

For a complete, compile-ready example with **manual listeners**, **annotations**, and **async dispatch**, see [example](src/test/java/example/Main.java).

---

## Notes

*   Events are dispatched by **exact class**.
*   `ASYNC` listeners run on a single dedicated worker thread.
*   `ASYNC_PARALLEL` listeners are submitted to `ForkJoinPool.commonPool()`.
*   Async listeners may finish **after** `post()` returns.

---

## License

MIT License
