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
import org.axonframework.common.property.Property;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Implementation of a {@link ChildEntity} that uses annotations on a target entity to resolve event and command
 * handlers.
 *
 * @param <P> the parent entity type
 * @param <C> the child entity type
 */
public class AnnotatedChildEntity<P, C> implements ChildEntity<P> {

    private final EntityModel<C> entityModel;
    private final boolean forwardEvents;
    private final boolean forwardEntityOriginatingEventsOnly;
    private final Map<String, MessageHandlingMember<? super P>> commandHandlers;
    private final BiFunction<EventMessage<?>, P, Iterable<C>> eventTargetResolver;

    /**
     * Initiates a new AnnotatedChildEntity instance that uses the provided {@code entityModel} to delegate command
     * and event handling to an annotated child entity.
     *
     * @param entityModel                        model describing the entity
     * @param forwardCommands                    flag indicating whether commands should be forwarded to the entity
     * @param forwardEvents                      flag indicating whether events should be forwarded to the entity
     * @param forwardEntityOriginatingEventsOnly flag indicating if only entity specific events should be forwarded
     * @param commandTargetResolver              resolver for command handler methods on the target
     * @param eventTargetResolver                resolver for event handler methods on the target
     */
    @SuppressWarnings("unchecked")
    public AnnotatedChildEntity(EntityModel<C> entityModel,
                                boolean forwardCommands,
                                boolean forwardEvents,
                                boolean forwardEntityOriginatingEventsOnly,
                                BiFunction<CommandMessage<?>, P, C> commandTargetResolver,
                                BiFunction<EventMessage<?>, P, Iterable<C>> eventTargetResolver) {
        this.entityModel = entityModel;
        this.forwardEvents = forwardEvents;
        this.forwardEntityOriginatingEventsOnly = forwardEntityOriginatingEventsOnly;
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
        if (!forwardEvents) {
            return;
        }

        Iterable<C> targets = eventTargetResolver.apply(msg, declaringInstance);
        if (targets != null) {
            safeForEach(targets, target -> publishToTarget(msg, target));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, MessageHandlingMember<? super P>> commandHandlers() {
        return commandHandlers;
    }

    private void safeForEach(Iterable<C> targets, Consumer<C> action) {
        Spliterator<C> spliterator = targets.spliterator();
        // if the spliterator is IMMUTABLE or CONCURRENT it is safe to use directly
        if (spliteratorIsImmutableOrConcurrent(spliterator)) {
            spliterator.forEachRemaining(action);
        } else {
            // possibly unsafe collection with a fail-fast iterator, create a copy and iterate over that instead
            copy(targets).forEach(action);
        }
    }

    private boolean spliteratorIsImmutableOrConcurrent(Spliterator<C> spliterator) {
        return spliterator.hasCharacteristics(Spliterator.IMMUTABLE) ||
                spliterator.hasCharacteristics(Spliterator.CONCURRENT);
    }

    private Iterable<C> copy(Iterable<C> original) {
        if (original instanceof Collection) {
            // use ArrayList because the size is known in advance
            return new ArrayList<>((Collection<C>) original);
        } else {
            // use LinkedList because the size is not known in advance; growing an ArrayList is expensive
            List<C> list = new LinkedList<>();
            original.forEach(list::add);
            return list;
        }
    }

    @SuppressWarnings("unchecked")
    private void publishToTarget(EventMessage<?> msg, C target) {
        if (forwardEntityOriginatingEventsOnly) {
            Property routingProperty = getProperty(msg.getPayloadType(), entityModel.routingKey());
            Object routingValue = routingProperty.getValue(msg.getPayload());
            if (Objects.equals(routingValue, entityModel.getIdentifier(target))) {
                return;
            }
        }

        this.entityModel.publish(msg, target);
    }
}
