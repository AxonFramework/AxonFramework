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

package org.axonframework.modelling;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;

import java.util.Optional;

/**
 * {@link ConfigurationEnhancer} that registers a decorator for the {@link StateManager} that, when a parent
 * configuration is present, wraps child and parent {@link StateManager} in a {@link HierarchicalStateManager} that
 * delegates to the parent if a state cannot be resolved by the current configuration.
 * <p>
 * To prevent modules not defining entities not being able to find the {@link StateManager} component to access the
 * parent configuration, an empty {@link StateManager} is registered when no {@link StateManager} is present.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class HierarchicalStateManagerConfigurationEnhancer implements ConfigurationEnhancer {

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        if (!componentRegistry.hasComponent(StateManager.class)) {
            componentRegistry.registerComponent(
                    StateManager.class,
                    (c) -> SimpleStateManager.builder("Empty").build()
            );
        }
        componentRegistry.registerDecorator(
                StateManager.class,
                Integer.MAX_VALUE,
                (config, componentName, component) -> {
                    Optional<StateManager> parentComponent = Optional
                            .ofNullable(config.getParent())
                            .flatMap(p -> p.getOptionalComponent(StateManager.class));
                    if (parentComponent.isPresent()) {
                        return HierarchicalStateManager.create(parentComponent.get(), component);
                    }
                    return component;
                });
    }
}
