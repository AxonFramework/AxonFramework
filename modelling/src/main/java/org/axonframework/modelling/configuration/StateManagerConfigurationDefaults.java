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
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.HierarchicalStateManager;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.annotation.InjectEntityParameterResolverFactory;

import java.util.Optional;

/**
 * {@link ConfigurationEnhancer} that registers the {@link InjectEntityParameterResolverFactory} to the
 * {@link ComponentRegistry}. In addition, it registers a decorator that, when a parent configuration is present, wraps
 * the {@link StateManager} in a {@link HierarchicalStateManager} that delegates to the parent if a state cannot be
 * resolved by the current configuration.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class StateManagerConfigurationDefaults implements ConfigurationEnhancer {

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        componentRegistry.registerDecorator(
                ParameterResolverFactory.class,
                Integer.MAX_VALUE >> 2,
                (config, componentName, component) -> {
                    return MultiParameterResolverFactory.ordered(
                            component,
                            new InjectEntityParameterResolverFactory(config));
                });

        componentRegistry.registerDecorator(
                StateManager.class,
                Integer.MAX_VALUE >> 1,
                (config, componentName, component) -> {
                    Optional<StateManager> parentComponent = Optional.ofNullable(config.getParent())
                                                                     .flatMap(p -> p.getOptionalComponent(StateManager.class));
                    if (parentComponent.isPresent()) {
                        return HierarchicalStateManager.create(parentComponent.get(), component);
                    }
                    return component;
                });
    }
}
