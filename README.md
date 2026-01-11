# Flora - EventBus for Java

_A high-performance hybrid event bus with direct handler calls, priority-based execution, and async support._

---

## Features

*   **Extreme performance** - Uses `LambdaMetafactory` (invokedynamic) instead of Reflection. As fast as direct method calls.
*   **Zero-Allocation dispatch** - GC friendly.
*   **Hybrid Dispatch** - Supports both **Synchronous** (Blocking) and **Asynchronous** (ForkJoinPool) listeners.
*   **Annotation Support** - Clean `@Commando` syntax with automatic registration.
*   **Thread-Safety** - Built on volatile arrays and copy-on-write principles.

---

## Usage

For a complete, compile-ready example demonstrating **annotations**, **zero-garbage dispatch**, and **async execution**, please see [example](src/test/java/example/Main.java).