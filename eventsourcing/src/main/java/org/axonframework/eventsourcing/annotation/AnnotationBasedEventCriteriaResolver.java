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

package org.axonframework.eventsourcing.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.Tag;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AnnotationBasedEventCriteriaResolver<ID, T> implements CriteriaResolver<ID>, DescribableComponent {

    private final Class<T> entityType;
    private final String tagName;
    private final Map<Class<?>, Method> builderMap;

    public AnnotationBasedEventCriteriaResolver(Class<T> entityType) {
        this.entityType = entityType;


        Map<String, Object> attributes = AnnotationUtils.findAnnotationAttributes(entityType, EventSourcingEntity.class)
                                                        .orElseThrow(() -> new IllegalArgumentException(
                                                                "The given class it not an @EventSourcingEntity"));

        String annotationTagName = (String) attributes.get("tagName");
        this.tagName = annotationTagName.isEmpty() ? null : annotationTagName;
        this.builderMap = Arrays.stream(entityType.getMethods())
                                .filter(m -> m.isAnnotationPresent(EventCriteriaBuilder.class))
                                .collect(Collectors.toMap(m -> m.getParameterTypes()[0],
                                                          m -> m));

        // Let's do some checks so we don't fail at runtime.
        this.builderMap.values().forEach(AnnotationBasedEventCriteriaResolver::validateEventCriteriaBuilderMethod);
    }

    private static void validateEventCriteriaBuilderMethod(Method m) {
        if (!EventCriteria.class.isAssignableFrom(m.getReturnType())) {
            throw new IllegalArgumentException(
                    "Method annotated with @EventCriteriaBuilder must return an EventCriteria. Violating method: %s".formatted(
                            m));
        }
        if (m.getParameterCount() != 1) {
            throw new IllegalArgumentException(
                    "Method annotated with @EventCriteriaBuilder must have exactly one parameter. Violating method: %s".formatted(
                            m));
        }
        if (!Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException(
                    "Method annotated with @EventCriteriaBuilder must be static. Violating method: %s".formatted(m));
        }
    }

    @Override
    public EventCriteria apply(ID id) {
        Optional<Object> builderResult = builderMap
                .keySet()
                .stream()
                .filter(c -> c.isInstance(id))
                .findFirst()
                .map(builderMap::get)
                .map(m -> {
                    // TODO: Do we want to use parameter resolvers here?
                    try {
                        return m.invoke(null, id);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Error invoking EventCriteriaBuilder method", e);
                    }
                });
        if (builderResult.isPresent()) {
            if (!(builderResult.get() instanceof EventCriteria)) {
                throw new IllegalArgumentException(
                        "Method annotated with @EventCriteriaBuilder must return an EventCriteria");
            }
            return (EventCriteria) builderResult.get();
        }
        String key = Objects.requireNonNullElseGet(tagName, entityType::getSimpleName);
        return EventCriteria.match()
                            .eventsOfAnyType()
                            .withTags(Tag.of(key, id.toString()));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType.getName());
        descriptor.describeProperty("tagName", tagName);
        descriptor.describeProperty("criteriaBuilders", builderMap);
    }
}
