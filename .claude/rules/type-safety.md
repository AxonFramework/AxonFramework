# Type Safety Rules

## TypeReference for Generic Types

When using any API that accepts `Type`, **always use `TypeReference`** instead of raw `Class` when the target type is generic (e.g., `Map<String, String>`, `List<SomeType>`). This preserves full generic type information at runtime and avoids `@SuppressWarnings("unchecked")`.

### Pattern: Static TypeReference Constant

```java
// CORRECT - preserves Map<String, String> generic info, no unchecked warning
private static final TypeReference<Map<String, String>> METADATA_MAP_TYPE_REF = new TypeReference<>() {
};

Map<String, String> result = converter.convert(data, METADATA_MAP_TYPE_REF.getType());
```

```java
// WRONG - loses generic info, produces unchecked cast warning
@SuppressWarnings("unchecked")
Map<String, String> result = converter.convert(data, Map.class);
```

### Rules

- Define `TypeReference` constants as `private static final` fields when the type is reused
- Use `new TypeReference<TargetType>() {}` (anonymous subclass captures the generic type)
- Pass `.getType()` to APIs expecting `java.lang.reflect.Type`
- For simple non-generic types, `Class<T>` is fine (e.g., `converter.convert(data, String.class)`)

### Existing Examples in Codebase

- `AggregateBasedJpaEventStorageEngine`: `METADATA_MAP_TYPE_REF` for `Map<String, String>`
- `JpaSequencedDeadLetterQueue`: `DIAGNOSTICS_MAP_TYPE_REF` for `Map<String, String>`
