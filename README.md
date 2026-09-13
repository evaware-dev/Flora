# Flora

Fast, lightweight, and allocation-free event bus for Java 17+ with priority dispatch, annotations, and built-in ring-buffer asynchronous workers.

## Features

- **Zero-allocation hot paths**: fast synchronous dispatch with prebuilt arrays.
- **Asynchronous modes**: ordered `ASYNC` lane and concurrent `ASYNC_PARALLEL` workers with progressive backpressure.
- **Priority dispatch**: deterministic listener ordering with short-circuit cancellation.
- **Flexible cancellation**: dynamic cancellation registration via `FloraConfigurator` without forced marker interfaces.
- **Annotations & Lambdas**: `@Commando` method handlers and inline lambda subscriptions share canonical buses.
- **Compile-time generation**: optional direct bus accessors with `@EventType`.
- **Pure Java 17+**: zero external runtime dependencies.

## Installation

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
    annotationProcessor 'sweetie.evaware:flora:VERSION' // optional, for @EventType code generation
}
```

### JitPack

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.evaware-dev:Flora:TAG'
    annotationProcessor 'com.github.evaware-dev:Flora:TAG' // optional, for @EventType code generation
}
```

## Documentation & Examples

- **[docs/SYNTAX.md](docs/SYNTAX.md)**: complete syntax reference separated for **Java** and **Kotlin**.
- **[example/](example/src/main/java/example/Main.java)**: runnable end-to-end sample application (`./gradlew :example:run`).

## License

GNU Lesser General Public License v3.0 only. See [LICENSE](LICENSE).
