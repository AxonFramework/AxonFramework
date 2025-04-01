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

package org.axonframework.modelling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.AxonApplication;
import org.axonframework.configuration.Component;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.DelegatingConfigurer;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.configuration.NewConfigurer;

import java.util.function.Consumer;

/**
 * The modelling {@link NewConfigurer} of Axon Framework's configuration API, providing registration methods to, for
 * example, register a {@link StatefulCommandHandlingModule}.
 * <p>
 * This configurer does not set any defaults other than the defaults granted by the {@link MessagingConfigurer} it
 * wraps.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class ModellingConfigurer
        extends DelegatingConfigurer<ModellingConfigurer>
        implements ApplicationConfigurer<ModellingConfigurer> {

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
        return new ModellingConfigurer(MessagingConfigurer.create());
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
        super(delegate);
    }

    /**
     * Registers the given {@link ModuleBuilder builder} for a {@link StatefulCommandHandlingModule} to use in this configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the result of the given {@code moduleBuilder}
     * will not be accessible from other {@code Modules} to enforce encapsulation.
     *
     * @param moduleBuilder The builder returning a stateful command handling module to register with
     *                      {@code this ModellingConfigurer}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public ModellingConfigurer registerStatefulCommandHandlingModule(
            ModuleBuilder<StatefulCommandHandlingModule> moduleBuilder
    ) {
        return registerModule(moduleBuilder.build());
    }

    /**
     * Delegates the given {@code configureTask} to the {@link MessagingConfigurer} this {@code ModellingConfigurer}
     * delegates to.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code MessagingConfigurer}.
     *
     * @param configureTask Lambda consuming the delegate {@link MessagingConfigurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public ModellingConfigurer messaging(@Nonnull Consumer<MessagingConfigurer> configureTask) {
        return delegate(MessagingConfigurer.class, configureTask);
    }

    /**
     * Delegates the given {@code configureTask} to the {@link AxonApplication} this {@code ModellingConfigurer}
     * delegates to.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code AxonApplication}.
     *
     * @param configureTask Lambda consuming the delegate {@link AxonApplication}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public ModellingConfigurer application(@Nonnull Consumer<AxonApplication> configureTask) {
        return delegate(AxonApplication.class, configureTask);
    }
}
