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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;

/**
 * Factory for creating {@link AnnotatedEntityMetamodel} instances for a given entity type. Used by the
 * {@link AnnotatedEntityMetamodel} to create child metamodels using the same configuration that were used to
 * create the parent metamodel.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@FunctionalInterface
@Internal
public interface AnnotatedEntityMetamodelFactory {

    /**
     * Creates an {@link AnnotatedEntityMetamodel} for the given entity type.
     *
     * @param entityType The type of the entity to create a metamodel for.
     * @param <C>        The type of the entity.
     * @return An {@link AnnotatedEntityMetamodel} for the given entity type.
     */
    @Nonnull
    <C> AnnotatedEntityMetamodel<C> createMetamodelForType(@Nonnull Class<C> entityType);
}
