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
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link DispatchInterceptorRegistry}, maintaining lists of {@link CommandMessage},
 * {@link EventMessage}, and {@link QueryMessage}-specific
 * {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
 * <p>
 * This implementation ensures give interceptor factory methods are only invoked once.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class DefaultDispatchInterceptorRegistry implements DispatchInterceptorRegistry {

    private static final TypeReference<MessageDispatchInterceptor<Message>> INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageDispatchInterceptor<? super CommandMessage>> COMMAND_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageDispatchInterceptor<? super EventMessage>> EVENT_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageDispatchInterceptor<? super QueryMessage>> QUERY_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };

    private final List<ComponentDefinition<MessageDispatchInterceptor<? super CommandMessage>>> commandInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageDispatchInterceptor<? super EventMessage>>> eventInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageDispatchInterceptor<? super QueryMessage>>> queryInterceptorDefinitions = new ArrayList<>();

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<Message>> interceptorBuilder
    ) {
        GenericInterceptorDefinition genericInterceptorDef = new GenericInterceptorDefinition(interceptorBuilder);

        registerCommandInterceptor(config -> {
            MessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message,
                    context,
                    (m, c) -> chain.proceed((CommandMessage) m, c)
            );
        });
        registerEventInterceptor(config -> {
            MessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message,
                    context,
                    (m, c) -> chain.proceed((EventMessage) m, c)
            );
        });
        registerQueryInterceptor(config -> {
            MessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message,
                    context,
                    (m, c) -> chain.proceed((QueryMessage) m, c)
            );
        });
        return this;
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerCommandInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super CommandMessage>> interceptorBuilder
    ) {
        this.commandInterceptorDefinitions.add(ComponentDefinition.ofType(COMMAND_INTERCEPTOR_TYPE_REF)
                                                                  .withBuilder(interceptorBuilder));
        return this;
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super EventMessage>> interceptorBuilder
    ) {
        this.eventInterceptorDefinitions.add(ComponentDefinition.ofType(EVENT_INTERCEPTOR_TYPE_REF)
                                                                .withBuilder(interceptorBuilder));
        return this;
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super QueryMessage>> interceptorBuilder
    ) {
        this.queryInterceptorDefinitions.add(ComponentDefinition.ofType(QUERY_INTERCEPTOR_TYPE_REF)
                                                                .withBuilder(interceptorBuilder));
        return this;
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors(@Nonnull Configuration config) {
        List<MessageDispatchInterceptor<? super CommandMessage>> dispatchInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageDispatchInterceptor<? super CommandMessage>> interceptorBuilder : commandInterceptorDefinitions) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageDispatchInterceptor<? super CommandMessage>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageDispatchInterceptor<? super CommandMessage> dispatchInterceptor = creator.createComponent().resolve(
                    config);
            dispatchInterceptors.add(dispatchInterceptor);
        }
        return dispatchInterceptors;
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors(@Nonnull Configuration config) {
        List<MessageDispatchInterceptor<? super EventMessage>> dispatchInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageDispatchInterceptor<? super EventMessage>> interceptorBuilder : eventInterceptorDefinitions) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageDispatchInterceptor<? super EventMessage>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageDispatchInterceptor<? super EventMessage> dispatchInterceptor = creator.createComponent().resolve(
                    config);
            dispatchInterceptors.add(dispatchInterceptor);
        }
        return dispatchInterceptors;
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors(@Nonnull Configuration config) {
        List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageDispatchInterceptor<? super QueryMessage>> interceptorBuilder : queryInterceptorDefinitions) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageDispatchInterceptor<? super QueryMessage>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageDispatchInterceptor<? super QueryMessage> dispatchInterceptor = creator.createComponent().resolve(
                    config);
            dispatchInterceptors.add(dispatchInterceptor);
        }
        return dispatchInterceptors;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandDispatchInterceptors", commandInterceptorDefinitions);
        descriptor.describeProperty("eventDispatchInterceptors", eventInterceptorDefinitions);
        descriptor.describeProperty("queryDispatchInterceptors", queryInterceptorDefinitions);
    }

    // Private class there to simplify use in registerInterceptor(...) only.
    private static class GenericInterceptorDefinition extends
            LazyInitializedComponentDefinition<MessageDispatchInterceptor<Message>, MessageDispatchInterceptor<Message>> {

        public GenericInterceptorDefinition(ComponentBuilder<MessageDispatchInterceptor<Message>> builder) {
            super(new Component.Identifier<>(INTERCEPTOR_TYPE_REF, null), builder);
        }
    }
}
