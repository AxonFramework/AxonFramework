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
 * A {@link NewConfigurer configurer} implementation that can be {@link #start() started}.
 * <p>
 * Starting a {@code Configurer} will initiate {@link #onStart(int, LifecycleHandler) registered start operations}
 * contained in the {@link AxonConfiguration} to be invoked.
 *
 * @param <S> The type of configurer this implementation returns. This generic allows us to support fluent interfacing.
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface StartableConfigurer<S extends NewConfigurer<S>> extends NewConfigurer<S> {

    /**
     * {@link #build() Builds the configuration} and starts it immediately.
     * <p>
     * It is not recommended to change any configuration on {@code this StartableConfigurer} once this method is
     * called.
     *
     * @return The fully initialized and started {@link AxonConfiguration}.
     */
    AxonConfiguration start();
}
