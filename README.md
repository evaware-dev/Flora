# Flora

Fast and lightweight event bus for Java with priority dispatch, annotations, and built-in asynchronous modes.

## Features

- Exact-type event routing with allocation-free synchronous dispatch.
- Ordered `ASYNC` and concurrent `ASYNC_PARALLEL` modes.
- Priority-based listeners with immutable snapshots and lock-free reads.
- Annotation registration through `@Commando` and `Flora.register()`.
- Bounded worker queues with backpressure instead of dropped events.
- Pure Java 17 with no runtime dependencies.

## Usage

See the compile-ready [example](src/test/java/example/Main.java) for manual listeners, annotations, priorities, and asynchronous dispatch.

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
