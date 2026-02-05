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
import jakarta.annotation.Nullable;
import org.axonframework.common.TypeReference;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.LazyInitializedComponentDefinition;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link DispatchInterceptorRegistry}, maintaining lists of {@link CommandMessage},
 * {@link EventMessage}, and {@link QueryMessage}-specific
 * {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
 * <p>
 * This implementation ensures give interceptor {@link ComponentBuilder builders} methods are only invoked <b>once</b>.
 * Note that this does not apply to given {@link DispatchInterceptorFactory factories}!
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

    private final List<DispatchInterceptorFactory<? super CommandMessage>> commandInterceptorFactories = new ArrayList<>();
    private final List<DispatchInterceptorFactory<? super EventMessage>> eventInterceptorFactories = new ArrayList<>();
    private final List<DispatchInterceptorFactory<? super QueryMessage>> queryInterceptorFactories = new ArrayList<>();
    private final List<DispatchInterceptorFactory<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptorFactories = new ArrayList<>();

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<Message>> interceptorBuilder
    ) {
        GenericInterceptorDefinition genericInterceptorDef = new GenericInterceptorDefinition(interceptorBuilder);
        return registerCommandInterceptor(config -> {
            MessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message,
                    context,
                    (m, c) -> chain.proceed((CommandMessage) m, c)
            );
        }).registerEventInterceptor(config -> {
            MessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message,
                    context,
                    (m, c) -> chain.proceed((EventMessage) m, c)
            );
        }).registerQueryInterceptor(config -> {
            MessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message,
                    context,
                    (m, c) -> chain.proceed((QueryMessage) m, c)
            );
        }).registerSubscriptionQueryUpdateInterceptor(config -> {
            MessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message,
                    context,
                    (m, c) -> chain.proceed((SubscriptionQueryUpdateMessage) m, c)
            );
        });
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerInterceptor(
            @Nonnull DispatchInterceptorFactory<Message> interceptorFactory
    ) {
        registerCommandInterceptor(interceptorFactory);
        registerEventInterceptor(interceptorFactory);
        registerQueryInterceptor(interceptorFactory);
        registerSubscriptionQueryUpdateInterceptor(interceptorFactory);
        return this;
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerCommandInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super CommandMessage>> interceptorBuilder
    ) {
        ComponentDefinition<MessageDispatchInterceptor<? super CommandMessage>> interceptorDefinition =
                ComponentDefinition.ofType(COMMAND_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerCommandInterceptor(factoryFromDefinition(interceptorDefinition));
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerCommandInterceptor(
            @Nonnull DispatchInterceptorFactory<? super CommandMessage> interceptorFactory
    ) {
        this.commandInterceptorFactories.add(interceptorFactory);
        return this;
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super EventMessage>> interceptorBuilder
    ) {
        ComponentDefinition<MessageDispatchInterceptor<? super EventMessage>> interceptorDefinition =
                ComponentDefinition.ofType(EVENT_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerEventInterceptor(factoryFromDefinition(interceptorDefinition));
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerEventInterceptor(
            @Nonnull DispatchInterceptorFactory<? super EventMessage> interceptorFactory
    ) {
        this.eventInterceptorFactories.add(interceptorFactory);
        return this;
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super QueryMessage>> interceptorBuilder
    ) {
        ComponentDefinition<MessageDispatchInterceptor<? super QueryMessage>> interceptorDefinition =
                ComponentDefinition.ofType(QUERY_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerQueryInterceptor(factoryFromDefinition(interceptorDefinition));
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerQueryInterceptor(
            @Nonnull DispatchInterceptorFactory<? super QueryMessage> interceptorFactory
    ) {
        this.queryInterceptorFactories.add(interceptorFactory);
        return this;
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerSubscriptionQueryUpdateInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> interceptorBuilder
    ) {
        ComponentDefinition<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> interceptorDefinition =
                ComponentDefinition.ofType(SUBSCRIPTION_QUERY_UPDATE_INTERCEPTOR_TYPE_REF)
                                   .withBuilder(interceptorBuilder);
        return registerSubscriptionQueryUpdateInterceptor(factoryFromDefinition(interceptorDefinition));
    }

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerSubscriptionQueryUpdateInterceptor(
            @Nonnull DispatchInterceptorFactory<? super SubscriptionQueryUpdateMessage> interceptorFactory
    ) {
        this.subscriptionQueryUpdateInterceptorFactories.add(interceptorFactory);
        return this;
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(commandInterceptorFactories, config, componentType, componentName);
    }


    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(eventInterceptorFactories, config, componentType, componentName);
    }


    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(queryInterceptorFactories, config, componentType, componentName);
    }


    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(subscriptionQueryUpdateInterceptorFactories, config, componentType, componentName);
    }

    @Nonnull
    private static <M extends Message> DispatchInterceptorFactory<M> factoryFromDefinition(
            ComponentDefinition<MessageDispatchInterceptor<? super M>> interceptorDefinition
    ) {
        if (!(interceptorDefinition instanceof ComponentDefinition.ComponentCreator<MessageDispatchInterceptor<? super M>> creator)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported component definition type: " + interceptorDefinition);
        }
        return (config, componentType, componentName) -> creator.createComponent().resolve(config);
    }

    /**
     * Resolves and combines multiple {@link MessageDispatchInterceptor} instances from the supplied {@code factories}
     * for a specific component type and name.
     *
     * @param <T>           the type of the {@link Message} the resulting {@link MessageDispatchInterceptor} will
     *                      intercept
     * @param factories     a list of {@link DispatchInterceptorFactory} instances for creating
     *                      {@link MessageDispatchInterceptor} instances
     * @param config        the {@link Configuration} to be used for resolving the components
     * @param componentType the type of the component the dispatch interceptors are build for
     * @param componentName the name of the component the dispatch interceptors are build for
     * @return a list of {@link MessageDispatchInterceptor} instances configured for the {@code componentType} and
     * {@code componentName} combination
     */
    private static <T extends Message> List<MessageDispatchInterceptor<? super T>> resolveInterceptors(
            List<DispatchInterceptorFactory<? super T>> factories,
            Configuration config,
            Class<?> componentType,
            String componentName
    ) {
        List<MessageDispatchInterceptor<? super T>> dispatchInterceptors = new ArrayList<>();
        for (DispatchInterceptorFactory<? super T> factory : factories) {
            MessageDispatchInterceptor<? super T> interceptor = factory.build(config, componentType, componentName);
            if (interceptor != null) {
                dispatchInterceptors.add(interceptor);
            }
        }
        return dispatchInterceptors;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandDispatchInterceptorFactories", commandInterceptorFactories);
        descriptor.describeProperty("eventDispatchInterceptorFactories", eventInterceptorFactories);
        descriptor.describeProperty("queryDispatchInterceptorFactories", queryInterceptorFactories);
        descriptor.describeProperty(
                "subscriptionQueryUpdateDispatchInterceptorFactories", subscriptionQueryUpdateInterceptorFactories
        );
    }

    // Private class there to simplify use in registerInterceptor(...) only.
    private static class GenericInterceptorDefinition extends
            LazyInitializedComponentDefinition<MessageDispatchInterceptor<Message>, MessageDispatchInterceptor<Message>> {

        public GenericInterceptorDefinition(ComponentBuilder<MessageDispatchInterceptor<Message>> builder) {
            super(new Component.Identifier<>(INTERCEPTOR_TYPE_REF, null), builder);
        }
    }
}