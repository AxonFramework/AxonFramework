/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.common.configuration.Configuration;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 *         will be used. You can also inject components from the {@link Configuration} as parameters to the method.
 *     </li>
 *     <li>
 *         If no matching {@link EventCriteriaBuilder} is found, the {@link EventSourcedEntity#tagKey()} will be used
 *         as the tag key, and the {@link Object#toString()} of the id will be used as value.
 *     </li>
 *     <li>
 *         If the {@link EventSourcedEntity#tagKey()} is empty, the {@link Class#getSimpleName()} of the entity will
 *         be used as tag key, and the {@link Object#toString()} of the id will be used as value.
 *     </li>
 * </ol>
 * <p>
 * Different methods can be combined, as can several {@link EventCriteriaBuilder} methods be defined as long as they
 * are for different id types. This resolver is the default when using the {@link EventSourcedEntity} annotation,
 * but specifying a custom {@link CriteriaResolver} will override this behavior.
 *
 * @param <E>  The type of the entity to create.
 * @param <ID> The type of the identifier of the entity to create.
 * @author Mitchell Herrijgers
 * @see EventSourcedEntity
 * @since 5.0.0
 */
public class AnnotationBasedEventCriteriaResolver<E, ID> implements CriteriaResolver<ID>, DescribableComponent {

    private final Configuration configuration;
    private final Class<ID> idType;
    private final Class<E> entityType;
    private final String tagKey;
    private final Map<Class<?>, WrappedEventCriteriaBuilderMethod> builderMap;

    /**
     * Initialize the resolver for the given {@code entityType}. The entity type should be annotated with
     * {@link EventSourcedEntity}, or this resolver will throw an {@link IllegalArgumentException}.
     * <p>
     * Will check for methods annotated with {@link EventCriteriaBuilder} and store them in a map for later use. If one
     * of the methods is invalid as defined in the Javadoc of {@link EventCriteriaBuilder}, an
     * {@link IllegalArgumentException} will be thrown.
     *
     * @param entityType    The entity type to resolve criteria for.
     * @param idType        The identifier type to resolve criteria for.
     * @param configuration The configuration to use for resolving the criteria.
     */
    public AnnotationBasedEventCriteriaResolver(@Nonnull Class<E> entityType,
                                                @Nonnull Class<ID> idType,
                                                @Nonnull Configuration configuration) {
        this.entityType = Objects.requireNonNull(entityType, "The entity type cannot be null.");
        this.idType = Objects.requireNonNull(idType, "The id type cannot be null.");
        this.configuration = Objects.requireNonNull(configuration, "The configuration cannot be null.");

        Map<String, Object> attributes = AnnotationUtils
                .findAnnotationAttributes(entityType, EventSourcedEntity.class)
                .orElseThrow(() -> new IllegalArgumentException("The given class is not an @EventSourcedEntity"));

        String annotationTagKey = (String) attributes.get("tagKey");
        this.tagKey = annotationTagKey.isEmpty() ? null : annotationTagKey;

        var eventCriteriaBuilders = Arrays
                .stream(entityType.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventCriteriaBuilder.class))
                .map(WrappedEventCriteriaBuilderMethod::new)
                .collect(Collectors.groupingBy(WrappedEventCriteriaBuilderMethod::getIdentifierType));

        eventCriteriaBuilders.entrySet().stream()
                             .filter(entry -> entry.getValue().size() > 1)
                             .findAny()
                             .ifPresent(list -> {
                                 throw new IllegalArgumentException(
                                         "Multiple @EventCriteriaBuilder methods found with the same parameter type: %s".formatted(
                                                 list.getValue()
                                                     .stream()
                                                     .map(wv -> ReflectionUtils.toDiscernibleSignature(wv.getMethod()))
                                                     .sorted()
                                                     .collect(Collectors.joining(", "))));
                             });

        this.builderMap = eventCriteriaBuilders.entrySet().stream()
                                               .collect(Collectors.toMap(Map.Entry::getKey,
                                                                         m -> m.getValue().getFirst()));
    }

    /**
     * Wraps a {@code method} that is annotated with {@link EventCriteriaBuilder} and returns an {@link EventCriteria}.
     * Validates that the method is valid, and retrieves the components from the configuration that are requested.
     */
    private class WrappedEventCriteriaBuilderMethod {

        private final Method method;
        private final Class<?> identifierType;
        private final Object[] optionalArgumentSuppliers;

        private WrappedEventCriteriaBuilderMethod(Method method) {
            if (!EventCriteria.class.isAssignableFrom(method.getReturnType())) {
                throw new IllegalArgumentException(
                        "Method annotated with @EventCriteriaBuilder must return an EventCriteria. Violating method: %s".formatted(
                                ReflectionUtils.toDiscernibleSignature(method)));
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException(
                        "Method annotated with @EventCriteriaBuilder must be static. Violating method: %s".formatted(
                                ReflectionUtils.toDiscernibleSignature(method)));
            }
            if (method.getParameterCount() == 0) {
                throw new IllegalArgumentException(
                        "Method annotated with @EventCriteriaBuilder must have at least one parameter which is the identifier. Violating method: %s".formatted(
                                ReflectionUtils.toDiscernibleSignature(method)));
            }
            this.method = ReflectionUtils.ensureAccessible(method);
            // Let's determine the identifier and create the argument suppliers
            this.identifierType = method.getParameterTypes()[0];
            int optionalParameterCount = method.getParameterCount() - 1;
            this.optionalArgumentSuppliers = new Object[optionalParameterCount];

            // And other arguments which can come from the configuration
            for (int i = 0; i < optionalParameterCount; i++) {
                Class<?> parameterType = method.getParameterTypes()[i + 1];

                // The whole configuration can be passed in
                if (parameterType == Configuration.class) {
                    optionalArgumentSuppliers[i] = configuration;
                    continue;
                }
                // Or a specific component of the configuration
                Optional<?> component = configuration.getOptionalComponent(parameterType);
                if (component.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Method annotated with @EventCriteriaBuilder declared a parameter which is not a component: %s. Violating method: %s".formatted(
                                    parameterType.getName(),
                                    ReflectionUtils.toDiscernibleSignature(method)
                            ));
                }
                optionalArgumentSuppliers[i] = component.get();
            }
        }

        private Object resolve(Object id) {
            Object[] args = new Object[method.getParameterCount()];
            args[0] = id;
            System.arraycopy(optionalArgumentSuppliers, 0, args, 1, optionalArgumentSuppliers.length);
            try {
                Object result = method.invoke(null, args);
                if (!(result instanceof EventCriteria)) {
                    throw new IllegalArgumentException(
                            "The @EventCriteriaBuilder method returned null. The method must return a non-null EventCriteria. Violating method: %s".formatted(
                                    ReflectionUtils.toDiscernibleSignature(method)));
                }
                return result;
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new IllegalArgumentException("Error invoking EventCriteriaBuilder method", e);
            }
        }

        public Class<?> getIdentifierType() {
            return identifierType;
        }

        public Method getMethod() {
            return method;
        }
    }

    @Nonnull
    @Override
    public EventCriteria resolve(@Nonnull Object id, @Nonnull ProcessingContext context) {
        Optional<Object> builderResult = builderMap
                .keySet()
                .stream()
                .filter(c -> c.isInstance(id))
                .findFirst()
                .map(builderMap::get)
                .map(m -> m.resolve(id));
        if (builderResult.isPresent()) {
            return (EventCriteria) builderResult.get();
        }
        String key = Objects.requireNonNullElseGet(tagKey, entityType::getSimpleName);
        return EventCriteria.havingTags(Tag.of(key, id.toString()));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("idType", idType.getName());
        descriptor.describeProperty("entityType", entityType.getName());
        descriptor.describeProperty("tagKey", tagKey);
        descriptor.describeProperty("criteriaBuilders", builderMap);
    }
}
