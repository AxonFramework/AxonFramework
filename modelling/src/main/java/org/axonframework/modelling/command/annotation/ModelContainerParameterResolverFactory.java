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

package org.axonframework.modelling.command.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.ModelContainer;
import org.axonframework.modelling.ModelRegistry;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import static java.util.Objects.requireNonNull;

/**
 * {@link ParameterResolverFactory} implementation that provides {@link ParameterResolver ParameterResolvers} the
 * {@link ModelContainer} parameter.
 *
 * @author Mitchell Herrijgers
 * @see ModelContainer
 * @since 5.0.0
 */
public class ModelContainerParameterResolverFactory implements ParameterResolverFactory {

    private final ModelRegistry registry;

    /**
     * Initialize the factory with the given {@code registry} t to get the {@link ModelContainer} from.
     *
     * @param registry The registry to get the {@link ModelContainer} from.
     */
    public ModelContainerParameterResolverFactory(@Nonnull ModelRegistry registry) {
        this.registry = requireNonNull(registry, "The ModelRegistry is required");
    }

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        Parameter parameter = parameters[parameterIndex];
        Class<?> type = parameter.getType();
        if (type.isAssignableFrom(ModelContainer.class)) {
            return new ModelContainerParameterResolver(registry);
        }
        return null;
    }
}
