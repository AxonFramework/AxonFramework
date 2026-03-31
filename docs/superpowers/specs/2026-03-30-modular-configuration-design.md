# Modular Configuration for Event Processor Extensions

**Date:** 2026-03-30
**Author:** Mateusz Nowak (with Claude)
**Status:** Draft
**Since:** 5.1.0

## Problem

`PooledStreamingEventProcessorConfiguration` directly references `DeadLetterQueueConfiguration`,
`CachingSequencedDeadLetterQueue`, and related DLQ types. This coupling prevents moving DLQ to a
separate module and blocks other features (metrics, tracing, custom replay strategies) from plugging
into the processor configuration without modifying the core class.

Similarly, `PooledStreamingEventProcessorModule` contains ~100 lines of DLQ-specific logic (queue
creation, component decoration, cache invalidation listeners) that should not live in the core
processor module.

## Goals

1. `PooledStreamingEventProcessorConfiguration` must know **nothing** about DLQ or any specific extension.
2. Any module should be able to extend the processor configuration with its own properties.
3. Type safety — extensions get the real parent type via `ConfigurationExtension<P>` generics,
   no casting inside extensions. The `extend()` call site uses runtime validation via constructor
   parameter type matching (not compile-time CRTP).
4. Preserve the `UnaryOperator` composition pattern inside extensions for natural merging.
5. Use existing framework infrastructure (`ConfigurationEnhancer`, `ComponentDecorator`) for behavior — no new behavioral abstractions.
6. Parent type compatibility validated at runtime via constructor parameter type matching.

## Non-Goals

- Redesigning the `Module` interface or introducing new module lifecycle concepts.
- Adding CRTP (Curiously Recurring Template Pattern) to `EventProcessorConfiguration` — see
  "Future Improvements" section.

## Design

### 1. `ExtensibleConfiguration` Interface

The contract for configurations that support modular extensions via `extend()`. Lives in the
`common` module.

```java
package org.axonframework.common.configuration;

/**
 * A configuration that supports modular extensions via {@link #extend(Class)}.
 * <p>
 * Extensions are created on first access and cached — subsequent calls to
 * {@code extend()} with the same type return the same instance. This enables
 * natural merging: defaults and per-instance overrides both mutate the same
 * extension object.
 *
 * @since 5.1.0
 */
public interface ExtensibleConfiguration {

    /**
     * Returns the extension of the given type, creating it on first access.
     * <p>
     * The extension is created via its single-argument constructor, receiving
     * {@code this} as the parent. If no compatible constructor exists (i.e., the
     * extension's parent type is not assignable from this configuration's type),
     * an {@link AxonConfigurationException} is thrown.
     * <p>
     * Subsequent calls with the same type return the cached instance.
     *
     * @param type The extension class.
     * @param <T>  The extension type.
     * @return The extension instance.
     * @throws AxonConfigurationException if the extension cannot be created.
     */
    <T extends ConfigurationExtension<?>> T extend(Class<T> type);
}
```

### 2. `ConfigurationExtension<P>` Abstract Class

The base class for all extensions. Parameterized with the parent type `P`, so extensions get
full typed access to the parent's API. Delegates `extend()` to the parent for chaining.
Lives in the `common` module.

```java
package org.axonframework.common.configuration;

/**
 * A modular extension of an {@link ExtensibleConfiguration}. Parameterized with
 * the parent configuration type {@code P} so that subclasses get full typed
 * access to the parent's API.
 * <p>
 * Extensions are pure data — they hold settings but carry no behavior.
 * Behavior that acts on extension data belongs in a {@link ConfigurationEnhancer}.
 * <p>
 * Subclasses must provide a single-argument constructor accepting their parent type.
 * The constructor parameter type doubles as the parent compatibility constraint —
 * {@link ExtensibleConfiguration#extend(Class)} validates compatibility by matching
 * the constructor parameter against the calling configuration's type.
 * <p>
 * Extensions can be chained: {@code config.extend(A.class).extend(B.class)} works
 * because {@link #extend(Class)} delegates to the parent's extension map.
 *
 * @param <P> The parent configuration type this extension is designed for.
 * @since 5.1.0
 */
public abstract class ConfigurationExtension<P extends ExtensibleConfiguration>
        implements ExtensibleConfiguration, DescribableComponent {

    protected final P parent;

    /**
     * Constructs the extension with its parent configuration.
     *
     * @param parent The parent configuration this extension belongs to.
     */
    protected ConfigurationExtension(P parent) {
        this.parent = parent;
    }

    /**
     * Delegates to the parent's extension map, enabling chaining:
     * {@code config.extend(A.class).extend(B.class)}.
     */
    @Override
    public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
        return parent.extend(type);
    }

    /**
     * Validates this extension's settings.
     *
     * @throws AxonConfigurationException if any settings are invalid.
     */
    public abstract void validate() throws AxonConfigurationException;
}
```

### 3. `ConfigurationExtensions` — Reusable Container Helper

An internal helper that encapsulates all extension management: instance tracking, reflective
creation, constructor-based parent type validation, and lifecycle delegation. Lives in the
`common` module.

```java
package org.axonframework.common.configuration;

/**
 * Manages {@link ConfigurationExtension} instances for an {@link ExtensibleConfiguration}.
 * Handles creation via reflection, parent type validation via constructor parameter matching,
 * instance caching, and lifecycle delegation (validate, describeTo).
 *
 * @since 5.1.0
 */
public class ConfigurationExtensions {

    private final ExtensibleConfiguration owner;
    private final Map<Class<? extends ConfigurationExtension<?>>, ConfigurationExtension<?>> extensions =
            new LinkedHashMap<>();

    public ConfigurationExtensions(ExtensibleConfiguration owner) {
        this.owner = owner;
    }

    @SuppressWarnings("unchecked")
    public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
        return type.cast(extensions.computeIfAbsent(type, cls -> {
            try {
                for (var ctor : cls.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() == 1
                            && ctor.getParameterTypes()[0].isAssignableFrom(owner.getClass())) {
                        return (ConfigurationExtension<?>) ctor.newInstance(owner);
                    }
                }
                throw new AxonConfigurationException(
                        cls.getSimpleName() + " has no compatible constructor for "
                                + owner.getClass().getSimpleName()
                );
            } catch (ReflectiveOperationException e) {
                throw new AxonConfigurationException(
                        "Cannot create extension " + cls.getSimpleName(), e
                );
            }
        }));
    }

    public void validate() throws AxonConfigurationException {
        extensions.values().forEach(ConfigurationExtension::validate);
    }

    public void describe(ComponentDescriptor descriptor) {
        extensions.values().forEach(ext -> ext.describeTo(descriptor));
    }
}
```

### 4. `EventProcessorConfiguration` Implements `ExtensibleConfiguration`

The base configuration class delegates to a `ConfigurationExtensions` instance.

```java
public class EventProcessorConfiguration implements ExtensibleConfiguration, DescribableComponent {

    private final ConfigurationExtensions extensions = new ConfigurationExtensions(this);

    @Override
    public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
        return extensions.extend(type);
    }

    @Override
    protected void validate() throws AxonConfigurationException {
        // ... existing validation ...
        extensions.validate();
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        // ... existing description ...
        extensions.describe(descriptor);
    }
}
```

The copy constructor copies the `ConfigurationExtensions` state so that subclass constructors
(like `PooledStreamingEventProcessorConfiguration(base)`) inherit extensions from the base.

**Runtime parent type validation via constructor matching:**
- `pooledConfig.extend(DeadLetterQueueConfigurationExtension.class)` — finds
  `constructor(PooledStreamingEventProcessorConfiguration)` — works.
- `subscribingConfig.extend(DeadLetterQueueConfigurationExtension.class)` — no matching
  constructor — fails with clear `AxonConfigurationException`.

### 4. DLQ Extension — Configuration Side

`DeadLetterQueueConfigurationExtension` extends `ConfigurationExtension<PooledStreamingEventProcessorConfiguration>`.
It wraps `DeadLetterQueueConfiguration` and exposes a lambda-based API for composition.

```java
package org.axonframework.messaging.eventhandling.deadletter;

public class DeadLetterQueueConfigurationExtension
        extends ConfigurationExtension<PooledStreamingEventProcessorConfiguration> {

    private UnaryOperator<DeadLetterQueueConfiguration> customization = UnaryOperator.identity();

    public DeadLetterQueueConfigurationExtension(PooledStreamingEventProcessorConfiguration parent) {
        super(parent);
    }

    /**
     * Customizes the DLQ configuration. Customizations compose — each call layers
     * on top of previous ones.
     */
    public DeadLetterQueueConfigurationExtension deadLetterQueue(
            UnaryOperator<DeadLetterQueueConfiguration> customization
    ) {
        var previous = this.customization;
        this.customization = config -> customization.apply(previous.apply(config));
        return this;
    }

    /**
     * Resolves the DLQ configuration by applying all accumulated customizations
     * to a fresh default instance.
     */
    public DeadLetterQueueConfiguration deadLetterQueue() {
        return customization.apply(new DeadLetterQueueConfiguration());
    }

    @Override
    public void validate() throws AxonConfigurationException {
        deadLetterQueue().validate();
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        deadLetterQueue().describeTo(descriptor);
    }
}
```

**`PooledStreamingEventProcessorConfiguration` changes:**
- **Remove:** `deadLetterQueueCustomization` field
- **Remove:** `deadLetterQueue(UnaryOperator<DeadLetterQueueConfiguration>)` setter
- **Remove:** `deadLetterQueue()` getter
- **Remove:** All DLQ-related imports
- **Remove:** DLQ entry from `describeTo()` (now handled generically via extensions)

**`DeadLetterQueueConfiguration` stays as-is** — no changes needed. It remains a standalone
fluent configuration class used inside the extension.

### 5. Usage — Chaining and Natural Merging

**Defaults + per-processor override:**
```java
configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
    // Global defaults
    .defaults(config -> {
        config.extend(DeadLetterQueueConfigurationExtension.class)
              .deadLetterQueue(dlq -> dlq.enabled().factory(jpaFactory));
        return config;
    })
    // Per-processor override — same extension instance, customizations compose
    .processor("my-processor", phase -> phase
        .eventHandlingComponents(...)
        .customized((cfg, config) -> {
            config.extend(DeadLetterQueueConfigurationExtension.class)
                  .deadLetterQueue(dlq -> dlq.cacheMaxSize(4096));
            return config;
        })
    )
));
// Result: enabled=true, factory=jpaFactory, cacheMaxSize=4096
```

**Chaining between extensions:**
```java
config.extend(DeadLetterQueueConfigurationExtension.class)
      .deadLetterQueue(dlq -> dlq.enabled())
      .extend(MetricsExtension.class)   // chains! delegates to parent.extend()
      .metricsEnabled(true);
```

### 6. DLQ Decoupling — Behavior Side

**`DeadLetterQueueEnhancer` implements `ConfigurationEnhancer`:**

Located in the DLQ package (`messaging/eventhandling/deadletter/`). Discovered via ServiceLoader
(`META-INF/services/org.axonframework.common.configuration.ConfigurationEnhancer`).

Responsibilities:
1. Registers a `ComponentDecorator` for `EventHandlingComponent` that:
   - Reads `DeadLetterQueueConfigurationExtension` from the processor config via `extend()`
   - Calls `deadLetterQueue()` to get the resolved DLQ config
   - If disabled: returns the delegate unchanged
   - If enabled: creates `SequencedDeadLetterQueue` via the configured factory, wraps with
     `CachingSequencedDeadLetterQueue` if `cacheMaxSize > 0`, and returns a
     `DeadLetteringEventHandlingComponent` wrapping the delegate
2. Handles segment change listener registration for DLQ cache invalidation
3. Uses `processorComponentDlqName()` naming pattern (moved from `PooledStreamingEventProcessorModule`)

Order: defined relative to the event processor defaults enhancer order constant (e.g.,
`EventProcessorDefaults.ENHANCER_ORDER + 1`), ensuring it runs after processor defaults are applied.

The enhancer is inherited into processor module nested registries via
`DefaultComponentRegistry.copyWithDecoratorsAndEnhancers()`. Within the module scope:
1. Module registers components (`EventHandlingComponent`, etc.)
2. Inherited enhancers run (`invokeEnhancers()` in `doBuild()`)
3. Decorators applied when components are resolved from the built `Configuration`

**Reading the extension from the enhancer's decorator:**
```java
(config, name, delegate) -> {
    var processorConfig = config.getOptionalComponent(PooledStreamingEventProcessorConfiguration.class);
    if (processorConfig.isEmpty()) {
        return delegate;
    }
    var dlqConfig = processorConfig.get()
                                   .extend(DeadLetterQueueConfigurationExtension.class)
                                   .deadLetterQueue();
    if (!dlqConfig.isEnabled()) {
        return delegate;
    }
    // Create queue, wrap delegate with DeadLetteringEventHandlingComponent
    ...
}
```

### 7. `PooledStreamingEventProcessorModule` Cleanup

Remove all DLQ logic:
- `registerDeadLetterQueues()` method — deleted entirely
- DLQ section of `registerCustomizedConfiguration()` — deleted (segment listener registration
  moves to enhancer)
- DLQ decorator logic in `registerEventHandlingComponents()` — deleted
- `processorComponentDlqName()` helper — moves to `DeadLetterQueueEnhancer`

The module's `build()` sequence becomes simpler:
1. `registerCustomizedConfiguration()` — no DLQ logic
2. ~~`registerDeadLetterQueues()`~~ — removed
3. `registerTokenStore()`
4. `registerUnitOfWorkFactory()`
5. `registerEventHandlingComponents()` — no DLQ decoration
6. `registerEventProcessor()`

### 8. Spring Integration

The Spring layer uses a SPI-based approach: each extension Spring module provides a customizer
bean that the main Spring configurer discovers and applies during processor defaults setup.

#### `ProcessorConfigurationExtensionCustomizer` — Spring SPI

```java
package org.axonframework.extension.spring.config;

/**
 * SPI for Spring modules to apply extension-specific settings to processor
 * configurations. Discovered via autowiring. Applied after core processor defaults.
 * <p>
 * Each extension Spring module provides a bean of this type. The main Spring
 * configurer collects all beans and invokes them during processor defaults setup.
 *
 * @since 5.1.0
 */
@FunctionalInterface
public interface ProcessorConfigurationExtensionCustomizer {

    /**
     * Customizes a processor configuration with extension-specific settings.
     *
     * @param axonConfig      The Axon configuration (for component lookup).
     * @param processorConfig The processor configuration to extend.
     */
    void customize(Configuration axonConfig, EventProcessorConfiguration processorConfig);
}
```

#### DLQ Properties — Separate `@ConfigurationProperties` on Same Prefix

DLQ properties stay under `axon.eventhandling.processors.<name>.dlq` (no YAML change for users).
A separate `@ConfigurationProperties` class binds to the same `axon.eventhandling` prefix and
reads only the DLQ fields. Spring binds each class to its own fields, ignoring unrecognized ones.

```java
// In DLQ Spring module — reads only dlq.* from processor settings
@ConfigurationProperties("axon.eventhandling")
public class DeadLetterQueueProcessorProperties {
    private Map<String, DlqProcessorSettings> processors = new LinkedHashMap<>();

    public DlqProcessorSettings forProcessor(String processorName) {
        return processors.getOrDefault(processorName, new DlqProcessorSettings());
    }

    public static class DlqProcessorSettings {
        private Dlq dlq = new Dlq();
        // getter, setter
    }

    public static class Dlq {
        private boolean enabled = false;
        private Cache cache = new Cache();
        // getters, setters
    }

    public static class Cache {
        private int size = SequenceIdentifierCache.DEFAULT_MAX_SIZE;
        // getter, setter
    }
}
```

YAML stays unchanged:
```yaml
axon:
  eventhandling:
    processors:
      my-processor:
        type: pooled-streaming
        dlq:
          enabled: true
          cache:
            size: 1024
```

`EventProcessorProperties.ProcessorSettings` reads `type`, ignores `dlq`.
`DeadLetterQueueProcessorProperties.DlqProcessorSettings` reads `dlq`, ignores `type`.

#### DLQ Auto-Configuration — Provides Customizer Bean

```java
@Configuration
@ConditionalOnClass(DeadLetterQueueConfigurationExtension.class)
public class DeadLetterQueueAutoConfiguration {

    @Bean
    @ConfigurationProperties("axon.eventhandling")
    DeadLetterQueueProcessorProperties deadLetterQueueProcessorProperties() {
        return new DeadLetterQueueProcessorProperties();
    }

    @Bean
    @ConditionalOnBean(SequencedDeadLetterQueueFactory.class)
    ProcessorConfigurationExtensionCustomizer dlqExtensionCustomizer(
            DeadLetterQueueProcessorProperties properties,
            SequencedDeadLetterQueueFactory factory
    ) {
        return (axonConfig, processorConfig) -> {
            var processorName = processorConfig.processorName();
            var dlqProps = properties.forProcessor(processorName);
            if (dlqProps.getDlq().isEnabled()) {
                processorConfig.extend(DeadLetterQueueConfigurationExtension.class)
                    .deadLetterQueue(dlq -> dlq.enabled()
                        .factory(factory)
                        .cacheMaxSize(dlqProps.getDlq().getCache().getSize()));
            }
        };
    }
}
```

#### Main Spring Configurer — Discovery and Composition

`SpringCustomizations` (or equivalent) autowires all `ProcessorConfigurationExtensionCustomizer`
beans and applies them during processor defaults:

```java
@Autowired(required = false)
List<ProcessorConfigurationExtensionCustomizer> extensionCustomizers = List.of();

// During processor defaults setup:
.defaults((axonConfig, config) -> {
    // Core defaults (tokenStore, eventSource, etc.)
    // ...

    // Apply all extension customizers
    for (var customizer : extensionCustomizers) {
        customizer.customize(axonConfig, config);
    }
    return config;
})
```

#### Spring Integration Flow

```
SPRING BOOT STARTUP:
  1. Spring binds DeadLetterQueueProcessorProperties from axon.eventhandling.processors.*.dlq.*
  2. JpaDeadLetterQueueAutoConfiguration creates SequencedDeadLetterQueueFactory bean
  3. DeadLetterQueueAutoConfiguration creates ProcessorConfigurationExtensionCustomizer bean
  4. Main Spring configurer autowires List<ProcessorConfigurationExtensionCustomizer>

CONFIGURATION PHASE:
  5. For each processor, defaults are applied:
     a. Core defaults (tokenStore, eventSource, etc.)
     b. extensionCustomizers.forEach(c -> c.customize(axonConfig, config))
        → DLQ customizer calls config.extend(DLQExtension.class).deadLetterQueue(...)
  6. Per-processor overrides applied via .customized() (if any)

BUILD PHASE:
  7. PooledStreamingEventProcessorModule.build() — no DLQ logic
  8. DeadLetterQueueEnhancer runs — reads extension, creates queues, decorates components
```

## File Changes Summary

### New Files
| File | Module | Purpose |
|------|--------|---------|
| `ExtensibleConfiguration` | `common` | Interface with `extend()` method |
| `ConfigurationExtension<P>` | `common` | Abstract base for typed extensions |
| `ConfigurationExtensions` | `common` | Reusable helper managing extension instances, validation, description |
| `DeadLetterQueueConfigurationExtension` | `messaging` (deadletter package) | DLQ extension wrapping `DeadLetterQueueConfiguration` |
| `DeadLetterQueueEnhancer` | `messaging` (deadletter package) | `ConfigurationEnhancer` providing DLQ behavior |
| `META-INF/services/ConfigurationEnhancer` | `messaging` | ServiceLoader entry for `DeadLetterQueueEnhancer` |

### Modified Files
| File | Module | Changes |
|------|--------|---------|
| `EventProcessorConfiguration` | `messaging` | Implements `ExtensibleConfiguration`, adds extension map + `extend()` |
| `PooledStreamingEventProcessorConfiguration` | `messaging` | Removes all DLQ fields, methods, imports |
| `PooledStreamingEventProcessorModule` | `messaging` | Removes ~100 lines of DLQ logic |

### Unchanged Files
| File | Module | Reason |
|------|--------|--------|
| `DeadLetterQueueConfiguration` | `messaging` | Stays as-is — used inside the extension |

### Spring Integration — New Files
| File | Module | Purpose |
|------|--------|---------|
| `ProcessorConfigurationExtensionCustomizer` | `spring` | SPI interface for extension modules to apply settings |
| `DeadLetterQueueProcessorProperties` | `spring-boot-autoconfigure` | `@ConfigurationProperties("axon.eventhandling")` for DLQ per-processor settings |

### Spring Integration — Modified Files
| File | Module | Changes |
|------|--------|---------|
| `DeadLetterQueueAutoConfiguration` | `spring-boot-autoconfigure` | Provides `ProcessorConfigurationExtensionCustomizer` bean instead of direct DLQ wiring |
| `EventProcessorProperties` | `spring-boot-autoconfigure` | Remove `Dlq` inner class from `ProcessorSettings` |
| `SpringCustomizations` | `spring` | Remove DLQ-specific logic, add extension customizer discovery via `List<ProcessorConfigurationExtensionCustomizer>` |

## Architectural Invariants

1. `PooledStreamingEventProcessorConfiguration` never imports any DLQ type.
2. `PooledStreamingEventProcessorModule` never imports any DLQ type.
3. `ConfigurationExtension` subclasses are pure data — no behavior.
4. Behavior for extensions lives in `ConfigurationEnhancer` implementations.
5. Extensions get full typed access to the parent via generic parameter `P`.
6. Parent type compatibility is validated at runtime via constructor parameter type matching — no
   separate `parentType()` method needed.
7. `extend()` is idempotent — first call creates, subsequent calls return cached instance.
8. Lambda-based composition (`UnaryOperator`) is preserved inside extensions for natural merging.
9. Extensions chain via delegated `extend()` — all extensions stored on the root parent's map.
10. The enhancer order is defined relative to processor defaults, not as a magic number.

## Design Decisions

### `extend()` is not compile-time constrained (no CRTP)

`ExtensibleConfiguration.extend(Class<T>)` accepts any `ConfigurationExtension<?>`. Parent type
compatibility is validated **at runtime** via constructor parameter type matching — if the
extension's constructor requires `PooledStreamingEventProcessorConfiguration` and you call
`extend()` on a `SubscribingEventProcessorConfiguration`, it fails immediately with a clear
`AxonConfigurationException`.

**Concrete limitation:** `extend()` cannot be overridden with a tighter bound in subclasses —
Java does not allow changing type parameter bounds in an override. This means the following
does NOT compile:

```java
// In PooledStreamingEventProcessorConfiguration — DOES NOT COMPILE
@Override
public <T extends ConfigurationExtension<? super PooledStreamingEventProcessorConfiguration>>
    T extend(Class<T> type) {
    return super.extend(type); // bounds don't match parent's signature
}
```

As a result, `extend()` accepts any `ConfigurationExtension<?>` at every call site.
Incompatible extensions (e.g., a DLQ extension on a subscribing processor) are caught only at
runtime by the constructor parameter type check in `ConfigurationExtensions.extend()`, which
throws a clear `AxonConfigurationException`.

**Where type safety IS enforced:**
- **Inside the extension:** `protected final P parent` gives full typed access to the parent
  API — no casting, full IDE completion.
- **At the call site:** the return type of `extend()` is correctly inferred as `T` — callers
  get the concrete extension type back.
- **At runtime:** constructor parameter matching fails fast with a descriptive error if the
  extension is incompatible with the parent.

**Where type safety is NOT enforced:**
- **At the `extend()` call site:** any `ConfigurationExtension<?>` is accepted by the compiler.
  There is no compile-time rejection of incompatible extensions. This is the trade-off of not
  using CRTP.

**Why not CRTP:** Adding `EventProcessorConfiguration<SELF extends EventProcessorConfiguration<SELF>>`
would enable compile-time constraints on `extend()` and also eliminate manual setter overrides in
subclasses (setters return `SELF`). However, it is a cross-cutting refactor that ripples through
the entire configurer DSL, module builders, customization interfaces, and all references to
`EventProcessorConfiguration`. This is a separate concern from the modular configuration goal
and should not be bundled with it.

## Future Improvements

### CRTP on `EventProcessorConfiguration` (potential)

If desired independently, `EventProcessorConfiguration` can be made generic via the Curiously
Recurring Template Pattern:

```java
public class EventProcessorConfiguration<SELF extends EventProcessorConfiguration<SELF>>
        implements ExtensibleConfiguration<SELF> {

    @SuppressWarnings("unchecked")
    public SELF errorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
        return (SELF) this;
    }

    // extend() would become compile-time constrained:
    public <T extends ConfigurationExtension<? super SELF>> T extend(Class<T> type) { ... }
}

public class PooledStreamingEventProcessorConfiguration
        extends EventProcessorConfiguration<PooledStreamingEventProcessorConfiguration> {
    // No setter overrides needed — SELF = PooledStreamingEventProcessorConfiguration
    // extend() only accepts extensions compatible with Pooled
}
```

**Benefits:** compile-time safety on `extend()`, eliminates manual setter overrides in subclasses.
**Cost:** every reference to `EventProcessorConfiguration` gains a type parameter, significant
codebase-wide refactor. Should be evaluated as a separate task/PR.

