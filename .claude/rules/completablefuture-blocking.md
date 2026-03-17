# CompletableFuture — Never Block (join() / get()) Without a Timeout

## Rule

**Never call `CompletableFuture.join()` or `.get()` without a timeout in framework code.** A blocking call without a deadline silently turns a transient issue (connection pool exhaustion, lock contention, deadlock, network partition) into a permanent thread leak with no diagnostics.

## Preferred Approach (Java 21+)

**`future.orTimeout(...).join()`** is the idiomatic choice in modern Java. It uses standard JDK APIs (available since Java 9), requires no utility classes, and composes naturally in `CompletableFuture` chains.

```java
Result result = future.orTimeout(30, TimeUnit.SECONDS).join();
```

Use this by default unless you have a specific reason to unwrap exceptions.

## Options for Adding a Timeout

### 1. `future.orTimeout(...).join()` — preferred, standard JDK

The idiomatic approach. Failures surface as `CompletionException` wrapping the cause. Use this when the caller already handles `CompletionException`, or when the result feeds into further `CompletableFuture` composition.

```java
Result result = future.orTimeout(30, TimeUnit.SECONDS).join();
```

### 2. `FutureUtils.joinAndUnwrap()` — bridging async internals behind synchronous contracts

Particularly useful when a synchronous API contract (often inherited from Axon Framework 4) must be preserved, but the underlying implementation is now async in Axon Framework 5. In these cases you need to block internally without leaking `CompletionException` through the public API — `joinAndUnwrap` sneaky-throws the original cause so the caller sees the real exception type as if the code were synchronous.

This is especially important when existing caller code has `try-catch` blocks expecting specific exception types (e.g., `catch (SomeBusinessException e)`). If the code that throws that exception now runs inside a `CompletableFuture`, a plain `join()` would wrap it in `CompletionException` and the existing catch would no longer match. `joinAndUnwrap` preserves the original exception type so surrounding try-catch blocks continue to work unchanged.

**This is a temporal bridge.** These blocking points should be eliminated as the framework moves to fully async across all layers. Each `joinAndUnwrap` call site is a candidate for future removal once the surrounding API becomes async-native.

```java
// Default 30-second safety-net timeout
Result result = FutureUtils.joinAndUnwrap(future);

// Explicit timeout
Result result = FutureUtils.joinAndUnwrap(future, Duration.ofSeconds(10));
```

### 3. `future.get(timeout, unit)` — legacy, avoid in new code

Requires catching three checked exceptions (`InterruptedException`, `ExecutionException`, `TimeoutException`). Prefer `orTimeout(...).join()` or `joinAndUnwrap()` in new code.

## Summary

| Approach                                         | Timeout    | Exception unwrapping       | When to use                                |
|--------------------------------------------------|------------|----------------------------|--------------------------------------------|
| `future.orTimeout(...).join()`                   | Explicit   | No — `CompletionException` | **Default choice** — standard JDK, idiomatic |
| `FutureUtils.joinAndUnwrap(future)`              | 30s default | Yes — sneaky-throws cause  | You want the original exception type       |
| `FutureUtils.joinAndUnwrap(future, duration)`    | Explicit   | Yes — sneaky-throws cause  | Same, with custom timeout                  |
| `future.get(timeout, unit)`                      | Explicit   | No — checked exceptions    | Legacy code only                           |
| `future.join()` / `future.get()`                 | **None**   | —                          | **Never use — blocks indefinitely**        |
