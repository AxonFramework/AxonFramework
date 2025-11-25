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
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.messaging.core.MessageTypeResolver;

/**
 * Defines how a {@link CriteriaResolver} should be constructed for an {@link EventSourcedEntity} annotated class. The
 * definition receives the {@code entityType} and {@code idType} to create the resolver for. In addition, it receives
 * the {@link Configuration} to resolve any component dependencies that are necessary for creating the resolver.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface CriteriaResolverDefinition {

    /**
     * Constructs a {@link CriteriaResolver} for the given {@code entityType} and {@code idType}. The
     * {@code configuration} can be used to retrieve components that help with the resolution of types. For example, a
     * {@link MessageTypeResolver}.
     *
     * @param entityType    The entity type the resolver is for.
     * @param idType        The identifier type the resolver is for.
     * @param configuration The configuration to use for creating the resolver.
     * @param <E>           The type of the entity to create.
     * @param <I>           The type of the identifier of the entity to create.
     * @return A {@link CriteriaResolver} for the given {@code entityType} and {@code idType}.
     */
    <E, I> CriteriaResolver<I> createEventCriteriaResolver(
            @Nonnull Class<E> entityType,
            @Nonnull Class<I> idType,
            @Nonnull Configuration configuration
    );
}
