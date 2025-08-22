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

package org.axonframework.messaging.configuration.reflection;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Priority;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.annotation.FixedValueParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Objects;

import static org.axonframework.common.Priority.LOW;

/**
 * A {@code ParameterResolverFactory} implementation that resolves parameters from available components in the
 * {@link Configuration} instance it was configured with.
 * <p>
 * This implementation is usually autoconfigured when using the Configuration API.
 *
 * @author Allard Buijze
 * @since 3.0.2
 */
@Priority(LOW)
public class ConfigurationParameterResolverFactory implements ParameterResolverFactory {

    private final Configuration configuration;

    /**
     * Initialize an instance using given {@code configuration} to supply the value to resolve parameters with.
     *
     * @param configuration The configuration to look for component with.
     */
    public ConfigurationParameterResolverFactory(@Nonnull Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "The configuration cannot be null.");
    }

    @Nullable
    @Override
    public ParameterResolver<?> createInstance(@Nonnull Executable executable, @Nonnull Parameter[] parameters, int parameterIndex) {
        Class<?> componentType = parameters[parameterIndex].getType();
        return configuration.getOptionalComponent(componentType)
                            .map(FixedValueParameterResolver::new)
                            .orElse(null);
    }
}
