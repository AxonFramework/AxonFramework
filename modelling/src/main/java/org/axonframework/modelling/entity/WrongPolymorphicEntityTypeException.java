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

import java.util.List;

/**
 * Exception indicating that the {@link PolymorphicEntityMetamodel} for a given class cannot handle a command
 * because it is of the wrong type. This typically occurs when a polymorphic entity is passed to a command handler, but
 * the entity type does not match the expected type for that command.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class WrongPolymorphicEntityTypeException extends RuntimeException {

    /**
     * Constructs the exception with the given {@code commandMessage}, the {@code givenEntity} that was passed to the
     * command handler, and the list of {@code supportedEntityTypes} that would be able to handle the command.
     *
     * @param commandMessage        The {@link CommandMessage} that was handled.
     * @param polymorphicEntityType The entity type that was passed to the command handler.
     * @param supportedEntityTypes  The list of entity types that are able to handle the command.
     * @param givenEntityType       The entity type that was passed to the command handler.
     * @param <E>                   The type of the polymorphic entity.
     */
    public <E> WrongPolymorphicEntityTypeException(@Nonnull CommandMessage commandMessage,
                                                   @Nonnull Class<E> polymorphicEntityType,
                                                   @Nonnull List<Class<E>> supportedEntityTypes,
                                                   @Nonnull Class<E> givenEntityType
    ) {
        super(String.format(
                "PolymorphicEntityMetamodel [%s] can not handle command [%s] as it is of the wrong type [%s]. Expected one of the following types: [%s]",
                polymorphicEntityType.getName(),
                commandMessage.type(),
                givenEntityType.getName(),
                supportedEntityTypes.stream()
                                    .map(Class::getName)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("none")
        ));
    }
}
