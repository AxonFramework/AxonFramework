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

import org.axonframework.common.FutureUtils;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentFactory;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.configuration.DecoratorDefinition;
import org.axonframework.common.configuration.InstantiatedComponentDefinition;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SegmentChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

/**
 * A {@link ConfigurationEnhancer} that adds Dead Letter Queue (DLQ) support to
 * {@link EventHandlingComponent EventHandlingComponents} within a
 * {@link PooledStreamingEventProcessorConfiguration pooled streaming event processor} module.
 * <p>
 * This enhancer registers a type-level decorator for all {@code EventHandlingComponent} instances. When components
 * are resolved, the decorator lazily reads the {@link DeadLetterQueueConfigurationExtension} from the processor
 * configuration and, if DLQ is enabled, wraps the component with a {@link DeadLetteringEventHandlingComponent}.
 * <p>
 * The DLQ behavior provided by this enhancer includes:
 * <ul>
 *     <li>Creating a {@link SequencedDeadLetterQueue} per event handling component using the configured
 *         {@link SequencedDeadLetterQueueFactory}</li>
 *     <li>Optionally wrapping the queue with a {@link CachingSequencedDeadLetterQueue} for optimized
 *         {@code contains()} lookups</li>
 *     <li>Registering {@link SegmentChangeListener segment change listeners} for cache invalidation
 *         when segments are released</li>
 *     <li>Registering a {@link ComponentFactory} for {@link SequencedDeadLetterProcessor} so that
 *         DLQ-decorated components are discoverable as dead letter processors</li>
 * </ul>
 * <p>
 * This enhancer operates only within module scopes that contain a
 * {@link PooledStreamingEventProcessorConfiguration}. In all other contexts (e.g., non-pooled processors),
 * the decorator returns the delegate unchanged.
 * <p>
 * The enhancer also guards against double-decoration: if a component is already wrapped in a
 * {@link DeadLetteringEventHandlingComponent} (e.g., by the module's per-name decorator during the migration
 * period), the type-level decorator from this enhancer becomes a no-op for that component.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see DeadLetterQueueConfigurationExtension
 * @see DeadLetteringEventHandlingComponent
 * @see CachingSequencedDeadLetterQueue
 */
public class DeadLetterQueueEnhancer implements ConfigurationEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public void enhance(ComponentRegistry registry) {
        registerDeadLetterQueueDecorator(registry);
        registerDeadLetterProcessorFactory(registry);
    }

    /**
     * Registers a type-level decorator for {@link EventHandlingComponent} that wraps components with
     * {@link DeadLetteringEventHandlingComponent} when DLQ is enabled.
     * <p>
     * The decorator operates lazily: all configuration reading and DLQ construction happen at component
     * resolution time, not at registration time. This ensures the processor configuration is fully built
     * before the decorator inspects it.
     */
    private static void registerDeadLetterQueueDecorator(ComponentRegistry registry) {
        registry.registerDecorator(
                DecoratorDefinition
                        .forType(EventHandlingComponent.class)
                        .<EventHandlingComponent>with((config, name, delegate) ->
                                decorateWithDeadLettering(config, name, delegate))
                        .order(DeadLetteringEventHandlingComponent.DECORATION_ORDER)
        );
    }

    /**
     * Decorates the given {@code delegate} with dead-lettering support if the processor configuration
     * has DLQ enabled via {@link DeadLetterQueueConfigurationExtension}.
     *
     * @param config   The configuration providing access to the processor configuration and other components.
     * @param name     The component name (e.g., {@code "EventHandlingComponent[myProcessor][myComponent]"}).
     * @param delegate The original {@link EventHandlingComponent} to potentially wrap.
     * @return The original delegate if DLQ is not applicable, or a {@link DeadLetteringEventHandlingComponent}
     *         wrapping the delegate when DLQ is enabled.
     */
    private static EventHandlingComponent decorateWithDeadLettering(
            Configuration config,
            String name,
            EventHandlingComponent delegate
    ) {
        // Guard against double-decoration during the migration period where both the module's
        // per-name decorator and this type-level decorator may be active.
        if (delegate instanceof DeadLetteringEventHandlingComponent) {
            return delegate;
        }

        // Only applicable within pooled streaming event processor modules.
        Optional<PooledStreamingEventProcessorConfiguration> optionalProcessorConfig =
                config.getOptionalComponent(PooledStreamingEventProcessorConfiguration.class);
        if (optionalProcessorConfig.isEmpty()) {
            return delegate;
        }
        PooledStreamingEventProcessorConfiguration processorConfig = optionalProcessorConfig.get();

        // Read DLQ configuration from the extension.
        DeadLetterQueueConfiguration dlqConfig =
                processorConfig.extend(DeadLetterQueueConfigurationExtension.class).deadLetterQueue();
        if (!dlqConfig.isEnabled()) {
            return delegate;
        }

        // Build the DLQ name following the established pattern: "DeadLetterQueue[processorName][componentName]"
        String dlqName = dlqNameFrom(name);

        // Create the underlying queue via the configured factory.
        SequencedDeadLetterQueue<EventMessage> dlq = dlqConfig.factory().create(dlqName, config);

        // Optionally wrap with a caching layer and register a segment change listener for cache invalidation.
        if (dlqConfig.cacheMaxSize() > 0) {
            var cachingDlq = new CachingSequencedDeadLetterQueue<EventMessage>(dlq, dlqConfig.cacheMaxSize());
            processorConfig.addSegmentChangeListener(SegmentChangeListener.onRelease(segment -> {
                var uow = processorConfig.unitOfWorkFactory().create();
                return uow.executeWithResult(context -> {
                    cachingDlq.invalidateCache(context.withResource(Segment.RESOURCE_KEY, segment));
                    return FutureUtils.emptyCompletedFuture();
                });
            }));
            dlq = cachingDlq;
        }

        logger.info("Dead letter queue enabled for component [{}] with queue name [{}].", name, dlqName);

        //noinspection unchecked
        return new DeadLetteringEventHandlingComponent(
                delegate,
                dlq,
                dlqConfig.enqueuePolicy(),
                processorConfig.unitOfWorkFactory(),
                dlqConfig.clearOnReset()
        );
    }

    /**
     * Registers a {@link ComponentFactory} for {@link SequencedDeadLetterProcessor}.
     * <p>
     * When a component of type {@code SequencedDeadLetterProcessor} is requested by name, this factory
     * looks up the {@link EventHandlingComponent} with the same name and checks if it implements
     * {@link SequencedDeadLetterProcessor} (which {@link DeadLetteringEventHandlingComponent} does).
     * If so, the component is returned; otherwise, the factory declines construction.
     * <p>
     * This enables external components (such as API endpoints or management tools) to discover and use
     * dead letter processors by name.
     */
    private static void registerDeadLetterProcessorFactory(ComponentRegistry registry) {
        registry.registerFactory(new DeadLetterProcessorFactory());
    }

    /**
     * Derives the DLQ name from the event handling component's registered name.
     * <p>
     * The component name follows the format {@code "EventHandlingComponent[processorName][componentName]"}.
     * This method replaces the {@code "EventHandlingComponent"} prefix with {@code "DeadLetterQueue"},
     * producing {@code "DeadLetterQueue[processorName][componentName]"} to match the naming convention
     * used by {@link org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule}.
     * <p>
     * If the component name does not follow the expected format, a fallback name using the full component
     * name is returned.
     *
     * @param componentName The full component name as registered in the configuration
     *                      (e.g., {@code "EventHandlingComponent[myProcessor][myComponent]"}).
     * @return The DLQ name (e.g., {@code "DeadLetterQueue[myProcessor][myComponent]"}).
     */
    static String dlqNameFrom(String componentName) {
        String eventHandlingComponentPrefix = "EventHandlingComponent[";
        if (componentName.startsWith(eventHandlingComponentPrefix)) {
            return "DeadLetterQueue[" + componentName.substring(eventHandlingComponentPrefix.length());
        }
        // Fallback: wrap the full component name
        return "DeadLetterQueue[" + componentName + "]";
    }

    /**
     * A {@link ComponentFactory} that provides {@link SequencedDeadLetterProcessor} instances by delegating
     * to the {@link EventHandlingComponent} registered under the same name.
     * <p>
     * When a {@link DeadLetteringEventHandlingComponent} decorates an event handling component, it implements
     * {@link SequencedDeadLetterProcessor}. This factory makes those processors discoverable through the
     * standard configuration component lookup mechanism.
     */
    private static class DeadLetterProcessorFactory implements ComponentFactory<SequencedDeadLetterProcessor> {

        @Override
        public Class<SequencedDeadLetterProcessor> forType() {
            return SequencedDeadLetterProcessor.class;
        }

        @Override
        public Optional<Component<SequencedDeadLetterProcessor>> construct(String name, Configuration config) {
            return config.getOptionalComponent(EventHandlingComponent.class, name)
                         .filter(SequencedDeadLetterProcessor.class::isInstance)
                         .map(SequencedDeadLetterProcessor.class::cast)
                         .map(dlp -> new InstantiatedComponentDefinition<>(
                                 new Component.Identifier<>(forType(), name),
                                 dlp
                         ));
        }

        @Override
        public void registerShutdownHandlers(LifecycleRegistry registry) {
            // No shutdown actions needed — lifecycle is managed by the EventHandlingComponent decorator.
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
            descriptor.describeProperty("type", forType());
            descriptor.describeProperty("description",
                    "Discovers SequencedDeadLetterProcessor instances from DLQ-decorated EventHandlingComponents");
        }
    }
}
