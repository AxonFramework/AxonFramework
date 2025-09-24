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
import org.axonframework.common.TypeReference;
import org.axonframework.common.annotations.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Component;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LazyInitializedComponentDefinition;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.QueryMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link HandlerInterceptorRegistry}, maintaining lists of {@link CommandMessage},
 * {@link EventMessage}, and {@link QueryMessage}-specific
 * {@link MessageHandlerInterceptor MessageHandlerInterceptors}.
 * <p>
 * This implementation ensures give interceptor factory methods are only invoked once.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class DefaultHandlerInterceptorRegistry implements HandlerInterceptorRegistry {

    private static final TypeReference<MessageHandlerInterceptor<Message>> MESSAGE_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageHandlerInterceptor<CommandMessage>> COMMAND_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageHandlerInterceptor<EventMessage>> EVENT_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageHandlerInterceptor<QueryMessage>> QUERY_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };

    private final List<ComponentDefinition<MessageHandlerInterceptor<CommandMessage>>> commandInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageHandlerInterceptor<EventMessage>>> eventInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageHandlerInterceptor<QueryMessage>>> queryInterceptorDefinitions = new ArrayList<>();

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<Message>> interceptorBuilder
    ) {
        GenericInterceptorDefinition genericInterceptorDef = new GenericInterceptorDefinition(interceptorBuilder);

        registerCommandInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnHandle(
                    message,
                    context,
                    (m, c) -> chain.proceed((CommandMessage) m, c)
            );
        });
        registerEventInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnHandle(
                    message,
                    context,
                    (m, c) -> chain.proceed((EventMessage) m, c)
            );
        });
        registerQueryInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
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
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super CommandMessage>> interceptorBuilder
    ) {
        //noinspection unchecked | Casting to CommandMessage is safe.
        this.commandInterceptorDefinitions.add(
                ComponentDefinition.ofType(COMMAND_INTERCEPTOR_TYPE_REF)
                                   .withBuilder(
                                           c -> (MessageHandlerInterceptor<CommandMessage>) interceptorBuilder.build(c)
                                   )
        );
        return this;
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super EventMessage>> interceptorBuilder
    ) {
        //noinspection unchecked | Casting to EventMessage is safe.
        this.eventInterceptorDefinitions.add(
                ComponentDefinition.ofType(EVENT_INTERCEPTOR_TYPE_REF)
                                   .withBuilder(
                                           c -> (MessageHandlerInterceptor<EventMessage>) interceptorBuilder.build(c)
                                   )
        );
        return this;
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super QueryMessage>> interceptorBuilder
    ) {
        //noinspection unchecked | Casting to QueryMessage is safe.
        this.queryInterceptorDefinitions.add(
                ComponentDefinition.ofType(QUERY_INTERCEPTOR_TYPE_REF)
                                   .withBuilder(
                                           c -> (MessageHandlerInterceptor<QueryMessage>) interceptorBuilder.build(c)
                                   )
        );
        return this;
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<CommandMessage>> commandInterceptors(@Nonnull Configuration config) {
        List<MessageHandlerInterceptor<CommandMessage>> commandHandlerInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageHandlerInterceptor<CommandMessage>> interceptorBuilder : commandInterceptorDefinitions) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageHandlerInterceptor<CommandMessage>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageHandlerInterceptor<CommandMessage> handlerInterceptor = creator.createComponent().resolve(config);
            commandHandlerInterceptors.add(handlerInterceptor);
        }
        return commandHandlerInterceptors;
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<EventMessage>> eventInterceptors(@Nonnull Configuration config) {
        List<MessageHandlerInterceptor<EventMessage>> eventHandlerInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageHandlerInterceptor<EventMessage>> interceptorBuilder : eventInterceptorDefinitions) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageHandlerInterceptor<EventMessage>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageHandlerInterceptor<EventMessage> handlerInterceptor = creator.createComponent().resolve(config);
            eventHandlerInterceptors.add(handlerInterceptor);
        }
        return eventHandlerInterceptors;
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<QueryMessage>> queryInterceptors(@Nonnull Configuration config) {
        List<MessageHandlerInterceptor<QueryMessage>> queryHandlerInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageHandlerInterceptor<QueryMessage>> interceptorBuilder : queryInterceptorDefinitions) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageHandlerInterceptor<QueryMessage>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageHandlerInterceptor<QueryMessage> handlerInterceptor = creator.createComponent().resolve(config);
            queryHandlerInterceptors.add(handlerInterceptor);
        }
        return queryHandlerInterceptors;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandHandlerInterceptors", commandInterceptorDefinitions);
        descriptor.describeProperty("eventHandlerInterceptors", eventInterceptorDefinitions);
        descriptor.describeProperty("queryHandlerInterceptors", queryInterceptorDefinitions);
    }

    // Private class there to simplify use in registerInterceptor(...) only.
    private static class GenericInterceptorDefinition extends
            LazyInitializedComponentDefinition<MessageHandlerInterceptor<Message>, MessageHandlerInterceptor<Message>> {

        public GenericInterceptorDefinition(ComponentBuilder<MessageHandlerInterceptor<Message>> builder) {
            super(new Component.Identifier<>(MESSAGE_INTERCEPTOR_TYPE_REF, null), builder);
        }
    }
}
