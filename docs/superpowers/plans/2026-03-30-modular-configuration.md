# Modular Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple `PooledStreamingEventProcessorConfiguration` and `PooledStreamingEventProcessorModule` from `DeadLetterQueueConfiguration` by introducing a general-purpose extension mechanism (`ExtensibleConfiguration` + `ConfigurationExtension<P>`), then moving DLQ logic into its own extension + `ConfigurationEnhancer`.

**Architecture:** Add `ExtensibleConfiguration` interface and `ConfigurationExtension<P>` abstract class to the `common` module. `EventProcessorConfiguration` implements `ExtensibleConfiguration` via a `ConfigurationExtensions` helper. DLQ config moves into `DeadLetterQueueConfigurationExtension`. DLQ behavior (queue creation, component decoration) moves into `DeadLetterQueueEnhancer` (ServiceLoader-discovered `ConfigurationEnhancer`). Spring integration uses `ProcessorConfigurationExtensionCustomizer` SPI with separate `DeadLetterQueueProcessorProperties`.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Maven (no new dependencies)

**Spec:** `docs/superpowers/specs/2026-03-30-modular-configuration-design.md`

---

### Task 1: Create `ExtensibleConfiguration` Interface

**Files:**
- Create: `common/src/main/java/org/axonframework/common/configuration/ExtensibleConfiguration.java`
- Test: `common/src/test/java/org/axonframework/common/configuration/ExtensibleConfigurationTest.java`

- [ ] **Step 1: Create `ExtensibleConfiguration` interface**

```java
/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.configuration;

import org.axonframework.common.AxonConfigurationException;

/**
 * A configuration that supports modular extensions via {@link #extend(Class)}.
 * <p>
 * Extensions are created on first access and cached — subsequent calls to
 * {@code extend()} with the same type return the same instance. This enables
 * natural merging: defaults and per-instance overrides both mutate the same
 * extension object.
 *
 * @author Mateusz Nowak
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

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw compile -pl common -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add common/src/main/java/org/axonframework/common/configuration/ExtensibleConfiguration.java
git commit -m "feat: add ExtensibleConfiguration interface for modular config extensions"
```

---

### Task 2: Create `ConfigurationExtension<P>` Abstract Class

**Files:**
- Create: `common/src/main/java/org/axonframework/common/configuration/ConfigurationExtension.java`

- [ ] **Step 1: Create `ConfigurationExtension` abstract class**

```java
/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.configuration;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.infra.DescribableComponent;

import java.util.Objects;

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
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public abstract class ConfigurationExtension<P extends ExtensibleConfiguration>
        implements ExtensibleConfiguration, DescribableComponent {

    /**
     * The parent configuration this extension belongs to.
     */
    protected final P parent;

    /**
     * Constructs the extension with its parent configuration.
     *
     * @param parent The parent configuration this extension belongs to.
     */
    protected ConfigurationExtension(P parent) {
        this.parent = Objects.requireNonNull(parent, "Parent configuration may not be null");
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

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw compile -pl common -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add common/src/main/java/org/axonframework/common/configuration/ConfigurationExtension.java
git commit -m "feat: add ConfigurationExtension<P> abstract base for typed extensions"
```

---

### Task 3: Create `ConfigurationExtensions` Helper + Tests

**Files:**
- Create: `common/src/main/java/org/axonframework/common/configuration/ConfigurationExtensions.java`
- Create: `common/src/test/java/org/axonframework/common/configuration/ConfigurationExtensionsTest.java`

- [ ] **Step 1: Write the failing tests**

Create `common/src/test/java/org/axonframework/common/configuration/ConfigurationExtensionsTest.java`:

```java
/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.configuration;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.infra.ComponentDescriptor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfigurationExtensionsTest {

    @Nested
    class WhenExtending {

        @Test
        void createsExtensionOnFirstAccess() {
            // given
            var owner = new StubExtensibleConfiguration();
            var extensions = new ConfigurationExtensions(owner);

            // when
            var extension = extensions.extend(StubExtension.class);

            // then
            assertThat(extension).isNotNull();
            assertThat(extension).isInstanceOf(StubExtension.class);
        }

        @Test
        void returnsSameInstanceOnSubsequentAccess() {
            // given
            var owner = new StubExtensibleConfiguration();
            var extensions = new ConfigurationExtensions(owner);

            // when
            var first = extensions.extend(StubExtension.class);
            var second = extensions.extend(StubExtension.class);

            // then
            assertThat(first).isSameAs(second);
        }

        @Test
        void passesOwnerAsParent() {
            // given
            var owner = new StubExtensibleConfiguration();
            var extensions = new ConfigurationExtensions(owner);

            // when
            var extension = extensions.extend(StubExtension.class);

            // then
            assertThat(extension.parent).isSameAs(owner);
        }

        @Test
        void throwsForIncompatibleConstructor() {
            // given
            var owner = new StubExtensibleConfiguration();
            var extensions = new ConfigurationExtensions(owner);

            // when / then
            assertThatThrownBy(() -> extensions.extend(IncompatibleExtension.class))
                    .isInstanceOf(AxonConfigurationException.class)
                    .hasMessageContaining("IncompatibleExtension")
                    .hasMessageContaining("has no compatible constructor");
        }

        @Test
        void supportsDifferentExtensionTypes() {
            // given
            var owner = new StubExtensibleConfiguration();
            var extensions = new ConfigurationExtensions(owner);

            // when
            var ext1 = extensions.extend(StubExtension.class);
            var ext2 = extensions.extend(AnotherStubExtension.class);

            // then
            assertThat(ext1).isNotSameAs(ext2);
            assertThat(ext1).isInstanceOf(StubExtension.class);
            assertThat(ext2).isInstanceOf(AnotherStubExtension.class);
        }
    }

    @Nested
    class WhenValidating {

        @Test
        void validatesAllExtensions() {
            // given
            var owner = new StubExtensibleConfiguration();
            var extensions = new ConfigurationExtensions(owner);
            var ext = extensions.extend(StubExtension.class);
            ext.validated = false;

            // when
            extensions.validate();

            // then
            assertThat(ext.validated).isTrue();
        }
    }

    @Nested
    class WhenDescribing {

        @Test
        void describesAllExtensions() {
            // given
            var owner = new StubExtensibleConfiguration();
            var extensions = new ConfigurationExtensions(owner);
            extensions.extend(StubExtension.class);
            var descriptor = mock(ComponentDescriptor.class);

            // when
            extensions.describe(descriptor);

            // then
            verify(descriptor).describeProperty("stub", "value");
        }
    }

    // --- Test fixtures ---

    static class StubExtensibleConfiguration implements ExtensibleConfiguration {
        private final ConfigurationExtensions extensions = new ConfigurationExtensions(this);

        @Override
        public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
            return extensions.extend(type);
        }
    }

    public static class StubExtension extends ConfigurationExtension<StubExtensibleConfiguration> {
        boolean validated = false;

        public StubExtension(StubExtensibleConfiguration parent) {
            super(parent);
        }

        @Override
        public void validate() {
            validated = true;
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
            descriptor.describeProperty("stub", "value");
        }
    }

    public static class AnotherStubExtension extends ConfigurationExtension<StubExtensibleConfiguration> {
        public AnotherStubExtension(StubExtensibleConfiguration parent) {
            super(parent);
        }

        @Override
        public void validate() {
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
        }
    }

    /**
     * Extension whose constructor requires a type that StubExtensibleConfiguration does NOT satisfy.
     */
    public static class IncompatibleExtension extends ConfigurationExtension<IncompatibleParent> {
        public IncompatibleExtension(IncompatibleParent parent) {
            super(parent);
        }

        @Override
        public void validate() {
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
        }
    }

    static class IncompatibleParent implements ExtensibleConfiguration {
        @Override
        public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
            return null;
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl common -Dtest=ConfigurationExtensionsTest -q`
Expected: FAIL (ConfigurationExtensions class doesn't exist yet)

- [ ] **Step 3: Create `ConfigurationExtensions` implementation**

Create `common/src/main/java/org/axonframework/common/configuration/ConfigurationExtensions.java`:

```java
/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.configuration;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages {@link ConfigurationExtension} instances for an {@link ExtensibleConfiguration}.
 * Handles creation via reflection, parent type validation via constructor parameter matching,
 * instance caching, and lifecycle delegation (validate, describeTo).
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class ConfigurationExtensions {

    private final ExtensibleConfiguration owner;
    private final Map<Class<? extends ConfigurationExtension<?>>, ConfigurationExtension<?>> extensions =
            new LinkedHashMap<>();

    /**
     * Constructs a new helper for the given owner configuration.
     *
     * @param owner The configuration that owns these extensions.
     */
    public ConfigurationExtensions(ExtensibleConfiguration owner) {
        this.owner = Objects.requireNonNull(owner, "Owner may not be null");
    }

    /**
     * Returns the extension of the given type, creating it on first access via reflection.
     * Validates that the extension's constructor parameter type is compatible with the owner's type.
     *
     * @param type The extension class.
     * @param <T>  The extension type.
     * @return The extension instance.
     * @throws AxonConfigurationException if no compatible constructor is found.
     */
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

    /**
     * Validates all registered extensions.
     *
     * @throws AxonConfigurationException if any extension fails validation.
     */
    public void validate() throws AxonConfigurationException {
        extensions.values().forEach(ConfigurationExtension::validate);
    }

    /**
     * Describes all registered extensions to the given descriptor.
     *
     * @param descriptor The descriptor to write to.
     */
    public void describe(ComponentDescriptor descriptor) {
        extensions.values().forEach(ext -> ext.describeTo(descriptor));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl common -Dtest=ConfigurationExtensionsTest -q`
Expected: BUILD SUCCESS — all tests pass

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/org/axonframework/common/configuration/ConfigurationExtensions.java
git add common/src/test/java/org/axonframework/common/configuration/ConfigurationExtensionsTest.java
git commit -m "feat: add ConfigurationExtensions helper for extension lifecycle management"
```

---

### Task 4: Make `EventProcessorConfiguration` Implement `ExtensibleConfiguration`

**Files:**
- Modify: `messaging/src/main/java/org/axonframework/messaging/eventhandling/configuration/EventProcessorConfiguration.java`

- [ ] **Step 1: Add `ExtensibleConfiguration` implementation to `EventProcessorConfiguration`**

Add import:
```java
import org.axonframework.common.configuration.ConfigurationExtension;
import org.axonframework.common.configuration.ConfigurationExtensions;
import org.axonframework.common.configuration.ExtensibleConfiguration;
```

Change class declaration from:
```java
public class EventProcessorConfiguration implements DescribableComponent {
```
to:
```java
public class EventProcessorConfiguration implements ExtensibleConfiguration, DescribableComponent {
```

Add field after existing fields (after line 66):
```java
    private final ConfigurationExtensions extensions = new ConfigurationExtensions(this);
```

Add `extend()` method (after `unitOfWorkFactory()` getter, around line 159):
```java
    @Override
    public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
        return extensions.extend(type);
    }
```

Update `validate()` method (line 139-141) to also validate extensions:
```java
    protected void validate() throws AxonConfigurationException {
        assertNonNull(unitOfWorkFactory, "The UnitOfWorkFactory is a hard requirement and should be provided");
        extensions.validate();
    }
```

Update `describeTo()` method (line 170-175) to also describe extensions:
```java
    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("errorHandler", errorHandler);
        descriptor.describeProperty("unitOfWorkFactory", unitOfWorkFactory);
        descriptor.describeProperty("interceptors", interceptors);
        extensions.describe(descriptor);
    }
```

Update copy constructor (line 97-105) to copy extensions state. Since `ConfigurationExtensions` uses a map internally, we need to add a copy method. First, add to `ConfigurationExtensions`:

In `ConfigurationExtensions.java`, add a `copyTo` method:
```java
    /**
     * Copies all registered extensions to the target helper.
     *
     * @param target The target to copy extensions to.
     */
    @Internal
    public void copyTo(ConfigurationExtensions target) {
        target.extensions.putAll(this.extensions);
    }
```

Then update the copy constructor in `EventProcessorConfiguration`:
```java
    @Internal
    public EventProcessorConfiguration(EventProcessorConfiguration base) {
        Objects.requireNonNull(base, "Base configuration may not be null");
        this.processorName = base.processorName;
        this.errorHandler = base.errorHandler();
        this.unitOfWorkFactory = base.unitOfWorkFactory();
        this.interceptors = new ArrayList<>(base.interceptors);
        this.interceptorBuilder = base.interceptorBuilder;
        this.monitorBuilder = base.monitorBuilder;
        base.extensions.copyTo(this.extensions);
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw compile -pl messaging -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run existing tests to ensure no regressions**

Run: `./mvnw test -pl messaging -Dtest=PooledStreamingEventProcessorConfigurationTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add messaging/src/main/java/org/axonframework/messaging/eventhandling/configuration/EventProcessorConfiguration.java
git add common/src/main/java/org/axonframework/common/configuration/ConfigurationExtensions.java
git commit -m "feat: EventProcessorConfiguration implements ExtensibleConfiguration"
```

---

### Task 5: Create `DeadLetterQueueConfigurationExtension`

**Files:**
- Create: `messaging/src/main/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueConfigurationExtension.java`
- Create: `messaging/src/test/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueConfigurationExtensionTest.java`

- [ ] **Step 1: Write the failing tests**

Create `messaging/src/test/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueConfigurationExtensionTest.java`:

```java
/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadLetterQueueConfigurationExtensionTest {

    private PooledStreamingEventProcessorConfiguration createParent() {
        return new PooledStreamingEventProcessorConfiguration(
                new org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration(
                        "test-processor", null
                )
        );
    }

    @Nested
    class WhenCreated {

        @Test
        void hasDisabledDlqByDefault() {
            // given
            var parent = createParent();

            // when
            var extension = new DeadLetterQueueConfigurationExtension(parent);

            // then
            assertThat(extension.deadLetterQueue().isEnabled()).isFalse();
        }

        @Test
        void hasParentReference() {
            // given
            var parent = createParent();

            // when
            var extension = new DeadLetterQueueConfigurationExtension(parent);

            // then
            assertThat(extension.parent).isSameAs(parent);
        }
    }

    @Nested
    class WhenCustomizing {

        @Test
        void enablesDlqViaLambda() {
            // given
            var parent = createParent();
            var extension = new DeadLetterQueueConfigurationExtension(parent);

            // when
            extension.deadLetterQueue(dlq -> dlq.enabled());

            // then
            assertThat(extension.deadLetterQueue().isEnabled()).isTrue();
        }

        @Test
        void composesMultipleCustomizations() {
            // given
            var parent = createParent();
            var extension = new DeadLetterQueueConfigurationExtension(parent);

            // when
            extension.deadLetterQueue(dlq -> dlq.enabled());
            extension.deadLetterQueue(dlq -> dlq.cacheMaxSize(2048));

            // then
            var resolved = extension.deadLetterQueue();
            assertThat(resolved.isEnabled()).isTrue();
            assertThat(resolved.cacheMaxSize()).isEqualTo(2048);
        }

        @Test
        void returnsFreshInstanceOnEachResolve() {
            // given
            var parent = createParent();
            var extension = new DeadLetterQueueConfigurationExtension(parent);
            extension.deadLetterQueue(dlq -> dlq.enabled());

            // when
            var first = extension.deadLetterQueue();
            var second = extension.deadLetterQueue();

            // then
            assertThat(first).isNotSameAs(second);
            assertThat(first.isEnabled()).isTrue();
            assertThat(second.isEnabled()).isTrue();
        }

        @Test
        void supportsFluentChaining() {
            // given
            var parent = createParent();
            var extension = new DeadLetterQueueConfigurationExtension(parent);

            // when
            var result = extension
                    .deadLetterQueue(dlq -> dlq.enabled())
                    .deadLetterQueue(dlq -> dlq.cacheMaxSize(4096));

            // then
            assertThat(result).isSameAs(extension);
            assertThat(result.deadLetterQueue().isEnabled()).isTrue();
            assertThat(result.deadLetterQueue().cacheMaxSize()).isEqualTo(4096);
        }
    }

    @Nested
    class WhenAccessedViaExtend {

        @Test
        void canBeAccessedFromPooledConfig() {
            // given
            var parent = createParent();

            // when
            var extension = parent.extend(DeadLetterQueueConfigurationExtension.class);
            extension.deadLetterQueue(dlq -> dlq.enabled());

            // then
            assertThat(parent.extend(DeadLetterQueueConfigurationExtension.class)
                              .deadLetterQueue().isEnabled()).isTrue();
        }

        @Test
        void failsOnIncompatibleParent() {
            // given
            var subscribingConfig = new org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorConfiguration(
                    new org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration(
                            "test", null
                    )
            );

            // when / then
            assertThatThrownBy(() -> subscribingConfig.extend(DeadLetterQueueConfigurationExtension.class))
                    .isInstanceOf(AxonConfigurationException.class)
                    .hasMessageContaining("DeadLetterQueueConfigurationExtension")
                    .hasMessageContaining("has no compatible constructor");
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl messaging -Dtest=DeadLetterQueueConfigurationExtensionTest -q`
Expected: FAIL (class doesn't exist yet)

- [ ] **Step 3: Create `DeadLetterQueueConfigurationExtension`**

Create `messaging/src/main/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueConfigurationExtension.java`:

```java
/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.configuration.ConfigurationExtension;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;

import java.util.function.UnaryOperator;

/**
 * A {@link ConfigurationExtension} that provides {@link DeadLetterQueueConfiguration} settings
 * for a {@link PooledStreamingEventProcessorConfiguration}.
 * <p>
 * Wraps {@link DeadLetterQueueConfiguration} and exposes a lambda-based API for composition.
 * Customizations compose — each call to {@link #deadLetterQueue(UnaryOperator)} layers on
 * top of previous ones.
 * <p>
 * This extension is pure data — the behavior (queue creation, component decoration) is
 * provided by {@code DeadLetterQueueEnhancer}.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public class DeadLetterQueueConfigurationExtension
        extends ConfigurationExtension<PooledStreamingEventProcessorConfiguration> {

    private UnaryOperator<DeadLetterQueueConfiguration> customization = UnaryOperator.identity();

    /**
     * Constructs the extension for the given pooled streaming processor configuration.
     *
     * @param parent The parent processor configuration.
     */
    public DeadLetterQueueConfigurationExtension(PooledStreamingEventProcessorConfiguration parent) {
        super(parent);
    }

    /**
     * Customizes the DLQ configuration. Customizations compose — each call layers
     * on top of previous ones.
     *
     * @param customization A function that modifies the DLQ configuration.
     * @return This extension instance for fluent chaining.
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
     *
     * @return The resolved dead letter queue configuration.
     */
    public DeadLetterQueueConfiguration deadLetterQueue() {
        return customization.apply(new DeadLetterQueueConfiguration());
    }

    @Override
    public void validate() throws AxonConfigurationException {
        // DLQ configuration has no hard requirements — it's valid even when disabled
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        deadLetterQueue().describeTo(descriptor);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl messaging -Dtest=DeadLetterQueueConfigurationExtensionTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add messaging/src/main/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueConfigurationExtension.java
git add messaging/src/test/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueConfigurationExtensionTest.java
git commit -m "feat: add DeadLetterQueueConfigurationExtension wrapping DLQ config"
```

---

### Task 6: Create `DeadLetterQueueEnhancer`

**Files:**
- Create: `messaging/src/main/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueEnhancer.java`
- Modify: `messaging/src/main/resources/META-INF/services/org.axonframework.common.configuration.ConfigurationEnhancer`

This is the behavior side — the enhancer registers decorators that read the DLQ extension config and create DLQ queue components + wrap `EventHandlingComponent` with `DeadLetteringEventHandlingComponent`.

- [ ] **Step 1: Create `DeadLetterQueueEnhancer`**

Create `messaging/src/main/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueEnhancer.java`.

This class moves the DLQ behavior from `PooledStreamingEventProcessorModule` into a `ConfigurationEnhancer`. The exact implementation depends on the `DecoratorDefinition` API and how `ComponentRegistry` works. The enhancer should:

1. Register a `ComponentDecorator` for `EventHandlingComponent` that reads the DLQ extension config, and if enabled, wraps the delegate with `DeadLetteringEventHandlingComponent`
2. Handle `SequencedDeadLetterQueue` component registration
3. Handle segment change listener registration for cache invalidation

The engineer should study the existing DLQ code in `PooledStreamingEventProcessorModule` (lines 126-141, 153-177, 236-268) and migrate it into the enhancer's `enhance()` method. The key patterns to preserve:
- `processorComponentDlqName()` naming: `"DeadLetterQueue[" + processorName + "][" + componentName + "]"`
- `CachingSequencedDeadLetterQueue` wrapping when `cacheMaxSize > 0`
- `SequencedDeadLetterProcessor` component registration
- Segment change listener for cache invalidation

**Note:** This task is the most complex. The engineer should read the spec section 6 ("DLQ Decoupling — Behavior Side") carefully. The decorator pattern shown in the spec provides the structure, but the exact `ComponentRegistry` API calls need to match the existing patterns in `PooledStreamingEventProcessorModule`.

- [ ] **Step 2: Add ServiceLoader entry**

Append to `messaging/src/main/resources/META-INF/services/org.axonframework.common.configuration.ConfigurationEnhancer`:
```
org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueEnhancer
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw compile -pl messaging -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add messaging/src/main/java/org/axonframework/messaging/eventhandling/deadletter/DeadLetterQueueEnhancer.java
git add messaging/src/main/resources/META-INF/services/org.axonframework.common.configuration.ConfigurationEnhancer
git commit -m "feat: add DeadLetterQueueEnhancer for DLQ behavior via ConfigurationEnhancer"
```

---

### Task 7: Remove DLQ from `PooledStreamingEventProcessorConfiguration`

**Files:**
- Modify: `messaging/src/main/java/org/axonframework/messaging/eventhandling/processing/streaming/pooled/PooledStreamingEventProcessorConfiguration.java`

- [ ] **Step 1: Remove DLQ imports**

Remove these imports:
```java
import org.axonframework.messaging.eventhandling.deadletter.CachingSequencedDeadLetterQueue;  // line 34
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfiguration;     // line 35
```

- [ ] **Step 2: Remove DLQ field**

Remove line 116:
```java
private UnaryOperator<DeadLetterQueueConfiguration> deadLetterQueueCustomization = UnaryOperator.identity();
```

- [ ] **Step 3: Remove `deadLetterQueue(UnaryOperator)` setter**

Remove lines 470-530 (the full method with its Javadoc).

- [ ] **Step 4: Remove `deadLetterQueue()` getter**

Remove lines 725-735 (the full method with its Javadoc).

- [ ] **Step 5: Remove DLQ from `describeTo()`**

Remove this line from `describeTo()` (line 753):
```java
descriptor.describeProperty("deadLetterQueue", deadLetterQueue());
```

- [ ] **Step 6: Verify it compiles**

Run: `./mvnw compile -pl messaging -q`
Expected: FAIL — `PooledStreamingEventProcessorModule` still references DLQ methods. This is expected; Task 8 will fix it.

- [ ] **Step 7: Commit**

```bash
git add messaging/src/main/java/org/axonframework/messaging/eventhandling/processing/streaming/pooled/PooledStreamingEventProcessorConfiguration.java
git commit -m "refactor: remove DLQ coupling from PooledStreamingEventProcessorConfiguration"
```

---

### Task 8: Remove DLQ from `PooledStreamingEventProcessorModule`

**Files:**
- Modify: `messaging/src/main/java/org/axonframework/messaging/eventhandling/processing/streaming/pooled/PooledStreamingEventProcessorModule.java`

- [ ] **Step 1: Remove DLQ imports**

Remove these imports:
```java
import org.axonframework.messaging.eventhandling.deadletter.CachingSequencedDeadLetterQueue;      // line 34
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfiguration;         // line 35
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventHandlingComponent;  // line 36
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;                       // line 38
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;                           // line 39
```

- [ ] **Step 2: Remove `registerDeadLetterQueues()` call from `build()`**

In `build()` (line 102-110), remove the call to `registerDeadLetterQueues()` (line 104).

- [ ] **Step 3: Delete `registerDeadLetterQueues()` method**

Delete the entire method (lines 153-177).

- [ ] **Step 4: Remove DLQ logic from `registerCustomizedConfiguration()`**

In `registerCustomizedConfiguration()` (lines 112-151), remove the DLQ section (lines 126-141) that reads `configuration.deadLetterQueue()` and adds segment change listeners for DLQ cache invalidation.

- [ ] **Step 5: Remove DLQ decorator from `registerEventHandlingComponents()`**

In `registerEventHandlingComponents()` (lines 215-271), remove:
- The DLQ decorator registration (lines 236-257 — the `DeadLetteringEventHandlingComponent` decorator)
- The `SequencedDeadLetterProcessor` component registration (lines 258-268)

- [ ] **Step 6: Delete `processorComponentDlqName()` method**

Delete the helper method (lines 287-289).

- [ ] **Step 7: Verify it compiles**

Run: `./mvnw compile -pl messaging -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Run existing tests**

Run: `./mvnw test -pl messaging -Dtest=PooledStreamingEventProcessorModuleTest -q`
Expected: Tests that test DLQ behavior may fail. These tests need to be updated to use the new extension mechanism. Evaluate which tests fail and update them to use `config.extend(DeadLetterQueueConfigurationExtension.class).deadLetterQueue(...)` instead of `config.deadLetterQueue(...)`.

- [ ] **Step 9: Commit**

```bash
git add messaging/src/main/java/org/axonframework/messaging/eventhandling/processing/streaming/pooled/PooledStreamingEventProcessorModule.java
git commit -m "refactor: remove DLQ coupling from PooledStreamingEventProcessorModule"
```

---

### Task 9: Update Existing Tests for New Extension API

**Files:**
- Modify: Tests that reference `config.deadLetterQueue(...)` or `PooledStreamingEventProcessorConfiguration.deadLetterQueue()`

- [ ] **Step 1: Find all test references to the old DLQ API**

Run: `grep -rn "deadLetterQueue\|DeadLetterQueueConfiguration" messaging/src/test/ --include="*.java" | grep -v "DeadLetterQueueConfigurationTest\|DeadLetterQueueConfigurationExtensionTest"`

Update each test to use the new extension API:

Old pattern:
```java
config.deadLetterQueue(dlq -> dlq.enabled())
```

New pattern:
```java
config.extend(DeadLetterQueueConfigurationExtension.class)
      .deadLetterQueue(dlq -> dlq.enabled())
```

Old pattern:
```java
config.deadLetterQueue()
```

New pattern:
```java
config.extend(DeadLetterQueueConfigurationExtension.class).deadLetterQueue()
```

- [ ] **Step 2: Run all messaging tests**

Run: `./mvnw test -pl messaging -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add messaging/src/test/
git commit -m "test: update tests to use DeadLetterQueueConfigurationExtension API"
```

---

### Task 10: Spring Integration — `ProcessorConfigurationExtensionCustomizer` SPI

**Files:**
- Create: `extensions/spring/spring/src/main/java/org/axonframework/extension/spring/config/ProcessorConfigurationExtensionCustomizer.java`

- [ ] **Step 1: Create the SPI interface**

```java
/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extension.spring.config;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;

/**
 * SPI for Spring modules to apply extension-specific settings to processor
 * configurations. Discovered via autowiring. Applied after core processor defaults.
 * <p>
 * Each extension Spring module provides a bean of this type. The main Spring
 * configurer collects all beans and invokes them during processor defaults setup.
 *
 * @author Mateusz Nowak
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

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw compile -pl extensions/spring/spring -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add extensions/spring/spring/src/main/java/org/axonframework/extension/spring/config/ProcessorConfigurationExtensionCustomizer.java
git commit -m "feat: add ProcessorConfigurationExtensionCustomizer Spring SPI"
```

---

### Task 11: Spring Integration — `DeadLetterQueueProcessorProperties`

**Files:**
- Create: `extensions/spring/spring-boot-autoconfigure/src/main/java/org/axonframework/extension/springboot/DeadLetterQueueProcessorProperties.java`

- [ ] **Step 1: Create the properties class**

```java
/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extension.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot configuration properties for Dead Letter Queue settings.
 * <p>
 * Binds to {@code axon.eventhandling.processors.<name>.dlq.*}. Uses the same
 * {@code axon.eventhandling} prefix as {@link EventProcessorProperties} — each class
 * reads its own fields, ignoring unrecognized ones.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@ConfigurationProperties("axon.eventhandling")
public class DeadLetterQueueProcessorProperties {

    private Map<String, DlqProcessorSettings> processors = new LinkedHashMap<>();

    public Map<String, DlqProcessorSettings> getProcessors() {
        return processors;
    }

    public void setProcessors(Map<String, DlqProcessorSettings> processors) {
        this.processors = processors;
    }

    /**
     * Returns the DLQ settings for the given processor name, or defaults if not configured.
     *
     * @param processorName The processor name.
     * @return The DLQ settings for the processor.
     */
    public DlqProcessorSettings forProcessor(String processorName) {
        return processors.getOrDefault(processorName, new DlqProcessorSettings());
    }

    public static class DlqProcessorSettings {
        private Dlq dlq = new Dlq();

        public Dlq getDlq() {
            return dlq;
        }

        public void setDlq(Dlq dlq) {
            this.dlq = dlq;
        }
    }

    public static class Dlq {
        private boolean enabled = false;
        private DlqCache cache = new DlqCache();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public DlqCache getCache() {
            return cache;
        }

        public void setCache(DlqCache cache) {
            this.cache = cache;
        }
    }

    public static class DlqCache {
        private int size = 1024;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw compile -pl extensions/spring/spring-boot-autoconfigure -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add extensions/spring/spring-boot-autoconfigure/src/main/java/org/axonframework/extension/springboot/DeadLetterQueueProcessorProperties.java
git commit -m "feat: add DeadLetterQueueProcessorProperties for Spring Boot DLQ config"
```

---

### Task 12: Spring Integration — Update `DeadLetterQueueAutoConfiguration`

**Files:**
- Modify: `extensions/spring/spring-boot-autoconfigure/src/main/java/org/axonframework/extension/springboot/autoconfig/JpaDeadLetterQueueAutoConfiguration.java`
- Modify: `extensions/spring/spring-boot-autoconfigure/src/main/java/org/axonframework/extension/springboot/autoconfig/JdbcDeadLetterQueueAutoConfiguration.java`

These autoconfigs already provide `SequencedDeadLetterQueueFactory` beans. Now they also need to provide a `ProcessorConfigurationExtensionCustomizer` bean.

- [ ] **Step 1: Add customizer bean to JPA auto-configuration**

Add a `ProcessorConfigurationExtensionCustomizer` bean to `JpaDeadLetterQueueAutoConfiguration` that reads `DeadLetterQueueProcessorProperties` and applies DLQ settings via the extension:

```java
@Bean
@ConditionalOnBean(SequencedDeadLetterQueueFactory.class)
ProcessorConfigurationExtensionCustomizer jpaDeadLetterQueueCustomizer(
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
```

Also add `@EnableConfigurationProperties(DeadLetterQueueProcessorProperties.class)` to the class or register the properties bean.

- [ ] **Step 2: Apply same pattern to JDBC auto-configuration**

Add the same customizer bean pattern to `JdbcDeadLetterQueueAutoConfiguration`.

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw compile -pl extensions/spring/spring-boot-autoconfigure -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add extensions/spring/spring-boot-autoconfigure/src/main/java/org/axonframework/extension/springboot/autoconfig/JpaDeadLetterQueueAutoConfiguration.java
git add extensions/spring/spring-boot-autoconfigure/src/main/java/org/axonframework/extension/springboot/autoconfig/JdbcDeadLetterQueueAutoConfiguration.java
git commit -m "feat: DLQ autoconfigs provide ProcessorConfigurationExtensionCustomizer beans"
```

---

### Task 13: Spring Integration — Update `SpringCustomizations` and `EventProcessorProperties`

**Files:**
- Modify: `extensions/spring/spring/src/main/java/org/axonframework/extension/spring/config/SpringCustomizations.java`
- Modify: `extensions/spring/spring-boot-autoconfigure/src/main/java/org/axonframework/extension/springboot/EventProcessorProperties.java`

- [ ] **Step 1: Remove DLQ logic from `SpringCustomizations`**

In `SpringCustomizations.SpringPooledStreamingEventProcessingModuleCustomization.apply()` (lines 164-170), remove the DLQ-specific block:
```java
if (settings.dlq().enabled()) {
    var dlqFactory = getComponent(configuration, SequencedDeadLetterQueueFactory.class, null, null);
    require(dlqFactory != null, ...);
    config = config.deadLetterQueue(dlq -> dlq.enabled().factory(dlqFactory).cacheMaxSize(...));
}
```

Add autowiring of `List<ProcessorConfigurationExtensionCustomizer>` and apply them after core defaults:
```java
for (var customizer : extensionCustomizers) {
    customizer.customize(configuration, config);
}
```

- [ ] **Step 2: Remove `Dlq` and `DlqCache` inner classes from `EventProcessorProperties`**

Remove `Dlq` class (lines 416-467) and `DlqCache` class (lines 472-498) from `EventProcessorProperties`.

Remove the `dlq` field from `ProcessorSettings` and the `dlq()` method from the `EventProcessorSettings.PooledEventProcessorSettings` conversion.

- [ ] **Step 3: Remove `DlqSettings` from `EventProcessorSettings`**

Remove the `dlq()` default method and `DlqSettings` interface from `EventProcessorSettings.java` (lines 145-147, 154-178).

- [ ] **Step 4: Verify it compiles**

Run: `./mvnw compile -pl extensions/spring -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Run Spring tests**

Run: `./mvnw test -pl extensions/spring -q`
Expected: Tests referencing `settings.dlq()` or `EventProcessorProperties.Dlq` need updating. Fix any failures.

- [ ] **Step 6: Commit**

```bash
git add extensions/spring/
git commit -m "refactor: remove DLQ coupling from SpringCustomizations and EventProcessorProperties"
```

---

### Task 14: Full Build Verification

**Files:** None (verification only)

- [ ] **Step 1: Run full build**

Run: `./mvnw clean verify -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run with examples profile**

Run: `./mvnw -Pexamples clean verify -q`
Expected: BUILD SUCCESS — example apps should still work (they use the Spring configurer DSL)

- [ ] **Step 3: Verify architectural invariants**

Run these grep checks to confirm no DLQ imports remain in the decoupled files:

```bash
# PooledStreamingEventProcessorConfiguration must NOT import any DLQ type
grep -n "deadletter\|DeadLetter" messaging/src/main/java/org/axonframework/messaging/eventhandling/processing/streaming/pooled/PooledStreamingEventProcessorConfiguration.java
# Expected: no output

# PooledStreamingEventProcessorModule must NOT import any DLQ type
grep -n "deadletter\|DeadLetter" messaging/src/main/java/org/axonframework/messaging/eventhandling/processing/streaming/pooled/PooledStreamingEventProcessorModule.java
# Expected: no output
```

- [ ] **Step 4: Final commit if any fixes were needed**

```bash
git add -A
git commit -m "fix: resolve full build issues after modular configuration migration"
```
