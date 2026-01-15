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

package org.axonframework.messaging.core.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.common.TypeReference;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.LazyInitializedComponentDefinition;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.queryhandling.QueryMessage;

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
    private static final TypeReference<MessageHandlerInterceptor<? super CommandMessage>> COMMAND_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageHandlerInterceptor<? super EventMessage>> EVENT_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageHandlerInterceptor<? super QueryMessage>> QUERY_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };

    private final List<ComponentDefinition<MessageHandlerInterceptor<? super CommandMessage>>> commandInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageHandlerInterceptor<? super EventMessage>>> eventInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageHandlerInterceptor<? super QueryMessage>>> queryInterceptorDefinitions = new ArrayList<>();

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
    public List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors(@Nonnull Configuration config) {
        return resolveInterceptors(commandInterceptorDefinitions, config);
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors(@Nonnull Configuration config) {
        return resolveInterceptors(eventInterceptorDefinitions, config);
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors(@Nonnull Configuration config) {
        return resolveInterceptors(queryInterceptorDefinitions, config);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandHandlerInterceptors", commandInterceptorDefinitions);
        descriptor.describeProperty("eventHandlerInterceptors", eventInterceptorDefinitions);
        descriptor.describeProperty("queryHandlerInterceptors", queryInterceptorDefinitions);
    }


    // Solves common verification, creation and combining for all handler interceptors.
    private static <T extends Message> List<MessageHandlerInterceptor<? super T>> resolveInterceptors(
            List<ComponentDefinition<MessageHandlerInterceptor<? super T>>> definitions,
            Configuration config
    ) {
        List<MessageHandlerInterceptor<? super T>> handlerInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageHandlerInterceptor<? super T>> interceptorBuilder : definitions) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageHandlerInterceptor<? super T>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageHandlerInterceptor<? super T> handlerInterceptor = creator.createComponent().resolve(config);
            handlerInterceptors.add(handlerInterceptor);
        }
        return handlerInterceptors;
    }

    // Private class there to simplify use in registerInterceptor(...) only.
    private static class GenericInterceptorDefinition extends
            LazyInitializedComponentDefinition<MessageHandlerInterceptor<Message>, MessageHandlerInterceptor<Message>> {

        public GenericInterceptorDefinition(ComponentBuilder<MessageHandlerInterceptor<Message>> builder) {
            super(new Component.Identifier<>(MESSAGE_INTERCEPTOR_TYPE_REF, null), builder);
        }
    }
}
