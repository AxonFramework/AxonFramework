/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.common.property.Property;
import org.axonframework.messaging.Message;
import org.axonframework.modelling.command.inspection.EntityModel;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Only forward messages of type {@code T} if the routing key of the message matches that of the entity. Essentially,
 * this means that events are only forwarded if the Message mentions the identifier of the entity instance.
 *
 * @param <T> the implementation {@code T} of the {@link org.axonframework.messaging.Message} being filtered.
 * @author Steven van Beelen
 * @since 3.1
 */
public class ForwardMatchingInstances<T extends Message<?>> implements ForwardingMode<T> {

    private static final String EMPTY_STRING = "";
    /**
     * Placeholder value for {@code null} properties, indicating that no property is available
     */
    private static final Property<Object> NO_PROPERTY = new NoProperty();

    private final Map<Class, Property> routingProperties = new ConcurrentHashMap<>();

    private String routingKey;
    private EntityModel childEntity;

    @Override
    public void initialize(@Nonnull Member member, @Nonnull EntityModel childEntity) {
        this.childEntity = childEntity;
        this.routingKey = findAnnotationAttributes((AnnotatedElement) member, AggregateMember.class)
                .map(map -> (String) map.get("routingKey"))
                .filter(key -> !Objects.equals(key, EMPTY_STRING))
                .orElse(childEntity.routingKey());
        routingProperties.clear();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E> Stream<E> filterCandidates(@Nonnull T message, @Nonnull Stream<E> candidates) {
        Property routingProperty = routingProperties.computeIfAbsent(message.getPayloadType(),
                                                                     this::resolveProperty);

        if (routingProperty == null || routingProperty == NO_PROPERTY) {
            return Stream.empty();
        }

        Object routingValue = routingProperty.getValue(message.getPayload());
        return candidates.filter(candidate -> matchesInstance(candidate, routingValue));
    }

    private Property<?> resolveProperty(Class<?> runtimeType) {
        Property<?> property = getProperty(runtimeType, routingKey);
        if (property == null) {
            return NO_PROPERTY;
        }
        return property;
    }

    @SuppressWarnings("unchecked")
    private <E> boolean matchesInstance(E candidate, Object routingValue) {
        Object identifier = childEntity.getIdentifier(candidate);

        return Objects.equals(routingValue, identifier);
    }

    private static class NoProperty implements Property<Object> {

        @Override
        public <V> V getValue(Object target) {
            // this code should never be reached
            throw new UnsupportedOperationException("Property not found on target");
        }
    }
}
