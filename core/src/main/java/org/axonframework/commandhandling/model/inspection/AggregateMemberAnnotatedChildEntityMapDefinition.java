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

import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.property.Property;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.axonframework.common.CollectionUtils.firstNonNull;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

public class AggregateMemberAnnotatedChildEntityMapDefinition implements ChildEntityDefinition {

/*
    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        // TODO: Find out if the declared type of field is an Axon Entity
        AggregateMember annotation = ReflectionUtils.findAnnotation(field, AggregateMember.class);
        if (annotation == null || !Map.class.isAssignableFrom(field.getType())) {
            return Optional.empty();
        }

        // TODO: Resolve map value type from generics or annotation declaration
        EntityModel entityModel = declaringEntity.modelOf(resolve type);
        String parentRoutingKey = declaringEntity.routingKey();
        //noinspection unchecked
        return Optional.of(new AnnotatedChildEntity<>(parentRoutingKey, field, entityModel,
                                                      annotation.forwardCommands(), annotation.forwardEvents(),
                                                      (msg, parent) -> ReflectionUtils.getFieldValue(field, parent)));
    }
*/

    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        Map<String, Object> attributes = AnnotationUtils.findAnnotationAttributes(field, AggregateMember.class).orElse(null);
        if (attributes == null || !Map.class.isAssignableFrom(field.getType())) {
            return Optional.empty();
        }

        EntityModel<Object> childEntityModel = declaringEntity.modelOf(resolveType(attributes, field));
        String parentRoutingKey = declaringEntity.routingKey();
        Map<String, Property<Object>> routingKeyProperties =
                childEntityModel.commandHandlers().values().stream()
                        .collect(Collectors.toConcurrentMap(
                                CommandMessageHandler::commandName,
                                h -> {
                                    String routingKey = firstNonNull(h.routingKey(),
                                                                     childEntityModel.routingKey());
                                    Property<Object> property = getProperty(h.payloadType(),
                                                                            routingKey);
                                    if (property == null) {
                                        throw new AxonConfigurationException(format("Command of type [%s] doesn't have a property matching the routing key [%s] necessary to route through field [%s]",
                                                                              h.payloadType(), routingKey, field.toGenericString()));
                                    }
                                    return property;
                                }));
        //noinspection unchecked
        return Optional.of((ChildEntity<T>) new AnnotatedChildEntity<>(
                parentRoutingKey, field, childEntityModel,
                (Boolean) attributes.get("forwardCommands"),
                (Boolean) attributes.get("forwardEvents"),
                (msg, parent) -> {
                    Object routingValue = routingKeyProperties.get(msg.getCommandName()).getValue(msg.getPayload());
                    Map<?, ?> fieldValue = (Map<?, ?>) ReflectionUtils.getFieldValue(field, parent);
                    return fieldValue.get(routingValue);
                }));

    }

    private static Class<?> resolveType(Map<String, Object> attributes, Field field) {
        Class<?> entityType = (Class<?>) attributes.get("type");
        if (Void.class.equals(entityType)) {
            entityType = ReflectionUtils.resolveGenericType(field, 1)
                    .orElseThrow(
                            () -> new AxonConfigurationException(
                                    format("Unable to resolve entity type of field [%s]. " +
                                                   "Please provide type explicitly in @AggregateMember annotation.",
                                           field.toGenericString())));
        }

        return entityType;
    }
}
