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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.configuration.EventProcessingConfigurer;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;

import java.util.Objects;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.axonframework.messaging.configuration.reflection.ParameterResolverFactoryUtils.registerToComponentRegistry;

/**
 * The messaging {@link ApplicationConfigurer} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for {@link #registerCommandBus(ComponentBuilder) command},
 * {@link #registerEventSink(ComponentBuilder) event}, and {@link #registerQueryBus(ComponentBuilder) query}
 * infrastructure components.
 * <p>
 * This configurer registers the following defaults:
 * <ul>
 *     <li>Registers a {@link org.axonframework.messaging.ClassBasedMessageTypeResolver} as the {@link org.axonframework.messaging.MessageTypeResolver}</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.gateway.DefaultCommandGateway} as the {@link org.axonframework.commandhandling.gateway.CommandGateway}</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.SimpleCommandBus} as the {@link CommandBus}</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.gateway.DefaultEventGateway} as the {@link org.axonframework.eventhandling.gateway.EventGateway}</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.SimpleEventBus} as the {@link org.axonframework.eventhandling.EventBus}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.DefaultQueryGateway} as the {@link org.axonframework.queryhandling.QueryGateway}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryBus} as the {@link QueryBus}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryUpdateEmitter} as the {@link QueryUpdateEmitter}</li>
 * </ul>
 * To replace or decorate any of these defaults, use their respective interfaces as the identifier. For example, to
 * adjust the {@code CommandBus}, invoke {@link #componentRegistry(Consumer)} and
 * {@link ComponentRegistry#registerComponent(Class, ComponentBuilder)} with {@code CommandBus.class} to replace it.
 * <p>
 * <pre><code>
 *     MessagingConfigurer.create()
 *                        .componentRegistry(cr -> cr.registerEnhancer(CommandBus.class, (config, component) -> ...));
 * </code></pre>
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class MessagingConfigurer implements ApplicationConfigurer {

    private final ApplicationConfigurer delegate;
    private final EventProcessingConfigurer eventProcessing;

    /**
     * Constructs a {@code MessagingConfigurer} based on the given {@code delegate}.
     *
     * @param delegate The delegate {@code ApplicationConfigurer} the {@code MessagingConfigurer} is based on.
     */
    private MessagingConfigurer(@Nonnull ApplicationConfigurer delegate) {
        this.delegate =
                requireNonNull(delegate, "The Application Configurer cannot be null.");
        this.eventProcessing = new EventProcessingConfigurer(this);
    }

    /**
     * Creates a MessagingConfigurer that enhances an existing {@code ApplicationConfigurer}. This method is useful when
     * applying multiple specialized Configurers to configure a single application.
     *
     * @param applicationConfigurer The {@code ApplicationConfigurer} to enhance with configuration of messaging
     *                              components.
     * @return The current instance of the {@code Configurer} for a fluent API.
     * @see #create()
     */
    public static MessagingConfigurer enhance(@Nonnull ApplicationConfigurer applicationConfigurer) {
        return new MessagingConfigurer(applicationConfigurer)
                .componentRegistry(cr -> cr
                        .registerEnhancer(new MessagingConfigurationDefaults())
                );
    }

    /**
     * Build a default {@code MessagingConfigurer} instance with several messaging defaults, as well as methods to
     * register (e.g.) a {@link #registerCommandBus(ComponentBuilder) command bus}.
     * <p>
     * Besides the specific operations, the {@code MessagingConfigurer} allows for configuring generic
     * {@link Component components}, {@link ComponentDecorator component decorators},
     * {@link ConfigurationEnhancer enhancers}, and {@link Module modules} for a message-driven application.
     *
     * @return A {@code MessagingConfigurer} instance for further configuring.
     * @see #enhance(ApplicationConfigurer)
     */
    public static MessagingConfigurer create() {
        return enhance(new DefaultAxonApplication());
    }

    /**
     * Registers the given {@link MessageTypeResolver} factory in this {@code Configurer}. This is the global
     * {@link MessageTypeResolver}, whose mappings can be accessed by all Modules and Components within the
     * application.
     * <p>
     * The {@code commandBusFactory} receives the {@link Configuration} as input and is expected to return a
     * {@link MessageTypeResolver} instance.
     *
     * @param messageTypeResolverFactory The factory building the {@link MessageTypeResolver}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerMessageTypeResolver(
            @Nonnull ComponentBuilder<MessageTypeResolver> messageTypeResolverFactory
    ) {
        delegate.componentRegistry(cr -> cr.registerComponent(
                MessageTypeResolver.class, messageTypeResolverFactory
        ));
        return this;
    }

    /**
     * Registers the given {@link CommandBus} factory in this {@code Configurer}.
     * <p>
     * The {@code commandBusBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@link CommandBus} instance.
     *
     * @param commandBusBuilder The builder constructing the {@link CommandBus}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerCommandBus(@Nonnull ComponentBuilder<CommandBus> commandBusBuilder) {
        delegate.componentRegistry(cr -> cr.registerComponent(CommandBus.class, commandBusBuilder));
        return this;
    }

    /**
     * Registers the given {@link EventSink} factory in this {@code Configurer}.
     * <p>
     * The {@code eventSinkBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@link EventSink} instance.
     *
     * @param eventSinkBuilder The builder constructing the {@link EventSink}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerEventSink(@Nonnull ComponentBuilder<EventSink> eventSinkBuilder) {
        delegate.componentRegistry(cr -> cr.registerComponent(EventSink.class, eventSinkBuilder));
        return this;
    }

    /**
     * Registers the given {@link QueryBus} factory in this {@code Configurer}.
     * <p>
     * The {@code queryBusBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@link QueryBus} instance.
     *
     * @param queryBusBuilder The builder constructing the {@link QueryBus}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerQueryBus(@Nonnull ComponentBuilder<QueryBus> queryBusBuilder) {
        delegate.componentRegistry(cr -> cr.registerComponent(QueryBus.class, queryBusBuilder));
        return this;
    }

    /**
     * Registers the given {@link ParameterResolverFactory} factory in this {@code Configurer}.
     * <p>
     * The {@code parameterResolverFactoryBuilder} receives the {@link Configuration} as input and is expected to return
     * a {@link ParameterResolverFactory} instance.
     *
     * @param parameterResolverFactoryBuilder The builder constructing the {@link ParameterResolverFactory}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerParameterResolverFactory(
            @Nonnull ComponentBuilder<ParameterResolverFactory> parameterResolverFactoryBuilder
    ) {
        delegate.componentRegistry(registry -> registerToComponentRegistry(
                registry,
                parameterResolverFactoryBuilder::build
        ));
        return this;
    }

    /**
     * Registers the given {@link QueryUpdateEmitter} factory in this {@code Configurer}.
     * <p>
     * The {@code queryUpdateEmitterBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@link QueryUpdateEmitter} instance.
     *
     * @param queryUpdateEmitterBuilder The builder constructing the {@link QueryUpdateEmitter}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerQueryUpdateEmitter(
            @Nonnull ComponentBuilder<QueryUpdateEmitter> queryUpdateEmitterBuilder
    ) {
        delegate.componentRegistry(
                cr -> cr.registerComponent(QueryUpdateEmitter.class, queryUpdateEmitterBuilder)
        );
        return this;
    }

    @Override
    public MessagingConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        delegate.componentRegistry(
                requireNonNull(componentRegistrar, "The configure task must no be null.")
        );
        return this;
    }

    @Override
    public MessagingConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        delegate.lifecycleRegistry(
                requireNonNull(lifecycleRegistrar, "The lifecycle registrar must not be null.")
        );
        return this;
    }


    public MessagingConfigurer eventProcessing(@Nonnull Consumer<EventProcessingConfigurer> configurerTask) {
        Objects.requireNonNull(configurerTask, "The configurerTask may not be null");
        configurerTask.accept(eventProcessing);
        return this;
    }

    @Override
    public AxonConfiguration build() {
        eventProcessing.build();
        return delegate.build();
    }
}
