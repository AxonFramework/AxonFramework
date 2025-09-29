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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.configuration.EventProcessingConfigurer;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.interceptors.DispatchInterceptorRegistry;
import org.axonframework.messaging.interceptors.HandlerInterceptorRegistry;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.configuration.QueryHandlingModule;

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
 * This configurer registers several defaults, provided by class {@link MessagingConfigurationDefaults}.<br/> To replace
 * or decorate any of these defaults, use their respective interfaces as the identifier. For example, to adjust the
 * {@code CommandBus}, invoke {@link #componentRegistry(Consumer)} and
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
     * Registers the given {@link UnitOfWorkFactory} factory in this {@code Configurer}.
     * <p>
     * The {@code unitOfWorkFactoryBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@link UnitOfWorkFactory} instance.
     *
     * @param unitOfWorkFactoryBuilder The builder constructing the {@link UnitOfWorkFactory}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerUnitOfWorkFactory(
            @Nonnull ComponentBuilder<UnitOfWorkFactory> unitOfWorkFactoryBuilder
    ) {
        delegate.componentRegistry(
                cr -> cr.registerComponent(UnitOfWorkFactory.class, unitOfWorkFactoryBuilder)
        );
        return this;
    }

    /**
     * Registers the given {@link CorrelationDataProvider} factory in this {@code providerBuilder}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@code CorrelationDataProvider} instance.
     * <p>
     * {@code CorrelationDataProviders} are typically automatically registered with all applicable infrastructure
     * components through the {@link CorrelationDataProviderRegistry}.
     *
     * @param providerBuilder The builder constructing the {@link CorrelationDataProvider}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerCorrelationDataProvider(
            @Nonnull ComponentBuilder<CorrelationDataProvider> providerBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                CorrelationDataProviderRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerProvider(providerBuilder)
        ));
        return this;
    }

    /**
     * Registers the given generic {@link Message} {@link MessageDispatchInterceptor} factory in this
     * {@code Configurer}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a generic
     * {@code Message} {@code MessageDispatchInterceptor} instance.
     * <p>
     * Generic {@code MessageDispatchInterceptors} are typically automatically registered with all applicable
     * infrastructure components through the {@link DispatchInterceptorRegistry}.
     *
     * @param interceptorBuilder The builder constructing the generic {@link Message}
     *                           {@link MessageDispatchInterceptor}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerDispatchInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<Message>> interceptorBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                DispatchInterceptorRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerInterceptor(interceptorBuilder)
        ));
        return this;
    }

    /**
     * Registers the given {@link CommandMessage}-specific {@link MessageDispatchInterceptor} factory in this
     * {@code Configurer}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@code CommandMessage}-specific {@code MessageDispatchInterceptor} instance.
     * <p>
     * {@code CommandMessage} {@code MessageDispatchInterceptors} are typically automatically registered with all
     * applicable infrastructure components through the {@link DispatchInterceptorRegistry}.
     *
     * @param interceptorBuilder The builder constructing the {@link CommandMessage}-specific
     *                           {@link MessageDispatchInterceptor}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerCommandDispatchInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super CommandMessage>> interceptorBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                DispatchInterceptorRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerCommandInterceptor(interceptorBuilder)
        ));
        return this;
    }

    /**
     * Registers the given {@link EventMessage}-specific {@link MessageDispatchInterceptor} factory in this
     * {@code Configurer}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@code EventMessage}-specific {@code MessageDispatchInterceptor} instance.
     * <p>
     * {@code EventMessage}-specific {@code MessageDispatchInterceptors} are typically automatically registered with all
     * applicable infrastructure components through the {@link DispatchInterceptorRegistry}.
     *
     * @param interceptorBuilder The builder constructing the {@link EventMessage}-specific
     *                           {@link MessageDispatchInterceptor}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerEventDispatchInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super EventMessage>> interceptorBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                DispatchInterceptorRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerEventInterceptor(interceptorBuilder)
        ));
        return this;
    }

    /**
     * Registers the given {@link QueryMessage}-specific {@link MessageDispatchInterceptor} factory in this
     * {@code Configurer}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@code QueryMessage}-specific {@code MessageDispatchInterceptor} instance.
     * <p>
     * {@code QueryMessage}-specific {@code MessageDispatchInterceptors} are typically automatically registered with all
     * applicable infrastructure components through the {@link DispatchInterceptorRegistry}.
     *
     * @param interceptorBuilder The builder constructing the {@link QueryMessage}-specific
     *                           {@link MessageDispatchInterceptor}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerQueryDispatchInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super QueryMessage>> interceptorBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                DispatchInterceptorRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerQueryInterceptor(interceptorBuilder)
        ));
        return this;
    }

    /**
     * Registers the given generic {@link Message} {@link MessageHandlerInterceptor} factory in this
     * {@code Configurer}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a generic
     * {@code Message} {@code MessageHandlerInterceptor} instance.
     * <p>
     * Generic {@code MessageHandlerInterceptors} are typically automatically registered with all applicable
     * infrastructure components through the {@link HandlerInterceptorRegistry}.
     *
     * @param interceptorBuilder The builder constructing the generic {@link Message}
     *                           {@link MessageHandlerInterceptor}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerMessageHandlerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<Message>> interceptorBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                HandlerInterceptorRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerInterceptor(interceptorBuilder)
        ));
        return this;
    }

    /**
     * Registers the given {@link CommandMessage} {@link MessageHandlerInterceptor} factory in this {@code Configurer}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@code CommandMessage} {@code MessageHandlerInterceptor} instance.
     * <p>
     * {@code CommandMessage} {@code MessageHandlerInterceptors} are typically automatically registered with all
     * applicable infrastructure components through the {@link HandlerInterceptorRegistry}.
     *
     * @param interceptorBuilder The builder constructing the {@link CommandMessage} {@link MessageHandlerInterceptor}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerCommandHandlerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super CommandMessage>> interceptorBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                HandlerInterceptorRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerCommandInterceptor(interceptorBuilder)
        ));
        return this;
    }

    /**
     * Registers the given {@link EventMessage} {@link MessageHandlerInterceptor} factory in this {@code Configurer}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@code EventMessage} {@code MessageHandlerInterceptor} instance.
     * <p>
     * {@code EventMessage} {@code MessageHandlerInterceptors} are typically automatically registered with all
     * applicable infrastructure components through the {@link HandlerInterceptorRegistry}.
     *
     * @param interceptorBuilder The builder constructing the {@link EventMessage} {@link MessageHandlerInterceptor}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerEventHandlerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super EventMessage>> interceptorBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                HandlerInterceptorRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerEventInterceptor(interceptorBuilder)
        ));
        return this;
    }

    /**
     * Registers the given {@link QueryMessage} {@link MessageHandlerInterceptor} factory in this {@code Configurer}.
     * <p>
     * The {@code interceptorBuilder} receives the {@link Configuration} as input and is expected to return a
     * {@code QueryMessage} {@code MessageHandlerInterceptor} instance.
     * <p>
     * {@code QueryMessage} {@code MessageHandlerInterceptors} are typically automatically registered with all
     * applicable infrastructure components through the {@link HandlerInterceptorRegistry}.
     *
     * @param interceptorBuilder The builder constructing the {@link QueryMessage} {@link MessageHandlerInterceptor}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public MessagingConfigurer registerQueryHandlerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super QueryMessage>> interceptorBuilder
    ) {
        delegate.componentRegistry(cr -> cr.registerDecorator(
                HandlerInterceptorRegistry.class,
                0,
                (config, name, delegate) -> delegate.registerQueryInterceptor(interceptorBuilder)
        ));
        return this;
    }

    /**
     * Registers the given {@link ModuleBuilder builder} for a {@link CommandHandlingModule} to use in this
     * configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the result of the given {@code moduleBuilder}
     * will not be accessible from other {@code Modules} to enforce encapsulation. The sole exception to this, are
     * {@code Modules} registered with the resulting {@link CommandHandlingModule} itself.
     *
     * @param moduleBuilder The builder returning a command handling module to register with
     *                      {@code this MessagingConfigurer}.
     * @return A {@code MessagingConfigurer} instance for further configuring.
     */
    @Nonnull
    public MessagingConfigurer registerCommandHandlingModule(
            @Nonnull ModuleBuilder<CommandHandlingModule> moduleBuilder
    ) {
        Objects.requireNonNull(moduleBuilder, "The moduleBuilder cannot be null.");
        delegate.componentRegistry(cr -> cr.registerModule(moduleBuilder.build()));
        return this;
    }

    /**
     * Registers the given {@link ModuleBuilder builder} for a {@link QueryHandlingModule} to use in this
     * configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the result of the given {@code moduleBuilder}
     * will not be accessible from other {@code Modules} to enforce encapsulation. The sole exception to this, are
     * {@code Modules} registered with the resulting {@link QueryHandlingModule} itself.
     *
     * @param moduleBuilder The builder returning a query handling module to register with
     *                      {@code this MessagingConfigurer}.
     * @return A {@code MessagingConfigurer} instance for further configuring.
     */
    @Nonnull
    public MessagingConfigurer registerQueryHandlingModule(
            @Nonnull ModuleBuilder<QueryHandlingModule> moduleBuilder
    ) {
        Objects.requireNonNull(moduleBuilder, "The moduleBuilder cannot be null.");
        delegate.componentRegistry(cr -> cr.registerModule(moduleBuilder.build()));
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


    /**
     * Delegates given {@code configurerTask} to the {@link EventProcessingConfigurer}.
     * <p>
     * Use this operation to configure defaults and register {@link EventProcessor}s.
     *
     * @param configurerTask Lambda consuming the {@link EventProcessingConfigurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
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