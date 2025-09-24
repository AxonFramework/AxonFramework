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
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.entity.annotation.AnnotatedEntityMetamodel;

/**
 * Definition for an {@link EntityIdResolver} that uses annotations to resolve the entity identifier.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AnnotationBasedEntityIdResolverDefinition implements EntityIdResolverDefinition {

    @Override
    public <E, ID> EntityIdResolver<ID> createIdResolver(@Nonnull Class<E> entityType,
                                                         @Nonnull Class<ID> idType,
                                                         @Nonnull AnnotatedEntityMetamodel<E> entityMetamodel,
                                                         @Nonnull Configuration configuration
    ) {
        return new AnnotationBasedEntityIdResolver<>();
    }
}
