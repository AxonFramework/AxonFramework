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

package org.axonframework.modelling.entity;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Functional interface describing a handler of a {@link CommandMessage} for that uses an entity to make a decision.
 * This is typically used in the context of an {@link EntityMetamodel} where the entity instance is passed to the
 * handler to allow for more complex command handling logic.
 *
 * @param <E> The type of the entity.
 * @author Mitchell Herrijgers
 * @see EntityMetamodel
 * @since 5.0.0
 */
@FunctionalInterface
public interface EntityCommandHandler<E> extends MessageHandler {

    /**
     * Handles the given {@link CommandMessage} for the given {@code entity}.
     *
     * @param command The {@link CommandMessage} to handle.
     * @param entity  The entity instance to handle the command for.
     * @param context The {@link ProcessingContext} for the command.
     * @return The result of the command handling, which may be a {@link CommandResultMessage} or an error message.
     */
    @Nonnull
    MessageStream.Single<CommandResultMessage> handle(@Nonnull CommandMessage command,
                                                      @Nonnull E entity,
                                                      @Nonnull ProcessingContext context);
}
