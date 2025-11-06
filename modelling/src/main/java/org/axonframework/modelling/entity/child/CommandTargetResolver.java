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
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.List;

/**
 * Resolves the target child entity for a given command message. This is used to determine which child entity should
 * handle a command when one multiple child entities are present.
 *
 * @param <E> The type of child entity to resolve.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@FunctionalInterface
public interface CommandTargetResolver<E> {

    /**
     * Returns the target child entity for the given command message. This method is called when multiple child entities
     * are present and the command needs to be routed to the correct one.
     * <p>
     * Can return {@code null} if no child entity is found that can handle the command. In that case, the
     * {@code CommandTargetResolver} should not throw an exception, but rather allow the command to be handled by
     * another child entity.
     *
     * @param candidates The list of candidate child entities that can handle the command.
     * @param message    The command message to resolve the target child entity for.
     * @param context    The processing context in which the command is being processed.
     * @return The target child entity that should handle the command, or {@code null} if no suitable entity is found.
     */
    @Nullable
    E getTargetChildEntity(@Nonnull List<E> candidates,
                           @Nonnull CommandMessage message,
                           @Nonnull ProcessingContext context);


    /**
     * Returns a {@code CommandTargetResolver} that matches any child entity. This means it will return the first
     * candidate child entity if there is one, or throw a {@link ChildAmbiguityException} if there are multiple
     * candidates. If there are no candidates, it will return {@code null}.
     *
     * @param <C> The type of child entity to resolve.
     * @return A {@code CommandTargetResolver} that matches any child entity.
     */
    static <C> CommandTargetResolver<C> MATCH_ANY() {
        return (candidates, message, context) -> {
            if (candidates.isEmpty()) {
                return null;
            }
            if (candidates.size() > 1) {
                throw new ChildAmbiguityException(
                        "Multiple candidates found for command [%s] with payload [%s]. Candidates: %s".formatted(
                                message.type(), message.type(), candidates)
                );
            }
            return candidates.getFirst();
        };
    }
}
