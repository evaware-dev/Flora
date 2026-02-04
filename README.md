# Flora - EventBus for Java

_A high-performance event bus with zero-allocation dispatch, priority-based execution, and async support._

---

## Features

*   **Extreme Performance** - Zero-allocation on hot path. Direct array iteration without iterators.
*   **Hybrid Dispatch** - Supports **Synchronous** (blocking), **Async Single** (single thread), and **Async Parallel** (ForkJoinPool).
*   **Priority System** - Listeners execute in priority order (highest first).
*   **Thread-Safe** - Built on volatile arrays and copy-on-write principles.
*   **Zero Dependencies** - Pure Java, no external libraries required.
*   **Lightweight** - Minimal API surface, maximum efficiency.

---

## Usage

For a complete, compile-ready example demonstrating **annotations**, **zero-garbage dispatch**, and **async execution**, please see [example](src/test/java/example/Main.java).

---

## License

MIT License