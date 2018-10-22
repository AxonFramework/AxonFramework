/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.modelling.command.inspection.EntityModel;
import org.axonframework.common.property.Property;
import org.axonframework.messaging.Message;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.stream.Stream;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Only forward messages of type {@code T} if the routing key of the message matches that of the entity. Essentially,
 * this means that events are only forwarded if the Message mentions the identifier of the entity instance.
 *
 * @param <T> the implementation {@code T} of the {@link org.axonframework.messaging.Message} being filtered.
 */
public class ForwardMatchingInstances<T extends Message<?>> implements ForwardingMode<T> {

    private static final String EMPTY_STRING = "";

    private String routingKey;
    private EntityModel childEntity;

    @Override
    public void initialize(Field field, EntityModel childEntity) {
        this.childEntity = childEntity;
        this.routingKey = findAnnotationAttributes(field, AggregateMember.class)
                .map(map -> (String) map.get("routingKey"))
                .filter(key -> !Objects.equals(key, EMPTY_STRING))
                .orElse(childEntity.routingKey());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> Stream<E> filterCandidates(T message, Stream<E> candidates) {
        Property routingProperty = getProperty(message.getPayloadType(), routingKey);
        if (routingProperty == null) {
            return Stream.empty();
        }

        Object routingValue = routingProperty.getValue(message.getPayload());
        return candidates.filter(candidate -> matchesInstance(candidate, routingValue));
    }

    @SuppressWarnings("unchecked")
    private <E> boolean matchesInstance(E candidate, Object routingValue) {
        Object identifier = childEntity.getIdentifier(candidate);

        return Objects.equals(routingValue, identifier);
    }
}
