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
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.repository.ManagedEntity;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.ReflectionUtils.constructWithOptionalArguments;

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

    private final StateManager stateManager;

    /**
     * Initialize the factory with the given {@code stateManager} to load entities from.
     *
     * @param stateManager The registry to load entities from.
     */
    public InjectEntityParameterResolverFactory(@Nonnull StateManager stateManager) {
        this.stateManager = requireNonNull(stateManager, "The StateManager is required");
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
        assertStateManagerCanLoadEntityClass(entityClass);
        return new InjectEntityParameterResolver(
                stateManager,
                entityClass,
                entityIdResolver,
                isManagedEntity
        );
    }


    /**
     * Let's do a boot-time check to see if it's actually valid. As the id type is defined runtime, we can only check
     * for the entity type. Still better than nothing!
     *
     * @param entityClass The entity class to check
     */
    private void assertStateManagerCanLoadEntityClass(Class<?> entityClass) {
        if (stateManager.registeredEntities().stream().noneMatch(entityClass::isAssignableFrom)) {
            throw new IllegalArgumentException(
                    "The entity class " + entityClass.getName()
                            + " is not registered with the StateManager. Can not construct a ParameterResolver for the @InjectModel of said class.");
        }
    }

    private static EntityIdResolver<?> getEntityIdResolver(InjectEntity annotation) {
        if (annotation.idProperty() != null && !annotation.idProperty().isEmpty()) {
            return new PropertyBasedEntityIdResolver(annotation.idProperty());
        }
        return constructWithOptionalArguments(annotation.idResolver());
    }
}
