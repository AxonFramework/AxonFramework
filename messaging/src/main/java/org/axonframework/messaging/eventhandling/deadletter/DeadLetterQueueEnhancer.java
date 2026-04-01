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
 * This enhancer registers:
 * <ul>
 *     <li>A {@link ComponentFactory} for {@link SequencedDeadLetterQueue} — creates queue instances
 *         on demand, optionally wrapped with {@link CachingSequencedDeadLetterQueue}</li>
 *     <li>A type-level decorator for {@link EventHandlingComponent} — wraps components with
 *         {@link DeadLetteringEventHandlingComponent} when DLQ is enabled</li>
 *     <li>A {@link ComponentFactory} for {@link SequencedDeadLetterProcessor} — makes DLQ-decorated
 *         components discoverable as dead letter processors</li>
 * </ul>
 * <p>
 * This enhancer operates only within module scopes that contain a
 * {@link PooledStreamingEventProcessorConfiguration}. In all other contexts (e.g., non-pooled processors),
 * the decorator returns the delegate unchanged.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see DeadLetterQueueConfiguration
 * @see DeadLetteringEventHandlingComponent
 * @see CachingSequencedDeadLetterQueue
 */
public class DeadLetterQueueEnhancer implements ConfigurationEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public void enhance(ComponentRegistry registry) {
        registry.registerFactory(new DeadLetterQueueComponentFactory());
        registerDeadLetterQueueDecorator(registry);
        registry.registerFactory(new DeadLetterProcessorFactory());
    }

    /**
     * Registers a type-level decorator for {@link EventHandlingComponent} that wraps components with
     * {@link DeadLetteringEventHandlingComponent} when DLQ is enabled.
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
     * has DLQ enabled via {@link DeadLetterQueueConfiguration}.
     */
    private static EventHandlingComponent decorateWithDeadLettering(
            Configuration config,
            String name,
            EventHandlingComponent delegate
    ) {
        if (delegate instanceof DeadLetteringEventHandlingComponent) {
            return delegate;
        }

        Optional<PooledStreamingEventProcessorConfiguration> optionalProcessorConfig =
                config.getOptionalComponent(PooledStreamingEventProcessorConfiguration.class);
        if (optionalProcessorConfig.isEmpty()) {
            return delegate;
        }
        PooledStreamingEventProcessorConfiguration processorConfig = optionalProcessorConfig.get();

        DeadLetterQueueConfiguration dlqConfig =
                processorConfig.extension(DeadLetterQueueConfiguration.class);
        if (!dlqConfig.isEnabled()) {
            return delegate;
        }

        String dlqName = dlqNameFrom(name);

        // Look up the DLQ from the component registry (created by DeadLetterQueueComponentFactory)
        @SuppressWarnings("unchecked")
        SequencedDeadLetterQueue<EventMessage> dlq =
                config.getComponent(SequencedDeadLetterQueue.class, dlqName);

        logger.info("Dead letter queue enabled for component [{}] with queue name [{}].", name, dlqName);

        return new DeadLetteringEventHandlingComponent(
                delegate,
                dlq,
                dlqConfig.enqueuePolicy(),
                processorConfig.unitOfWorkFactory(),
                dlqConfig.clearOnReset()
        );
    }

    /**
     * Derives the DLQ name from the event handling component's registered name.
     * <p>
     * Wraps the component name in {@code "DeadLetterQueue["} and {@code "]"}, producing
     * e.g. {@code "DeadLetterQueue[EventHandlingComponent[myProcessor][myComponent]]"}.
     *
     * @param componentName The full component name as registered in the configuration.
     * @return The DLQ name.
     */
    static String dlqNameFrom(String componentName) {
        return "DeadLetterQueue[" + componentName + "]";
    }

    /**
     * A {@link ComponentFactory} that creates {@link SequencedDeadLetterQueue} instances on demand.
     * <p>
     * When a queue is requested by name (e.g., {@code "DeadLetterQueue[processorName][componentName]"}),
     * this factory reads the {@link DeadLetterQueueConfiguration} from the processor configuration and
     * creates the queue using the configured {@link SequencedDeadLetterQueueFactory}. If caching is enabled,
     * the queue is wrapped with {@link CachingSequencedDeadLetterQueue} and a {@link SegmentChangeListener}
     * is registered for cache invalidation.
     */
    private static class DeadLetterQueueComponentFactory implements ComponentFactory<SequencedDeadLetterQueue> {

        @Override
        public Class<SequencedDeadLetterQueue> forType() {
            return SequencedDeadLetterQueue.class;
        }

        @Override
        public Optional<Component<SequencedDeadLetterQueue>> construct(String name, Configuration config) {
            Optional<PooledStreamingEventProcessorConfiguration> optionalProcessorConfig =
                    config.getOptionalComponent(PooledStreamingEventProcessorConfiguration.class);
            if (optionalProcessorConfig.isEmpty()) {
                return Optional.empty();
            }
            PooledStreamingEventProcessorConfiguration processorConfig = optionalProcessorConfig.get();

            DeadLetterQueueConfiguration dlqConfig =
                    processorConfig.extension(DeadLetterQueueConfiguration.class);
            if (!dlqConfig.isEnabled()) {
                return Optional.empty();
            }

            SequencedDeadLetterQueue<EventMessage> dlq = dlqConfig.factory().create(name, config);

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

            @SuppressWarnings("unchecked")
            SequencedDeadLetterQueue<EventMessage> finalDlq = dlq;
            return Optional.of(new InstantiatedComponentDefinition<>(
                    new Component.Identifier<>(forType(), name),
                    finalDlq
            ));
        }

        @Override
        public void registerShutdownHandlers(LifecycleRegistry registry) {
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
            descriptor.describeProperty("type", forType());
            descriptor.describeProperty("description",
                    "Creates SequencedDeadLetterQueue instances per event handling component");
        }
    }

    /**
     * A {@link ComponentFactory} that provides {@link SequencedDeadLetterProcessor} instances by delegating
     * to the {@link EventHandlingComponent} registered under the same name.
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
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
            descriptor.describeProperty("type", forType());
            descriptor.describeProperty("description",
                    "Discovers SequencedDeadLetterProcessor instances from DLQ-decorated EventHandlingComponents");
        }
    }
}
