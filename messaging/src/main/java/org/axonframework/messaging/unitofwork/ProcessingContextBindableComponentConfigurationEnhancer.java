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

package org.axonframework.messaging.unitofwork;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

/**
 * {@link ConfigurationEnhancer} that registers a decorator for the {@link ParameterResolverFactory} that, wraps any
 * {@link ParameterResolverFactory} in a {@link ProcessingContextBindingParameterResolverFactory}. This wrapped
 * variation will automatically bind any {@link ProcessingContextBindableComponent} to the current
 * {@link ProcessingContext} when resolving parameters.
 *
 * @author Mitchell Herrijgers
 * @see ProcessingContextBindingParameterResolverFactory
 * @since 5.0.0
 */
public class ProcessingContextBindableComponentConfigurationEnhancer implements ConfigurationEnhancer {

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        componentRegistry.registerDecorator(
                ParameterResolverFactory.class,
                // We want this to be executed very late, but still allow for a bit of flexibility for users to add
                // resolvers after this enhancer.
                Integer.MAX_VALUE - 100,
                (config, componentName, component) -> new ProcessingContextBindingParameterResolverFactory(component));
    }
}
