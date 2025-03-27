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
import org.axonframework.common.ReflectionUtils;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Annotation-based {@link CriteriaResolver} implementation which resolves {@link EventCriteria} based on the given
 * {@code id} for loading the entity. This is the default when using the {@link EventSourcedEntity} annotation.
 * <p>
 * There are various ways to define how the {@link EventCriteria} should be resolved. In order of precedence:
 * <ol>
 *     <li>
 *         By defining a static method in the entity class annotated with {@link EventCriteriaBuilder} which returns an
 *         {@link EventCriteria} and accepts the {@code id} as a parameter. This method should be static and return an
 *         {@link EventCriteria}. Multiple methods can be defined with different id types, and the first matching method
 *         will be used.
 *     </li>
 *     <li>
 *         If no matching {@link EventCriteriaBuilder} is found, the {@link EventSourcedEntity#tagKey()} will be used as the tag key, and the {@link Object#toString()} of the id will be used as value.
 *     </li>
 *     <li>
 *         If the {@link EventSourcedEntity#tagKey()} is empty, the {@link Class#getSimpleName()} of the entity will be used as tag key, and the {@link Object#toString()} of the id will be used as value.
 *     </li>
 * </ol>
 *
 * @author Mitchell Herrijgers
 * @see EventSourcedEntity
 * @since 5.0.0
 */
public class AnnotationBasedEventCriteriaResolver implements CriteriaResolver<Object>, DescribableComponent {

    private final Class<?> entityType;
    private final String tagKey;
    private final Map<Class<?>, Method> builderMap;

    /**
     * Initialize the resolver for the given {@code entityType}. The entity type should be annotated with
     * {@link EventSourcedEntity}, or this resolver will throw an {@link IllegalArgumentException}.
     * <p>
     * Will check for methods annotated with {@link EventCriteriaBuilder} and store them in a map for later use. If one
     * of the methods is invalid as defined in the Javadoc of {@link EventCriteriaBuilder}, an
     * {@link IllegalArgumentException} will be thrown.
     *
     * @param entityType The entity type to resolve criteria for
     */
    public AnnotationBasedEventCriteriaResolver(Class<?> entityType) {
        this.entityType = entityType;


        Map<String, Object> attributes = AnnotationUtils
                .findAnnotationAttributes(entityType, EventSourcedEntity.class)
                .orElseThrow(() -> new IllegalArgumentException("The given class is not an @EventSourcedEntity"));

        String annotationTagKey = (String) attributes.get("tagKey");
        this.tagKey = annotationTagKey.isEmpty() ? null : annotationTagKey;

        Set<Method> eventCriteriaBuilders = Arrays.stream(entityType.getMethods())
                                                  .filter(m -> m.isAnnotationPresent(EventCriteriaBuilder.class))
                                                  .collect(Collectors.toSet());

        // Validate each method meets requirements
        eventCriteriaBuilders.forEach(AnnotationBasedEventCriteriaResolver::validateEventCriteriaBuilderMethod);

        // Check for duplicate parameter types
        eventCriteriaBuilders.stream()
                             .collect(Collectors.groupingBy(m -> m.getParameterTypes()[0]))
                             .values()
                             .stream()
                             .filter(list -> list.size() > 1)
                             .findAny()
                             .ifPresent(list -> {
                                 throw new IllegalArgumentException(
                                         "Multiple @EventCriteriaBuilder methods found with the same parameter type: %s".formatted(
                                                 list.stream()
                                                     .map(ReflectionUtils::toDiscernibleSignature)
                                                     .collect(Collectors.joining(", "))));
                             });

        this.builderMap = eventCriteriaBuilders.stream()
                                .collect(Collectors.toMap(m -> m.getParameterTypes()[0],
                                                          m -> m));
    }

    @Override
    public EventCriteria apply(Object id) {
        Optional<Object> builderResult = builderMap
                .keySet()
                .stream()
                .filter(c -> c.isInstance(id))
                .findFirst()
                .map(builderMap::get)
                .map(m -> {
                    Object result;
                    try {
                        result = m.invoke(null, id);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Error invoking EventCriteriaBuilder method", e);
                    }
                    if (!(result instanceof EventCriteria)) {
                        throw new IllegalArgumentException(
                                "The @EventCriteriaBuilder method returned null. The method must return a non-null EventCriteria. Violating method: %s".formatted(
                                        ReflectionUtils.toDiscernibleSignature(m)));
                    }
                    return result;
                });
        if (builderResult.isPresent()) {
            return (EventCriteria) builderResult.get();
        }
        String key = Objects.requireNonNullElseGet(tagKey, entityType::getSimpleName);
        return EventCriteria.match()
                            .eventsOfAnyType()
                            .withTags(Tag.of(key, id.toString()));
    }

    private static void validateEventCriteriaBuilderMethod(Method m) {
        if (!EventCriteria.class.isAssignableFrom(m.getReturnType())) {
            throw new IllegalArgumentException(
                    "Method annotated with @EventCriteriaBuilder must return an EventCriteria. Violating method: %s".formatted(
                            ReflectionUtils.toDiscernibleSignature(m)));
        }
        if (m.getParameterCount() != 1) {
            throw new IllegalArgumentException(
                    "Method annotated with @EventCriteriaBuilder must have exactly one parameter. Violating method: %s".formatted(
                            ReflectionUtils.toDiscernibleSignature(m)));
        }
        if (!Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException(
                    "Method annotated with @EventCriteriaBuilder must be static. Violating method: %s".formatted(
                            ReflectionUtils.toDiscernibleSignature(m)));
        }
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType.getName());
        descriptor.describeProperty("tagKey", tagKey);
        descriptor.describeProperty("criteriaBuilders", builderMap);
    }
}
