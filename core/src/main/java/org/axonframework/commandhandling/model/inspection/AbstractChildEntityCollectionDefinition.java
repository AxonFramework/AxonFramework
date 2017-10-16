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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.property.Property;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

public abstract class AbstractChildEntityCollectionDefinition implements ChildEntityDefinition {

    protected Class<?> resolveType(Map<String, Object> attributes, Field field) {
        Class<?> entityType = (Class<?>) attributes.get("type");
        if (Void.class.equals(entityType)) {
            entityType = resolveGenericType(field, 0).orElseThrow(() -> new AxonConfigurationException(format(
                    "Unable to resolve entity type of field [%s]. Please provide type explicitly in @AggregateMember annotation.",
                    field.toGenericString()
            )));
        }

        return entityType;
    }

    /**
     * @param field
     * @param index
     * @return
     */
    protected abstract Optional<Class<?>> resolveGenericType(Field field, Integer index);

    /**
     * @param field
     * @param childEntityModel
     * @return
     */
    protected Map<String, Property<Object>> getRoutingKeyProperties(Field field, EntityModel<Object> childEntityModel) {
        return childEntityModel.commandHandlers()
                               .values()
                               .stream()
                               .map(commandHandler -> commandHandler.unwrap(CommandMessageHandlingMember.class)
                                                                    .orElse(null))
                               .filter(Objects::nonNull)
                               .collect(Collectors.toMap(
                                       CommandMessageHandlingMember::commandName,
                                       commandHandler -> getRoutingKeyProperty(field, childEntityModel, commandHandler)
                               ));
    }

    @SuppressWarnings("unchecked")
    private Property<Object> getRoutingKeyProperty(Field field,
                                                   EntityModel<Object> childEntityModel,
                                                   CommandMessageHandlingMember commandHandler) {
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
}
