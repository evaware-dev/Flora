# Flora

Ultra-high-performance, allocation-free event system for Java 17+ and Kotlin with deterministic priority dispatch, polymorphic hierarchy caching, annotations, and built-in lock-free ring-buffer async workers.

## Features

- **Zero-allocation synchronous hot paths**: Cache-friendly dispatch over prebuilt flat arrays (`Consumer<T>[]`) with no iterator, wrapper, or lambda allocations.
- **Polymorphic dispatch**: Hierarchical listener resolution with `ClassValue` caching and lock-free per-event invalidation.
- **Asynchronous ring-buffer workers**: Dedicated ordered `ASYNC` lanes and distributed `ASYNC_PARALLEL` worker pools with progressive backpressure and disruptor-style sequence management.
- **Deterministic priorities**: Explicit execution ordering with synchronous short-circuit cancellation.
- **Dynamic cancellation**: Flexible predicate registration via `FloraConfigurator` without mandatory marker interfaces.
- **Annotations & Lambdas**: Method handlers (`@Commando`) converted to direct JVM call sites via `LambdaMetafactory`, alongside type-safe lambda subscriptions.
- **Compile-time bus generation**: Optional `@EventType` annotation processor generating direct bus accessors with configurable package and bus naming.
- **Pure Java 17+**: Zero external runtime dependencies.

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

ext {
    // See latest release: https://github.com/evaware-dev/Flora/releases
    flora_version = 'VERSION'
}

dependencies {
    implementation "sweetie.evaware:flora:$flora_version"

    // Optional, for @EventType compile-time generation (Java):
    annotationProcessor "sweetie.evaware:flora:$flora_version"

    // For Kotlin projects using kapt:
    // kapt "sweetie.evaware:flora:$flora_version"
}
```

### JitPack

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

ext {
    // See latest release: https://github.com/evaware-dev/Flora/releases
    flora_version = 'TAG'
}

dependencies {
    implementation "com.github.evaware-dev:Flora:$flora_version"

    // Optional, for @EventType compile-time generation:
    annotationProcessor "com.github.evaware-dev:Flora:$flora_version"
    // kapt "com.github.evaware-dev:Flora:$flora_version"
}
```

## Documentation & Examples

- **[docs/SYNTAX.md](docs/SYNTAX.md)**: complete syntax reference separated for **Java** and **Kotlin**.
- **[example/](example/src/main/java/example/Main.java)**: runnable end-to-end sample application (`./gradlew :example:run`).
