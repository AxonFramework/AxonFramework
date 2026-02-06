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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorConfiguration;
import org.springframework.beans.factory.config.BeanDefinition;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Defines the configuration for an event processor, including which event handlers it should process and how it should
 * be configured.
 * <p>
 * This interface provides a fluent API for defining event processors. The typical usage flow is:
 * <ol>
 *     <li>Start with a static factory method (e.g., {@link #pooledStreaming(String)} or
 *     {@link #subscribing(String)})</li>
 *     <li>Define which event handlers should be assigned to this processor using
 *     {@link ProcessorDefinitionSelectorStep#assigningHandlers(Predicate)}</li>
 *     <li>Either apply custom configuration using {@link ProcessorDefinitionConfigurationStep#customized(Function)}
 *     or use default settings with {@link ProcessorDefinitionConfigurationStep#notCustomized()}</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * EventProcessorDefinition.pooledStreaming("myProcessor")
 *     .assigningHandlers(descriptor -> descriptor.beanName().startsWith("order"))
 *     .customized(config -> config.maxClaimedSegments(4));
 * }</pre>
 *
 * Any configuration set by the `.customized` method will override any configuration provided using properties
 * files.
 *
 * @author Allard Buijze
 * @since 5.0.2
 */
public interface EventProcessorDefinition {

    /**
     * Creates a new processor definition for a pooled streaming event processor with the given name.
     * <p>
     * Pooled streaming event processors distribute event processing across multiple threads, allowing for parallel
     * processing of events within a single processor instance.
     *
     * @param name The name of the processor.
     * @return The next step in the fluent API to define the handler selection criteria.
     */
    @Nonnull
    static ProcessorDefinitionSelectorStep<PooledStreamingEventProcessorConfiguration> pooledStreaming(
            @Nonnull String name
    ) {
        return new ProcessorDefinitionBuilder<>(name, EventProcessorSettings.ProcessorMode.POOLED);
    }

    /**
     * Creates a new processor definition for a subscribing event processor with the given name.
     * <p>
     * With Subscribing event processors, the processor relies on the threading model of the
     * {@link org.axonframework.messaging.core.SubscribableEventSource}, potentially providing low-latency processing
     * but without the ability to replay events or track progress.
     *
     * @param name The name of the processor.
     * @return The next step in the fluent API to define the handler selection criteria.
     */
    @Nonnull
    static ProcessorDefinitionSelectorStep<SubscribingEventProcessorConfiguration> subscribing(
            @Nonnull String name
    ) {
        return new ProcessorDefinitionBuilder<>(name, EventProcessorSettings.ProcessorMode.SUBSCRIBING);
    }

    /**
     * Determines whether the given event handler descriptor matches this processor's selection criteria.
     *
     * @param eventHandlerDescriptor The descriptor of the event handler to check.
     * @return {@code true} if the event handler should be assigned to this processor, {@code false} otherwise.
     */
    boolean matchesSelector(@Nonnull EventHandlerDescriptor eventHandlerDescriptor);

    /**
     * Applies this processor's configuration settings to the given settings object.
     *
     * @param settings The base settings to apply configuration to.
     * @return The configured settings, potentially modified by this processor's configuration.
     */
    @Nonnull
    EventProcessorConfiguration applySettings(@Nonnull EventProcessorConfiguration settings);

    /**
     * Returns the name of this processor.
     *
     * @return The processor name.
     */
    @Nonnull
    String name();

    /**
     * Returns the mode (type) of this processor.
     *
     * @return The processor mode.
     */
    @Nonnull
    EventProcessorSettings.ProcessorMode mode();

    /**
     * Describes an event handler component that can be assigned to an event processor.
     * <p>
     * This descriptor provides access to various aspects of the event handler bean, allowing processor definitions to
     * make decisions about which handlers to assign based on bean name, type, or other characteristics.
     */
    interface EventHandlerDescriptor {

        /**
         * Returns the Spring bean name of this event handler.
         *
         * @return The bean name.
         */
        @Nonnull
        String beanName();

        /**
         * Returns the Spring {@link BeanDefinition} for this event handler.
         *
         * @return The bean definition.
         */
        BeanDefinition beanDefinition();

        /**
         * Returns the Java type of this event handler bean.
         *
         * @return The bean type.
         */
        @Nullable
        Class<?> beanType();

        /**
         * Resolves and returns the actual event handler bean instance.
         *
         * @return The event handler bean.
         */
        @Nonnull
        Object resolveBean();

        /**
         * Returns the component builder for this event handler.
         *
         * @return The component builder.
         */
        @Nonnull
        ComponentBuilder<Object> component();
    }

    /**
     * The second step in the processor definition fluent API for selecting which event handlers should be assigned to
     * the processor.
     *
     * @param <T> The type of {@link EventProcessorConfiguration} for this processor.
     */
    interface ProcessorDefinitionSelectorStep<T extends EventProcessorConfiguration> {

        /**
         * Defines the selection criteria for event handlers to be assigned to this processor.
         * <p>
         * The provided predicate will be evaluated for each event handler component discovered in the Spring context.
         * Handlers for which the predicate returns {@code true} will be assigned to this processor.
         * <p>
         * Example:
         * <pre>{@code
         * assigningHandlers(descriptor -> descriptor.beanName().startsWith("order"))
         * }</pre>
         *
         * @param selector A predicate that determines which event handlers to assign to this processor.
         * @return The next step in the fluent API to configure the processor settings.
         */
        @Nonnull
        ProcessorDefinitionConfigurationStep<T> assigningHandlers(@Nonnull Predicate<EventHandlerDescriptor> selector);
    }

    /**
     * Final step in the processor definition fluent API for configuring the processor settings.
     *
     * @param <T> The type of {@link EventProcessorConfiguration} for this processor.
     */
    interface ProcessorDefinitionConfigurationStep<T extends EventProcessorConfiguration> {

        /**
         * Applies custom configuration to the processor using the provided configuration function.
         * <p>
         * The function receives the default configuration for the processor type and should return a modified
         * configuration. This allows for customizing aspects such as thread pool size, batch size, error handling, and
         * other processor-specific settings.
         * <p>
         * Example:
         * <pre>{@code
         * customized(config -> config
         *     .maxClaimedSegments(4)
         *     .batchSize(100))
         * }</pre>
         *
         * @param configurer A function that modifies the processor configuration.
         * @return The completed processor definition.
         */
        @Nonnull
        EventProcessorDefinition customized(@Nonnull Function<T, T> configurer);

        /**
         * Completes the processor definition using default settings for this processor type.
         * <p>
         * No custom configuration will be applied; the processor will use the default configuration values appropriate
         * for its type.
         *
         * @return The completed processor definition.
         */
        @Nonnull
        EventProcessorDefinition notCustomized();
    }
}
