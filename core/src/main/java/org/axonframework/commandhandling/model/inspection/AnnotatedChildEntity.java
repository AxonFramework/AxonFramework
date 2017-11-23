/*
 * Copyright (c) 2010-2017. Axon Framework
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
import org.axonframework.commandhandling.model.ForwardingMode;
import org.axonframework.common.property.Property;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Implementation of a {@link ChildEntity} that uses annotations on a target entity to resolve event and command
 * handlers.
 *
 * @param <P> the parent entity type.
 * @param <C> the child entity type.
 */
public class AnnotatedChildEntity<P, C> implements ChildEntity<P> {

    private static final String EMPTY_STRING = "";

    private final EntityModel<C> entityModel;
    private final ForwardingMode eventForwardingMode;
    private final String eventRoutingKey;
    private final Map<String, MessageHandlingMember<? super P>> commandHandlers;
    private final BiFunction<EventMessage<?>, P, Stream<C>> eventTargetResolver;

    /**
     * Initiates a new AnnotatedChildEntity instance that uses the provided {@code entityModel} to delegate command
     * and event handling to an annotated child entity.
     *
     * @param entityModel           a {@link EntityModel} describing
     *                              the entity.
     * @param forwardCommands       flag indicating whether commands should be forwarded to the entity
     * @param eventForwardingMode   a {@link ForwardingMode} enum describing the
     *                              used forwarding mode for events.
     *                              only entity specific events should be forwarded
     * @param eventRoutingKey       a {@link String} specifying the routing key for all events within this
     *                              Entity.
     * @param commandTargetResolver resolver for command handler methods on the target
     * @param eventTargetResolver   resolver for event handler methods on the target
     */
    @SuppressWarnings("unchecked")
    public AnnotatedChildEntity(EntityModel<C> entityModel,
                                boolean forwardCommands,
                                ForwardingMode eventForwardingMode,
                                String eventRoutingKey,
                                BiFunction<CommandMessage<?>, P, C> commandTargetResolver,
                                BiFunction<EventMessage<?>, P, Stream<C>> eventTargetResolver) {
        this.entityModel = entityModel;
        this.eventForwardingMode = eventForwardingMode;
        this.eventRoutingKey = eventRoutingKey;
        this.eventTargetResolver = eventTargetResolver;
        this.commandHandlers = new HashMap<>();
        if (forwardCommands) {
            entityModel.commandHandlers().forEach(
                    (commandType, childHandler) -> commandHandlers.put(
                            commandType,
                            new ChildForwardingCommandMessageHandlingMember<>(childHandler, commandTargetResolver)
                    )
            );
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void publish(EventMessage<?> msg, P declaringInstance) {
        if (eventForwardingMode == ForwardingMode.NONE) {
            return;
        }

        eventTargetResolver.apply(msg, declaringInstance)
                           .forEach(target -> publishToTarget(msg, target));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, MessageHandlingMember<? super P>> commandHandlers() {
        return commandHandlers;
    }

    @SuppressWarnings("unchecked")
    private void publishToTarget(EventMessage<?> msg, C target) {
        if (eventForwardingMode == ForwardingMode.ALL) {
            entityModel.publish(msg, target);
        } else if (eventForwardingMode == ForwardingMode.ROUTING_KEY) {
            Property eventRoutingProperty = getProperty(msg.getPayloadType(), eventRoutingKey());

            Object eventRoutingValue = eventRoutingProperty.getValue(msg.getPayload());
            Object entityIdentifier = entityModel.getIdentifier(target);

            if (Objects.equals(eventRoutingValue, entityIdentifier)) {
                entityModel.publish(msg, target);
            }
        }
    }

    private String eventRoutingKey() {
        return Objects.equals(eventRoutingKey, EMPTY_STRING) ? entityModel.routingKey() : eventRoutingKey;
    }
}
