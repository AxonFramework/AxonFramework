/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.annotation.RoutingKey;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.child.ChildEntityMatcher;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.getMemberValueType;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

public class RoutingKeyChildEntityMatcherDefinition implements ChildEntityMatcherDefinition {

    @Override
    public <E> ChildEntityMatcher<E, Message<?>> createChildEntityMatcher(@Nonnull AnnotatedEntityModel<E> entity,
                                                                          @Nonnull Member member) {
        Optional<String> messageRoutingField = getRoutingKeyForMember((AnnotatedElement) member, entity.entityType());
        Optional<String> entityRoutingField = getRoutingKeyPropertyForEntityType(entity.entityType());
        if(messageRoutingField.isPresent() && entityRoutingField.isPresent()) {
            return new RoutingKeyChildEntityMatcher<>(entity, entityRoutingField.get(), messageRoutingField.get());
        }
        if(entityRoutingField.isPresent()) {
            return new RoutingKeyChildEntityMatcher<>(entity, entityRoutingField.get(), entityRoutingField.get());
        }
        // Only a message routing key (or none at all), is not enough to create a matcher.
        Class<?> memberValueType = getMemberValueType(member);
        if (memberValueType.isAssignableFrom(Iterable.class)) {
            throw new AxonConfigurationException(
                    format("Member [%s] of type [%s] is a collection type, but the child does not define a @RoutingKey. "
                                   + "Please implement a custom child entity matcher for this collection type or add a @RoutingKey.",
                           member,
                           memberValueType));
        }
        // If the member is not a collection type, we can assume it is a single entity.
        // This does not require an explicit @RoutingKey, as there might only be one in the aggregate.
        // If the user has multiple single-entity child entities in the same parent, commands will lead to an ChildAmbiguityException, which is clear enough.
        // That said, if in the user we want to be explicit about this, this method would need a list with already discovered child entitiy types, and check if it's not in there.
        return ((message, candidate) -> true);
    }


    protected Optional<String> getRoutingKeyForMember(AnnotatedElement member, Class<?> childEntityClass) {
        Optional<Map<String, Object>> attributes = findAnnotationAttributes(member, EntityMember.class);
        if (attributes.isEmpty()) {
            return Optional.empty();
        }
        String routingKeyProperty = (String) attributes.get().get("routingKey");
        if (!routingKeyProperty.isEmpty()) {
            return Optional.of(routingKeyProperty);
        }
        return Optional.empty();
    }

    protected Optional<String> getRoutingKeyPropertyForEntityType(Class<?> childEntityClass) {
        return Arrays.stream(childEntityClass.getDeclaredFields())
                     .filter(field -> field.isAnnotationPresent(RoutingKey.class))
                     .findFirst()
                     .map(Field::getName);
    }
}
