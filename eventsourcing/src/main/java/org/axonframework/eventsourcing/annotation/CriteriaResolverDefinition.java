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
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.messaging.MessageTypeResolver;

/**
 * Defines how a {@link CriteriaResolver} should be constructed for an {@link EventSourcedEntity} annotated class. The
 * definition receives the {@code entityType} and {@code idType} to create a resolver for the given types. In addition,
 * it receives a {@link MessageTypeResolver} to resolve the type of the message that is being processed.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@FunctionalInterface
public interface CriteriaResolverDefinition {

    /**
     * Constructs a {@link CriteriaResolver} for the given {@code entityType} and {@code idType}. The
     * {@code messageTypeResolver} can be used to resolve the type of the message that is being processed.
     *
     * @param entityType          The entity type the resolver is for.
     * @param idType              The identifier type the resolver is for.
     * @param messageTypeResolver The message type resolver to use for resolving the type of the message.
     * @param <E>                 The type of the entity to create.
     * @param <ID>                The type of the identifier of the entity to create.
     * @return A {@link CriteriaResolver} for the given {@code entityType} and {@code idType}.
     */
    <E, ID> CriteriaResolver<ID> construct(
            @Nonnull Class<E> entityType,
            @Nonnull Class<ID> idType,
            @Nonnull MessageTypeResolver messageTypeResolver
    );
}
