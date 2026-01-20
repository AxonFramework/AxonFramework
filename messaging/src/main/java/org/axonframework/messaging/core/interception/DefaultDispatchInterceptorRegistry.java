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
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

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
    private static final TypeReference<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> SUBSCRIPTION_QUERY_UPDATE_INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };

    private final List<ComponentDefinition<MessageDispatchInterceptor<? super CommandMessage>>> commandInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageDispatchInterceptor<? super EventMessage>>> eventInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageDispatchInterceptor<? super QueryMessage>>> queryInterceptorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>>> subscriptionQueryUpdateInterceptorDefinitions = new ArrayList<>();

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
        registerSubscriptionQueryUpdateInterceptor(config -> {
            MessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message,
                    context,
                    (m, c) -> chain.proceed((SubscriptionQueryUpdateMessage) m, c)
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
    public DispatchInterceptorRegistry registerSubscriptionQueryUpdateInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> interceptorBuilder
    ) {
        this.subscriptionQueryUpdateInterceptorDefinitions.add(ComponentDefinition.ofType(SUBSCRIPTION_QUERY_UPDATE_INTERCEPTOR_TYPE_REF)
                                                                                   .withBuilder(interceptorBuilder));
        return this;
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors(@Nonnull Configuration config) {
        return resolveInterceptors(commandInterceptorDefinitions, config);
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors(@Nonnull Configuration config) {
        return resolveInterceptors(eventInterceptorDefinitions, config);
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors(@Nonnull Configuration config) {
        return resolveInterceptors(queryInterceptorDefinitions, config);
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors(@Nonnull Configuration config) {
        return resolveInterceptors(subscriptionQueryUpdateInterceptorDefinitions, config);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandDispatchInterceptors", commandInterceptorDefinitions);
        descriptor.describeProperty("eventDispatchInterceptors", eventInterceptorDefinitions);
        descriptor.describeProperty("queryDispatchInterceptors", queryInterceptorDefinitions);
        descriptor.describeProperty("subscriptionQueryUpdateDispatchInterceptors", subscriptionQueryUpdateInterceptorDefinitions);
    }

    // Private class there to simplify use in registerInterceptor(...) only.
    private static class GenericInterceptorDefinition extends
            LazyInitializedComponentDefinition<MessageDispatchInterceptor<Message>, MessageDispatchInterceptor<Message>> {

        public GenericInterceptorDefinition(ComponentBuilder<MessageDispatchInterceptor<Message>> builder) {
            super(new Component.Identifier<>(INTERCEPTOR_TYPE_REF, null), builder);
        }
    }

    // Solves common verification, creation and combining for all dispatch interceptors.
    private static <T extends Message> List<MessageDispatchInterceptor<? super T>> resolveInterceptors(
            List<ComponentDefinition<MessageDispatchInterceptor<? super T>>> definitions,
            Configuration config
    ) {
        List<MessageDispatchInterceptor<? super T>> dispatchInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageDispatchInterceptor<? super T>> interceptorBuilder : definitions) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageDispatchInterceptor<? super T>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageDispatchInterceptor<? super T> dispatchInterceptor = creator.createComponent().resolve(config);
            dispatchInterceptors.add(dispatchInterceptor);
        }
        return dispatchInterceptors;
    }
}
