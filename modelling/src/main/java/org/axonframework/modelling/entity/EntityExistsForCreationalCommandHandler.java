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

/**
 * Exception indicating that a creational command handler was invoked for an entity that already exists. If this command
 * is valid for instances of the entity, as well as creational commands, an instance command can be defined for the same
 * {@link CommandMessage#type()}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class EntityExistsForCreationalCommandHandler extends RuntimeException {

    /**
     * Creates a new exception with the given {@code commandMessage} and {@code existingEntity}.
     *
     * @param commandMessage The {@link CommandMessage} that was handled.
     * @param existingEntity The existing entity that was found.
     */
    public EntityExistsForCreationalCommandHandler(CommandMessage commandMessage, Object existingEntity) {
        super(String.format(
                "Creational command handler for command [%s] encountered an already existing entity: [%s]",
                commandMessage.type(),
                existingEntity
        ));
    }
}
