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
import org.axonframework.messaging.commandhandling.annotation.RoutingKey;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.modelling.entity.child.CommandTargetResolver;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Optional;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.getMemberValueType;

/**
 * Definition for creating {@link CommandTargetResolver} instances based on the routing key definitions.
 * <p>
 * The routing key of the message is determined by the {@link EntityMember#routingKey} annotation on the declaring
 * member in the parent entity, or the {@link RoutingKey} annotation on the child entity's member if absent. The routing
 * key of the child entity is determined by the {@link RoutingKey} on a member of the child entity class.
 * <p>
 * The routing key of the message and of the entity are then matched to determine if a child entity should handle a
 * given message.
 *
 * @author Mitchell Herrijgers
 * @see RoutingKeyEventTargetMatcher
 * @since 5.0.0
 */
public class RoutingKeyCommandTargetResolverDefinition implements CommandTargetResolverDefinition {


    @Nonnull
    @Override
    public <E> CommandTargetResolver<E> createCommandTargetResolver(
            @Nonnull AnnotatedEntityMetamodel<E> metamodel,
            @Nonnull Member member) {
        Optional<String> messageRoutingField = RoutingKeyUtils.getMessageRoutingKey((AnnotatedElement) member);
        Optional<String> entityRoutingField = RoutingKeyUtils.getEntityRoutingKey(metamodel.entityType());
        if (messageRoutingField.isPresent() && entityRoutingField.isPresent()) {
            return new RoutingKeyCommandTargetResolver<>(metamodel,
                                                         entityRoutingField.get(),
                                                         messageRoutingField.get());
        }
        if (entityRoutingField.isPresent()) {
            // Default the message routing key to the entity routing key if it is not explicitly defined.
            return new RoutingKeyCommandTargetResolver<>(metamodel, entityRoutingField.get(), entityRoutingField.get());
        }
        // Only a message routing key (or none at all) is not enough to create a matcher.
        Class<?> memberValueType = getMemberValueType(member);
        if (Iterable.class.isAssignableFrom(memberValueType)) {
            throw new AxonConfigurationException(
                    format("Member [%s] of type [%s] is a collection type, but the child does not define a @RoutingKey. "
                                   + "Please implement a custom CommandTargetResolver for this collection type or add @RoutingKey to a field or method to identify the child entity correctly.",
                           member,
                           memberValueType));
        }
        // If the member is not a collection type, we can assume it is a single entity.
        // This does not require an explicit @RoutingKey, as there might only be one in the aggregate.
        // If the user has multiple single-entity child entities in the same parent, commands will lead to a ChildAmbiguityException, which is clear enough.
        return CommandTargetResolver.MATCH_ANY();
    }
}
