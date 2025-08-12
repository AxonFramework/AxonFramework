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
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.modelling.StateManager;

/**
 * Functional interface describing how to build a component of type {@code C} using the {@link Configuration} during
 * construction.
 *
 * @param <C> The component to be built.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@FunctionalInterface
public interface StatefulComponentBuilder<C> extends ComponentBuilder<C> {

    default C build(@Nonnull Configuration config) {
        return build(config, config.getComponent(StateManager.class));
    }

    C build(@Nonnull Configuration config, @Nonnull StateManager stateManager);
}
