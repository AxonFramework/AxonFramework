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
import org.springframework.beans.factory.config.BeanDefinition;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Defines the configuration for an event processor, including which event handlers it should process and how it should
 * be configured.
 * <p>
 * This interface provides a fluent API for defining event processors. The typical usage flow is:
 * <ol>
 *     <li>Start with a static factory method (e.g., {@link #pooledStreamingProcessor(String)} or
 *     {@link #subscribingProcessor(String)})</li>
 *     <li>Define which event handlers should be assigned to this processor using
 *     {@link ProcessorDefinitionSelectorStep#assigningHandlers(Predicate)}</li>
 *     <li>Either apply custom configuration using {@link ProcessorDefinitionConfigurationStep#withConfiguration(Function)}
 *     or use default settings with {@link ProcessorDefinitionConfigurationStep#withDefaultSettings()}</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * ProcessorDefinition.pooledStreamingProcessor("myProcessor")
 *     .assigningHandlers(descriptor -> descriptor.beanName().startsWith("order"))
 *     .withConfiguration(config -> config.maxClaimedSegments(4));
 * }</pre>
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public interface ProcessorDefinition {

    /**
     * Creates a new processor definition for a pooled streaming event processor with the given name.
     * <p>
     * Pooled streaming event processors distribute event processing across multiple threads, allowing for parallel
     * processing of events within a single processor instance.
     *
     * @param name the name of the processor
     * @return the next step in the fluent API to define the handler selection criteria
     */
    static ProcessorDefinitionSelectorStep<PooledStreamingEventProcessorConfiguration> pooledStreamingProcessor(
            String name) {
        return new ProcessorDefinitionBuilder<>(name, EventProcessorSettings.ProcessorMode.POOLED);
    }

    /**
     * Creates a new processor definition for a subscribing event processor with the given name.
     * <p>
     * Subscribing event processors process events in the thread that publishes them, providing low-latency processing
     * but without the ability to replay events or track progress.
     *
     * @param name the name of the processor
     * @return the next step in the fluent API to define the handler selection criteria
     */
    static ProcessorDefinitionSelectorStep<SubscribingEventProcessorConfiguration> subscribingProcessor(String name) {
        return new ProcessorDefinitionBuilder<>(name, EventProcessorSettings.ProcessorMode.SUBSCRIBING
        );
    }

    /**
     * Determines whether the given event handler descriptor matches this processor's selection criteria.
     *
     * @param eventHandlerDescriptor the descriptor of the event handler to check
     * @return {@code true} if the event handler should be assigned to this processor, {@code false} otherwise
     */
    boolean matchesSelector(EventHandlerDescriptor eventHandlerDescriptor);

    /**
     * Applies this processor's configuration settings to the given settings object.
     *
     * @param settings the base settings to apply configuration to
     * @return the configured settings, potentially modified by this processor's configuration
     */
    EventProcessorConfiguration applySettings(EventProcessorConfiguration settings);

    /**
     * Returns the name of this processor.
     *
     * @return the processor name
     */
    String name();

    /**
     * Returns the mode (type) of this processor.
     *
     * @return the processor mode
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
         * @return the bean name
         */
        String beanName();

        /**
         * Returns the Spring {@link BeanDefinition} for this event handler.
         *
         * @return the bean definition
         */
        BeanDefinition beanDefinition();

        /**
         * Returns the Java type of this event handler bean.
         *
         * @return the bean type
         */
        Class<?> beanType();

        /**
         * Resolves and returns the actual event handler bean instance.
         *
         * @return the event handler bean
         */
        Object resolveBean();

        /**
         * Returns the component builder for this event handler.
         *
         * @return the component builder
         */
        ComponentBuilder<Object> component();
    }

    /**
     * Second step in the processor definition fluent API for selecting which event handlers should be assigned to the
     * processor.
     *
     * @param <T> the type of {@link EventProcessorConfiguration} for this processor
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
         * @param selector a predicate that determines which event handlers to assign to this processor
         * @return the next step in the fluent API to configure the processor settings
         */
        ProcessorDefinitionConfigurationStep<T> assigningHandlers(Predicate<EventHandlerDescriptor> selector);
    }

    /**
     * Final step in the processor definition fluent API for configuring the processor settings.
     *
     * @param <T> the type of {@link EventProcessorConfiguration} for this processor
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
         * withConfiguration(config -> config
         *     .maxClaimedSegments(4)
         *     .batchSize(100))
         * }</pre>
         *
         * @param configurer a function that modifies the processor configuration
         * @return the completed processor definition
         */
        ProcessorDefinition withConfiguration(Function<T, T> configurer);

        /**
         * Completes the processor definition using default settings for this processor type.
         * <p>
         * No custom configuration will be applied; the processor will use the default configuration values appropriate
         * for its type.
         *
         * @return the completed processor definition
         */
        ProcessorDefinition withDefaultSettings();
    }
}
