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
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.repository.ManagedEntity;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.ConstructorUtils.getConstructorFunctionWithZeroArguments;

/**
 * {@link ParameterResolverFactory} implementation that provides {@link ParameterResolver ParameterResolvers} for
 * parameters annotated with {@link InjectEntity}. The parameter can either be a {@link ManagedEntity} or the entity
 * itself. The order of resolving the identity id is as specified on the {@link InjectEntity} annotation.
 *
 * @author Mitchell Herrijgers
 * @see InjectEntity
 * @since 5.0.0
 */
public class InjectEntityParameterResolverFactory implements ParameterResolverFactory {

    private final NewConfiguration configuration;

    /**
     * Initialize the factory with the given {@code configuration}. The {@code configuration} should
     * contain a {@link org.axonframework.modelling.StateManager} to load entities from.
     * <p>
     * This constructor depends on the {@link NewConfiguration} instead of the {@link StateManager} to prevent
     * circular dependencies during creation of message handlers. For example, if the repository uses an annotation-based
     * event state applier, it would construct methods, which would then require the {@link StateManager} to be
     * created during the construction of the parameter resolvers. This would lead to a circular dependency.
     *
     * @param configuration The {@link NewConfiguration} to use for loading entities.
     */
    public InjectEntityParameterResolverFactory(@Nonnull NewConfiguration configuration) {
        this.configuration = requireNonNull(configuration, "The NewConfiguration is required");
    }

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        Parameter parameter = parameters[parameterIndex];
        if (!parameter.isAnnotationPresent(InjectEntity.class)) {
            return null;
        }

        InjectEntity annotation = parameter.getAnnotation(InjectEntity.class);
        EntityIdResolver<?> entityIdResolver = getEntityIdResolver(annotation);

        // If the parameter is a ManagedEntity, we need to extract the actual entity type
        Class<?> entityClass = parameter.getType();
        boolean isManagedEntity = ManagedEntity.class.isAssignableFrom(entityClass);
        if (isManagedEntity) {
            ParameterizedType parameterizedType = (ParameterizedType) parameter.getParameterizedType();
            entityClass = (Class<?>) parameterizedType.getActualTypeArguments()[1];
        }
        return new InjectEntityParameterResolver(
                configuration,
                entityClass,
                entityIdResolver,
                isManagedEntity
        );
    }

    private static EntityIdResolver<?> getEntityIdResolver(InjectEntity annotation) {
        if (annotation.idProperty() != null && !annotation.idProperty().isEmpty()) {
            return new PropertyBasedEntityIdResolver(annotation.idProperty());
        }
        try {
            return getConstructorFunctionWithZeroArguments(annotation.idResolver()).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate id resolver: " + annotation.idResolver().getName(), e);
        }
    }
}
