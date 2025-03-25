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
import org.axonframework.configuration.NewConfigurer;

import java.util.function.Consumer;

/**
 * The modelling {@link NewConfigurer} of Axon Framework's configuration API.
 * <p>
 * TODO DISCUSS - Any other methods we would need here at all?
 *
 * @author Steven van Beelen
 * @since 5.0.o
 */
public class ModellingConfigurer
        extends DelegatingConfigurer<ModellingConfigurer>
        implements ApplicationConfigurer<ModellingConfigurer> {

    /**
     * Build a default {@code ModellingConfigurer} instance with several modelling defaults.
     * <p>
     * Besides the specific operations, the {@code ModellingConfigurer} allows for configuring generic
     * {@link Component components}, {@link ComponentDecorator component decorators},
     * {@link ConfigurationEnhancer enhancers}, and {@link Module modules} for a message-driven application.
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
     * Registers the given stateful command handling {@code module} to use in this configuration.
     * <p>
     * As a {@link Module} implementation, any components registered with the given {@code module} will not be
     * accessible from other {@code Modules} to enforce encapsulation.
     *
     * @param module The stateful command handling module to register with {@code this ModellingConfigurer}.
     * @return A {@code ModellingConfigurer} instance for further configuring.
     */
    public ModellingConfigurer registerStatefulCommandHandlingModule(StatefulCommandHandlingModule module) {
        return registerModule(module);
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
