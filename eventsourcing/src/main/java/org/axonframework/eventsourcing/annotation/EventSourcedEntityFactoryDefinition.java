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

package org.axonframework.eventsourcing.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;

import java.util.Set;

/**
 * Defines how an {@link EventSourcedEntityFactory} should be constructed for an {@link EventSourcedEntity} annotated
 * class. The definition receives the {@code entityType} and {@code idType} to create the factory for. In addition, it
 * receives the {@link Configuration} to resolve any component dependencies that are necessary for creating the
 * factory.
 *
 * @param <E>  The type of the entity to create.
 * @param <ID> The type of the identifier of the entity to create.
 * @author Mitchell Herrijgers
 * @see EventSourcedEntity
 * @since 5.0.0
 */
public interface EventSourcedEntityFactoryDefinition<E, ID> {

    /**
     * Creates a new {@link EventSourcedEntityFactory} for the given {@code entityType} and {@code idType}. In addition,
     * it receives the {@link Configuration} to resolve any component dependencies that are necessary for creating the
     * factory.
     *
     * @param entityType     The type of the entity to create.
     * @param entitySubTypes The subtypes of the entity in case the entity is polymorphic.
     * @param idType         The identifier type of the entity to create.
     * @param configuration  The configuration to use for creating the factory.
     * @return A new {@link EventSourcedEntityFactory} for the given {@code entityType} and {@code idType}.
     */
    EventSourcedEntityFactory<ID, E> createFactory(@Nonnull Class<E> entityType,
                                                   @Nonnull Set<Class<? extends E>> entitySubTypes,
                                                   @Nonnull Class<ID> idType,
                                                   @Nonnull Configuration configuration);
}
