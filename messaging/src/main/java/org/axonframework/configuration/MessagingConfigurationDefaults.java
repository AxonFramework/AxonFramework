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
import org.axonframework.commandhandling.SimpleCommandBus;
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
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.json.JacksonConverter;

/**
 * A {@link ConfigurationEnhancer} registering the default components of the {@link MessagingConfigurer}.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link org.axonframework.messaging.ClassBasedMessageTypeResolver} for class {@link org.axonframework.messaging.MessageTypeResolver}</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.gateway.DefaultCommandGateway} for class {@link org.axonframework.commandhandling.gateway.CommandGateway}</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.SimpleCommandBus} for class {@link CommandBus}</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.gateway.DefaultEventGateway} for class {@link org.axonframework.eventhandling.gateway.EventGateway}</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.SimpleEventBus} for class {@link org.axonframework.eventhandling.EventBus}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.DefaultQueryGateway} for class {@link org.axonframework.queryhandling.QueryGateway}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryBus} for class {@link QueryBus}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryUpdateEmitter} for class {@link QueryUpdateEmitter}</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class MessagingConfigurationDefaults implements ConfigurationEnhancer {

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(MessageTypeResolver.class,
                                      MessagingConfigurationDefaults::defaultMessageTypeResolver)
                .registerIfNotPresent(Converter.class, c -> new JacksonConverter())
                .registerIfNotPresent(CommandGateway.class, MessagingConfigurationDefaults::defaultCommandGateway)
                .registerIfNotPresent(CommandBus.class, MessagingConfigurationDefaults::defaultCommandBus)
                .registerIfNotPresent(EventGateway.class, MessagingConfigurationDefaults::defaultEventGateway)
                .registerIfNotPresent(EventSink.class, MessagingConfigurationDefaults::defaultEventSink)
                .registerIfNotPresent(EventBus.class, MessagingConfigurationDefaults::defaultEventBus)
                .registerIfNotPresent(QueryGateway.class, MessagingConfigurationDefaults::defaultQueryGateway)
                .registerIfNotPresent(QueryBus.class, MessagingConfigurationDefaults::defaultQueryBus)
                .registerIfNotPresent(QueryUpdateEmitter.class,
                                      MessagingConfigurationDefaults::defaultQueryUpdateEmitter);
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
                config.getComponent(MessageTypeResolver.class)
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
}
