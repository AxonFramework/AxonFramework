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
import org.axonframework.commandhandling.CommandPriorityCalculator;
import org.axonframework.commandhandling.RoutingStrategy;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.ConvertingCommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.json.JacksonConverter;

/**
 * A {@link ConfigurationEnhancer} registering the default components of the {@link MessagingConfigurer}.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link org.axonframework.messaging.ClassBasedMessageTypeResolver} for class {@link org.axonframework.messaging.MessageTypeResolver}.</li>
 *     <li>Registers a {@link org.axonframework.serialization.json.JacksonConverter} for class {@link org.axonframework.serialization.Converter}.</li>
 *     <li>Registers a {@link org.axonframework.serialization.json.JacksonConverter} for class {@link org.axonframework.serialization.Converter} under the {@link #MESSAGE_CONVERTER_NAME}.</li>
 *     <li>Registers a {@link org.axonframework.serialization.json.JacksonConverter} for class {@link org.axonframework.serialization.Converter} under the {@link #EVENT_CONVERTER_NAME}.</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.gateway.DefaultCommandGateway} for class {@link org.axonframework.commandhandling.gateway.CommandGateway}.</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.SimpleCommandBus} for class {@link CommandBus}.</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.annotation.AnnotationRoutingStrategy} for class {@link RoutingStrategy}.</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.gateway.DefaultEventGateway} for class {@link org.axonframework.eventhandling.gateway.EventGateway}.</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.SimpleEventBus} for class {@link org.axonframework.eventhandling.EventBus}.</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.DefaultQueryGateway} for class {@link org.axonframework.queryhandling.QueryGateway}.</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryBus} for class {@link QueryBus}.</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryUpdateEmitter} for class {@link QueryUpdateEmitter}.</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class MessagingConfigurationDefaults implements ConfigurationEnhancer {

    /**
     * The order in which the {@link ConvertingCommandGateway} is applied to the {@link CommandGateway} in the
     * {@link ComponentRegistry}. As such, any decorator with a lower value will be applied to the delegate, and any
     * higher value will be applied to the {@link ConvertingCommandGateway} itself. Using the same value can either lead
     * to application of the decorator to the delegate or the converting command gateway, depending on the order of
     * registration.
     * <p>
     * The order of the {@link ConvertingCommandGateway} is set to {@code Integer.MIN_VALUE + 100} to ensure it is
     * applied very early in the configuration process, but not the earliest to allow for other decorators to be
     * applied.
     */
    public static final int CONVERTING_COMMAND_GATEWAY_ORDER = Integer.MIN_VALUE + 100;

    /**
     * Name used to register the messaging-specific {@link Converter} with the {@link ComponentRegistry}.
     * <p>
     * This {@code Converter} is used to convert <b>all</b> {@link Message#payload() Message payloads} by default. The
     * payload for {@link EventMessage EventMessages} may be overwritten by the {@code Converter} configured under the
     * {@link #EVENT_CONVERTER_NAME}.
     */
    public static final String MESSAGE_CONVERTER_NAME = "messageConverter";
    /**
     * Name used to register the event-specific {@link Converter} with the {@link ComponentRegistry}.
     * <p>
     * This {@code Converter} is used to convert <b>all</b> {@link EventMessage#payload() Message payloads} by default.
     */
    public static final String EVENT_CONVERTER_NAME = "eventConverter";

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(MessageTypeResolver.class,
                                      MessagingConfigurationDefaults::defaultMessageTypeResolver)
                .registerIfNotPresent(Converter.class, c -> new JacksonConverter())
                .registerIfNotPresent(Converter.class, MESSAGE_CONVERTER_NAME,
                                      c -> c.getComponent(Converter.class))
                .registerIfNotPresent(Converter.class, EVENT_CONVERTER_NAME,
                                      c -> c.getComponent(Converter.class, MESSAGE_CONVERTER_NAME))
                .registerIfNotPresent(CommandGateway.class, MessagingConfigurationDefaults::defaultCommandGateway)
                .registerIfNotPresent(CommandBus.class, MessagingConfigurationDefaults::defaultCommandBus)
                .registerIfNotPresent(RoutingStrategy.class, MessagingConfigurationDefaults::defaultRoutingStrategy)
                .registerIfNotPresent(EventGateway.class, MessagingConfigurationDefaults::defaultEventGateway)
                .registerIfNotPresent(EventSink.class, MessagingConfigurationDefaults::defaultEventSink)
                .registerIfNotPresent(EventBus.class, MessagingConfigurationDefaults::defaultEventBus)
                .registerIfNotPresent(QueryGateway.class, MessagingConfigurationDefaults::defaultQueryGateway)
                .registerIfNotPresent(QueryBus.class, MessagingConfigurationDefaults::defaultQueryBus)
                .registerIfNotPresent(QueryUpdateEmitter.class,
                                      MessagingConfigurationDefaults::defaultQueryUpdateEmitter);
        registry.registerDecorator(
                CommandGateway.class,
                CONVERTING_COMMAND_GATEWAY_ORDER,
                (config, name, delegate) -> new ConvertingCommandGateway(
                        delegate,
                        config.getComponent(Converter.class, MESSAGE_CONVERTER_NAME)
                )
        );
    }

    private static MessageTypeResolver defaultMessageTypeResolver(Configuration config) {
        return new ClassBasedMessageTypeResolver();
    }

    private static CommandBus defaultCommandBus(Configuration config) {
        return config.getOptionalComponent(TransactionManager.class)
                     .map(SimpleCommandBus::new)
                     .orElse(new SimpleCommandBus());
    }

    private static CommandGateway defaultCommandGateway(Configuration config) {
        return new DefaultCommandGateway(
                config.getComponent(CommandBus.class),
                config.getComponent(MessageTypeResolver.class),
                config.getOptionalComponent(CommandPriorityCalculator.class).orElse(null),
                config.getOptionalComponent(RoutingStrategy.class).orElse(null)
        );
    }

    private static EventBus defaultEventBus(Configuration config) {
        return SimpleEventBus.builder()
                             .build();
    }

    // TODO #3392 - Replace for actual EventSink implementation.
    private static EventSink defaultEventSink(Configuration config) {
        EventBus eventBus = config.getComponent(EventBus.class);
        return (context, events) -> {
            eventBus.publish(events);
            return FutureUtils.emptyCompletedFuture();
        };
    }

    private static EventGateway defaultEventGateway(Configuration config) {
        return new DefaultEventGateway(
                config.getComponent(EventSink.class),
                config.getComponent(MessageTypeResolver.class)
        );
    }

    private static QueryGateway defaultQueryGateway(Configuration config) {
        return DefaultQueryGateway.builder()
                                  .queryBus(config.getComponent(QueryBus.class))
                                  .build();
    }

    private static QueryBus defaultQueryBus(Configuration config) {
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

    private static QueryUpdateEmitter defaultQueryUpdateEmitter(Configuration config) {
        return SimpleQueryUpdateEmitter.builder().build();
    }

    private static RoutingStrategy defaultRoutingStrategy(Configuration config) {
        return new AnnotationRoutingStrategy();
    }
}