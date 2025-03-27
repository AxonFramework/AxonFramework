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

package org.axonframework.configuration;

import java.util.function.Consumer;

/**
 * An ApplicationConfigurer combines the component registry with the notion of lifecycle. When started, lifecycle handlers
 * are invoked in the order specified during registration.
 * <p>
 * Building it exposes the {@link AxonConfiguration}, which can be {@link AxonConfiguration#start() started} and
 * {@link AxonConfiguration#shutdown() stopped}. Furthermore, there's a convenience {@link #start()} operation on this
 * interface, which invokes {@code build()} and {@code AxonConfiguration#start()} in one go.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface ApplicationConfigurer {

    /**
     * Executes the given {@code componentRegistrar} on the component registry associated with this
     * ApplicationConfigurer.
     *
     * @param componentRegistrar the actions to take on the component registry
     * @return this ApplicationConfigurer for a fluent API
     */
    ApplicationConfigurer componentRegistry(Consumer<ComponentRegistry> componentRegistrar);

    /**
     * Executes the given {@code lifecycleRegistrar} on the lifecycle registry associated with this
     * ApplicationConfigurer.
     *
     * @param lifecycleRegistrar the actions to take on the lifecycle registry
     * @return this ApplicationConfigurer for a fluent API
     */
    ApplicationConfigurer lifecycleRegistry(Consumer<LifecycleRegistry> lifecycleRegistrar);

    /**
     * Returns the completely initialized {@link NewConfiguration} instance of type {@code C} built using this
     * {@code Configurer} implementation.
     * <p>
     * It is not recommended to change any configuration on {@code this AxonApplicationConfigurer} once this method is
     * called.
     *
     * @return The fully initialized {@link NewConfiguration} instance of type {@code C}.
     */
    AxonConfiguration build();

    /**
     * {@link #build() Builds the configuration} and starts it immediately, by invoking
     * {@link AxonConfiguration#start()}.
     * <p>
     * It is not recommended to change any configuration on {@code this AxonApplicationConfigurer} once this method is
     * called.
     *
     * @return The fully initialized and started {@link AxonConfiguration}.
     */
    default AxonConfiguration start() {
        AxonConfiguration configuration = build();
        configuration.start();
        return configuration;
    }
}
