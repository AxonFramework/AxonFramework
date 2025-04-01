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
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.Component;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.modelling.configuration.ModellingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

import java.util.function.Consumer;

/**
 * The event sourcing {@link ApplicationConfigurer} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for {@link #registerEventStorageEngine(ComponentFactory)} the event storage engine} and
 * {@link #registerEventStore(ComponentFactory) event store} infrastructure components.
 * <p>
 * This configurer registers the following defaults:
 * <ul>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver} for class {@link org.axonframework.eventsourcing.eventstore.TagResolver}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine} for class {@link org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.SimpleEventStore} for class {@link org.axonframework.eventsourcing.eventstore.AsyncEventStore}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.SimpleEventStore} for class {@link EventSink}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.AggregateSnapshotter} for class {@link org.axonframework.eventsourcing.Snapshotter}</li>
 * </ul>
 * To replace or decorate any of these defaults, use their respective interfaces as the identifier. For example, to
 * adjust the {@code EventStore}, invoke {@link #registerComponent(Class, ComponentFactory)} with
 * {@code EventStore.class} to replace it.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class EventSourcingConfigurer implements ApplicationConfigurer {

    private final ModellingConfigurer delegate;

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
        return new EventSourcingConfigurer(ModellingConfigurer.create());
    }

    /**
     * Construct a {@code EventSourcingConfigurer} using the given {@code delegate} to delegate all registry-specific
     * operations to.
     * <p>
     * It is recommended to use the {@link #create()} method in most cases instead of this constructor.
     *
     * @param delegate The delegate {@code ModellingConfigurer} the {@code EventSourcingConfigurer} is based on.
     */
    public EventSourcingConfigurer(@Nonnull ModellingConfigurer delegate) {
        delegate.componentRegistry(cr -> cr.registerEnhancer(new EventSourcingConfigurationDefaults()));
        this.delegate = delegate;
    }

    /**
     * Registers the given stateful command handling {@code moduleBuilder} to use in this configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the result of the given {@code moduleBuilder}
     * will not be accessible from other {@code Modules} to enforce encapsulation.
     *
     * @param moduleBuilder The builder returning a stateful command handling module to register with
     *                      {@code this ModellingConfigurer}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public EventSourcingConfigurer registerStatefulCommandHandlingModule(
            ModuleBuilder<StatefulCommandHandlingModule> moduleBuilder
    ) {
        return modelling(modellingConfigurer -> modellingConfigurer.registerStatefulCommandHandlingModule(
                moduleBuilder
        ));
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
        componentRegistry(cr -> cr.registerComponent(TagResolver.class, tagResolverFactory));
        return this;
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
        componentRegistry(cr -> cr.registerComponent(AsyncEventStorageEngine.class, eventStorageEngineFactory));
        return this;
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
        componentRegistry(cr -> cr.registerComponent(AsyncEventStore.class, eventStoreFactory));
        return this;
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
        componentRegistry(cr -> cr.registerComponent(Snapshotter.class, snapshotterFactory));
        return this;
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
        configureTask.accept(delegate);
        return this;
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
        delegate.messaging(configureTask);
        return this;
    }

    @Override
    public ApplicationConfigurer componentRegistry(Consumer<ComponentRegistry> componentRegistrar) {
        return delegate.componentRegistry(componentRegistrar);
    }

    @Override
    public ApplicationConfigurer lifecycleRegistry(Consumer<LifecycleRegistry> lifecycleRegistrar) {
        return delegate.lifecycleRegistry(lifecycleRegistrar);
    }

    @Override
    public AxonConfiguration build() {
        return delegate.build();
    }
}
