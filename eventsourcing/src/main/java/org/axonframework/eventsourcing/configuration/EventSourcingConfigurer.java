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
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.AxonApplication;
import org.axonframework.configuration.Component;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.DelegatingConfigurer;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.configuration.NewConfigurer;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.modelling.configuration.ModellingConfigurer;

import java.util.function.Consumer;

/**
 * The event sourcing {@link NewConfigurer} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for {@link #registerEventStorageEngine(ComponentFactory)} the event storage engine} and
 * {@link #registerEventStore(ComponentFactory) event store} infrastructure components.
 * <p>
 * This configurer registers the following defaults:
 * <ul>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver} for class {@link org.axonframework.eventsourcing.eventstore.TagResolver}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine} for class {@link org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.SimpleEventStore} for class {@link org.axonframework.eventsourcing.eventstore.AsyncEventStore}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.AggregateSnapshotter} for class {@link org.axonframework.eventsourcing.Snapshotter}</li>
 * </ul>
 * To replace or decorate any of these defaults, use their respective interfaces as the identifier. For example, to
 * adjust the {@code EventStore}, invoke {@link #registerComponent(Class, ComponentFactory)} with
 * {@code EventStore.class} to replace it.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class EventSourcingConfigurer
        extends DelegatingConfigurer<EventSourcingConfigurer>
        implements ApplicationConfigurer<EventSourcingConfigurer> {

    /**
     * Build a default {@code EventSourcingConfigurer} instance with several event sourcing defaults.
     * <p>
     * Besides the specific operations, the {@code EventSourcingConfigurer} allows for configuring generic
     * {@link Component components}, {@link ComponentDecorator component decorators},
     * {@link ConfigurationEnhancer enhancers}, and {@link Module modules} for an event-sourced application.
     * <p>
     * Note that this configurer uses a {@link ModellingConfigurer} to support event-sourced entities.
     *
     * @return A {@code EventSourcingConfigurer} instance for further configuring.
     */
    public static EventSourcingConfigurer create() {
        return new EventSourcingConfigurer(ModellingConfigurer.create())
                .registerEnhancer(new EventSourcingConfigurationDefaults());
    }

    /**
     * Construct a {@code ModellingConfigurer} using the given {@code delegate} to delegate all registry-specific
     * operations to.
     * <p>
     * It is recommended to use the {@link #create()} method in most cases instead of this constructor.
     *
     * @param delegate The delegate {@code ModellingConfigurer} the {@code EventSourcingConfigurer} is based on.
     */
    public EventSourcingConfigurer(@Nonnull ModellingConfigurer delegate) {
        super(delegate);
    }

    /**
     * Registers the given {@link TagResolver} factory in this {@code Configurer}.
     * <p>
     * The {@code eventStorageEngineFactory} receives the {@link NewConfiguration} as input and is expected to return a
     * {@link TagResolver} instance.
     *
     * @param tagResolverFactory The factory building the {@link TagResolver}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer registerTagResolver(@Nonnull ComponentFactory<TagResolver> tagResolverFactory) {
        return registerComponent(TagResolver.class, tagResolverFactory);
    }

    /**
     * Registers the given {@link AsyncEventStorageEngine} factory in this {@code Configurer}.
     * <p>
     * The {@code eventStorageEngineFactory} receives the {@link NewConfiguration} as input and is expected to return a
     * {@link AsyncEventStorageEngine} instance.
     *
     * @param eventStorageEngineFactory The factory building the {@link AsyncEventStorageEngine}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer registerEventStorageEngine(
            @Nonnull ComponentFactory<AsyncEventStorageEngine> eventStorageEngineFactory
    ) {
        return registerComponent(AsyncEventStorageEngine.class, eventStorageEngineFactory);
    }

    /**
     * Registers the given {@link AsyncEventStore} factory in this {@code Configurer}.
     * <p>
     * The {@code eventStoreFactory} receives the {@link NewConfiguration} as input and is expected to return a
     * {@link AsyncEventStore} instance.
     *
     * @param eventStoreFactory The factory building the {@link AsyncEventStore}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer registerEventStore(@Nonnull ComponentFactory<AsyncEventStore> eventStoreFactory) {
        return registerComponent(AsyncEventStore.class, eventStoreFactory);
    }

    /**
     * Registers the given {@link Snapshotter} factory in this {@code Configurer}.
     * <p>
     * The {@code snapshotterFactory} receives the {@link NewConfiguration} as input and is expected to return a
     * {@link Snapshotter} instance.
     *
     * @param snapshotterFactory The factory building the {@link Snapshotter}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer registerSnapshotter(
            @Nonnull ComponentFactory<Snapshotter> snapshotterFactory
    ) {
        return registerComponent(Snapshotter.class, snapshotterFactory);
    }

    /**
     * Delegates the given {@code configureTask} to the {@link ModellingConfigurer} this {@code EventSourcingConfigurer}
     * delegates to.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code ModellingConfigurer}.
     *
     * @param configureTask Lambda consuming the delegate {@link ModellingConfigurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer modelling(@Nonnull Consumer<ModellingConfigurer> configureTask) {
        return delegate(ModellingConfigurer.class, configureTask);
    }

    /**
     * Delegates the given {@code configureTask} to the {@link MessagingConfigurer} this {@code EventSourcingConfigurer}
     * delegates to.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code MessagingConfigurer}.
     *
     * @param configureTask Lambda consuming the delegate {@link MessagingConfigurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer messaging(@Nonnull Consumer<MessagingConfigurer> configureTask) {
        return delegate(MessagingConfigurer.class, configureTask);
    }

    /**
     * Delegates the given {@code configureTask} to the {@link AxonApplication} this {@code EventSourcingConfigurer}
     * delegates to.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code AxonApplication}.
     *
     * @param configureTask Lambda consuming the delegate {@link AxonApplication}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer application(@Nonnull Consumer<AxonApplication> configureTask) {
        return delegate(AxonApplication.class, configureTask);
    }
}
