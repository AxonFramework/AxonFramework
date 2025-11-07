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

package org.axonframework.modelling.entity.child;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Functional interface that determines if a given entity should be targeted for a specific {@link EventMessage}.
 * Typically used to test whether an entity qualifies to process an event and evolve its state accordingly.
 * <p>
 * Before version 5.0.0, this interface was known as {@code org.axonframework.modelling.command.ForwardingMode}.
 * The interface is now a predicate, instead of a function that returns a part of a list. This allows for
 * more flexible and efficient matching of entities against events in a processing context.
 *
 * @param <E> The type of entity this matcher is applied to.
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 3.1
 */
@FunctionalInterface
public interface EventTargetMatcher<E> {

    /**
     * Tests whether the given {@code entity} should be
     * {@link org.axonframework.modelling.EntityEvolver#evolve(Object, EventMessage, ProcessingContext) evolved} for the
     * given {@link EventMessage}.
     *
     * @param targetEntity      The entity of type {@code E} to test.
     * @param message           The {@link EventMessage} to test.
     * @param processingContext The {@link ProcessingContext} in which the message is being processed.
     * @return {@code true} if the entity should be invoked for the message, {@code false} otherwise.
     */
    boolean matches(@Nonnull E targetEntity,
                    @Nonnull EventMessage message,
                    @Nonnull ProcessingContext processingContext);


    /**
     * Returns a matcher that matches any entity, meaning it will always return {@code true} for any entity and
     * {@link EventMessage}.
     *
     * @param <E> The type of the entity to match.
     * @return An {@link EventTargetMatcher} that matches any entity.
     */
    static <E> EventTargetMatcher<E> MATCH_ANY() {
        return (targetEntity, message, processingContext) -> true;
    }
}
