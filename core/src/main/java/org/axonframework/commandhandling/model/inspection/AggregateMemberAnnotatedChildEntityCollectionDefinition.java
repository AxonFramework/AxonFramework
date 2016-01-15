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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static org.axonframework.common.CollectionUtils.firstNonNull;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

public class AggregateMemberAnnotatedChildEntityCollectionDefinition implements ChildEntityDefinition {

    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        Map<String, Object> attributes = AnnotationUtils.findAnnotationAttributes(field, AggregateMember.class).orElse(null);
        if (attributes == null || !Iterable.class.isAssignableFrom(field.getType())) {
            return Optional.empty();
        }

        EntityModel<Object> childEntityModel = declaringEntity.modelOf(resolveType(attributes, field));
        String parentRoutingKey = declaringEntity.routingKey();
        Map<String, Property<Object>> routingKeyProperties =
                childEntityModel.commandHandlers().values().stream()
                        .collect(Collectors.toConcurrentMap(
                                CommandMessageHandler::commandName,
                                h -> getProperty(h.payloadType(),
                                                 firstNonNull(h.routingKey(), childEntityModel.routingKey()))));
        //noinspection unchecked
        return Optional.of((ChildEntity<T>) new AnnotatedChildEntity<>(
                parentRoutingKey, field, childEntityModel,
                (Boolean) attributes.get("forwardCommands"),
                (Boolean) attributes.get("forwardEvents"),
                (msg, parent) -> {
                    Object routingValue = routingKeyProperties.get(msg.getCommandName()).getValue(msg.getPayload());
                    Iterable<?> iterable = (Iterable) ReflectionUtils.getFieldValue(field, parent);
                    return StreamSupport.stream(iterable.spliterator(), false)
                            .filter(i -> Objects.equals(routingValue, childEntityModel.getIdentifier(i)))
                            .findFirst()
                            .orElse(null);
                }));
    }

    private static Class<?> resolveType(Map<String, Object> attributes, Field field) {
        Class<?> entityType = (Class<?>) attributes.get("type");
        if (Void.class.equals(entityType)) {
            entityType = ReflectionUtils.resolveGenericType(field, 0)
                    .orElseThrow(
                            () -> new AxonConfigurationException(
                                    format("Unable to resolve entity type of field [%s]. " +
                                                   "Please provide type explicitly in @AggregateMember annotation.",
                                           field.toGenericString())));
        }

        return entityType;
    }

}
