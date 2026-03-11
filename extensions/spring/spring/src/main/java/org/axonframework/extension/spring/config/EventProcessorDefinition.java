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

import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorConfiguration;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;

import java.util.function.Function;

/**
 * Defines the configuration for an event processor, including which event handlers it should process and how it should
 * be configured.
 * <p>
 * This interface provides a fluent API for defining event processors. The typical usage flow is:
 * <ol>
 *     <li>Start with a static factory method (e.g., {@link #pooledStreaming(String)} or
 *     {@link #subscribing(String)})</li>
 *     <li>Define which event handlers should be assigned to this processor using
 *     {@link SelectorStep#assigningHandlers(EventHandlerSelector)}</li>
 *     <li>Either apply custom configuration using {@link ConfigurationStep#customized(Function)}
 *     or use default settings with {@link ConfigurationStep#notCustomized()}</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * EventProcessorDefinition.pooledStreaming("myProcessor")
 *     .assigningHandlers(descriptor -> descriptor.beanName().startsWith("order"))
 *     .customized(config -> config.maxClaimedSegments(4));
 * }</pre>
 * <p>
 * Any configuration set by the `.customized` method will override any configuration provided using properties
 * files.
 *
 * @author Allard Buijze
 * @since 5.0.2
 */
public interface EventProcessorDefinition {

    /**
     * Creates a new processor definition for a
     * {@link org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor pooled
     * streaming event processor} with the given {@code name}.
     * <p>
     * Pooled streaming event processors distribute event processing across multiple threads, allowing for parallel
     * processing of events within a single processor instance.
     *
     * @param name the name of the processor
     * @return the next step in the fluent API to define the handler selection criteria
     */
    static SelectorStep<PooledStreamingEventProcessorConfiguration> pooledStreaming(String name) {
        return new EventProcessorDefinitionBuilder<>(name, EventProcessorSettings.ProcessorMode.POOLED);
    }

    /**
     * Creates a new processor definition for a
     * {@link org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor pooled
     * streaming event processor} with the given {@code name}, automatically selecting any event handler that's part of
     * a namespace matching the {@code name}, including them to the pooled streaming event processor.
     * <p>
     * Pooled streaming event processors distribute event processing across multiple threads, allowing for parallel
     * processing of events within a single processor instance.
     * <p>
     * Selecting the event handlers is done based on the presence and contents of the
     * {@link org.axonframework.messaging.core.annotation.Namespace}.
     *
     * @param name the name of the processor
     * @return the next step in the fluent API to define the handler selection criteria
     */
    static ConfigurationStep<PooledStreamingEventProcessorConfiguration> pooledStreamingMatching(String name) {
        return new EventProcessorDefinitionBuilder<PooledStreamingEventProcessorConfiguration>(
                name, EventProcessorSettings.ProcessorMode.POOLED
        ).assigningHandlers(EventHandlerSelector.matchesNamespaceOnType(name));
    }

    /**
     * Creates a new processor definition for a
     * {@link org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessor subscribing
     * event processor} with the given {@code name}.
     * <p>
     * With Subscribing event processors, the processor relies on the threading model of the
     * {@link org.axonframework.messaging.core.SubscribableEventSource}, potentially providing low-latency processing
     * but without the ability to replay events or track progress.
     *
     * @param name the name of the processor
     * @return the next step in the fluent API to define the handler selection criteria
     */
    static SelectorStep<SubscribingEventProcessorConfiguration> subscribing(String name) {
        return new EventProcessorDefinitionBuilder<>(name, EventProcessorSettings.ProcessorMode.SUBSCRIBING);
    }

    /**
     * Creates a new processor definition for a
     * {@link org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessor subscribing
     * event processor} with the given {@code name}, automatically selecting any event handler that's part of a
     * namespace matching the {@code name}, including them to the subscribing event processor.
     * <p>
     * With Subscribing event processors, the processor relies on the threading model of the
     * {@link org.axonframework.messaging.core.SubscribableEventSource}, potentially providing low-latency processing
     * but without the ability to replay events or track progress.
     * <p>
     * Selecting the event handlers is done based on the presence and contents of the
     * {@link org.axonframework.messaging.core.annotation.Namespace}.
     *
     * @param name the name of the processor
     * @return the next step in the fluent API to define the handler selection criteria
     */
    static ConfigurationStep<SubscribingEventProcessorConfiguration> subscribingMatching(String name) {
        return new EventProcessorDefinitionBuilder<SubscribingEventProcessorConfiguration>(
                name, EventProcessorSettings.ProcessorMode.SUBSCRIBING
        ).assigningHandlers(EventHandlerSelector.matchesNamespaceOnType(name));
    }

    /**
     * Determines whether the given event handler descriptor matches this processor's selection criteria.
     *
     * @param eventHandlerDescriptor The descriptor of the event handler to check.
     * @return {@code true} if the event handler should be assigned to this processor, {@code false} otherwise.
     */
    boolean matchesSelector(EventHandlerDescriptor eventHandlerDescriptor);

    /**
     * Applies this processor's configuration settings to the given settings object.
     *
     * @param settings The base settings to apply configuration to.
     * @return The configured settings, potentially modified by this processor's configuration.
     */
    EventProcessorConfiguration applySettings(EventProcessorConfiguration settings);

    /**
     * Returns the name of this processor.
     *
     * @return The processor name.
     */
    String name();

    /**
     * Returns the mode (type) of this processor.
     *
     * @return The processor mode.
     */
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
        Object resolveBean();

        /**
         * Returns the component builder for this event handler.
         *
         * @return The component builder.
         */
        ComponentBuilder<Object> component();
    }

    /**
     * The second step in the processor definition fluent API for selecting which event handlers should be assigned to
     * the processor.
     *
     * @param <T> The type of {@link EventProcessorConfiguration} for this processor.
     */
    interface SelectorStep<T extends EventProcessorConfiguration> {

        /**
         * Defines the selection criteria for event handlers to be assigned to this processor.
         * <p>
         * The provided {@code selector} will be evaluated for each event handler component discovered in the Spring
         * context. Handlers for which the {@code selector} returns {@code true} will be assigned to this processor.
         * Note that the {@link EventHandlerSelector} describes a number of out-of-the-box selector options that you can
         * use for convenience.
         * <p>
         * Example:
         * <pre>{@code
         * assigningHandlers(descriptor -> descriptor.beanName().startsWith("order"))
         * }</pre>
         *
         * @param selector a selector that determines which event handlers to assign to this processor
         * @return the next step in the fluent API to configure the processor settings
         * @see EventHandlerSelector
         */
        ConfigurationStep<T> assigningHandlers(EventHandlerSelector selector);
    }

    /**
     * Final step in the processor definition fluent API for configuring the processor settings.
     *
     * @param <T> The type of {@link EventProcessorConfiguration} for this processor.
     */
    interface ConfigurationStep<T extends EventProcessorConfiguration> {

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
        EventProcessorDefinition customized(Function<T, T> configurer);

        /**
         * Completes the processor definition using default settings for this processor type.
         * <p>
         * No custom configuration will be applied; the processor will use the default configuration values appropriate
         * for its type.
         *
         * @return The completed processor definition.
         */
        EventProcessorDefinition notCustomized();
    }
}
