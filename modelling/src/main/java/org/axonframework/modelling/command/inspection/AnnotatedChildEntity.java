/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.annotation.CommandMessageHandlingMember;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of a {@link ChildEntity} that uses annotations on a target entity to resolve event and command
 * handlers.
 *
 * @param <P> the parent entity type
 * @param <C> the child entity type
 * @author Allard Buijze
 * @since 3.0
 */
public class AnnotatedChildEntity<P, C> implements ChildEntity<P> {

    private final EntityModel<C> entityModel;
    private final List<MessageHandlingMember<? super P>> commandHandlers;
    private final BiFunction<EventMessage<?>, P, Stream<C>> eventTargetResolver;

    /**
     * Initiates a new AnnotatedChildEntity instance that uses the provided {@code entityModel} to delegate command and
     * event handling to an annotated child entity.
     *
     * @param entityModel           a {@link EntityModel} describing the entity.
     * @param forwardCommands       flag indicating whether commands should be forwarded to the entity.
     * @param commandTargetResolver resolver for command handler methods on the target.
     * @param eventTargetResolver   resolver for event handler methods on the target.
     */
    public AnnotatedChildEntity(EntityModel<C> entityModel,
                                boolean forwardCommands,
                                BiFunction<CommandMessage<?>, P, C> commandTargetResolver,
                                BiFunction<EventMessage<?>, P, Stream<C>> eventTargetResolver) {
        this.entityModel = entityModel;
        this.eventTargetResolver = eventTargetResolver;
        this.commandHandlers = new ArrayList<>();
        if (forwardCommands) {
            entityModel.commandHandlers(entityModel.entityClass())
                       .filter(eh -> eh.unwrap(CommandMessageHandlingMember.class).isPresent())
                       .map(childHandler -> (ChildForwardingCommandMessageHandlingMember<? super P, C>)
                               new ChildForwardingCommandMessageHandlingMember<>(
                                       entityModel.commandHandlerInterceptors(entityModel.entityClass())
                                                  .collect(Collectors.toList()),
                                       childHandler,
                                       commandTargetResolver
                               )
                       )
                       .forEach(commandHandlers::add);
        }
    }

    @Override
    public void publish(EventMessage<?> msg, P declaringInstance) {
        eventTargetResolver.apply(msg, declaringInstance)
                           .collect(Collectors.toList()) // Creates copy to prevent ConcurrentModificationException.
                           .forEach(target -> entityModel.publish(msg, target));
    }

    @Override
    public List<MessageHandlingMember<? super P>> commandHandlers() {
        return commandHandlers;
    }
}
