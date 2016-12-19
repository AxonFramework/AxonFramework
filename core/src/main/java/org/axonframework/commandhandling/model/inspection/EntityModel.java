/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.util.Map;

import static java.lang.String.format;

/**
 * Interface of an entity model that describes the properties and capabilities of an entity of type {@code T}. The
 * entity may be child entity or an aggregate root.
 *
 * @param <T> The type of entity described by this model
 */
public interface EntityModel<T> {

    /**
     * Get the identifier of the given {@code target} entity.
     *
     * @param target The entity instance
     * @return The identifier of the given target entity
     */
    Object getIdentifier(T target);

    /**
     * Get the name of the routing key property on commands and events that provides the identifier that should be used
     * to target entities of this kind.
     *
     * @return The name of the routing key that holds the identifier used to target this sort of entity
     */
    String routingKey();

    /**
     * Publish given event {@code message} on the given {@code target} entity.
     *
     * @param message The event message to publish
     * @param target  The target entity for the event
     */
    void publish(EventMessage<?> message, T target);

    /**
     * Get a mapping of {@link MessageHandlingMember} to command name (obtained via {@link
     * CommandMessage#getCommandName()}).
     *
     * @return Map of message handler to command name
     */
    Map<String, MessageHandlingMember<? super T>> commandHandlers();

    /**
     * Get the {@link MessageHandlingMember} capable of handling commands with given {@code commandName} (see {@link
     * CommandMessage#getCommandName()}). If the entity is not capable of handling such commands an exception is
     * raised.
     *
     * @param commandName The name of the command
     * @return The handler for the command
     * @throws NoHandlerForCommandException In case the entity is not capable of handling commands of given name
     */
    default MessageHandlingMember<? super T> commandHandler(String commandName) {
        MessageHandlingMember<? super T> handler = commandHandlers().get(commandName);
        if (handler == null) {
            throw new NoHandlerForCommandException(format("No handler available to handle command [%s]", commandName));
        }
        return handler;
    }

    /**
     * Get the EntityModel of an entity of type {@code childEntityType} in case it is the child of the modeled entity.
     *
     * @param childEntityType The class instance of the child entity type
     * @param <C> the type of the child entity
     * @return An EntityModel for the child entity
     */
    <C> EntityModel<C> modelOf(Class<? extends C> childEntityType);
}
