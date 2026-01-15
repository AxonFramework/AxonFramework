/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.child.EntityChildMetamodel;

/**
 * Exception indicating that there was no child entity available to handle a command. This happens when one or multiple
 * children declare the command as supported via {@link EntityChildMetamodel#supportedCommands()}, but none of
 * them return true for {@link EntityChildMetamodel#canHandle(CommandMessage, Object, ProcessingContext)}. This
 * indicates that the command is not valid for the current state of the child entity.
 *
 * <h4>Before 5.0.0</h4>
 * This exception was called {@code org.axonframework.modelling.command.AggregateEntityNotFoundException} and was thrown
 * when no child entity was available for an aggregate. It has been renamed to reflect the move to entities in Axon
 * 5.0.0. In all other aspects, it remains the same.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 3.3
 */
public class ChildEntityNotFoundException extends RuntimeException {

    /**
     * Creates a new exception with the given {@code message}.
     *
     * @param message The message describing the cause of the exception.
     */
    public ChildEntityNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given {@code commandMessage} and {@code parentEntity}.
     *
     * @param commandMessage The {@link CommandMessage} that was handled.
     * @param parentEntity   The parent entity instance that was expected to handle the command.
     */
    public ChildEntityNotFoundException(@Nonnull CommandMessage commandMessage,
                                        @Nonnull Object parentEntity) {
        super("No available child entity found for command of type [%s]. State of parent entity [%s]: [%s]"
                      .formatted(commandMessage.type(), parentEntity.getClass().getName(), parentEntity)
        );
    }
}
