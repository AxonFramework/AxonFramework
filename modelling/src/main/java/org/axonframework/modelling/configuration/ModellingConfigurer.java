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

package org.axonframework.modelling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.common.configuration.*;
import org.axonframework.common.configuration.Module;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.ComponentDecorator;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.common.configuration.ModuleBuilder;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The modelling {@link ApplicationConfigurer} of Axon Framework's configuration API, providing registration methods to,
 * for example, register a {@link CommandHandlingModule}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class ModellingConfigurer implements ApplicationConfigurer {

    private final MessagingConfigurer delegate;

    /**
     * This configurer does not set any defaults other than the defaults granted by the {@link MessagingConfigurer} it
     * wraps.
     * <p>
     * Besides the specific operations, the {@code ModellingConfigurer} allows for configuring generic
     * {@link Component components}, {@link ComponentDecorator component decorators},
     * {@link ConfigurationEnhancer enhancers}, and {@link Module modules} for an application using entity modelling.
     * <p>
     * Note that this configurer uses a {@link MessagingConfigurer} to support all this in a message-driven style.
     *
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public static ModellingConfigurer create() {
        return enhance(MessagingConfigurer.create());
    }

    /**
     * Creates a ModellingConfigurer that enhances an existing {@code MessagingConfigurer}. This method is useful when
     * applying multiple specialized Configurers to configure a single application.
     *
     * @param messagingConfigurer The {@code MessagingConfigurer} to enhance with configuration of messaging
     *                            components.
     * @return The current instance of the {@code Configurer} for a fluent API.
     * @see #create()
     */
    public static ModellingConfigurer enhance(@Nonnull MessagingConfigurer messagingConfigurer) {
        return new ModellingConfigurer(messagingConfigurer)
                .componentRegistry(cr -> cr
                        .registerEnhancer(new ModellingConfigurationDefaults())
                );
    }

    /**
     * Construct a {@code ModellingConfigurer} using the given {@code delegate} to delegate all registry-specific
     * operations to.
     * <p>
     * It is recommended to use the {@link #create()} method in most cases instead of this constructor.
     *
     * @param delegate The delegate {@code MessagingConfigurer} the {@code ModellingConfigurer} is based on.
     */
    public ModellingConfigurer(@Nonnull MessagingConfigurer delegate) {
        Objects.requireNonNull(delegate, "The delegate MessagingConfigurer may not be null");
        this.delegate = delegate;
    }

    /**
     * Registers the given {@link ModuleBuilder builder} for a {@link CommandHandlingModule} to use in this
     * configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the result of the given {@code moduleBuilder}
     * will not be accessible from other {@code Modules} to enforce encapsulation. The sole exception to this, are
     * {@code Modules} registered with the resulting {@link CommandHandlingModule} itself.
     *
     * @param moduleBuilder The builder returning a command handling module to register with
     *                      {@code this ModellingConfigurer}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    @Nonnull
    public ModellingConfigurer registerCommandHandlingModule(
            @Nonnull ModuleBuilder<CommandHandlingModule> moduleBuilder
    ) {
        return messaging(messagingConfigurer -> messagingConfigurer.registerCommandHandlingModule(
                moduleBuilder
        ));
    }

    /**
     * Registers the given {@link ModuleBuilder builder} for a {@link QueryHandlingModule} to use in this
     * configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the result of the given {@code moduleBuilder}
     * will not be accessible from other {@code Modules} to enforce encapsulation. The sole exception to this, are
     * {@code Modules} registered with the resulting {@link QueryHandlingModule} itself.
     *
     * @param moduleBuilder The builder returning a query handling module to register with
     *                      {@code this ModellingConfigurer}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    @Nonnull
    public ModellingConfigurer registerQueryHandlingModule(
            @Nonnull ModuleBuilder<QueryHandlingModule> moduleBuilder
    ) {
        return messaging(messagingConfigurer -> messagingConfigurer.registerQueryHandlingModule(
                moduleBuilder
        ));
    }

    /**
     * Registers the given {@code entityModule} on the root-level {@link Configuration}.
     * This will make the entity available in the globally available {@link org.axonframework.modelling.StateManager}.
     *
     * @param entityModule The entity module to register.
     * @param <I>          The type of identifier used to identify the entity that's being built.
     * @param <E>          The type of the entity being built.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    @Nonnull
    public <I, E> ModellingConfigurer registerEntity(@Nonnull EntityModule<I, E> entityModule) {
        Objects.requireNonNull(entityModule, "EntityModule may not be null");
        delegate.componentRegistry(cr -> cr.registerModule(entityModule));
        return this;
    }

    /**
     * Delegates the given {@code configurerTask} to the {@link MessagingConfigurer} this {@code ModellingConfigurer}
     * delegates.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code MessagingConfigurer}.
     *
     * @param configurerTask Lambda consuming the delegate {@link MessagingConfigurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public ModellingConfigurer messaging(@Nonnull Consumer<MessagingConfigurer> configurerTask) {
        configurerTask.accept(delegate);
        return this;
    }

    @Override
    public ModellingConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        delegate.componentRegistry(componentRegistrar);
        return this;
    }

    @Override
    public ModellingConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        delegate.lifecycleRegistry(lifecycleRegistrar);
        return this;
    }

    @Override
    public AxonConfiguration build() {
        return delegate.build();
    }
}
