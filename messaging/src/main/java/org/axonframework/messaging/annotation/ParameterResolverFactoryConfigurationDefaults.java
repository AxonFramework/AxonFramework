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

package org.axonframework.messaging.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.configuration.ConfigurationParameterResolverFactory;

import java.util.Optional;

/**
 * {@link ConfigurationEnhancer} that registers the {@link ClasspathParameterResolverFactory} and
 * {@link ConfigurationParameterResolverFactory} as the default {@link ParameterResolverFactory} for the
 * {@link org.axonframework.messaging.annotation.ParameterResolverFactory} component.
 * <p>
 * In addition registers a decorator that, when a parent configuration is present, wraps the
 * {@link ParameterResolverFactory} in a {@link HierarchicalParameterResolverFactory} that delegates to the parent if a
 * parameter cannot be resolved by the current configuration.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ParameterResolverFactoryConfigurationDefaults implements ConfigurationEnhancer {

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        if(!componentRegistry.hasComponent(ParameterResolverFactory.class)) {
            componentRegistry.registerComponent(
                    ParameterResolverFactory.class,
                    (c) -> MultiParameterResolverFactory.ordered(
                            new ConfigurationParameterResolverFactory(c),
                            ClasspathParameterResolverFactory.forClass(c.getClass())
                    )
            );
        }
        componentRegistry.registerDecorator(
                ParameterResolverFactory.class,
                Integer.MAX_VALUE >> 1,
                (config, componentName, component) -> {
                    Optional<ParameterResolverFactory> parentComponent = Optional
                            .ofNullable(config.getParent())
                            .flatMap(p -> p.getOptionalComponent(ParameterResolverFactory.class));
                    if (parentComponent.isPresent()) {
                        return HierarchicalParameterResolverFactory.create(
                                parentComponent.get(),
                                component
                        );
                    }
                    return component;
                });
    }
}
