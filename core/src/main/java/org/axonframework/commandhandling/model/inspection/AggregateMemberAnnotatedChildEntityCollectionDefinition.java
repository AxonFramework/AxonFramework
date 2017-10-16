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

import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.property.Property;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

/**
 * Implementation of a {@link AbstractChildEntityCollectionDefinition} that is used to detect Collections of entities
 * (field type assignable to {@link Iterable}) annotated with {@link AggregateMember}. If such a field is found a {@link
 * ChildEntity} is created that delegates to the entities in the annotated collection.
 */
public class AggregateMemberAnnotatedChildEntityCollectionDefinition extends AbstractChildEntityCollectionDefinition {

    @Override
    protected Optional<Class<?>> resolveGenericType(Field field, Integer index) {
        return ReflectionUtils.resolveGenericType(field, 0);
    }

    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        Map<String, Object> attributes = findAnnotationAttributes(field, AggregateMember.class).orElse(null);
        if (attributes == null || !Iterable.class.isAssignableFrom(field.getType())) {
            return Optional.empty();
        }
        EntityModel<Object> childEntityModel = declaringEntity.modelOf(resolveType(attributes, field));

        Map<String, Property<Object>> routingKeyProperties = getRoutingKeyProperties(field, childEntityModel);

        return Optional.of(new AnnotatedChildEntity<>(
                childEntityModel,
                (Boolean) attributes.get("forwardCommands"),
                (Boolean) attributes.get("forwardEvents"),
                (Boolean) attributes.get("forwardEntityOriginatingEventsOnly"),
                (msg, parent) -> {
                    Object routingValue = routingKeyProperties.get(msg.getCommandName()).getValue(msg.getPayload());
                    Iterable<?> iterable = ReflectionUtils.getFieldValue(field, parent);
                    return StreamSupport.stream(iterable.spliterator(), false)
                                        .filter(i -> Objects.equals(routingValue, childEntityModel.getIdentifier(i)))
                                        .findFirst()
                                        .orElse(null);
                },
                (msg, parent) -> ReflectionUtils.getFieldValue(field, parent)
        ));
    }
}
