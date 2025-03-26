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
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.configuration.NewConfigurer;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;

import java.util.Objects;

/**
 * A {@link ConfigurationEnhancer} registering the default components of the {@link EventSourcingConfigurer}.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver} for class {@link org.axonframework.eventsourcing.eventstore.TagResolver}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine} for class {@link org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.SimpleEventStore} for class {@link org.axonframework.eventsourcing.eventstore.AsyncEventStore}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.AggregateSnapshotter} for class {@link org.axonframework.eventsourcing.Snapshotter}</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class EventSourcingConfigurationDefaults implements ConfigurationEnhancer {

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void enhance(@Nonnull NewConfigurer<?> configurer) {
        Objects.requireNonNull(configurer, "Cannot enhance a null Configurer.");

        registerIfNotPresent(configurer, TagResolver.class,
                             EventSourcingConfigurationDefaults::defaultTagResolver);
        registerIfNotPresent(configurer, AsyncEventStorageEngine.class,
                             EventSourcingConfigurationDefaults::defaultEventStorageEngine);
        registerIfNotPresent(configurer, AsyncEventStore.class,
                             EventSourcingConfigurationDefaults::defaultEventStore);
        registerIfNotPresent(configurer, Snapshotter.class,
                             EventSourcingConfigurationDefaults::defaultSnapshotter);
    }

    private <C> void registerIfNotPresent(NewConfigurer<?> configurer,
                                          Class<C> type,
                                          ComponentFactory<C> factory) {
        if (!configurer.hasComponent(type)) {
            configurer.registerComponent(type, factory);
        }
    }

    private static TagResolver defaultTagResolver(NewConfiguration configuration) {
        return new AnnotationBasedTagResolver();
    }

    private static AsyncEventStorageEngine defaultEventStorageEngine(NewConfiguration config) {
        return new AsyncInMemoryEventStorageEngine();
    }

    private static AsyncEventStore defaultEventStore(NewConfiguration config) {
        return new SimpleEventStore(config.getComponent(AsyncEventStorageEngine.class),
                                    config.getComponent(TagResolver.class));
    }

    private static Snapshotter defaultSnapshotter(NewConfiguration config) {
        return (aggregateType, aggregateIdentifier) -> {
            // TODO #3105 - Replace this Snapshotter for the new Snapshotter
        };
    }
}
