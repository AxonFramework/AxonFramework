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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Exception indicating that no command handler was found for a command of a certain type in an entity. This can happen
 * when the {@link EntityModel#handle(CommandMessage, Object, ProcessingContext)} method is called, without them being
 * able to handle the command.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class MissingCommandHandlerException extends RuntimeException {

    /**
     * Creates a new exception with the given {@code message} and {@code entityType}.
     *
     * @param message    The {@link CommandMessage} that was handled.
     * @param entityType The {@link Class} of the entity that was expected to handle the command.
     */
    public MissingCommandHandlerException(CommandMessage<?> message, Class<?> entityType) {
        super(String.format(
                "No command handler was found for command of type [%s] for entity [%s]",
                message.type(),
                entityType.getName()
        ));
    }
}
