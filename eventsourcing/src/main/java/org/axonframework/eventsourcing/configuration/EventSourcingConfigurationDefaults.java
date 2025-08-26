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

package org.axonframework.eventsourcing.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventstreaming.StreamableEventSource;

/**
 * A {@link ConfigurationEnhancer} registering the default components of the {@link EventSourcingConfigurer}.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link AnnotationBasedTagResolver} for class {@link TagResolver}</li>
 *     <li>Registers a {@link InMemoryEventStorageEngine} for class {@link EventStorageEngine}</li>
 *     <li>Registers a {@link SimpleEventStore} for class {@link EventStore}</li>
 *     <li>Registers a {@link SimpleEventStore} for class {@link EventSink}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.AggregateSnapshotter} for class {@link Snapshotter}</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class EventSourcingConfigurationDefaults implements ConfigurationEnhancer {

    @Override
    public int order() {
        return Integer.MAX_VALUE - 10;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(TagResolver.class, EventSourcingConfigurationDefaults::defaultTagResolver)
                .registerIfNotPresent(EventStorageEngine.class,
                                      EventSourcingConfigurationDefaults::defaultEventStorageEngine)
                .registerIfNotPresent(EventStore.class, EventSourcingConfigurationDefaults::defaultEventStore)
                .registerIfNotPresent(Snapshotter.class, EventSourcingConfigurationDefaults::defaultSnapshotter)
                .registerDecorator(PooledStreamingEventProcessorModule.Customization.class,
                                   Integer.MAX_VALUE,
                                   (config, name, delegate) -> delegate.andThen(
                                           (c, d) -> d.eventSource() == null
                                                   ? d.eventSource(defaultStreamableEventSource(config)) : d
                                   )
                );
    }

    private static TagResolver defaultTagResolver(Configuration configuration) {
        return new AnnotationBasedTagResolver();
    }

    private static EventStorageEngine defaultEventStorageEngine(Configuration config) {
        return new InMemoryEventStorageEngine();
    }

    private static EventStore defaultEventStore(Configuration config) {
        return new SimpleEventStore(config.getComponent(EventStorageEngine.class),
                                    config.getComponent(TagResolver.class));
    }

    private static EventSink defaultEventSink(Configuration config) {
        return config.getComponent(EventStore.class);
    }

    private static Snapshotter defaultSnapshotter(Configuration config) {
        return (aggregateType, aggregateIdentifier) -> {
            // TODO #3105 - Replace this Snapshotter for the new Snapshotter
        };
    }

    private static StreamableEventSource<? extends EventMessage<?>> defaultStreamableEventSource(
            Configuration configuration
    ) {
        EventStore eventStore = configuration.getComponent(EventStore.class);
        if (eventStore instanceof StreamableEventSource) {
            return (StreamableEventSource<? extends EventMessage<?>>) eventStore;
        }
        throw new AxonConfigurationException(
                "The EventStore is not a StreamableEventSource, so the StreamableEventSource must be configured explicitly.");
    }
}
