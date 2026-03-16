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

import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A registry of {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} for the Reactor extension.
 * <p>
 * Provides operations to register generic {@link Message}, {@link CommandMessage}-specific,
 * {@link EventMessage}-specific, and {@link QueryMessage}-specific {@code ReactorMessageDispatchInterceptors}.
 * Registered interceptors can be retrieved through
 * {@link #commandInterceptors(Configuration, Class, String)},
 * {@link #eventInterceptors(Configuration, Class, String)}, and
 * {@link #queryInterceptors(Configuration, Class, String)}.
 * <p>
 * Generic {@link Message} interceptors registered via
 * {@link #registerInterceptor(ComponentBuilder)} or
 * {@link #registerInterceptor(ReactorDispatchInterceptorFactory)}
 * are automatically applied to all message types.
 * <p>
 * This registry follows the same pattern as
 * {@link org.axonframework.messaging.core.interception.DispatchInterceptorRegistry DispatchInterceptorRegistry} but is
 * specific to reactor-based interceptors.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactorMessageDispatchInterceptor
 * @see ReactorDispatchInterceptorFactory
 */
@Internal
public interface ReactorDispatchInterceptorRegistry extends DescribableComponent {

    /**
     * Registers the given {@code interceptorBuilder} for a generic {@link Message}
     * {@link ReactorMessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only built <b>once</b>.
     *
     * @param interceptorBuilder the generic {@link Message} {@link ReactorMessageDispatchInterceptor} builder to
     *                           register
     * @return this registry, for fluent interfacing
     */
    ReactorDispatchInterceptorRegistry registerInterceptor(
            ComponentBuilder<ReactorMessageDispatchInterceptor<Message>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for a generic {@link Message}
     * {@link ReactorMessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link ReactorDispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the
     * factory, ensuring uniqueness per given type and name. If the interceptor will be identical regardless of the
     * given type or name, please use {@link #registerInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the generic {@link Message} {@link ReactorMessageDispatchInterceptor} factory to
     *                           register
     * @return this registry, for fluent interfacing
     */
    ReactorDispatchInterceptorRegistry registerInterceptor(
            ReactorDispatchInterceptorFactory<Message> interceptorFactory
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link CommandMessage}-specific
     * {@link ReactorMessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only built <b>once</b>.
     *
     * @param interceptorBuilder the {@link CommandMessage}-specific {@link ReactorMessageDispatchInterceptor} builder
     *                           to register
     * @return this registry, for fluent interfacing
     */
    ReactorDispatchInterceptorRegistry registerCommandInterceptor(
            ComponentBuilder<ReactorMessageDispatchInterceptor<? super CommandMessage>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for a {@link CommandMessage}-specific
     * {@link ReactorMessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link ReactorDispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the
     * factory, ensuring uniqueness per given type and name. If the interceptor will be identical regardless of the
     * given type or name, please use {@link #registerCommandInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the {@link CommandMessage}-specific {@link ReactorMessageDispatchInterceptor} factory
     *                           to register
     * @return this registry, for fluent interfacing
     */
    ReactorDispatchInterceptorRegistry registerCommandInterceptor(
            ReactorDispatchInterceptorFactory<? super CommandMessage> interceptorFactory
    );

    /**
     * Registers the given {@code interceptorBuilder} for an {@link EventMessage}-specific
     * {@link ReactorMessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only built <b>once</b>.
     *
     * @param interceptorBuilder the {@link EventMessage}-specific {@link ReactorMessageDispatchInterceptor} builder to
     *                           register
     * @return this registry, for fluent interfacing
     */
    ReactorDispatchInterceptorRegistry registerEventInterceptor(
            ComponentBuilder<ReactorMessageDispatchInterceptor<? super EventMessage>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for an {@link EventMessage}-specific
     * {@link ReactorMessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link ReactorDispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the
     * factory, ensuring uniqueness per given type and name. If the interceptor will be identical regardless of the
     * given type or name, please use {@link #registerEventInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the {@link EventMessage}-specific {@link ReactorMessageDispatchInterceptor} factory to
     *                           register
     * @return this registry, for fluent interfacing
     */
    ReactorDispatchInterceptorRegistry registerEventInterceptor(
            ReactorDispatchInterceptorFactory<? super EventMessage> interceptorFactory
    );

    /**
     * Registers the given {@code interceptorBuilder} for a {@link QueryMessage}-specific
     * {@link ReactorMessageDispatchInterceptor}.
     * <p>
     * Registering an interceptor per a {@link ComponentBuilder} ensures the interceptor is only built <b>once</b>.
     *
     * @param interceptorBuilder the {@link QueryMessage}-specific {@link ReactorMessageDispatchInterceptor} builder to
     *                           register
     * @return this registry, for fluent interfacing
     */
    ReactorDispatchInterceptorRegistry registerQueryInterceptor(
            ComponentBuilder<ReactorMessageDispatchInterceptor<? super QueryMessage>> interceptorBuilder
    );

    /**
     * Registers the given component-aware {@code interceptorFactory} for a {@link QueryMessage}-specific
     * {@link ReactorMessageDispatchInterceptor}.
     * <p>
     * The factory will receive the component type and name when the interceptor is retrieved allowing for
     * component-specific customization of the interceptor. Registering an interceptor per a
     * {@link ReactorDispatchInterceptorFactory} enforces construction of the interceptor for every invocation of the
     * factory, ensuring uniqueness per given type and name. If the interceptor will be identical regardless of the
     * given type or name, please use {@link #registerQueryInterceptor(ComponentBuilder)} instead.
     *
     * @param interceptorFactory the {@link QueryMessage}-specific {@link ReactorMessageDispatchInterceptor} factory to
     *                           register
     * @return this registry, for fluent interfacing
     */
    ReactorDispatchInterceptorRegistry registerQueryInterceptor(
            ReactorDispatchInterceptorFactory<? super QueryMessage> interceptorFactory
    );

    /**
     * Returns the list of {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} registered for
     * {@link CommandMessage CommandMessages} for a specific {@code componentType} and {@code componentName}.
     *
     * @param config        the configuration to build all {@link CommandMessage}-specific
     *                      {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} with
     * @param componentType the type of the component being intercepted
     * @param componentName the name of the component being intercepted
     * @return the list of command dispatch interceptors
     */
    List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    );

    /**
     * Returns the list of {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} registered for
     * {@link EventMessage EventMessages} for a specific {@code componentType} and {@code componentName}.
     *
     * @param config        the configuration to build all {@link EventMessage}-specific
     *                      {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} with
     * @param componentType the type of the component being intercepted
     * @param componentName the name of the component being intercepted
     * @return the list of event dispatch interceptors
     */
    List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    );

    /**
     * Returns the list of {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} registered for
     * {@link QueryMessage QueryMessages} for a specific {@code componentType} and {@code componentName}.
     *
     * @param config        the configuration to build all {@link QueryMessage}-specific
     *                      {@link ReactorMessageDispatchInterceptor ReactorMessageDispatchInterceptors} with
     * @param componentType the type of the component being intercepted
     * @param componentName the name of the component being intercepted
     * @return the list of query dispatch interceptors
     */
    List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    );
}
