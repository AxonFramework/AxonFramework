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
import org.axonframework.common.property.Property;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.modelling.entity.child.ChildEntityMatcher;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * {@link ChildEntityMatcher} implementation that matches the routing key of a message to the routing key of the child
 * entity instance.
 * <p>
 * Note: This class was known as {code org.axonframework.modelling.command.ForwardMatchingInstances} before version
 * 5.0.0.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 3.1
 */
class RoutingKeyChildEntityMatcher<E> implements ChildEntityMatcher<E, Message<?>> {

    private final Map<MessageType, Property<Object>> messageRoutingKeyProperties = new ConcurrentHashMap<>();
    private final Map<Class<?>, Property<Object>> entityRoutingKeyProperties = new ConcurrentHashMap<>();

    private final String entityRoutingProperty;
    private final String messageRoutingKey;
    private final AnnotatedEntityModel<E> entity;

    public RoutingKeyChildEntityMatcher(AnnotatedEntityModel<E> entity,
                                        String entityRoutingProperty,
                                        String messageRoutingProperty) {
        this.entity = entity;
        this.messageRoutingKey = messageRoutingProperty;
        this.entityRoutingProperty = entityRoutingProperty;
    }


    @Override
    public boolean matches(@Nonnull Message<?> message, @Nonnull E candidate) {
        var payloadType = entity.getExpectedRepresentation(message.type().qualifiedName());
        if(payloadType == null) {
            return false;
        }
        Property<Object> routingProperty = messageRoutingKeyProperties.computeIfAbsent(
                message.type(), unused -> resolveProperty(payloadType)
        );
        if (routingProperty == null) {
            return false;
        }

        Object routingValue = routingProperty.getValue(message.getPayload());
        return matchesInstance(candidate, routingValue);
    }

    private Property<Object> resolveProperty(Class<?> runtimeType) {
        return getProperty(runtimeType, messageRoutingKey);
    }

    private boolean matchesInstance(E candidate, Object routingValue) {
        Object identifier = entityRoutingKeyProperties.computeIfAbsent(
                candidate.getClass(), c -> getProperty(entity.entityType(), entityRoutingProperty)
        ).getValue(candidate);

        return Objects.equals(routingValue, identifier);
    }
}
