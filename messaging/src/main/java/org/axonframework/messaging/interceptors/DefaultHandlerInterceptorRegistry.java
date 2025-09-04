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

package org.axonframework.messaging.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.QueryMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link HandlerInterceptorRegistry}, maintaining lists of {@link CommandMessage},
 * {@link EventMessage}, and {@link QueryMessage}-specific
 * {@link MessageHandlerInterceptor MessageHandlerInterceptors}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class DefaultHandlerInterceptorRegistry implements HandlerInterceptorRegistry {

    private final List<ComponentBuilder<MessageHandlerInterceptor<CommandMessage>>> commandInterceptorBuilders = new ArrayList<>();
    private final List<ComponentBuilder<MessageHandlerInterceptor<EventMessage>>> eventInterceptorBuilders = new ArrayList<>();
    private final List<ComponentBuilder<MessageHandlerInterceptor<QueryMessage>>> queryInterceptorBuilders = new ArrayList<>();

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<Message>> interceptorBuilder
    ) {
        registerCommandInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = interceptorBuilder.build(config);
            return (message, context, chain) -> genericInterceptor.interceptOnHandle(
                    message,
                    context,
                    (m, c) -> chain.proceed((CommandMessage) m, c)
            );
        });
        registerEventInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = interceptorBuilder.build(config);
            return (message, context, chain) -> genericInterceptor.interceptOnHandle(
                    message,
                    context,
                    (m, c) -> chain.proceed((EventMessage) m, c)
            );
        });
        registerQueryInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = interceptorBuilder.build(config);
            return (message, context, chain) -> genericInterceptor.interceptOnHandle(
                    message,
                    context,
                    (m, c) -> chain.proceed((QueryMessage) m, c)
            );
        });
        return this;
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerCommandInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<CommandMessage>> interceptorBuilder
    ) {
        this.commandInterceptorBuilders.add(interceptorBuilder);
        return this;
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<EventMessage>> interceptorBuilder
    ) {
        this.eventInterceptorBuilders.add(interceptorBuilder);
        return this;
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<QueryMessage>> interceptorBuilder
    ) {
        this.queryInterceptorBuilders.add(interceptorBuilder);
        return this;
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<CommandMessage>> commandInterceptors(@Nonnull Configuration config) {
        List<MessageHandlerInterceptor<CommandMessage>> commandHandlerInterceptors = new ArrayList<>();
        for (ComponentBuilder<MessageHandlerInterceptor<CommandMessage>> interceptorBuilder : commandInterceptorBuilders) {
            MessageHandlerInterceptor<CommandMessage> handlerInterceptor = interceptorBuilder.build(config);
            commandHandlerInterceptors.add(handlerInterceptor);
        }
        return commandHandlerInterceptors;
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<EventMessage>> eventInterceptors(@Nonnull Configuration config) {
        List<MessageHandlerInterceptor<EventMessage>> eventHandlerInterceptors = new ArrayList<>();
        for (ComponentBuilder<MessageHandlerInterceptor<EventMessage>> interceptorBuilder : eventInterceptorBuilders) {
            MessageHandlerInterceptor<EventMessage> handlerInterceptor = interceptorBuilder.build(config);
            eventHandlerInterceptors.add(handlerInterceptor);
        }
        return eventHandlerInterceptors;
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<QueryMessage>> queryInterceptors(@Nonnull Configuration config) {
        List<MessageHandlerInterceptor<QueryMessage>> queryHandlerInterceptors = new ArrayList<>();
        for (ComponentBuilder<MessageHandlerInterceptor<QueryMessage>> interceptorBuilder : queryInterceptorBuilders) {
            MessageHandlerInterceptor<QueryMessage> handlerInterceptor = interceptorBuilder.build(config);
            queryHandlerInterceptors.add(handlerInterceptor);
        }
        return queryHandlerInterceptors;
    }
}
