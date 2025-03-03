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

import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.ModelContainer;
import org.axonframework.modelling.command.ModelIdResolver;
import org.axonframework.modelling.ModelRegistry;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import static org.axonframework.common.ReflectionUtils.ensureAccessible;

/**
 * {@link ParameterResolverFactory} implementation that provides {@link ParameterResolver}s for parameters annotated
 * with {@link InjectModel}.
 * <p>
 * If the {@link InjectModel} annotation has a {@link InjectModel#idProperty()}, a {@link PropertyBasedModelIdResolver}
 * will be constructed to resolve the model identifier. If the {@link InjectModel} annotation has a
 * {@link InjectModel#idResolver()}, an instance of the given {@link ModelIdResolver} will be constructed to resolve the
 * model identifier. If it has both, the {@link InjectModel#idResolver()} will be used. If it has neither, the
 * {@link AnnotationBasedModelIdResolver} will be used.
 *
 * @author Mitchell Herrijgers
 * @see InjectModel
 * @since 5.0.0
 */
public class InjectModelParameterResolverFactory implements ParameterResolverFactory {

    private final ModelRegistry registry;

    /**
     * Initialize the factory with the given {@code registry} to resolve models from.
     *
     * @param registry The registry to resolve models from
     */
    public InjectModelParameterResolverFactory(ModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        Parameter parameter = parameters[parameterIndex];
        Class<?> type = parameter.getType();
        if (type.isAssignableFrom(ModelContainer.class)) {
            return new ModelContainerParameterResolver(registry);
        }
        if (!parameter.isAnnotationPresent(InjectModel.class)) {
            return null;
        }

        InjectModel annotation = parameter.getAnnotation(InjectModel.class);
        ModelIdResolver<?> modelIdResolver;
        if (annotation.idProperty() != null && !annotation.idProperty().isEmpty()) {
            modelIdResolver = new PropertyBasedModelIdResolver(annotation.idProperty());
        } else {

            modelIdResolver = constructCustomModelIdResolver(annotation);
        }
        return new InjectModelParameterResolver(
                registry,
                type,
                modelIdResolver
        );
    }

    private static ModelIdResolver<?> constructCustomModelIdResolver(InjectModel annotation) {
        try {
            var constructor = annotation.idResolver().getDeclaredConstructor();
            ensureAccessible(constructor);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
