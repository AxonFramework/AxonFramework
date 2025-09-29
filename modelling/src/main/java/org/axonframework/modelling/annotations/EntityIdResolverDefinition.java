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

package org.axonframework.modelling.annotations;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.Configuration;
import org.axonframework.modelling.EntityIdResolver;
import org.axonframework.modelling.entity.annotations.AnnotatedEntityMetamodel;

/**
 * Definition describing how to create an {@link EntityIdResolver} for a given entity type and identifier type.
 * <p>
 * Used by annotation-based entities to resolve the entity identifier from the command.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@FunctionalInterface
public interface EntityIdResolverDefinition {

    /**
     * Creates an {@link EntityIdResolver} for the given entity type and identifier type.
     *
     * @param entityType      The type of the entity for which the resolver is created.
     * @param idType          The type of the identifier for which the resolver is created.
     * @param entityMetamodel The metamodel of the entity.
     * @param configuration   The configuration of the application, providing access to the components available.
     * @param <E>             The type of the entity for which the resolver is created.
     * @param <ID>            The type of the identifier for which the resolver is created.
     * @return The {@link EntityIdResolver} for the given entity type and identifier type.
     */
    <E, ID> EntityIdResolver<ID> createIdResolver(
            @Nonnull Class<E> entityType,
            @Nonnull Class<ID> idType,
            @Nonnull AnnotatedEntityMetamodel<E> entityMetamodel,
            @Nonnull Configuration configuration
    );
}
