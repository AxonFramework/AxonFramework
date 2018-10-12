/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.ForwardingMode;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.property.Property;
import org.axonframework.eventhandling.EventMessage;

import java.lang.reflect.Field;
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
 * Abstract implementation of the {@link ChildEntityDefinition} to
 * provide reusable functionality for collections of ChildEntityDefinitions.
 */
public abstract class AbstractChildEntityDefinition implements ChildEntityDefinition {

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        Map<String, Object> attributes = findAnnotationAttributes(field, AggregateMember.class).orElse(null);
        if (attributes == null || !isFieldTypeSupported(field)) {
            return Optional.empty();
        }

        EntityModel<Object> childEntityModel = extractChildEntityModel(declaringEntity, attributes, field);

        ForwardingMode eventForwardingMode = instantiateForwardingMode(
                field, childEntityModel, (Class<? extends ForwardingMode>) attributes.get("eventForwardingMode")
        );

        return Optional.of(new AnnotatedChildEntity<>(
                childEntityModel,
                (Boolean) attributes.get("forwardCommands"),
                (msg, parent) -> resolveCommandTarget(msg, parent, field, childEntityModel),
                (msg, parent) -> resolveEventTargets(msg, parent, field, eventForwardingMode)
        ));
    }

    /**
     * Check whether the given {@link java.lang.reflect.Field} is of a type supported by this definition.
     *
     * @param field A {@link java.lang.reflect.Field} containing a Child Entity.
     * @return true if the type is as required by the implementation and false if it is not.
     */
    protected abstract boolean isFieldTypeSupported(Field field);

    /**
     * Extracts the Child Entity contained in the given {@code declaringEntity} as an
     * {@link EntityModel}. The type of the Child Entity is defined
     * through a key in the provided {@code attributes} or based on given {@link java.lang.reflect.Field}.
     *
     * @param declaringEntity The {@link EntityModel} declaring the
     *                        given {@code field}.
     * @param attributes      A {@link java.util.Map} containing the
     *                        {@link AggregateMember} attributes.
     * @param field           The {@link java.lang.reflect.Field} containing the Child Entity.
     * @param <T>             The type {@code T} of the given {@code declaringEntity}
     *                        {@link EntityModel}.
     * @return the Child Entity contained in the {@code declaringEntity}.
     */
    protected abstract <T> EntityModel<Object> extractChildEntityModel(EntityModel<T> declaringEntity,
                                                                       Map<String, Object> attributes,
                                                                       Field field);

    private ForwardingMode instantiateForwardingMode(Field field,
                                                     EntityModel<Object> childEntityModel,
                                                     Class<? extends ForwardingMode> forwardingModeClass) {
        ForwardingMode forwardingMode;
        try {
            forwardingMode = forwardingModeClass.newInstance();
            forwardingMode.initialize(field, childEntityModel);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new AxonConfigurationException(
                    String.format("Failed to instantiate ForwardingMode of type [%s].", forwardingModeClass)
            );
        }

        return forwardingMode;
    }

    /**
     * Resolve the target of an incoming {@link org.axonframework.commandhandling.CommandMessage} to the right Child
     * Entity. Returns the Child Entity the {@code msg} needs to be routed to.
     *
     * @param msg              The {@link org.axonframework.commandhandling.CommandMessage} which is being resolved to a
     *                         target entity.
     * @param parent           The {@code parent} Entity of type {@code T} of this Child Entity.
     * @param field            The {@link java.lang.reflect.Field} containing the Child Entity.
     * @param childEntityModel The {@link EntityModel} for the Child
     *                         Entity.
     * @param <T>              The type {@code T} of the given {@code parent} Entity.
     * @return The Child Entity which is the target of the incoming {@link org.axonframework.commandhandling.CommandMessage}.
     */
    protected abstract <T> Object resolveCommandTarget(CommandMessage<?> msg,
                                                       T parent,
                                                       Field field,
                                                       EntityModel<Object> childEntityModel);

    /**
     * Retrieves the routing keys of every command handler on the given {@code childEntityModel} to be able to correctly
     * route commands to Entities.
     *
     * @param field            a {@link java.lang.reflect.Field} denoting the Child Entity upon which the {@code
     *                         childEntityModel} is based.
     * @param childEntityModel a {@link EntityModel} to retrieve the
     *                         routing key properties from.
     * @return a {@link java.util.Map} of key/value types {@link java.lang.String}/
     * {@link org.axonframework.common.property.Property} from Command Message name to routing key.
     */
    @SuppressWarnings("WeakerAccess")
    protected Map<String, Property<Object>> extractCommandHandlerRoutingKeys(Field field,
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
                                                                                         field
                                       )
                               ));
    }

    @SuppressWarnings("unchecked")
    private Property<Object> extractCommandHandlerRoutingKey(EntityModel<Object> childEntityModel,
                                                             CommandMessageHandlingMember commandHandler,
                                                             Field field) {
        String routingKey = getOrDefault(commandHandler.routingKey(), childEntityModel.routingKey());

        Property<Object> property = getProperty(commandHandler.payloadType(), routingKey);

        if (property == null) {
            throw new AxonConfigurationException(format(
                    "Command of type [%s] doesn't have a property matching the routing key [%s] necessary to route through field [%s]",
                    commandHandler.payloadType(),
                    routingKey,
                    field.toGenericString())
            );
        }
        return property;
    }

    /**
     * Resolve the targets of an incoming {@link org.axonframework.eventhandling.EventMessage} to the right Child
     * Entities. Returns a {@link java.util.stream.Stream} of all the Child Entities the Event Message should be
     * routed to.
     *
     * @param message             The {@link org.axonframework.eventhandling.EventMessage} to route.
     * @param parentEntity        The {@code parent} Entity of type {@code T} of this Child Entity.
     * @param field               The {@link java.lang.reflect.Field} containing the Child Entity.
     * @param eventForwardingMode The {@link ForwardingMode} used to filter the
     *                            {@code message} to route based on the ForwardingMode implementation.
     * @param <T>                 The type {@code T} of the given {@code parent} Entity.
     * @return A filtered {@link java.util.stream.Stream} of Child Entity targets for the incoming
     * {@link org.axonframework.eventhandling.EventMessage}.
     */
    protected abstract <T> Stream<Object> resolveEventTargets(EventMessage message,
                                                              T parentEntity,
                                                              Field field,
                                                              ForwardingMode eventForwardingMode);
}

