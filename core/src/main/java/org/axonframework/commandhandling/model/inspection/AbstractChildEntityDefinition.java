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
import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.commandhandling.model.ForwardingMode;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.property.Property;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Abstract implementation of the {@link org.axonframework.commandhandling.model.inspection.ChildEntityDefinition} to
 * provide reusable functionality for collections of ChildEntityDefinitions.
 */
public abstract class AbstractChildEntityDefinition implements ChildEntityDefinition {

    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        Map<String, Object> attributes = findAnnotationAttributes(field, AggregateMember.class).orElse(null);
        if (attributes == null || fieldIsOfType(field)) {
            return Optional.empty();
        }
        EntityModel<Object> childEntityModel = declaringEntity.modelOf(resolveType(attributes, field));

        Boolean forwardEvents = (Boolean) attributes.get("forwardEvents");
        ForwardingMode eventForwardingMode = (ForwardingMode) attributes.get("eventForwardingMode");
        Map<String, Property<Object>> commandHandlerRoutingKeys =
                extractCommandHandlerRoutingKeys(field, childEntityModel);

        return Optional.of(new AnnotatedChildEntity<>(
                childEntityModel,
                (Boolean) attributes.get("forwardCommands"),
                eventForwardingMode(forwardEvents, eventForwardingMode),
                (String) attributes.get("eventRoutingKey"),
                (msg, parent) -> createCommandTargetResolvers(msg,
                                                              parent,
                                                              commandHandlerRoutingKeys,
                                                              field,
                                                              childEntityModel),
                (msg, parent) -> createEventTargetResolvers(field, parent)
        ));
    }

    /**
     * @param field
     * @return
     */
    protected abstract boolean fieldIsOfType(Field field);

    /**
     * Resolves the type of the Child Entity, either by pulling it from the {@link org.axonframework.commandhandling.model.AggregateMember}
     * its attributes, or by resolving it him self through the {@link AbstractChildEntityDefinition#resolveType(Map,
     * Field)} function.
     *
     * @param attributes a {@link java.util.Map} of key/value types {@link java.lang.String}/{@link java.lang.Object}
     *                   containing the attributes of the {@link org.axonframework.commandhandling.model.AggregateMember}
     *                   annotation.
     * @param field      a {@link java.lang.reflect.Field} denoting the Child Entity to resolve the type of.
     * @return the type as a {@link java.lang.Class} of the Child Entity.
     */
    private Class<?> resolveType(Map<String, Object> attributes, Field field) {
        Class<?> entityType = (Class<?>) attributes.get("type");
        if (Void.class.equals(entityType)) {
            entityType = resolveGenericType(field).orElseThrow(() -> new AxonConfigurationException(format(
                    "Unable to resolve entity type of field [%s]. Please provide type explicitly in @AggregateMember annotation.",
                    field.toGenericString()
            )));
        }

        return entityType;
    }

    /**
     * Resolves the generic type of a {@link java.lang.reflect.Field} Child Entity.
     *
     * @param field a {@link java.lang.reflect.Field} denoting the Child Entity to resolve the type of.
     * @return the type as a {@link java.lang.Class} of the given {@code field}.
     */
    protected abstract Optional<Class<?>> resolveGenericType(Field field);

    /**
     * Retrieves the routing keys of every command handler on the given {@code childEntityModel} to be able to correctly
     * route commands to Entities.
     *
     * @param field            a {@link java.lang.reflect.Field} denoting the Child Entity upon which the {@code
     *                         childEntityModel} is based.
     * @param childEntityModel a {@link org.axonframework.commandhandling.model.inspection.EntityModel} to retrieve the
     *                         routing key properties from.
     * @return a {@link java.util.Map} of key/value types {@link java.lang.String}/{@link
     * org.axonframework.common.property.Property} from Command Message name to routing key.
     */
    private Map<String, Property<Object>> extractCommandHandlerRoutingKeys(Field field,
                                                                           EntityModel<Object> childEntityModel) {
        return childEntityModel.commandHandlers()
                               .values()
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
     * Determine the event forwarding mode of this Child Entity, based on the deprecated {@code forwardEvents} field
     * and
     * the {@code eventForwardingMode} field. Returns {@code ForwardingMode.NONE} if {@code forwardEvents} is {@code
     * false} , and the supplied {@code eventForwardingMode} if {@code forwardEvents} is {@code true}.
     * <p>
     * As long as the {@link org.axonframework.commandhandling.model.AggregateMember} still services the {@code
     * forwardEvents} field, this function needs to exists.
     *
     * @param forwardEvents       a {@link java.lang.Boolean} flag to forward events yes/no.
     * @param eventForwardingMode a {@link org.axonframework.commandhandling.model.ForwardingMode} describing the
     *                            desired forwarding modes of events for this Child Entity.
     * @return {@code ForwardingMode.NONE} if {@code forwardEvents} is {@code false}, and the given {@code
     * eventForwardingMode} if {@code forwardEvents} is {@code true}.
     */
    private ForwardingMode eventForwardingMode(Boolean forwardEvents, ForwardingMode eventForwardingMode) {
        return !forwardEvents ? ForwardingMode.NONE : eventForwardingMode;
    }

    /**
     * @param msg
     * @param parent
     * @param commandHandlerRoutingKeys
     * @param field
     * @param childEntityModel
     * @param <T>
     * @return
     */
    protected abstract <T> Object createCommandTargetResolvers(CommandMessage<?> msg,
                                                               T parent,
                                                               Map<String, Property<Object>> commandHandlerRoutingKeys,
                                                               Field field,
                                                               EntityModel<Object> childEntityModel);

    /**
     * @param field
     * @param parent
     * @param <T>
     * @return
     */
    protected abstract <T> Iterable<Object> createEventTargetResolvers(Field field, T parent);
}

