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

package org.axonframework.extension.reactor.messaging.core.interception;

import org.axonframework.common.TypeReference;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.LazyInitializedComponentDefinition;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link ReactorDispatchInterceptorRegistry}, maintaining lists of
 * {@link CommandMessage}, {@link EventMessage}, and {@link QueryMessage}-specific
 * {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors}.
 * <p>
 * This implementation ensures given interceptor {@link ComponentBuilder builders} methods are only invoked <b>once</b>.
 * Note that this does not apply to given {@link ReactorDispatchInterceptorFactory factories}!
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
public class DefaultReactorDispatchInterceptorRegistry implements ReactorDispatchInterceptorRegistry {

    private static final TypeReference<ReactorMessageDispatchInterceptor<Message>> INTERCEPTOR_TYPE_REF =
            new TypeReference<>() {
            };
    private static final TypeReference<ReactorMessageDispatchInterceptor<? super CommandMessage>> COMMAND_INTERCEPTOR_TYPE_REF =
            new TypeReference<>() {
            };
    private static final TypeReference<ReactorMessageDispatchInterceptor<? super EventMessage>> EVENT_INTERCEPTOR_TYPE_REF =
            new TypeReference<>() {
            };
    private static final TypeReference<ReactorMessageDispatchInterceptor<? super QueryMessage>> QUERY_INTERCEPTOR_TYPE_REF =
            new TypeReference<>() {
            };

    private final List<ReactorDispatchInterceptorFactory<? super CommandMessage>> commandInterceptorFactories =
            new ArrayList<>();
    private final List<ReactorDispatchInterceptorFactory<? super EventMessage>> eventInterceptorFactories =
            new ArrayList<>();
    private final List<ReactorDispatchInterceptorFactory<? super QueryMessage>> queryInterceptorFactories =
            new ArrayList<>();

    @Override
    public ReactorDispatchInterceptorRegistry registerInterceptor(
            ComponentBuilder<ReactorMessageDispatchInterceptor<Message>> interceptorBuilder
    ) {
        GenericInterceptorDefinition genericInterceptorDef = new GenericInterceptorDefinition(interceptorBuilder);
        return registerCommandInterceptor(config -> {
            ReactorMessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message, context, (m, c) -> chain.proceed((CommandMessage) m, c)
            );
        }).registerEventInterceptor(config -> {
            ReactorMessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message, context, (m, c) -> chain.proceed((EventMessage) m, c)
            );
        }).registerQueryInterceptor(config -> {
            ReactorMessageDispatchInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnDispatch(
                    message, context, (m, c) -> chain.proceed((QueryMessage) m, c)
            );
        });
    }

    @Override
    public ReactorDispatchInterceptorRegistry registerInterceptor(
            ReactorDispatchInterceptorFactory<Message> interceptorFactory
    ) {
        registerCommandInterceptor(interceptorFactory);
        registerEventInterceptor(interceptorFactory);
        registerQueryInterceptor(interceptorFactory);
        return this;
    }

    @Override
    public ReactorDispatchInterceptorRegistry registerCommandInterceptor(
            ComponentBuilder<ReactorMessageDispatchInterceptor<? super CommandMessage>> interceptorBuilder
    ) {
        ComponentDefinition<ReactorMessageDispatchInterceptor<? super CommandMessage>> interceptorDefinition =
                ComponentDefinition.ofType(COMMAND_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerCommandInterceptor(factoryFromDefinition(interceptorDefinition));
    }

    @Override
    public ReactorDispatchInterceptorRegistry registerCommandInterceptor(
            ReactorDispatchInterceptorFactory<? super CommandMessage> interceptorFactory
    ) {
        this.commandInterceptorFactories.add(interceptorFactory);
        return this;
    }

    @Override
    public ReactorDispatchInterceptorRegistry registerEventInterceptor(
            ComponentBuilder<ReactorMessageDispatchInterceptor<? super EventMessage>> interceptorBuilder
    ) {
        ComponentDefinition<ReactorMessageDispatchInterceptor<? super EventMessage>> interceptorDefinition =
                ComponentDefinition.ofType(EVENT_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerEventInterceptor(factoryFromDefinition(interceptorDefinition));
    }

    @Override
    public ReactorDispatchInterceptorRegistry registerEventInterceptor(
            ReactorDispatchInterceptorFactory<? super EventMessage> interceptorFactory
    ) {
        this.eventInterceptorFactories.add(interceptorFactory);
        return this;
    }

    @Override
    public ReactorDispatchInterceptorRegistry registerQueryInterceptor(
            ComponentBuilder<ReactorMessageDispatchInterceptor<? super QueryMessage>> interceptorBuilder
    ) {
        ComponentDefinition<ReactorMessageDispatchInterceptor<? super QueryMessage>> interceptorDefinition =
                ComponentDefinition.ofType(QUERY_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerQueryInterceptor(factoryFromDefinition(interceptorDefinition));
    }

    @Override
    public ReactorDispatchInterceptorRegistry registerQueryInterceptor(
            ReactorDispatchInterceptorFactory<? super QueryMessage> interceptorFactory
    ) {
        this.queryInterceptorFactories.add(interceptorFactory);
        return this;
    }

    @Override
    public List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(commandInterceptorFactories, config, componentType, componentName);
    }

    @Override
    public List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(eventInterceptorFactories, config, componentType, componentName);
    }

    @Override
    public List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(queryInterceptorFactories, config, componentType, componentName);
    }

    private static <M extends Message> ReactorDispatchInterceptorFactory<M> factoryFromDefinition(
            ComponentDefinition<ReactorMessageDispatchInterceptor<? super M>> interceptorDefinition
    ) {
        if (!(interceptorDefinition instanceof ComponentDefinition.ComponentCreator<ReactorMessageDispatchInterceptor<? super M>> creator)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported component definition type: " + interceptorDefinition);
        }
        return (config, componentType, componentName) -> creator.createComponent().resolve(config);
    }

    private static <T extends Message> List<ReactorMessageDispatchInterceptor<? super T>> resolveInterceptors(
            List<ReactorDispatchInterceptorFactory<? super T>> factories,
            Configuration config,
            Class<?> componentType,
            String componentName
    ) {
        List<ReactorMessageDispatchInterceptor<? super T>> dispatchInterceptors = new ArrayList<>();
        for (ReactorDispatchInterceptorFactory<? super T> factory : factories) {
            ReactorMessageDispatchInterceptor<? super T> interceptor =
                    factory.build(config, componentType, componentName);
            if (interceptor != null) {
                dispatchInterceptors.add(interceptor);
            }
        }
        return dispatchInterceptors;
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandDispatchInterceptorFactories", commandInterceptorFactories);
        descriptor.describeProperty("eventDispatchInterceptorFactories", eventInterceptorFactories);
        descriptor.describeProperty("queryDispatchInterceptorFactories", queryInterceptorFactories);
    }

    // Private class there to simplify use in registerInterceptor(...) only.
    private static class GenericInterceptorDefinition extends
            LazyInitializedComponentDefinition<ReactorMessageDispatchInterceptor<Message>, ReactorMessageDispatchInterceptor<Message>> {

        public GenericInterceptorDefinition(
                ComponentBuilder<ReactorMessageDispatchInterceptor<Message>> builder
        ) {
            super(new Component.Identifier<>(INTERCEPTOR_TYPE_REF, null), builder);
        }
    }
}
