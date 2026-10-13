# java-deep-copy-task

A universal deep copy of objects of arbitrary structure: `CopyUtils.deepCopy`.

## Problem

Implement a method that makes a full (deep) copy of an object:

```java
ComplexObject obj = ...;
ComplexObject copy = CopyUtils.deepCopy(obj);
```

Java classes can be arbitrarily complex: arbitrary field types, nested arrays and
collections, recursive structures (an object references itself).

Requirements:
- standard J2SE public API only;
- Java 21+;
- `java.io.Serializable` and `java.lang.Cloneable` are **forbidden**;
- a working `main()` for demonstration is required.

## Approach

- Traverse the object graph using reflection.
- `IdentityHashMap` (original → copy) breaks cycles and preserves the topology of
  shared references (aliasing); the copy is registered **before** fields/elements
  are traversed.
- Type categories:
  - immutable types (wrappers, `String`, `BigInteger`/`BigDecimal`, `enum`, `Class`,
    `java.time.*`, `UUID`, etc.) — by reference;
  - arrays (including multi-dimensional and primitive) — a new array of the same type;
  - collections (`List`/`Set`/`Queue`/`Deque`) and `Map` — a new instance of the right type;
  - `record` — via the canonical constructor;
  - immutable/wrapper collections (`List.of`, `Collections.unmodifiable*`) — immutability
    is preserved (`List.copyOf`/`Set.copyOf`/`Map.copyOf`, `unmodifiable*`);
  - plain objects — create the instance and copy fields across the whole hierarchy.
- Instance creation without `sun.misc.Unsafe` (path B):
  1. an accessible no-arg constructor;
  2. otherwise a constructor invoked with default arguments (`null`/`0`/`false`),
     followed by overwriting every field;
  3. otherwise `DeepCopyException`.

## Structure

```
src/main/java/com/example/deepcopy/
  CopyUtils.java          — public entry point deepCopy
  ObjectCreator.java      — instance creation strategy
  DeepCopyException.java  — copy exception
  Man.java                — the class from the spec
  Main.java               — demonstration
src/test/java/com/example/deepcopy/
  CopyUtilsTest.java      — JUnit 5 tests
```

## Build & run

JDK 21+ is required. A global `mvn` is not needed — the project wrapper is used.

```powershell
# tests
.\mvnw.cmd test

# full build (compile + tests + jar)
.\mvnw.cmd clean package

# run the demonstration
.\mvnw.cmd -q exec:java
```

## Limitations

- Only public J2SE reflection is used. For a class without a no-arg constructor,
  a constructor is invoked with default arguments and the fields are overwritten
  afterwards. If the constructor has side effects or strict argument validation,
  copying may fail with `DeepCopyException` (see the `uncopyableThrows` test).
- Records are copied via their canonical constructor. A cyclic structure that passes
  through a record is not supported and raises `DeepCopyException`.
- `transient` fields are copied like regular fields.
- Non-static inner classes cannot be copied (their constructor requires the enclosing
  instance) and raise `DeepCopyException`.
- Unmodifiable sorted views are reproduced as `SortedSet`/`SortedMap` wrappers, so a
  `NavigableSet`/`NavigableMap` type is not preserved.
