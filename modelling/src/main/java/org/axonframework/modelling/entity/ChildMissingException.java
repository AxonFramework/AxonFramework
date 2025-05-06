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
import org.axonframework.modelling.entity.child.EntityChildModel;

import java.util.List;

/**
 * Exception indicating that there was no child entity available to handle a command. This happens when on or multiple
 * children declare the command as supported via {@link EntityChildModel#supportedCommands()}, but none of them return
 * true for {@link EntityChildModel#canHandle(CommandMessage, Object, ProcessingContext)}. This indicates that the
 * command is not valid for the current state of the child entity.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ChildMissingException extends RuntimeException {

    /**
     * Creates a new exception with the given {@code parentEntityType}, {@code commandMessage} and
     * {@code matchingChildModels}.
     *
     * @param parentEntityType      The {@link Class} of the parent entity that was expected to handle the command.
     * @param commandMessage        The {@link CommandMessage} that was handled.
     * @param consideredChildModels The list of {@link EntityChildModel} instances that are able to handle the command.
     * @param <E>                   The type of the parent entity.
     */
    public <E> ChildMissingException(Class<E> parentEntityType,
                                     CommandMessage<?> commandMessage,
                                     List<EntityChildModel<?, E>> consideredChildModels) {
        super("No available child entity found for entity [%s] while handling command with type [%s]. The following child entities are defined: [%s]"
                      .formatted(parentEntityType, commandMessage.type(), consideredChildModels));
    }
}
