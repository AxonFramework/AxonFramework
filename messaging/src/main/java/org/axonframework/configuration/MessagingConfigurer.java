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

import java.util.ServiceLoader;
import java.util.function.Consumer;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.config.CommandBusBuilder;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;

/**
 * The messaging {@link NewConfigurer} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for {@link #registerCommandBus(ComponentBuilder) command},
 * {@link #registerEventSink(ComponentBuilder) evnet}, and {@link #registerQueryBus(ComponentBuilder) query}
 * infrastructure components.
 * <p>
 * This configurer registers the following defaults:
 * <ul>
 *     <li>Registers a {@link org.axonframework.messaging.ClassBasedMessageTypeResolver} as the {@link org.axonframework.messaging.MessageTypeResolver}</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.gateway.DefaultCommandGateway} as the {@link org.axonframework.commandhandling.gateway.CommandGateway}</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.SimpleCommandBus} as the {@link CommandBus}</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.gateway.DefaultEventGateway} as the {@link org.axonframework.eventhandling.gateway.EventGateway}</li>
 *     <li>Registers a TODO as the {@link EventSink}</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.SimpleEventBus} as the {@link org.axonframework.eventhandling.EventBus}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.DefaultQueryGateway} as the {@link org.axonframework.queryhandling.QueryGateway}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryBus} as the {@link QueryBus}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryUpdateEmitter} as the {@link QueryUpdateEmitter}</li>
 * </ul>
 * To replace or decorate any of these defaults, use their respective interfaces as the identifier. For example, to
 * adjust the {@code CommandBus}, invoke {@link #registerComponent(Class, ComponentBuilder)} with
 * {@code CommandBus.class} to replace it.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public class MessagingConfigurer extends DelegatingConfigurer<MessagingConfigurer> {

    /**
     * Build a default {@code MessagingConfigurer} instance with several messaging defaults, as well as methods to
     * register (e.g.) a {@link #registerCommandBus(ComponentBuilder) command bus}.
     * <p>
     * Besides the specific operations, the {@code MessagingConfigurer} allows for configuring generic
     * {@link Component components}, {@link ComponentDecorator component decorators},
     * {@link ConfigurerEnhancer enhancers}, and {@link Module modules} for a message-driven application.
     *
     * @return A {@code MessagingConfigurer} instance for further configuring.
     */
    public static MessagingConfigurer defaultConfigurer() {
        return configurer(true);
    }

    /**
     * Build a {@code MessagingConfigurer} instance with several messaging defaults, as well as methods to register
     * (e.g.) a {@link #registerCommandBus(ComponentBuilder) command bus}.
     * <p>
     * Besides the specific operations, the {@code MessagingConfigurer} allows for configuring generic
     * {@link Component components}, {@link ComponentDecorator component decorators},
     * {@link ConfigurerEnhancer enhancers}, and {@link Module modules} for a message-driven application.
     * <p>
     * When {@code autoLocateEnhancers} is {@code true}, a {@link ServiceLoader} will be used to locate all declared
     * instances of type {@link ConfigurerEnhancer}. Each of the discovered instances will be invoked, allowing it to
     * set default values for the returned {@code MessagingConfigurer}.
     *
     * @param autoLocateEnhancers Flag indicating whether {@link ConfigurerEnhancer} on the classpath should be
     *                            automatically retrieved. Should be set to {@code false} when using an application
     *                            container, such as Spring or CDI.
     * @return A {@code MessagingConfigurer} instance for further configuring.
     */
    public static MessagingConfigurer configurer(boolean autoLocateEnhancers) {
        return new MessagingConfigurer(RootConfigurer.configurer(autoLocateEnhancers))
                .registerComponent(MessageTypeResolver.class, MessagingConfigurer::defaultMessageTypeResolver)
                .registerComponent(CommandGateway.class, MessagingConfigurer::defaultCommandGateway)
                .registerComponent(CommandBus.class, MessagingConfigurer::defaultCommandBus)
                .registerComponent(EventGateway.class, MessagingConfigurer::defaultEventGateway)
                .registerComponent(EventSink.class, MessagingConfigurer::defaultEventSink)
                .registerComponent(EventBus.class, MessagingConfigurer::defaultEventBus)
                .registerComponent(QueryGateway.class, MessagingConfigurer::defaultQueryGateway)
                .registerComponent(QueryBus.class, MessagingConfigurer::defaultQueryBus)
                .registerComponent(QueryUpdateEmitter.class, MessagingConfigurer::defaultQueryUpdateEmitter);
    }

    /**
     * Private constructor to enforce usage of {@link #defaultConfigurer()} or {@link #configurer(boolean)}.
     */
    private <C extends NewConfigurer<C>> MessagingConfigurer(@Nonnull C delegate) {
        super(delegate);
    }

    /**
     * Configures the given Command Bus to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link CommandBus} instance.
     *
     * @param commandBusBuilder The builder function for the {@link CommandBus}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerCommandBus(@Nonnull ComponentBuilder<CommandBus> commandBusBuilder) {
        return registerComponent(CommandBus.class, commandBusBuilder);
    }

    /**
     * Configures the given Event Bus to use in this configuration. The builder receives the Configuration as input and
     * is expected to return a fully initialized {@link EventSink} instance.
     * <p>
     * Note that this builder should not be used when an Event Store is configured. Since Axon 3, the Event Store will
     * act as Event Bus implementation as well.
     *
     * @param eventSinkBuilder The builder function for the {@link EventSink}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerEventSink(@Nonnull ComponentBuilder<EventSink> eventSinkBuilder) {
        return registerComponent(EventSink.class, eventSinkBuilder);
    }

    /**
     * Configures the given Query Bus to use in this configuration. The builder receives the Configuration as input and
     * is expected to return a fully initialized {@link QueryBus} instance.
     *
     * @param queryBusBuilder The builder function for the {@link QueryBus}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerQueryBus(@Nonnull ComponentBuilder<QueryBus> queryBusBuilder) {
        return registerComponent(QueryBus.class, queryBusBuilder);
    }

    /**
     * Configures the given Query Update Emitter to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link QueryUpdateEmitter} instance.
     *
     * @param queryUpdateEmitterBuilder The builder function for the {@link QueryUpdateEmitter}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerQueryUpdateEmitter(
            @Nonnull ComponentBuilder<QueryUpdateEmitter> queryUpdateEmitterBuilder
    ) {
        return registerComponent(QueryUpdateEmitter.class, queryUpdateEmitterBuilder);
    }

    /**
     * Delegates the given {@code configureTask} to the {@link RootConfigurer} this {@code MessagingConfigurer}
     * delegates to.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code RootConfigurer}.
     *
     * @param configureTask Lambda consuming the delegate {@link RootConfigurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer root(@Nonnull Consumer<RootConfigurer> configureTask) {
        return delegate(RootConfigurer.class, configureTask);
    }

    private static MessageTypeResolver defaultMessageTypeResolver(NewConfiguration config) {
        return new ClassBasedMessageTypeResolver();
    }

    private static CommandBus defaultCommandBus(NewConfiguration config) {
        // TODO #3067 - Discuss to adjust this to registerComponent-and-Decorator invocations
        CommandBusBuilder commandBusBuilder = CommandBusBuilder.forSimpleCommandBus();
        config.getOptionalComponent(TransactionManager.class)
              .ifPresent(commandBusBuilder::withTransactions);
        return commandBusBuilder.build();
    }

    private static CommandGateway defaultCommandGateway(NewConfiguration config) {
        return new DefaultCommandGateway(
                config.getComponent(CommandBus.class),
                config.getComponent(MessageTypeResolver.class)
        );
    }

    private static EventBus defaultEventBus(NewConfiguration config) {
        return SimpleEventBus.builder()
                             .build();
    }

    private static EventSink defaultEventSink(NewConfiguration config) {
        EventBus eventBus = config.getComponent(EventBus.class);
        return (context, events) -> {
            eventBus.publish(events);
            return FutureUtils.emptyCompletedFuture();
        };
    }

    private static EventGateway defaultEventGateway(NewConfiguration config) {
        return DefaultEventGateway.builder()
                                  .eventBus(config.getComponent(EventBus.class))
                                  .build();
    }

    private static QueryGateway defaultQueryGateway(NewConfiguration config) {
        return DefaultQueryGateway.builder()
                                  .queryBus(config.getComponent(QueryBus.class))
                                  .build();
    }

    private static QueryBus defaultQueryBus(NewConfiguration config) {
        return SimpleQueryBus.builder()
                             .transactionManager(config.getComponent(
                                     TransactionManager.class,
                                     NoTransactionManager::instance
                             ))
                             .errorHandler(config.getComponent(
                                     QueryInvocationErrorHandler.class,
                                     () -> LoggingQueryInvocationErrorHandler.builder().build()
                             ))
                             .queryUpdateEmitter(config.getComponent(QueryUpdateEmitter.class))
                             .build();
    }

    private static QueryUpdateEmitter defaultQueryUpdateEmitter(NewConfiguration config) {
        return SimpleQueryUpdateEmitter.builder().build();
    }
}
