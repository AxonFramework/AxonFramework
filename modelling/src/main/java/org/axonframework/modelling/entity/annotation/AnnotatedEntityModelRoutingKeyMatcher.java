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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Utility class that matches an entity instance to a message based on the routing key of a message and the routing key
 * of the entity. To get the expected representation of the message, it uses the {@link AnnotatedEntityModel} to resolve
 * the expected payload type of the message. It then retrieves the routing key property from the message payload and
 * compares it to the routing key property of the entity instance.
 *
 * @param <E> The type of the entity this matcher is used for.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AnnotatedEntityModelRoutingKeyMatcher<E> {

    private final Map<MessageType, Property<Object>> messageRoutingKeyProperties = new ConcurrentHashMap<>();
    private final Map<Class<?>, Property<Object>> entityRoutingKeyProperties = new ConcurrentHashMap<>();

    private final String entityRoutingProperty;
    private final String messageRoutingKey;
    private final AnnotatedEntityModel<?> entity;

    /**
     *
     * @param entity
     * @param entityRoutingProperty
     * @param messageRoutingProperty
     */
    public AnnotatedEntityModelRoutingKeyMatcher(AnnotatedEntityModel<E> entity,
                                                 String entityRoutingProperty,
                                                 String messageRoutingProperty) {
        this.entity = entity;
        this.messageRoutingKey = messageRoutingProperty;
        this.entityRoutingProperty = entityRoutingProperty;
    }

    protected boolean matches(@Nonnull E entity,
                              @Nonnull Message<?> message
    ) {
        var payloadType = this.entity.getExpectedRepresentation(message.type().qualifiedName());
        if (payloadType == null) {
            // This message is not handled in this entity model, so we cannot match it.
            return false;
        }
        Property<Object> routingProperty = messageRoutingKeyProperties.computeIfAbsent(
                message.type(), unused -> resolveProperty(payloadType)
        );
        if (routingProperty == null) {
            return false;
        }

        Object routingValue = routingProperty.getValue(message.getPayload());
        return matchesInstance(entity, routingValue);
    }

    private Property<Object> resolveProperty(Class<?> runtimeType) {
        return getProperty(runtimeType, messageRoutingKey);
    }

    private boolean matchesInstance(E candidate, Object routingValue) {
        Property<Object> objectProperty = entityRoutingKeyProperties.computeIfAbsent(
                candidate.getClass(), c -> getProperty(entity.entityType(), entityRoutingProperty)
        );
        if (objectProperty == null) {
            throw new IllegalStateException(String.format(
                    "No value found for routing key property [%s] found in entity type [%s]",
                    entityRoutingProperty,
                    candidate.getClass()));
        }
        Object identifier = objectProperty.getValue(candidate);

        return Objects.equals(routingValue, identifier);
    }
}
