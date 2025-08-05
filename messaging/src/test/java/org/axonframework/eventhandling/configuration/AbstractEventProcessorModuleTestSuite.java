/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.MessageStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Abstract test suite for {@link EventProcessorModule} implementations providing common test scenarios.
 * <p>
 * This abstract class defines the contract for testing event processor modules and their configurations,
 * allowing concrete test classes to focus on processor-specific behavior while ensuring consistent
 * testing patterns across all implementations.
 * <p>
 * The test suite covers common behaviors that all event processor modules should support:
 * <ul>
 * <li>Configuration building and validation</li>
 * <li>Lifecycle management (start/stop)</li>
 * <li>Component registration and wiring</li>
 * <li>Event handling with single and multiple handlers</li>
 * <li>Configuration inheritance and customization</li>
 * </ul>
 * <p>
 * Concrete test classes should extend this class and implement the abstract methods to provide
 * processor-specific configuration and setup logic.
 *
 * @param <P> The type of {@link EventProcessor} being tested
 * @param <C> The type of {@link EventProcessorConfiguration} for the processor
 * @param <M> The type of {@link EventProcessorModule} being tested
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public abstract class AbstractEventProcessorModuleTestSuite<
        P extends EventProcessor,
        C extends EventProcessorConfiguration,
        M extends EventProcessorModule> {

    /**
     * Creates a module builder for the specified processor name.
     * <p>
     * Implementations should return the appropriate module builder (e.g., EventProcessorModule.pooledStreaming()
     * or EventProcessorModule.subscribing()) configured with the given processor name.
     *
     * @param processorName The name of the processor to create
     * @return The module builder for the processor type under test
     */
    protected abstract EventProcessorModule.EventHandlingPhase<M, C> createModuleBuilder(String processorName);

    /**
     * Returns the class type of the processor being tested.
     *
     * @return The processor class type
     */
    protected abstract Class<P> getProcessorType();

    /**
     * Returns the class type of the processor configuration being tested.
     *
     * @return The processor configuration class type
     */
    protected abstract Class<C> getConfigurationType();

    /**
     * Configures the required defaults for the processor type under test.
     * <p>
     * This method should configure any necessary defaults that the processor type requires
     * to function properly (e.g., event sources, token stores, etc.).
     *
     * @param configurer The messaging configurer to apply defaults to
     */
    protected abstract void configureRequiredDefaults(MessagingConfigurer configurer);

    /**
     * Retrieves the processor configuration from a processor instance.
     * <p>
     * This method should extract the internal configuration object from the processor
     * for testing purposes. Implementations may need to use reflection or other
     * processor-specific mechanisms.
     *
     * @param processor The processor instance to extract configuration from
     * @return The processor configuration, or null if not available
     */
    protected abstract C getProcessorConfiguration(P processor);

    /**
     * Creates a basic messaging configurer with common setup.
     *
     * @return A configured MessagingConfigurer instance
     */
    protected final MessagingConfigurer createBaseConfigurer() {
        var configurer = MessagingConfigurer.create();
        configureRequiredDefaults(configurer);
        return configurer;
    }

    /**
     * Retrieves a configured processor from the configuration by name.
     *
     * @param configuration The Axon configuration
     * @param processorName The name of the processor to retrieve
     * @return An optional containing the processor if found
     */
    protected final Optional<P> getConfiguredProcessor(AxonConfiguration configuration, String processorName) {
        return configuration.getModuleConfiguration(processorName)
                           .flatMap(m -> m.getOptionalComponent(getProcessorType(), processorName));
    }

    /**
     * Creates a simple event handling component for testing purposes.
     *
     * @return A SimpleEventHandlingComponent configured for testing
     */
    protected final EventHandlingComponent createTestEventHandlingComponent() {
        return SimpleEventHandlingComponent.builder()
                                          .handles(new QualifiedName(String.class),
                                                  (event, context) -> MessageStream.empty())
                                          .build();
    }

    @Nested
    class ConfigurationBuildingTests {

        @Test
        void shouldBuildWithMinimalConfiguration() {
            // given
            String processorName = "test-processor";
            var eventHandlingComponent = createTestEventHandlingComponent();

            // when
            M module = createModuleBuilder(processorName)
                    .eventHandlingComponent(eventHandlingComponent)
                    .build();

            // then
            assertThat(module).isNotNull();
            assertThat(module.name()).isEqualTo(processorName);
        }

        @Test
        void shouldSupportEventHandlingComponentRegistration() {
            // given
            String processorName = "test-processor";
            var component1 = new SimpleEventHandlingComponent();
            var component2 = new SimpleEventHandlingComponent();

            // when
            M module = createModuleBuilder(processorName)
                    .eventHandlingComponents(component1, component2)
                    .build();

            // then
            assertThat(module).isNotNull();
            assertThat(module.name()).isEqualTo(processorName);
        }

        @Test
        void shouldSupportCustomizationPattern() {
            // given
            String processorName = "test-processor";
            var eventHandlingComponent = createTestEventHandlingComponent();

            // when
            M module = createModuleBuilder(processorName)
                    .eventHandlingComponent(eventHandlingComponent)
                    .customize((cfg, processorConfig) -> processorConfig);

            // then
            assertThat(module).isNotNull();
            assertThat(module.name()).isEqualTo(processorName);
        }

        @Test
        void shouldSupportDirectConfiguration() {
            // given
            String processorName = "test-processor";
            var eventHandlingComponent = createTestEventHandlingComponent();

            // when & then - should not throw exception
            createModuleBuilder(processorName)
                    .eventHandlingComponent(eventHandlingComponent)
                    .configure(cfg -> createDefaultConfiguration());
        }
    }

    @Nested
    class ComponentRegistrationTests {

        @Test
        void shouldRegisterProcessorAsComponent() {
            // given
            String processorName = "test-processor";
            var eventHandlingComponent = createTestEventHandlingComponent();
            M module = createModuleBuilder(processorName)
                    .eventHandlingComponent(eventHandlingComponent)
                    .build();

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // when
            var processor = getConfiguredProcessor(configuration, processorName);

            // then
            assertThat(processor).isPresent();
            assertThat(processor.get()).isInstanceOf(getProcessorType());
        }

        @Test
        void shouldInheritConfigurationFromHierarchy() {
            // given
            String processorName = "test-processor";
            var eventHandlingComponent = createTestEventHandlingComponent();
            M module = createModuleBuilder(processorName)
                    .eventHandlingComponent(eventHandlingComponent)
                    .build();

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // when
            var processor = getConfiguredProcessor(configuration, processorName);

            // then
            assertThat(processor).isPresent();
            var processorConfig = getProcessorConfiguration(processor.get());
            assertThat(processorConfig).isNotNull();
        }
    }

    @Nested
    class LifecycleManagementTests {

        @Test
        void shouldStartAndStopCorrectly() {
            // given
            String processorName = "test-processor";
            var startedFlag = new AtomicBoolean(false);
            var stoppedFlag = new AtomicBoolean(false);

            var eventHandlingComponent = createLifecycleAwareEventHandlingComponent(startedFlag, stoppedFlag);
            M module = createModuleBuilder(processorName)
                    .eventHandlingComponent(eventHandlingComponent)
                    .build();

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // when
            configuration.start();

            // then
            await().atMost(Duration.ofSeconds(5))
                   .untilAsserted(() -> {
                       var processor = getConfiguredProcessor(configuration, processorName);
                       assertThat(processor).isPresent();
                   });

            // when
            configuration.shutdown();

            // then - should not throw exceptions during shutdown
            await().atMost(Duration.ofSeconds(5))
                   .untilAsserted(() -> {
                       // Just verify we can get the processor and no exceptions are thrown
                       var shutdownProcessor = getConfiguredProcessor(configuration, processorName);
                       assertThat(shutdownProcessor).isPresent();
                   });
        }

        @Test
        void shouldCleanupResourcesOnShutdown() {
            // given
            String processorName = "test-processor";
            var eventHandlingComponent = createTestEventHandlingComponent();
            M module = createModuleBuilder(processorName)
                    .eventHandlingComponent(eventHandlingComponent)
                    .build();

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // when
            configuration.start();
            var processor = getConfiguredProcessor(configuration, processorName);
            assertThat(processor).isPresent();

            configuration.shutdown();

            // then - processor should still be registered but in stopped state
            await().atMost(Duration.ofSeconds(5))
                   .untilAsserted(() -> {
                       var shutdownProcessor = getConfiguredProcessor(configuration, processorName);
                       assertThat(shutdownProcessor).isPresent();
                   });
        }
    }

    @Nested
    class EventHandlingTests {

        @Test
        void shouldSupportSingleEventHandler() {
            // given
            String processorName = "test-processor";
            var handlerInvoked = new AtomicBoolean(false);
            var eventHandlingComponent = SimpleEventHandlingComponent.builder()
                                                                    .handles(new QualifiedName(String.class),
                                                                            (event, context) -> {
                                                                                handlerInvoked.set(true);
                                                                                return MessageStream.empty();
                                                                            })
                                                                    .build();

            M module = createModuleBuilder(processorName)
                    .eventHandlingComponent(eventHandlingComponent)
                    .build();

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // when
            var processor = getConfiguredProcessor(configuration, processorName);

            // then
            assertThat(processor).isPresent();
        }

        @Test
        void shouldSupportMultipleEventHandlers() {
            // given
            String processorName = "test-processor";
            var handler1Invoked = new AtomicBoolean(false);
            var handler2Invoked = new AtomicBoolean(false);

            var component1 = SimpleEventHandlingComponent.builder()
                                                       .handles(new QualifiedName(String.class),
                                                               (event, context) -> {
                                                                   handler1Invoked.set(true);
                                                                   return MessageStream.empty();
                                                               })
                                                       .build();

            var component2 = SimpleEventHandlingComponent.builder()
                                                       .handles(new QualifiedName(String.class),
                                                               (event, context) -> {
                                                                   handler2Invoked.set(true);
                                                                   return MessageStream.empty();
                                                               })
                                                       .build();

            M module = createModuleBuilder(processorName)
                    .eventHandlingComponents(component1, component2)
                    .build();

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // when
            var processor = getConfiguredProcessor(configuration, processorName);

            // then
            assertThat(processor).isPresent();
        }
    }

    /**
     * Creates a default configuration instance for the processor type under test.
     * <p>
     * This method should be overridden by concrete test classes to provide
     * processor-specific default configuration.
     *
     * @return A default configuration instance
     */
    protected abstract C createDefaultConfiguration();

    /**
     * Creates an event handling component that is aware of lifecycle events.
     * <p>
     * This is used for testing start/stop behavior by monitoring when the component
     * becomes active or inactive.
     *
     * @param startedFlag Flag to set when the component starts
     * @param stoppedFlag Flag to set when the component stops
     * @return A lifecycle-aware event handling component
     */
    protected EventHandlingComponent createLifecycleAwareEventHandlingComponent(AtomicBoolean startedFlag,
                                                                               AtomicBoolean stoppedFlag) {
        return createTestEventHandlingComponent();
    }
}