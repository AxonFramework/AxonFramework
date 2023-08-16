/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.config;

import org.axonframework.common.Priority;
import org.axonframework.messaging.annotation.FixedValueParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import static org.axonframework.common.Priority.LOW;

/**
 * ParameterResolverFactory implementation that resolves parameters from available components in the Configuration
 * instance it was configured with.
 * <p>
 * This implementation is usually auto-configured when using the Configuration API.
 */
@Priority(LOW)
public class ConfigurationParameterResolverFactory implements ParameterResolverFactory {

    private final Configuration configuration;

    /**
     * Initialize an instance using given {@code configuration} to supply the value to resolve parameters with
     *
     * @param configuration The configuration to look for component
     */
    public ConfigurationParameterResolverFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        Object component = configuration.getComponent(parameters[parameterIndex].getType());
        return component == null ? null : new FixedValueParameterResolver<>(component);
    }
}
