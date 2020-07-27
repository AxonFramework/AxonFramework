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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.property.Property;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.ForwardingMode;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Abstract implementation of the {@link ChildEntityDefinition} to provide reusable functionality for collections of
 * ChildEntityDefinitions.
 *
 * @author Steven van Beelen
 * @since 3.1
 */
public abstract class AbstractChildEntityDefinition implements ChildEntityDefinition {

    @SuppressWarnings("unchecked")  // Suppresses cast to Class of ForwardingMode
    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Member member, EntityModel<T> declaringEntity) {
        Map<String, Object> attributes =
                findAnnotationAttributes((AnnotatedElement) member, AggregateMember.class).orElse(null);
        if (attributes == null || !isMemberTypeSupported(member)) {
            return Optional.empty();
        }

        EntityModel<Object> childEntityModel = extractChildEntityModel(declaringEntity, attributes, member);

        ForwardingMode<EventMessage<?>> eventForwardingMode = instantiateForwardingMode(
                member,
                childEntityModel,
                (Class<? extends ForwardingMode<EventMessage<?>>>) attributes.get("eventForwardingMode")
        );

        return Optional.of(new AnnotatedChildEntity<>(
                childEntityModel,
                (Boolean) attributes.get("forwardCommands"),
                (msg, parent) -> resolveCommandTarget(msg, parent, member, childEntityModel),
                (msg, parent) -> resolveEventTargets(msg, parent, member, eventForwardingMode)
        ));
    }

    /**
     * Check whether the given {@link Field} is of a type supported by this definition.
     *
     * @param field a {@link Field} containing a Child Entity
     * @return true if the type is as required by the implementation and false if it is not
     * @deprecated in favour of {@link #isMemberTypeSupported(Member)}
     */
    @Deprecated
    protected boolean isFieldTypeSupported(Field field) {
        return isMemberTypeSupported(field);
    }

    /**
     * Check whether the given {@link Member} is of a type supported by this definition.
     *
     * @param member a {@link Member} containing or returning a Child Entity
     * @return true if the type is as required by the implementation and false if it is not
     */
    protected abstract boolean isMemberTypeSupported(Member member);

    /**
     * Extracts the Child Entity contained in the given {@code declaringEntity} as an {@link EntityModel}. The type of
     * the Child Entity is defined through a key in the provided {@code attributes} or based on given {@link Field}.
     *
     * @param declaringEntity the {@link EntityModel} declaring the given {@code field}
     * @param attributes      a {@link Map} containing the {@link AggregateMember} attributes
     * @param member          the {@link Member} containing the Child Entity.
     * @param <T>             the type {@code T} of the given {@code declaringEntity} {@link EntityModel}
     * @return the Child Entity contained in the {@code declaringEntity}
     */
    protected abstract <T> EntityModel<Object> extractChildEntityModel(EntityModel<T> declaringEntity,
                                                                       Map<String, Object> attributes,
                                                                       Member member);

    private ForwardingMode<EventMessage<?>> instantiateForwardingMode(Member member,
                                                                      EntityModel<Object> childEntityModel,
                                                                      Class<? extends ForwardingMode<EventMessage<?>>> forwardingModeClass) {
        ForwardingMode<EventMessage<?>> forwardingMode;
        try {
            forwardingMode = forwardingModeClass.getDeclaredConstructor().newInstance();
            forwardingMode.initialize(member, childEntityModel);
        } catch (ReflectiveOperationException e) {
            throw new AxonConfigurationException(
                    String.format("Failed to instantiate ForwardingMode of type [%s].", forwardingModeClass)
            );
        }

        return forwardingMode;
    }

    /**
     * Resolve the target of an incoming {@link CommandMessage} to the right Child Entity. Returns the Child Entity the
     * {@code msg} needs to be routed to.
     *
     * @param msg              the {@link CommandMessage} which is being resolved to a target entity
     * @param parent           the {@code parent} Entity of type {@code T} of this Child Entity
     * @param member           the {@link Member} containing the Child Entity.
     * @param childEntityModel the {@link EntityModel} for the Child Entity
     * @param <T>              the type {@code T} of the given {@code parent} Entity
     * @return the Child Entity which is the target of the incoming {@link CommandMessage}.
     */
    protected abstract <T> Object resolveCommandTarget(CommandMessage<?> msg,
                                                       T parent,
                                                       Member member,
                                                       EntityModel<Object> childEntityModel);

    /**
     * Retrieves the routing keys of every command handler on the given {@code childEntityModel} to be able to correctly
     * route commands to Entities.
     *
     * @param member           a {@link Member} denoting the Child Entity upon which the {@code childEntityModel} is
     *                         based
     * @param childEntityModel a {@link EntityModel} to retrieve the routing key properties from
     * @return a {@link Map} of key/value types {@link String} {@link Property} from Command Message name to routing key
     */
    protected Map<String, Property<Object>> extractCommandHandlerRoutingKeys(Member member,
                                                                             EntityModel<Object> childEntityModel) {
        return childEntityModel.commandHandlers()
                               .stream()
                               .map(commandHandler -> commandHandler.unwrap(CommandMessageHandlingMember.class)
                                                                    .orElse(null))
                               .filter(Objects::nonNull)
                               .collect(Collectors.toMap(
                                       CommandMessageHandlingMember::commandName,
                                       commandHandler -> extractCommandHandlerRoutingKey(childEntityModel,
                                                                                         commandHandler,
                                                                                         member
                                       )
                               ));
    }


    private Property<Object> extractCommandHandlerRoutingKey(EntityModel<Object> childEntityModel,
                                                             CommandMessageHandlingMember<?> commandHandler,
                                                             Member member) {
        Class<?> commandPayloadType = commandHandler.payloadType();

        String routingKey = getOrDefault(commandHandler.routingKey(), childEntityModel.routingKey());
        if (routingKey == null) {
            throw new AxonConfigurationException(format(
                    "Unable to route command of type [%s] since no routing key was defined. "
                            + "Either specify an entity id on the Aggregate Member [%s] "
                            + "or define a routing key on the command handler annotation",
                    commandPayloadType,
                    childEntityModel.entityClass()
            ));
        }

        Property<Object> property = getProperty(commandPayloadType, routingKey);
        if (property == null) {
            throw new AxonConfigurationException(format(
                    "Command of type [%s] doesn't have a property matching the routing key [%s] necessary to route through member [%s]",
                    commandPayloadType,
                    routingKey,
                    ReflectionUtils.getMemberGenericString(member))
            );
        }

        return property;
    }

    /**
     * Resolve the targets of an incoming {@link EventMessage} to the right Child Entities. Returns a {@link Stream} of
     * all the Child Entities the Event Message should be routed to.
     *
     * @param message             the {@link EventMessage} to route
     * @param parentEntity        the {@code parent} Entity of type {@code T} of this Child Entity
     * @param member              the {@link Member} containing the Child Entity
     * @param eventForwardingMode the {@link ForwardingMode} used to filter the {@code message} to route based on the
     *                            ForwardingMode implementation
     * @param <T>                 the type {@code T} of the given {@code parent} Entity
     * @return a filtered {@link Stream} of Child Entity targets for the incoming {@link EventMessage}
     */
    protected abstract <T> Stream<Object> resolveEventTargets(EventMessage message,
                                                              T parentEntity,
                                                              Member member,
                                                              ForwardingMode eventForwardingMode);
}

