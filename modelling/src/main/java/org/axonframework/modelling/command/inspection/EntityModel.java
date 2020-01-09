/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.modelling.command.inspection;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * Gets a mapping of {@link MessageHandlingMember} to command name (obtained via {@link
     * org.axonframework.commandhandling.CommandMessage#getCommandName()}) for this {@link #entityClass()}.
     *
     * @return a map of message handler to command name
     * @deprecated use {@link #allCommandHandlers()} or {@link #commandHandlers(Class)} instead
     */
    @Deprecated
    default List<MessageHandlingMember<? super T>> commandHandlers() {
        return commandHandlers(entityClass()).collect(Collectors.toList());
    }

    /**
     * Gets all command handlers per type in this aggregate hierarchy.
     *
     * @return a map of command handlers per type
     */
    default Map<Class<?>, List<MessageHandlingMember<? super T>>> allCommandHandlers() {
        return Collections.emptyMap();
    }

    /**
     * Gets command handlers for provided {@code type} in this aggregate hierarchy.
     *
     * @param type the aggregate type in this hierarchy
     * @return a stream of command handlers for provided {@code type}
     */
    default Stream<MessageHandlingMember<? super T>> commandHandlers(Class<? extends T> type) {
        return Stream.empty();
    }

    /**
     * Gets a list of command handler interceptors for this {@link #entityClass()}.
     *
     * @return list of command handler interceptors
     * @deprecated use {@link #allCommandHandlerInterceptors()} or {@link #commandHandlerInterceptors(Class)} instead
     */
    @Deprecated
    default List<MessageHandlingMember<? super T>> commandHandlerInterceptors() {
        return commandHandlerInterceptors(entityClass()).collect(Collectors.toList());
    }

    /**
     * Gets all command handler interceptors per type in this aggregate hierarchy.
     *
     * @return a map of command handler interceptors per type
     */
    default Map<Class<?>, List<MessageHandlingMember<? super T>>> allCommandHandlerInterceptors() {
        return Collections.emptyMap();
    }

    /**
     * Gets command handler interceptors for provided {@code type} in this aggregate hierarchy.
     *
     * @param type the aggregate type in this hierarchy
     * @return a stream of command handler interceptors for provided {@code type}
     */
    default Stream<MessageHandlingMember<? super T>> commandHandlerInterceptors(Class<? extends T> type) {
        return Stream.empty();
    }

    /**
     * Gets all event handlers per type in this aggregate hierarchy.
     *
     * @return a map of event handlers per type
     */
    default Map<Class<?>, List<MessageHandlingMember<? super T>>> allEventHandlers() {
        return Collections.emptyMap();
    }

    /**
     * Get the EntityModel of an entity of type {@code childEntityType} in case it is the child of the modeled entity.
     *
     * @param childEntityType The class instance of the child entity type
     * @param <C>             the type of the child entity
     * @return An EntityModel for the child entity
     */
    <C> EntityModel<C> modelOf(Class<? extends C> childEntityType);

    /**
     * Returns the class this model describes
     *
     * @return the class this model describes
     */
    Class<? extends T> entityClass();
}
