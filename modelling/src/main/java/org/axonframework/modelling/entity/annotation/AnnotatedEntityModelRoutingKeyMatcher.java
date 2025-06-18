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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.property.Property;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Utility class that matches an entity instance to a message based on the routing key of a message and the routing key
 * of the entity. The expected payload type of the message is requested from the {@link AnnotatedEntityModel} to be able
 * to resolve the payload and extract the requested properties. Once extracted, both routing keys are then compared for
 * a match.
 *
 * @param <E> The type of the entity this matcher is used for.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class AnnotatedEntityModelRoutingKeyMatcher<E> {

    private final Map<MessageType, Property<Object>> messageRoutingPropertyCache;
    private final Map<Class<?>, Property<Object>> entityRoutingPropertyCache;

    private final String entityRoutingProperty;
    private final String messageRoutingProperty;
    private final AnnotatedEntityModel<E> entityModel;

    /**
     * Constructs an {@code AnnotatedEntityModelRoutingKeyMatcher} that matches the routing key of the given
     * {@code entity} against the routing key of a message. The routing key of the entity is determined by the
     * {@code entityRoutingProperty} and the routing key of the message is determined by the
     * {@code messageRoutingProperty}.
     *
     * @param model                  The {@link AnnotatedEntityModel} of the entity to match against.
     * @param entityRoutingProperty  The routing key property of the entity, which is used to match against the message
     *                               routing key.
     * @param messageRoutingProperty The routing key property of the message, which is used to match against the entity
     *                               routing key.
     */
    public AnnotatedEntityModelRoutingKeyMatcher(@Nonnull AnnotatedEntityModel<E> model,
                                                 @Nonnull String entityRoutingProperty,
                                                 @Nonnull String messageRoutingProperty) {
        this.entityModel = Objects.requireNonNull(model, "The model may not be null.");
        this.entityRoutingProperty = Objects.requireNonNull(entityRoutingProperty,
                                                            "The entityRoutingProperty may not be null.");
        this.messageRoutingProperty = Objects.requireNonNull(messageRoutingProperty,
                                                             "The messageRoutingProperty may not be null.");
        this.messageRoutingPropertyCache = new ConcurrentHashMap<>();
        this.entityRoutingPropertyCache = new ConcurrentHashMap<>();
    }

    /**
     * Matches the given entity against the provided message based on the routing keys of both. The routing key of the
     * message is extracted from the expected payload type of the message, and compared to the routing key of the
     * entity.
     *
     * @param entity  The entity to match against.
     * @param message The message to match against.
     * @return {@code true} if the routing keys match, {@code false} otherwise.
     */
    public boolean matches(@Nonnull E entity, @Nonnull Message<?> message) {
        Class<?> payloadType = entityModel.getExpectedRepresentation(message.type().qualifiedName());
        if (payloadType == null) {
            // This message is not handled in this entity model, so we cannot match it.
            return false;
        }
        Property<Object> routingProperty = messageRoutingPropertyCache.computeIfAbsent(
                message.type(), unused -> resolveProperty(payloadType)
        );
        if (routingProperty == null) {
            throw new UnknownRoutingKeyException(format(
                    "Message of type [%s] doesn't have a property matching the routing key [%s] necessary to route to child entity of type [%s]",
                    message.type(),
                    messageRoutingProperty,
                    entityModel.entityType()));
        }

        Object routingValue = routingProperty.getValue(message.getPayload());
        return matchesInstance(entity, routingValue);
    }

    private Property<Object> resolveProperty(Class<?> runtimeType) {
        return getProperty(runtimeType, messageRoutingProperty);
    }

    private boolean matchesInstance(E candidate, Object routingValue) {
        Property<Object> objectProperty = entityRoutingPropertyCache.computeIfAbsent(
                candidate.getClass(), c -> getProperty(entityModel.entityType(), entityRoutingProperty)
        );
        if (objectProperty == null) {
            throw new IllegalStateException(format(
                    "No value found for routing key property [%s] found in entity type [%s]",
                    entityRoutingProperty,
                    candidate.getClass()));
        }
        Object identifier = objectProperty.getValue(candidate);

        return Objects.equals(routingValue, identifier);
    }
}
