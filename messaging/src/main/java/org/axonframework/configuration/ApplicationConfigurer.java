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

/**
 * A specification of the {@link NewConfigurer} providing a means to {@link #build()} it and as such is intended as the
 * base configurer to use when starting an Axon Framework application.
 * <p>
 * Building it exposes the {@link AxonConfiguration}, which can be {@link AxonConfiguration#start() started} and
 * {@link AxonConfiguration#shutdown() stopped}. Furthermore, there's a convenience {@link #start()} operation on this
 * interface, which invokes {@code build()} and {@code AxonConfiguration#start()} in one go.
 *
 * @param <S> The type of configurer this implementation returns. This generic allows us to support fluent interfacing.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface ApplicationConfigurer<S extends ApplicationConfigurer<S>> extends NewConfigurer<S> {

    /**
     * Returns the completely initialized {@link NewConfiguration} instance of type {@code C} built using this
     * {@code Configurer} implementation.
     * <p>
     * It is not recommended to change any configuration on {@code this AxonApplicationConfigurer} once this method is
     * called.
     *
     * @param <C> The {@link NewConfiguration} implementation of type {@code C} returned by this method.
     * @return The fully initialized {@link NewConfiguration} instance of type {@code C}.
     */
    <C extends NewConfiguration> C build();

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
