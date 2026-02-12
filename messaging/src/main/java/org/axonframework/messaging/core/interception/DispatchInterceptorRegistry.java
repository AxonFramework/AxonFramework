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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.DecoratorDefinition;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.interception.InterceptingCommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.List;

/**
 * A registry of {@link MessageDispatchInterceptor MessageDispatchInterceptors}, acting as a collection of
 * {@link ComponentRegistry#registerComponent(ComponentDefinition) registered MessageDispatchInterceptors components}.
 * <p>
 * Provides operations to register generic {@link Message}, {@link CommandMessage}-specific,
 * {@link EventMessage}-specific, {@link QueryMessage}-specific, or {@link SubscriptionQueryUpdateMessage}-specific
 * {@code MessageDispatchInterceptor}. Registered type specific {@code MessageDispatchInterceptor} can be retrieved
 * through {@link #commandInterceptors(Configuration, Class, String)},
 * {@link #eventInterceptors(Configuration, Class, String)}, {@link #queryInterceptors(Configuration, Class, String)},
 * and {@link #subscriptionQueryUpdateInterceptors(Configuration, Class, String)}.
 * <p>
 * These operations are expected to be invoked within a {@link DecoratorDefinition}. As such, <b>any</b> registered
 * interceptors are <b>only</b> applied when the infrastructure component requiring them is constructed. When, for
 * example, an {@link InterceptingCommandBus} is constructed, this registry is invoked to retrieve interceptors.
 * Interceptors that are registered once the {@code InterceptingCommandBus} has already been constructed are not taken
 * into account.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public interface DispatchInterceptorRegistry extends DescribableComponent {

    /**
     * Registers the given {@code interceptorBuilder} for a generic {@link Message} {@link MessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only built <b>once</b>.
     *
     * @param interceptorBuilder the generic {@link Message} {@link MessageDispatchInterceptor} builder to register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<Message>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for a generic {@link Message}
     * {@link MessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link DispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the factory,
     * ensuring uniqueness per given typa and name. If the interceptor will be identical regardless of the given type or
     * name, please use {@link #registerInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the generic {@link Message} {@link MessageDispatchInterceptor} factory to register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerInterceptor(@Nonnull DispatchInterceptorFactory<Message> interceptorFactory);

    /**
     * Registers the given {@code interceptorBuilder} for a {@link CommandMessage}-specific
     * {@link MessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only build <b>once</b>.
     *
     * @param interceptorBuilder the {@link CommandMessage}-specific {@link MessageDispatchInterceptor} builder to
     *                           register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerCommandInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super CommandMessage>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for a {@link CommandMessage}-specific
     * {@link MessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link DispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the factory,
     * ensuring uniqueness per given typa and name. If the interceptor will be identical regardless of the given type or
     * name, please use {@link #registerCommandInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the {@link CommandMessage}-specific {@link MessageDispatchInterceptor} factory to
     *                           register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerCommandInterceptor(
            @Nonnull DispatchInterceptorFactory<? super CommandMessage> interceptorFactory
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link EventMessage}-specific
     * {@link MessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only build <b>once</b>.
     *
     * @param interceptorBuilder the {@link EventMessage}-specific {@link MessageDispatchInterceptor} builder to
     *                           register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerEventInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super EventMessage>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for a {@link EventMessage}-specific
     * {@link MessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link DispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the factory,
     * ensuring uniqueness per given typa and name. If the interceptor will be identical regardless of the given type or
     * name, please use {@link #registerEventInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the {@link EventMessage}-specific {@link MessageDispatchInterceptor} factory to
     *                           register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerEventInterceptor(
            @Nonnull DispatchInterceptorFactory<? super EventMessage> interceptorFactory
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link QueryMessage}-specific
     * {@link MessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only build <b>once</b>.
     *
     * @param interceptorBuilder the {@link QueryMessage}-specific {@link MessageDispatchInterceptor} builder to
     *                           register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerQueryInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super QueryMessage>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for a {@link QueryMessage}-specific
     * {@link MessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link DispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the factory,
     * ensuring uniqueness per given typa and name. If the interceptor will be identical regardless of the given type or
     * name, please use {@link #registerQueryInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the {@link QueryMessage}-specific {@link MessageDispatchInterceptor} factory to
     *                           register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerQueryInterceptor(
            @Nonnull DispatchInterceptorFactory<? super QueryMessage> interceptorFactory
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link SubscriptionQueryUpdateMessage}-specific
     * {@link MessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only build <b>once</b>.
     *
     * @param interceptorBuilder the {@link SubscriptionQueryUpdateMessage}-specific {@link MessageDispatchInterceptor}
     *                           builder to register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerSubscriptionQueryUpdateInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for a
     * {@link SubscriptionQueryUpdateMessage}-specific {@link MessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link DispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the factory,
     * ensuring uniqueness per given typa and name. If the interceptor will be identical regardless of the given type or
     * name, please use {@link #registerSubscriptionQueryUpdateInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the {@link SubscriptionQueryUpdateMessage}-specific {@link MessageDispatchInterceptor}
     *                           factory to register
     * @return this {@code InterceptorRegistry}, for fluent interfacing
     */
    @Nonnull
    DispatchInterceptorRegistry registerSubscriptionQueryUpdateInterceptor(
            @Nonnull DispatchInterceptorFactory<? super SubscriptionQueryUpdateMessage> interceptorFactory
    );

    /**
     * Returns the list of {@link CommandMessage}-specific
     * {@link MessageDispatchInterceptor MessageDispatchInterceptors} registered in this registry for a specific
     * {@code componentType} and {@code componentName}.
     * <p>
     * This collection contains generic {@link Message} {@code MessageDispatchInterceptors} that have been
     * {@link #registerInterceptor(DispatchInterceptorFactory) registered} when the generic builder returns an
     * instance.
     *
     * @param config        the configuration to build all {@link CommandMessage}-specific
     *                      {@link MessageDispatchInterceptor MessageDispatchInterceptors} with
     * @param componentType the type of the component being intercepted
     * @param componentName the name of the component being intercepted
     * @return the list of {@link CommandMessage}-specific
     * {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );

    /**
     * Returns the list of {@link EventMessage}-specific {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     * registered in this registry for a specific {@code componentType} and {@code componentName}.
     * <p>
     * This collection contains generic {@link Message} {@code MessageDispatchInterceptors} that have been
     * {@link #registerInterceptor(DispatchInterceptorFactory) registered} when the generic builder returns an
     * instance.
     *
     * @param config        the configuration to build all {@link EventMessage}-specific
     *                      {@link MessageDispatchInterceptor MessageDispatchInterceptors} with
     * @param componentType the type of the component to retrieve a dispatch interceptor for
     * @param componentName the name of the component to retrieve a dispatch interceptor for
     * @return the list of {@link EventMessage}-specific {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );

    /**
     * Returns the list of {@link QueryMessage}-specific {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     * registered in this registry for a specific {@code componentType} and {@code componentName}.
     * <p>
     * This collection contains generic {@link Message} {@code MessageDispatchInterceptors} that have been
     * {@link #registerInterceptor(DispatchInterceptorFactory) registered} when the generic builder returns an
     * instance.
     *
     * @param config        the configuration to build all {@link QueryMessage}-specific
     *                      {@link MessageDispatchInterceptor MessageDispatchInterceptors} with
     * @param componentType the type of the component to retrieve a dispatch interceptor for
     * @param componentName the name of the component to retrieve a dispatch interceptor for
     * @return the list of {@link QueryMessage}-specific {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );

    /**
     * Returns the list of {@link SubscriptionQueryUpdateMessage}-specific
     * {@link MessageDispatchInterceptor MessageDispatchInterceptors} registered in this registry for a specific
     * {@code componentType} and {@code componentName}.
     * <p>
     * This collection contains generic {@link Message} {@code MessageDispatchInterceptors} that have been
     * {@link #registerInterceptor(DispatchInterceptorFactory) registered} when the generic builder returns an
     * instance.
     *
     * @param config        the configuration to build all {@link SubscriptionQueryUpdateMessage}-specific
     *                      {@link MessageDispatchInterceptor MessageDispatchInterceptors} with
     * @param componentType the type of the component to retrieve a dispatch interceptor for
     * @param componentName the name of the component to retrieve a dispatch interceptor for
     * @return the list of {@link SubscriptionQueryUpdateMessage}-specific
     * {@link MessageDispatchInterceptor MessageDispatchInterceptors}
     */
    @Nonnull
    List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );
}