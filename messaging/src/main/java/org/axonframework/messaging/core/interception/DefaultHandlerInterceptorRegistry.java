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
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.eventhandling.EventMessage;
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

    private final List<HandlerInterceptorBuilder<? super CommandMessage>> commandInterceptorBuilders = new ArrayList<>();
    private final List<HandlerInterceptorBuilder<? super EventMessage>> eventInterceptorBuilders = new ArrayList<>();
    private final List<HandlerInterceptorBuilder<? super QueryMessage>> queryInterceptorBuilders = new ArrayList<>();

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<Message>> interceptorBuilder
    ) {
        GenericInterceptorDefinition genericInterceptorDef = new GenericInterceptorDefinition(interceptorBuilder);

        return registerCommandInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnHandle(
                    message,
                    context,
                    (m, c) -> chain.proceed((CommandMessage) m, c)
            );
        }).registerEventInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnHandle(
                    message,
                    context,
                    (m, c) -> chain.proceed((EventMessage) m, c)
            );
        }).registerQueryInterceptor(config -> {
            MessageHandlerInterceptor<Message> genericInterceptor = genericInterceptorDef.doResolve(config);
            return (message, context, chain) -> genericInterceptor.interceptOnHandle(
                    message,
                    context,
                    (m, c) -> chain.proceed((QueryMessage) m, c)
            );
        });
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerInterceptor(
            @Nonnull HandlerInterceptorBuilder<Message> interceptorBuilder
    ) {
        registerCommandInterceptor(interceptorBuilder);
        registerEventInterceptor(interceptorBuilder);
        registerQueryInterceptor(interceptorBuilder);
        return this;
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerCommandInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super CommandMessage>> interceptorBuilder
    ) {
        ComponentDefinition<MessageHandlerInterceptor<? super CommandMessage>> interceptorDefinition =
                ComponentDefinition.ofType(COMMAND_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerCommandInterceptor(builderOfComponentDefinition(interceptorDefinition));
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerCommandInterceptor(
            @Nonnull HandlerInterceptorBuilder<? super CommandMessage> interceptorBuilder
    ) {
        this.commandInterceptorBuilders.add(interceptorBuilder);
        return this;
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super EventMessage>> interceptorBuilder
    ) {
        ComponentDefinition<MessageHandlerInterceptor<? super EventMessage>> interceptorDefinition =
                ComponentDefinition.ofType(EVENT_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerEventInterceptor(builderOfComponentDefinition(interceptorDefinition));
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerEventInterceptor(
            @Nonnull HandlerInterceptorBuilder<? super EventMessage> interceptorBuilder
    ) {
        this.eventInterceptorBuilders.add(interceptorBuilder);
        return this;
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageHandlerInterceptor<? super QueryMessage>> interceptorBuilder
    ) {
        ComponentDefinition<MessageHandlerInterceptor<? super QueryMessage>> interceptorDefinition =
                ComponentDefinition.ofType(QUERY_INTERCEPTOR_TYPE_REF).withBuilder(interceptorBuilder);
        return registerQueryInterceptor(builderOfComponentDefinition(interceptorDefinition));
    }

    @Nonnull
    @Override
    public HandlerInterceptorRegistry registerQueryInterceptor(
            @Nonnull HandlerInterceptorBuilder<? super QueryMessage> interceptorBuilder
    ) {
        this.queryInterceptorBuilders.add(interceptorBuilder);
        return this;
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(commandInterceptorBuilders, config, componentType, componentName);
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(eventInterceptorBuilders, config, componentType, componentName);
    }

    @Nonnull
    @Override
    public List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveInterceptors(queryInterceptorBuilders, config, componentType, componentName);
    }

    @Nonnull
    private static <M extends Message> HandlerInterceptorBuilder<M> builderOfComponentDefinition(
            ComponentDefinition<MessageHandlerInterceptor<? super M>> interceptorDefinition
    ) {
        if (!(interceptorDefinition instanceof ComponentDefinition.ComponentCreator<MessageHandlerInterceptor<? super M>> creator)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported component definition type: " + interceptorDefinition);
        }
        return (config, componentType, componentName) -> creator.createComponent().resolve(config);
    }

    /**
     * Resolves and combines multiple {@link MessageHandlerInterceptor} instances from the supplied builders for a
     * specific component type and name.
     *
     * @param <T>           the type of the {@link Message} the resulting {@link MessageHandlerInterceptor} will
     *                      intercept
     * @param builders      a list of {@link HandlerInterceptorBuilder} instances for creating
     *                      {@link MessageHandlerInterceptor} instances
     * @param config        the {@link Configuration} to be used for resolving the components
     * @param componentType the type of the component the handler interceptors are build for
     * @param componentName the name of the component the handler interceptors are build for
     * @return a list of {@link MessageHandlerInterceptor} instances configured for the {@code componentType} and
     * {@code componentName} combination
     */
    private static <T extends Message> List<MessageHandlerInterceptor<? super T>> resolveInterceptors(
            List<HandlerInterceptorBuilder<? super T>> builders,
            Configuration config,
            Class<?> componentType,
            String componentName
    ) {
        List<MessageHandlerInterceptor<? super T>> handlerInterceptors = new ArrayList<>();
        for (HandlerInterceptorBuilder<? super T> builder : builders) {
            MessageHandlerInterceptor<? super T> interceptor = builder.build(config, componentType, componentName);
            if (interceptor != null) {
                handlerInterceptors.add(interceptor);
            }
        }
        return handlerInterceptors;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandHandlerInterceptors", commandInterceptorBuilders);
        descriptor.describeProperty("eventHandlerInterceptors", eventInterceptorBuilders);
        descriptor.describeProperty("queryHandlerInterceptors", queryInterceptorBuilders);
    }

    // Private class there to simplify use in registerInterceptor(...) only.
    private static class GenericInterceptorDefinition extends
            LazyInitializedComponentDefinition<MessageHandlerInterceptor<Message>, MessageHandlerInterceptor<Message>> {

        public GenericInterceptorDefinition(ComponentBuilder<MessageHandlerInterceptor<Message>> builder) {
            super(new Component.Identifier<>(MESSAGE_INTERCEPTOR_TYPE_REF, null), builder);
        }
    }
}