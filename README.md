<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/banner-dark.png">
    <source media="(prefers-color-scheme: light)" srcset="assets/banner-light.png">
    <img alt="Flora" src="assets/banner-dark.png" height="72">
  </picture>

  <p>Allocation-free event bus for Java 17+ and Kotlin with lock-free async ring buffers.</p>

  <p>
    <img alt="Java 17+" src="https://img.shields.io/badge/Java-17%2B-1f2328?style=flat&labelColor=0d1117">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-1f2328?style=flat&labelColor=0d1117"></a>
  </p>
</div>

## Features

- **Zero-allocation sync dispatch**: flat `Consumer<T>[]` hot paths without iterator, wrapper, or lambda allocations.
- **Polymorphic hierarchy caching**: `ClassValue`-backed listener resolution with lock-free per-event invalidation.
- **Lock-free async ring buffers**: ordered `ASYNC` lanes and distributed `ASYNC_PARALLEL` worker pools with progressive backpressure.
- **Priorities & cancellation**: deterministic ordering, short-circuit cancellation, and predicate registration via `Config` without marker interfaces.
- **Annotations & lambdas**: `@Commando` methods bound directly via `LambdaMetafactory`, alongside type-safe lambda subscriptions.
- **Global or isolated buses**: use the static facade or independent `EventSystem` instances with separate worker pools and no external runtime dependencies.

## Async behavior

- Queues are bounded; external producers wait for capacity, while posting from an async callback to a full queue throws `RejectedExecutionException` (in `ASYNC_PARALLEL`, earlier callbacks may already be enqueued).
- Nonfatal callback and cancellation-predicate exceptions are routed to the system exception handler; failed cancellation checks skip the queued task and keep the worker running.
- `VirtualMachineError` terminates the affected worker and closes its queue; per-bus exception handlers apply to synchronous listeners only.

## Installation

### GitHub Packages

```groovy
repositories {
    mavenCentral()
    maven {
        url 'https://maven.pkg.github.com/evaware-dev/Flora'
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation "sweetie.evaware:flora:$flora_version"
}
```

### JitPack

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation "com.github.evaware-dev:Flora:$flora_version"
}
```

Releases: [github.com/evaware-dev/Flora/releases](https://github.com/evaware-dev/Flora/releases)

## Documentation

- [Syntax reference](docs/SYNTAX.md) — Java and Kotlin usage guide
- [Example project](example/src/main/java/example/Main.java) — runnable sample (`./gradlew :example:run`)

[MIT License](LICENSE)
