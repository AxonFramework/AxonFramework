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

package org.axonframework.eventhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.configuration.reflection.ParameterResolverFactoryUtils;

/**
 * Configuration enhancer that registers the {@link EventAppenderParameterResolverFactory} to the
 * {@link ComponentRegistry} of the {@link org.axonframework.configuration.NewConfiguration}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class EventAppenderParameterResolverFactoryConfigurationEnhancer implements ConfigurationEnhancer {

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        ParameterResolverFactoryUtils.registerToComponentRegistry(
                registry, EventAppenderParameterResolverFactory::new
        );
    }
}
