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

package org.axonframework.eventsourcing.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDecorator;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.common.configuration.Module;
import org.axonframework.common.configuration.ModuleBuilder;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.modelling.configuration.EntityModule;
import org.axonframework.modelling.configuration.ModellingConfigurer;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The event sourcing {@link ApplicationConfigurer} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for {@link #registerEventStorageEngine(ComponentBuilder)} the event storage engine} and
 * {@link #registerEventStore(ComponentBuilder) event store} infrastructure components.
 * <p>
 * This configurer registers the following defaults:
 * <ul>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver} for class {@link TagResolver}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine} for class {@link EventStorageEngine}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore} for class {@link EventStore}</li>
 *     <li>Registers a {@link org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore} for class {@link EventSink}</li>
 * </ul>
 * To replace or decorate any of these defaults, use their respective interfaces as the identifier. For example, to
 * adjust the {@code EventStore}, do
 * <pre><code>
 *     configurer.componentRegistry(cr ->
 *                  cr.registerComponent(EventStore.class, c -> new CustomEventStore()))
 * </code></pre>
 * to replace it.
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
        return enhance(ModellingConfigurer.create());
    }

    /**
     * Creates a EventSourcingConfigurer that enhances an existing {@code ModellingConfigurer}. This method is useful
     * when applying multiple specialized Configurers to configure a single application.
     *
     * @param modellingConfigurer The {@code ModellingConfigurer} to enhance with configuration of modelling
     *                            components.
     * @return The current instance of the {@code Configurer} for a fluent API.
     * @see #create()
     */
    public static EventSourcingConfigurer enhance(@Nonnull ModellingConfigurer modellingConfigurer) {
        return new EventSourcingConfigurer(modellingConfigurer)
                .componentRegistry(cr -> cr
                        .registerEnhancer(new EventSourcingConfigurationDefaults())
                );
    }

    /**
     * Construct a {@code EventSourcingConfigurer} using the given {@code delegate} to delegate all registry-specific
     * operations to.
     * <p>
     * It is recommended to use the {@link #create()} method in most cases instead of this constructor.
     *
     * @param delegate The delegate {@code ModellingConfigurer} the {@code EventSourcingConfigurer} is based on.
     */
    private EventSourcingConfigurer(@Nonnull ModellingConfigurer delegate) {
        Objects.requireNonNull(delegate, "The delegate ModellingConfigurer may not be null.");
        this.delegate = delegate;
    }

    /**
     * Registers the given command handling {@code moduleBuilder} to use in this configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the result of the given {@code moduleBuilder}
     * will not be accessible from other {@code Modules} to enforce encapsulation.
     *
     * @param moduleBuilder The builder returning a command handling module to register with
     *                      {@code this EventSourcingConfigurer}.
     * @return A {@code EventSourcingConfigurer} instance for further configuring.
     */
    public EventSourcingConfigurer registerCommandHandlingModule(
            ModuleBuilder<CommandHandlingModule> moduleBuilder
    ) {
        return modelling(modellingConfigurer -> modellingConfigurer.registerCommandHandlingModule(
                moduleBuilder
        ));
    }

    /**
     * Registers the given query handling {@code moduleBuilder} to use in this configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the result of the given {@code moduleBuilder}
     * will not be accessible from other {@code Modules} to enforce encapsulation.
     *
     * @param moduleBuilder The builder returning a query handling module to register with
     *                      {@code this EventSourcingConfigurer}.
     * @return A {@code EventSourcingConfigurer} instance for further configuring.
     */
    public EventSourcingConfigurer registerQueryHandlingModule(
            ModuleBuilder<QueryHandlingModule> moduleBuilder
    ) {
        return modelling(modellingConfigurer -> modellingConfigurer.registerQueryHandlingModule(
                moduleBuilder
        ));
    }

    /**
     * Registers the given {@code entityModule} on the root-level {@link Configuration}.
     * This will make the entity available in the globally available {@link org.axonframework.modelling.StateManager}.
     *
     * @param entityModule The entity module to register.
     * @param <I>          The type of identifier used to identify the entity that's being built.
     * @param <E>          The type of the entity being built.
     * @return A {@code EventSourcingConfigurer} instance for further configuring.
     */
    @Nonnull
    public <I, E> EventSourcingConfigurer registerEntity(@Nonnull EntityModule<I, E> entityModule) {
        return modelling(modellingConfigurer -> modellingConfigurer.registerEntity(entityModule));
    }

    /**
     * Registers the given {@link TagResolver} factory in this {@code Configurer}.
     * <p>
     * The {@code eventStorageEngineFactory} receives the {@link Configuration} as input and is expected to return a
     * {@link TagResolver} instance.
     *
     * @param tagResolverFactory The factory building the {@link TagResolver}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer registerTagResolver(@Nonnull ComponentBuilder<TagResolver> tagResolverFactory) {
        delegate.componentRegistry(cr -> cr.registerComponent(TagResolver.class, tagResolverFactory));
        return this;
    }

    /**
     * Registers the given {@link EventStorageEngine} factory in this {@code Configurer}.
     * <p>
     * The {@code eventStorageEngineFactory} receives the {@link Configuration} as input and is expected to return a
     * {@link EventStorageEngine} instance.
     *
     * @param eventStorageEngineFactory The factory building the {@link EventStorageEngine}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer registerEventStorageEngine(
            @Nonnull ComponentBuilder<EventStorageEngine> eventStorageEngineFactory
    ) {
        delegate.componentRegistry(
                cr -> cr.registerComponent(EventStorageEngine.class, eventStorageEngineFactory)
        );
        return this;
    }

    /**
     * Registers the given {@link EventStore} factory in this {@code Configurer}.
     * <p>
     * The {@code eventStoreFactory} receives the {@link Configuration} as input and is expected to return a
     * {@link EventStore} instance.
     *
     * @param eventStoreFactory The factory building the {@link EventStore}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer registerEventStore(@Nonnull ComponentBuilder<EventStore> eventStoreFactory) {
        delegate.componentRegistry(cr -> cr.registerComponent(EventStore.class, eventStoreFactory));
        return this;
    }

    /**
     * Delegates the given {@code configurerTask} to the {@link ModellingConfigurer} this
     * {@code EventSourcingConfigurer} delegates.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code ModellingConfigurer}.
     *
     * @param configurerTask Lambda consuming the delegate {@link ModellingConfigurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer modelling(@Nonnull Consumer<ModellingConfigurer> configurerTask) {
        configurerTask.accept(delegate);
        return this;
    }

    /**
     * Delegates the given {@code configurerTask} to the {@link MessagingConfigurer} this
     * {@code EventSourcingConfigurer} delegates.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code MessagingConfigurer}.
     *
     * @param configurerTask Lambda consuming the delegate {@link MessagingConfigurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public EventSourcingConfigurer messaging(@Nonnull Consumer<MessagingConfigurer> configurerTask) {
        delegate.messaging(configurerTask);
        return this;
    }

    @Override
    public EventSourcingConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        delegate.componentRegistry(componentRegistrar);
        return this;
    }

    @Override
    public EventSourcingConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        delegate.lifecycleRegistry(lifecycleRegistrar);
        return this;
    }

    @Override
    public AxonConfiguration build() {
        return delegate.build();
    }
}
