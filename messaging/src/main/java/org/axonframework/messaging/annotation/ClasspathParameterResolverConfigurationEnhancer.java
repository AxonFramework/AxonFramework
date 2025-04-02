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

/**
 * {@link ConfigurationEnhancer} that registers the {@link ClasspathParameterResolverFactory} as the default
 * {@link ParameterResolverFactory}. Disabling this enhancer will disable the {@link ParameterResolverFactory} component
 * registration, and will thus not invoke any decorators registering more. Make sure you provide an alternative
 * when disabling this enhancer.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ClasspathParameterResolverConfigurationEnhancer implements ConfigurationEnhancer {

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        componentRegistry.registerComponent(
                ParameterResolverFactory.class,
                (c) -> ClasspathParameterResolverFactory.forClass(c.getClass())
        );
    }
}
