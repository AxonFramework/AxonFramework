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

package org.axonframework.messaging;

import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.function.Function;

/**
 * Utility class that provides methods to register a {@link ParameterResolverFactory} to the {@link ComponentRegistry}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ParameterResolverFactoryUtils {

    /**
     * Register a {@link ParameterResolverFactory} to the {@link ComponentRegistry} using the given {@code factory}
     * function. It will be registered with order {@code 0}.
     *
     * @param componentRegistry The {@link ComponentRegistry} to register the {@link ParameterResolverFactory} to.
     * @param factory           The {@link Function} that creates the {@link ParameterResolverFactory} based on the
     *                          {@link NewConfiguration}.
     */
    public static void registerToComponentRegistry(ComponentRegistry componentRegistry,
                                                   Function<NewConfiguration, ParameterResolverFactory> factory
    ) {
        registerToComponentRegistry(componentRegistry, 0, factory);
    }

    /**
     * Register a {@link ParameterResolverFactory} to the {@link ComponentRegistry} using the given {@code factory}
     * function.
     *
     * @param componentRegistry The {@link ComponentRegistry} to register the {@link ParameterResolverFactory} to.
     * @param order             The order in which the {@link ParameterResolverFactory} should be registered.
     * @param factory           The {@link Function} that creates the {@link ParameterResolverFactory} based on the
     *                          {@link NewConfiguration}.
     */
    public static void registerToComponentRegistry(ComponentRegistry componentRegistry,
                                                   int order,
                                                   Function<NewConfiguration, ParameterResolverFactory> factory
    ) {
        componentRegistry.registerDecorator(
                ParameterResolverFactory.class,
                order,
                (config, componentName, component) -> MultiParameterResolverFactory.ordered(
                        component, factory.apply(config)
                )
        );
    }


    private ParameterResolverFactoryUtils() {
        // Utility class
    }
}
