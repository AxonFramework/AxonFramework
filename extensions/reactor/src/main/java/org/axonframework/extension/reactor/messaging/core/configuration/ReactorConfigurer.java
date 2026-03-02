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

package org.axonframework.extension.reactor.messaging.core.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.ReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.ReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.ReactiveQueryGateway;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The Reactor {@link ApplicationConfigurer} of Axon Framework's configuration API.
 * <p>
 * Since Reactor support is <b>orthogonal</b> to the Axon Framework configurer hierarchy
 * ({@link MessagingConfigurer} &rarr; {@code ModellingConfigurer} &rarr;
 * {@code EventSourcingConfigurer}), this configurer wraps any {@link ApplicationConfigurer} and adds reactive-specific
 * registration methods for {@link ReactiveMessageDispatchInterceptor}s.
 * <p>
 * Provides register operations for:
 * <ul>
 *     <li>{@link #registerReactiveCommandDispatchInterceptor(ComponentBuilder) command} dispatch interceptors</li>
 *     <li>{@link #registerReactiveEventDispatchInterceptor(ComponentBuilder) event} dispatch interceptors</li>
 *     <li>{@link #registerReactiveQueryDispatchInterceptor(ComponentBuilder) query} dispatch interceptors</li>
 * </ul>
 * <p>
 * Usage (simple):
 * <pre>{@code
 * ReactorConfigurer.create()
 *                  .registerReactiveCommandDispatchInterceptor(config -> myInterceptor)
 *                  .start();
 * }</pre>
 * <p>
 * Usage (combined with event sourcing):
 * <pre>{@code
 * var esConfigure = EventSourcingConfigurer.create();
 * var reactor = ReactorConfigurer.enhance(esConfigure);
 * reactor.registerReactiveCommandDispatchInterceptor(config -> myInterceptor);
 * esConfigure.registerEventStorageEngine(...);
 * esConfigure.start();
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactorConfigurationDefaults
 * @see ReactiveCommandGateway
 * @see ReactiveEventGateway
 * @see ReactiveQueryGateway
 */
public class ReactorConfigurer implements ApplicationConfigurer {

    private final ApplicationConfigurer delegate;

    private ReactorConfigurer(@Nonnull ApplicationConfigurer delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The ApplicationConfigurer may not be null.");
    }

    /**
     * Creates a {@code ReactorConfigurer} that enhances an existing {@link ApplicationConfigurer}. This method is
     * useful when applying multiple specialized configurers to configure a single application.
     * <p>
     * Registers the {@link ReactorConfigurationDefaults} enhancer on the delegate to ensure default reactive gateways
     * are available.
     *
     * @param applicationConfigurer The {@link ApplicationConfigurer} to enhance with configuration of reactor
     *                              components.
     * @return A new {@code ReactorConfigurer} wrapping the given configurer.
     * @see #create()
     */
    public static ReactorConfigurer enhance(@Nonnull ApplicationConfigurer applicationConfigurer) {
        return new ReactorConfigurer(applicationConfigurer)
                .componentRegistryInternal(cr -> cr.registerEnhancer(new ReactorConfigurationDefaults()));
    }

    /**
     * Build a default {@code ReactorConfigurer} instance backed by a {@link MessagingConfigurer}.
     * <p>
     * This is a shorthand for {@code enhance(MessagingConfigurer.create())}.
     *
     * @return A {@code ReactorConfigurer} instance for further configuring.
     * @see #enhance(ApplicationConfigurer)
     */
    public static ReactorConfigurer create() {
        return enhance(MessagingConfigurer.create());
    }

    /**
     * Registers a {@link ReactiveMessageDispatchInterceptor} for {@link CommandMessage}s in this configurer.
     * <p>
     * The {@code interceptorBuilder} receives the {@link org.axonframework.common.configuration.Configuration} as
     * input and is expected to return a {@link ReactiveMessageDispatchInterceptor} instance.
     * <p>
     * The interceptor is registered by decorating the {@link ReactiveCommandGateway} component.
     *
     * @param interceptorBuilder The builder constructing the {@link ReactiveMessageDispatchInterceptor}.
     * @return The current instance of the configurer for a fluent API.
     */
    public ReactorConfigurer registerReactiveCommandDispatchInterceptor(
            @Nonnull ComponentBuilder<ReactiveMessageDispatchInterceptor<CommandMessage>> interceptorBuilder
    ) {
        Objects.requireNonNull(interceptorBuilder, "The interceptorBuilder may not be null.");
        delegate.componentRegistry(cr -> cr.registerDecorator(
                ReactiveCommandGateway.class, 0,
                (config, name, gateway) -> {
                    gateway.registerDispatchInterceptor(interceptorBuilder.build(config));
                    return gateway;
                }
        ));
        return this;
    }

    /**
     * Registers a {@link ReactiveMessageDispatchInterceptor} for {@link EventMessage}s in this configurer.
     * <p>
     * The {@code interceptorBuilder} receives the {@link org.axonframework.common.configuration.Configuration} as
     * input and is expected to return a {@link ReactiveMessageDispatchInterceptor} instance.
     * <p>
     * The interceptor is registered by decorating the {@link ReactiveEventGateway} component.
     *
     * @param interceptorBuilder The builder constructing the {@link ReactiveMessageDispatchInterceptor}.
     * @return The current instance of the configurer for a fluent API.
     */
    public ReactorConfigurer registerReactiveEventDispatchInterceptor(
            @Nonnull ComponentBuilder<ReactiveMessageDispatchInterceptor<EventMessage>> interceptorBuilder
    ) {
        Objects.requireNonNull(interceptorBuilder, "The interceptorBuilder may not be null.");
        delegate.componentRegistry(cr -> cr.registerDecorator(
                ReactiveEventGateway.class, 0,
                (config, name, gateway) -> {
                    gateway.registerDispatchInterceptor(interceptorBuilder.build(config));
                    return gateway;
                }
        ));
        return this;
    }

    /**
     * Registers a {@link ReactiveMessageDispatchInterceptor} for {@link QueryMessage}s in this configurer.
     * <p>
     * The {@code interceptorBuilder} receives the {@link org.axonframework.common.configuration.Configuration} as
     * input and is expected to return a {@link ReactiveMessageDispatchInterceptor} instance.
     * <p>
     * The interceptor is registered by decorating the {@link ReactiveQueryGateway} component.
     *
     * @param interceptorBuilder The builder constructing the {@link ReactiveMessageDispatchInterceptor}.
     * @return The current instance of the configurer for a fluent API.
     */
    public ReactorConfigurer registerReactiveQueryDispatchInterceptor(
            @Nonnull ComponentBuilder<ReactiveMessageDispatchInterceptor<QueryMessage>> interceptorBuilder
    ) {
        Objects.requireNonNull(interceptorBuilder, "The interceptorBuilder may not be null.");
        delegate.componentRegistry(cr -> cr.registerDecorator(
                ReactiveQueryGateway.class, 0,
                (config, name, gateway) -> {
                    gateway.registerDispatchInterceptor(interceptorBuilder.build(config));
                    return gateway;
                }
        ));
        return this;
    }

    @Override
    public ReactorConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        delegate.componentRegistry(componentRegistrar);
        return this;
    }

    @Override
    public ReactorConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        delegate.lifecycleRegistry(lifecycleRegistrar);
        return this;
    }

    @Override
    public AxonConfiguration build() {
        return delegate.build();
    }

    private ReactorConfigurer componentRegistryInternal(Consumer<ComponentRegistry> componentRegistrar) {
        delegate.componentRegistry(componentRegistrar);
        return this;
    }
}
